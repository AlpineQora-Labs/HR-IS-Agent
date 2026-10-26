import { useEffect, useRef, useState, type KeyboardEvent, type ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAskAssistant } from '@/api/hooks'
import type { AssistantIntent, AssistantReply, AssistantTurn } from '@/api/types'
import { PORTAL_ASSISTANT } from '@/lib/brand'
import { useDrawerOpen, useFocusOnScreen, type Focus } from '@/lib/assistantFocus'
import { IconChat, IconSend } from './icons'
import { DRAWER_WIDTH } from './SlideOver'

/* The portal assistant: a chat on every recruiter screen that briefs the recruiter on what they
   have open, and explains why things are as they are. It only reads. The server builds the
   briefing and words the answer; this component says what is open, sends the question, and draws
   the answer. The chat lives here, in the browser, and is sent back with each question. */

type Msg =
  | { id: number; who: 'recruiter'; text: string; ctx: string }
  | { id: number; who: 'assistant'; text: string; ctx: string; links: AssistantReply['links']; byModel: boolean }
  | { id: number; who: 'note'; text: string; ctx: string }
/** A message before it is given its id: each kind keeps its own fields. */
type WithoutId<T> = T extends unknown ? Omit<T, 'id'> : never
type NewMsg = WithoutId<Msg>

const UUID = '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'
const ASKS: { intent: AssistantIntent; label: string }[] = [
  { intent: 'BRIEF', label: 'Brief me' },
  { intent: 'WHY', label: 'Explain why' },
]
/** Earlier turns about the same thing sent with a question; the server keeps fewer still. */
const HISTORY = 12
const PANEL_WIDTH = 400
/** The launcher's place: up from the bottom and in from the right edge, clear of the page's own corner. */
const EDGE = 40
const LAUNCHER = 56

const rank = (f: Focus | null) => (!f ? 0 : f.applicationId ? 3 : f.candidateId ? 2 : f.jobId ? 1 : 0)
const keyOf = (f: Focus | null) => (rank(f) === 0 ? 'day' : `${f?.applicationId ?? ''}|${f?.candidateId ?? ''}|${f?.jobId ?? ''}`)

/** What the address alone says is open: a candidate's record, a job, or a job's pipeline. */
function fromRoute(pathname: string, search: string): Focus | null {
  const c = pathname.match(new RegExp(`^/candidates/(${UUID})`, 'i'))
  if (c) return { candidateId: c[1] }
  const j = pathname.match(new RegExp(`^/jobs/(${UUID})`, 'i'))
  if (j) return { jobId: j[1] }
  if (pathname === '/pipeline') {
    const job = new URLSearchParams(search).get('job')
    if (job) return { jobId: job }
  }
  return null
}

