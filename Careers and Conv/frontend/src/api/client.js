// API client adapter — bridges the existing TalentBridge career UI to Olivia's backend.
// In dev the SPA calls same-origin /v1/* paths; Vite proxies them to Olivia on :8092.
// Olivia drives the whole candidate flow in-chat (name/email/phone, knockout screening,
// inline scheduling, application creation), so the legacy apply/self-schedule/identity
// branches are stubbed and kept only to preserve the imported export names.

// Per-conversation cursor: how many transcript messages have already been emitted (for deltas).
const cursors = new Map();

async function request(path, options = {}) {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try {
      const body = await res.json();
      if (body && body.message) message = body.message;
    } catch {
      /* non-JSON error body — keep the generic message */
    }
    throw new Error(message);
  }
  if (res.status === 204) return null;
  return res.json();
}

// ----- shape mappers -------------------------------------------------------

function mapJob(j) {
  return {
    id: j.id,
    jobRef: j.id,
    flowKey: j.id,
    title: j.title,
    location: j.location,
    workMode: j.workMode,
    department: j.department,
    skills: [],
  };
}

// Map an Olivia ChatState into the StepResponse shape the existing components expect.
function mapStep(chat) {
  const seen = cursors.get(chat.conversationId) || 0;
  const all = chat.messages || [];
  const fresh = all.slice(seen).filter((m) => m.sender === 'OLIVIA').map((m) => ({ text: m.body }));
  cursors.set(chat.conversationId, all.length);
  const completed = chat.status === 'COMPLETED';
  const opts = chat.options || [];
  return {
    sessionId: chat.conversationId,
    messages: fresh,
    awaiting: completed
      ? null
      : {
          nodeKey: chat.step || 'reply',
          text: null,
          quickReplies: opts,
          answerType: opts.length ? 'CHOICE' : 'TEXT',
          placeholder: 'Type your reply…',
        },
    trail: [],
    // resultStatus stays null on purpose — Olivia applies+schedules inline, so the
    // TalentBridge identity / self-schedule branch must NOT trigger.
    resultStatus: null,
    sessionStatus: completed ? 'COMPLETED' : 'ACTIVE',
  };
}

// ----- jobs ----------------------------------------------------------------

/** All open job postings (browse / no-match fallback). */
export async function getJobs() {
  return (await request('/v1/careers/jobs')).map(mapJob);
}

/** Deterministic job search by free-text query and optional location (client-side filter). */
export async function searchJobs(q, location) {
  const jobs = await request('/v1/careers/jobs');
  const ql = (q || '').toLowerCase();
  const ll = (location || '').toLowerCase();
  return jobs
    .filter((j) => {
      const hay = `${j.title || ''} ${j.department || ''} ${j.location || ''}`.toLowerCase();
      const matchesQuery = !q || hay.includes(ql);
      const matchesLocation = !location || (j.location || '').toLowerCase().includes(ll);
      return matchesQuery && matchesLocation;
    })
    .map(mapJob);
}

/** Published flows available to screen against (one per job posting). */
export async function getPublishedFlows() {
  const jobs = await request('/v1/careers/jobs');
  return jobs.map((j) => ({
    id: j.id,
    name: `${j.title} · ${j.location}`,
    version: 1,
    jobRef: j.id,
    flowKey: j.id,
  }));
}

// ----- chat session --------------------------------------------------------

/** Start an Olivia chat session for a job. Returns the first StepResponse. */
export async function startSession({ flowKey, jobRef }) {
  const chat = await request('/v1/chat/start', {
    method: 'POST',
    body: JSON.stringify({ jobId: jobRef || flowKey, channel: 'WEB_CHAT', language: 'en' }),
  });
  cursors.set(chat.conversationId, 0);
  return mapStep(chat);
}

/** Submit a candidate reply. Returns the next StepResponse. */
export async function answer(sessionId, _nodeKey, value) {
  const chat = await request(`/v1/chat/${sessionId}/reply`, {
    method: 'POST',
    body: JSON.stringify({ text: value }),
  });
  return mapStep(chat);
}

// ----- stubs (Olivia handles these inline) ---------------------------------

export const applyToPosition = async () => ({ status: 'APPLIED' });
export const proposeInterviewSlots = async () => [];
export const bookInterviewSlot = async () => null;
export const getIdentity = async () => ({ status: 'NOT_STARTED' });
export const identityConsent = async () => ({ status: 'NOT_STARTED' });
export const identityVerify = async () => ({ status: 'NOT_STARTED' });
export const identityDecline = async () => ({ status: 'IN_PERSON_REQUIRED' });
export const askFaq = async () => ({
  matched: false,
  answer:
    'For questions about pay, benefits, work mode, timeline, or sponsorship, our recruiting team will follow up after you apply.',
  topics: [],
});
