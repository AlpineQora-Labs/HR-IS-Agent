import { create } from 'zustand';
import {
  getPublishedFlows, startSession, answer, getJobs, searchJobs, applyToPosition,
  proposeInterviewSlots, bookInterviewSlot,
  getIdentity, identityConsent, identityVerify, identityDecline,
} from '../api/client.js';

// Make the typing indicator perceptible even when the local API responds instantly (§7: a typing
// indicator between turns). The bot turn waits at least this long before rendering.
const MIN_TYPING_MS = 450;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

let bubbleSeq = 0;
const bot = (text) => ({ id: `b${bubbleSeq++}`, who: 'bot', text });
const user = (text) => ({ id: `u${bubbleSeq++}`, who: 'user', text });

// Synthetic awaiting node so the composer shows a free-text box during job search (the search is a
// frontend deterministic flow, not a backend FSM node).
const SEARCH_NODE = {
  nodeKey: '__search__', text: null, quickReplies: [], answerType: 'TEXT',
  placeholder: 'Search by title, skill, or location…',
};

const SEARCH_GREETING =
  'Sure — tell me a job title, skill, or location (e.g. “Java in Charlotte” or “remote LLM”) ' +
  'and I’ll pull matching openings.';

export const useSession = create((set, get) => ({
  phase: 'landing', // 'landing' | 'chat'
  mode: 'screening', // 'search' | 'screening' (within chat)
  candidateName: '',
  demographics: { gender: '', raceEthnicity: '', ageBand: '' }, // voluntary EEO self-ID
  flows: [],
  currentFlow: null,
  sessionId: null,
  searchResults: [], // job postings from the latest deterministic search
  log: [], // [{ id, who, text }]
  awaiting: null, // NodeView (or SEARCH_NODE) the bot is waiting on
  trail: [], // [TrailItem]
  resultStatus: null,
  sessionStatus: null,
  typing: false,
  error: null,
  applied: false,
  application: null,
  applying: false,
  interviewSlots: null,
  interviewBooking: null,
  scheduling: false,
  identity: null, // { status, consentGiven, challengeCodeHint, attemptsRemaining, message }
  identityBusy: false,

  setCandidateName: (name) => set({ candidateName: name }),

  loadIdentity: async () => {
    const { sessionId } = get();
    if (!sessionId) return;
    try { set({ identity: await getIdentity(sessionId) }); } catch (e) { set({ error: e.message }); }
  },
  giveConsent: async () => {
    const { sessionId } = get();
    set({ identityBusy: true, error: null });
    try { set({ identity: await identityConsent(sessionId) }); } catch (e) { set({ error: e.message }); }
    finally { set({ identityBusy: false }); }
  },
  verifyIdentity: async (code) => {
    const { sessionId } = get();
    set({ identityBusy: true, error: null });
    try { set({ identity: await identityVerify(sessionId, code) }); } catch (e) { set({ error: e.message }); }
    finally { set({ identityBusy: false }); }
  },
  declineIdentity: async () => {
    const { sessionId } = get();
    set({ identityBusy: true, error: null });
    try { set({ identity: await identityDecline(sessionId) }); } catch (e) { set({ error: e.message }); }
    finally { set({ identityBusy: false }); }
  },
  setDemographic: (key, value) => set((s) => ({ demographics: { ...s.demographics, [key]: value } })),

  apply: async () => {
    const { sessionId } = get();
    if (!sessionId) return;
    set({ applying: true, error: null });
    try {
      const application = await applyToPosition(sessionId);
      set({ applied: true, application, applying: false });
    } catch (e) {
      set({ applying: false, error: e.message });
    }
  },

  proposeInterview: async () => {
    const { sessionId } = get();
    set({ scheduling: true, error: null });
    try {
      set({ interviewSlots: await proposeInterviewSlots(sessionId), scheduling: false });
    } catch (e) {
      set({ scheduling: false, error: e.message });
    }
  },

  bookInterview: async (slotId) => {
    const { sessionId } = get();
    set({ scheduling: true, error: null });
    try {
      const interviewBooking = await bookInterviewSlot(sessionId, slotId);
      set({ interviewBooking, interviewSlots: null, scheduling: false });
    } catch (e) {
      set({ scheduling: false, error: e.message });
    }
  },

  loadFlows: async () => {
    try {
      const flows = await getPublishedFlows();
      set({ flows, error: null });
    } catch (e) {
      set({ error: e.message });
    }
  },

  // ----- job search (deterministic, frontend-driven) -----------------------

  startSearch: () => set({
    phase: 'chat', mode: 'search', currentFlow: null, sessionId: null, searchResults: [],
    log: [bot(SEARCH_GREETING)], trail: [], awaiting: SEARCH_NODE,
    resultStatus: null, sessionStatus: null, typing: false, error: null,
    applied: false, application: null, applying: false,
    interviewSlots: null, interviewBooking: null, scheduling: false, identity: null, identityBusy: false,
  }),

  runSearch: async (query) => {
    try {
      const [results] = await Promise.all([searchJobs(query), sleep(MIN_TYPING_MS)]);
      if (results.length > 0) {
        const n = results.length;
        set((s) => ({
          log: [...s.log, bot(`Here ${n === 1 ? 'is' : 'are'} ${n} matching role${n > 1 ? 's' : ''}. ` +
            'Tap one to get screened — or refine your search.')],
          searchResults: results, typing: false,
        }));
      } else {
        const all = await getJobs();
        set((s) => ({
          log: [...s.log, bot(`I couldn’t find a match for “${query}”. Here are all our open roles — ` +
            'tap one to get screened, or try another search.')],
          searchResults: all, typing: false,
        }));
      }
    } catch (e) {
      set({ typing: false, error: e.message });
    }
  },

  pickJob: async (job) => {
    set((s) => ({
      log: [...s.log, user(`Apply — ${job.title} (${job.location})`)],
      searchResults: [], mode: 'screening', currentFlow: { name: `${job.title} · ${job.location}` },
      awaiting: null, typing: true, error: null, applied: false, application: null, applying: false,
    }));
    try {
      const [step] = await Promise.all([
        startSession({ flowKey: job.flowKey, jobRef: job.jobRef, candidateName: get().candidateName || 'Candidate', demographics: get().demographics }),
        sleep(MIN_TYPING_MS),
      ]);
      set({ sessionId: step.sessionId });
      get().ingest(step);
    } catch (e) {
      set({ typing: false, error: e.message });
    }
  },

  // ----- screening ---------------------------------------------------------

  ingest: (step) => {
    const bubbles = [];
    (step.messages || []).forEach((m) => {
      if (m.text) bubbles.push(bot(m.text));
    });
    if (step.awaiting && step.awaiting.text) bubbles.push(bot(step.awaiting.text));
    set((s) => ({
      log: [...s.log, ...bubbles],
      awaiting: step.awaiting || null,
      trail: step.trail || [],
      resultStatus: step.resultStatus || null,
      sessionStatus: step.sessionStatus || null,
      applied: step.sessionStatus === 'COMPLETED' ? true : s.applied,
      typing: false,
    }));
  },

  begin: async (flow) => {
    set({
      phase: 'chat', mode: 'screening', currentFlow: flow, sessionId: null, log: [], trail: [],
      searchResults: [], awaiting: null, resultStatus: null, sessionStatus: null, typing: true, error: null,
      applied: false, application: null, applying: false,
    interviewSlots: null, interviewBooking: null, scheduling: false, identity: null, identityBusy: false,
    });
    try {
      const [step] = await Promise.all([
        startSession({ flowKey: flow.flowKey, candidateName: get().candidateName || 'Candidate', demographics: get().demographics }),
        sleep(MIN_TYPING_MS),
      ]);
      set({ sessionId: step.sessionId });
      get().ingest(step);
    } catch (e) {
      set({ typing: false, error: e.message });
    }
  },

  // Unified composer submit: a search query in search mode, a screening answer otherwise.
  send: async (value) => {
    if (value === undefined || value === null || value === '') return;
    const { mode, awaiting } = get();
    set((s) => ({ log: [...s.log, user(String(value))], typing: true, error: null }));
    if (mode === 'search') {
      await get().runSearch(String(value));
      return;
    }
    if (!awaiting) return;
    set({ awaiting: null });
    try {
      const [step] = await Promise.all([
        answer(get().sessionId, awaiting.nodeKey, String(value)),
        sleep(MIN_TYPING_MS),
      ]);
      get().ingest(step);
    } catch (e) {
      set({ typing: false, error: e.message });
    }
  },

  reset: () => set({
    phase: 'landing', mode: 'screening', currentFlow: null, sessionId: null, log: [], trail: [],
    searchResults: [], awaiting: null, resultStatus: null, sessionStatus: null, typing: false, error: null,
    applied: false, application: null, applying: false,
    interviewSlots: null, interviewBooking: null, scheduling: false, identity: null, identityBusy: false,
  }),
}));