const why = (e: unknown) =>
  (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'The assistant could not answer just now. Try again.'

export default function PortalAssistant() {
  const [open, setOpen] = useState(false)
  const [msgs, setMsgs] = useState<Msg[]>([])
  const [draft, setDraft] = useState('')
  const [about, setAbout] = useState<Record<string, string>>({})
  const nextId = useRef(1)
  const bodyRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const ask = useAskAssistant()
  const navigate = useNavigate()
  const location = useLocation()
  const drawerOpen = useDrawerOpen()

  // What is open: a drawer's application or candidate beats the page's own, which beats nothing (the day).
  const onScreen = useFocusOnScreen()
  const routed = fromRoute(location.pathname, location.search)
  const focus = rank(onScreen) >= rank(routed) ? onScreen : routed
  const ctx = keyOf(focus)
  const label = about[ctx] ?? focus?.label ?? (rank(focus) === 0 ? 'Interviews across every job' : focus?.applicationId || focus?.candidateId ? 'This candidate' : 'This job')

  const push = (m: NewMsg) => setMsgs((all) => [...all, { ...m, id: nextId.current++ } as Msg])

  // When the recruiter opens something else, say so in the chat: answers from here on are about it.
  const lastCtx = useRef(ctx)
  useEffect(() => {
    if (lastCtx.current === ctx) return
    lastCtx.current = ctx
    setMsgs((all) => (all.length === 0 ? all : [...all, { id: nextId.current++, who: 'note', text: `Now about: ${label}`, ctx }]))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ctx])

  useEffect(() => {
    bodyRef.current?.scrollTo({ top: bodyRef.current.scrollHeight, behavior: 'smooth' })
  }, [msgs, ask.isPending])
  useEffect(() => {
    if (open) inputRef.current?.focus()
  }, [open])

  const send = (intent: AssistantIntent, question?: string) => {
    if (ask.isPending) return
    // Only turns about this same thing go back: names in a chat about someone else are not the server's to hide.
    const history: AssistantTurn[] = msgs
      .filter((m) => m.ctx === ctx && m.who !== 'note')
      .map((m) => ({ from: m.who === 'recruiter' ? 'RECRUITER' : 'ASSISTANT', text: m.text.slice(0, 4000) }) as AssistantTurn)
      .slice(-HISTORY)
    const said = intent === 'ASK' ? question ?? '' : ASKS.find((a) => a.intent === intent)?.label ?? intent
    const askedFor = ctx
    push({ who: 'recruiter', text: said, ctx: askedFor })
    ask.mutate(
      {
        intent,
        question: intent === 'ASK' ? question : undefined,
        context: { candidateId: focus?.candidateId, applicationId: focus?.applicationId, jobId: focus?.jobId },
        history,
      },
      {
        onSuccess: (r) => {
          setAbout((a) => ({ ...a, [askedFor]: r.about }))
          push({ who: 'assistant', text: r.answer, ctx: askedFor, links: r.links, byModel: r.byModel })
        },
        onError: (e) => push({ who: 'note', text: why(e), ctx: askedFor }),
      },
    )
  }

  const submit = () => {
    const q = draft.trim()
    if (!q) return
    setDraft('')
    send('ASK', q.slice(0, 500))
  }

  const onPanelKey = (e: KeyboardEvent) => {
    // Escape closes the chat, not the drawer underneath it.
    if (e.key === 'Escape') {
      e.stopPropagation()
      setOpen(false)
    }
  }

  // Step out of an open drawer's way: it sits 12 from the right edge.
  const beside = 12 + DRAWER_WIDTH + 16
  const right = drawerOpen ? `min(${beside}px, calc(100vw - ${LAUNCHER + 24}px))` : `${EDGE}px`
  const panelRight = drawerOpen ? `min(${beside}px, calc(100vw - ${PANEL_WIDTH + 16}px))` : `${EDGE}px`

  return (
    <>
      {open && (
        <section
          role="dialog"
          aria-label={PORTAL_ASSISTANT}
          onKeyDown={onPanelKey}
          style={{
            position: 'fixed', right: panelRight, bottom: EDGE + LAUNCHER + 12, width: PANEL_WIDTH, maxWidth: 'calc(100vw - 32px)',
            height: 'min(560px, calc(100vh - 190px))', zIndex: 62, display: 'flex', flexDirection: 'column',
            background: 'var(--app-panel)', borderRadius: 16, overflow: 'hidden',
            boxShadow: '0 18px 48px rgba(16,22,38,0.24), 0 4px 14px rgba(16,22,38,0.12)',
          }}
        >
          <header style={{ background: 'var(--bofa-navy)', color: '#fff', padding: '14px 16px', display: 'flex', alignItems: 'flex-start', gap: 10 }}>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontWeight: 600, fontSize: 15 }}>{PORTAL_ASSISTANT}</div>
              <div style={{ fontSize: 12.5, opacity: 0.85, marginTop: 2, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={label}>
                About: {label}
              </div>
            </div>
            {msgs.length > 0 && (
              <button type="button" onClick={() => setMsgs([])} style={headButton} aria-label="Clear the chat">
                Clear
              </button>
            )}
            <button type="button" onClick={() => setOpen(false)} style={{ ...headButton, fontSize: 18, lineHeight: 1, padding: '2px 8px' }} aria-label="Close the assistant">
              ×
            </button>
          </header>

          <div ref={bodyRef} aria-live="polite" style={{ flex: 1, overflowY: 'auto', padding: '14px 14px 6px', display: 'flex', flexDirection: 'column', gap: 10, background: 'var(--app-sunken)' }}>
            {msgs.length === 0 && (
              <p style={{ margin: '4px 2px 0', color: 'var(--ink-2)', fontSize: 13.5, lineHeight: 1.55 }}>
                Ask about what you have open. Brief me says where things stand; Explain why says what is waiting and why. Answers come from the portal's records, and nothing is changed.
              </p>
            )}
            {msgs.map((m) =>
              m.who === 'note' ? (
                <div key={m.id} style={{ alignSelf: 'center', fontSize: 12, color: 'var(--ink-3)', textAlign: 'center', padding: '2px 8px' }}>
                  {m.text}
                </div>
              ) : m.who === 'recruiter' ? (
                <div key={m.id} style={{ ...bubble, alignSelf: 'flex-end', background: 'var(--bofa-navy)', color: '#fff', borderBottomRightRadius: 5 }}>
                  {m.text}
                </div>
              ) : (
                <div key={m.id} style={{ ...bubble, alignSelf: 'flex-start', background: 'var(--app-panel)', color: 'var(--ink-1)', border: '1px solid var(--line)', borderBottomLeftRadius: 5 }}>
                  <AnswerText text={m.text} />
                  {m.links.length > 0 && (
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 10 }}>
                      {m.links.map((l) => (
                        <button key={l.path + l.label} type="button" className="btn btn--outline btn--sm" onClick={() => navigate(l.path)}>
                          {l.label}
                        </button>
                      ))}
                    </div>
                  )}
                  <div style={{ marginTop: 8, fontSize: 11.5, color: 'var(--ink-3)' }}>
                    {m.byModel ? 'Worded by AI from the records' : 'Laid out from the records'}
                  </div>
                </div>
              ),
            )}
            {ask.isPending && (
              <div style={{ ...bubble, alignSelf: 'flex-start', background: 'var(--app-panel)', color: 'var(--ink-3)', border: '1px solid var(--line)' }}>
                Reading the records
              </div>
            )}
          </div>

          <div style={{ padding: '10px 12px 12px', borderTop: '1px solid var(--line)', background: 'var(--app-panel)' }}>
            <div style={{ display: 'flex', gap: 6, marginBottom: 8 }}>
              {ASKS.map((a) => (
                <button key={a.intent} type="button" className="btn btn--outline btn--sm" disabled={ask.isPending} onClick={() => send(a.intent)}>
                  {a.label}
                </button>
              ))}
            </div>
            <form
              onSubmit={(e) => {
                e.preventDefault()
                submit()
              }}
              style={{ display: 'flex', gap: 6 }}
            >
              <input
                ref={inputRef}
                className="input"
                style={{ flex: 1, minWidth: 0 }}
                value={draft}
                maxLength={500}
                onChange={(e) => setDraft(e.target.value)}
                placeholder="Ask about this page"
                aria-label="Your question"
              />
              <button type="submit" className="btn btn--primary btn--icon" disabled={ask.isPending || !draft.trim()} aria-label="Send">
                <IconSend className="ic" />
              </button>
            </form>
          </div>
        </section>
      )}

      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        aria-label={open ? 'Close the assistant' : `Open the ${PORTAL_ASSISTANT.toLowerCase()}`}
        title="Ask about this page"
        style={{
          position: 'fixed', right, bottom: EDGE, zIndex: 62, width: LAUNCHER, height: LAUNCHER, borderRadius: '50%',
          border: 0, background: 'var(--bofa-red)', color: '#fff', display: 'grid', placeItems: 'center',
          boxShadow: '0 8px 24px rgba(16,22,38,0.28)', cursor: 'pointer',
        }}
      >
        <IconChat className="ic" style={{ width: 22, height: 22 }} />
      </button>
    </>
  )
}

const headButton = {
  background: 'transparent', border: '1px solid rgba(255,255,255,0.35)', color: '#fff',
  borderRadius: 8, fontSize: 12, padding: '3px 8px', cursor: 'pointer',
} as const

const bubble = { maxWidth: '88%', padding: '10px 13px', borderRadius: 14, fontSize: 13.5, lineHeight: 1.5 } as const

/** The answer's plain text: lines starting "- " become a list; a line just before a list is its heading. */
function AnswerText({ text }: { text: string }) {
  type Block = { kind: 'p'; text: string; heading: boolean } | { kind: 'ul'; items: string[] }
  const blocks: Block[] = []
  for (const raw of text.split('\n')) {
    const line = raw.trim()
    if (!line) continue
    const last = blocks[blocks.length - 1]
    if (line.startsWith('- ')) {
      if (last?.kind === 'ul') last.items.push(line.slice(2))
      else {
        if (last?.kind === 'p') last.heading = true
        blocks.push({ kind: 'ul', items: [line.slice(2)] })
      }
    } else {
      blocks.push({ kind: 'p', text: line, heading: false })
    }
  }
  const out: ReactNode[] = blocks.map((b, i) =>
    b.kind === 'ul' ? (
      <ul key={i} style={{ margin: '4px 0 8px', paddingLeft: 18 }}>
        {b.items.map((it, j) => (
          <li key={j} style={{ margin: '2px 0' }}>{it}</li>
        ))}
      </ul>
    ) : (
      <p key={i} style={{ margin: i === 0 ? '0 0 6px' : '8px 0 4px', fontWeight: b.heading && i > 0 ? 600 : undefined }}>
        {b.text}
      </p>
    ),
  )
  return <>{out}</>
}
