import { useEffect, useMemo, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api } from '@/api/client'
import { DISPLAY_TYPES, effectiveValue, fieldVisible, flattenIntake, normalizeIntake, resolveField, type IntakeField, type IntakeForm } from './admin/FormBuilder'
import { IntakeInput, answerText, type Answer } from './admin/IntakeInput'

/* The public event-registration surface — what the QR code on the booth banner
   opens. Shell-less (no recruiter chrome). One form definition
   (EVENT_REGISTRATION), two renderers side by side:
   - Form: the classic web form, if/then rules mirrored live
   - Aria: the same questions asked conversationally, rules driving follow-ups
   Either path lands on the same authoritative backend service. */

interface EventInfo {
  id: string
  name: string
  type: string
  location: string
  startsAt: string
  endsAt: string | null
  timezone: string
  /** OPEN: there is a seat. WAITLIST: full, taking names. FULL / CLOSED: not taking registrations. */
  registration: 'OPEN' | 'WAITLIST' | 'FULL' | 'CLOSED'
  /** Seats still free; null when the event has no limit. */
  seatsLeft: number | null
  registrationClosesAt: string | null
  /** The words the student agrees to before their details are kept. */
  consentText: string
}

/** Who can still register, in a line: "12 seats left · register by Oct 12". */
const seatsLine = (e: EventInfo) => {
  const by = e.registrationClosesAt
    ? `registration closes ${new Date(e.registrationClosesAt).toLocaleString('en-US', { timeZone: e.timezone || 'America/New_York', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit', timeZoneName: 'short' })}`
    : null
  const seats = e.registration === 'WAITLIST' ? 'Every seat is taken. You can join the waitlist' : e.seatsLeft !== null ? `${e.seatsLeft} ${e.seatsLeft === 1 ? 'seat' : 'seats'} left` : null
  return [seats, by].filter(Boolean).join(' · ')
}

interface ChatTurn {
  conversationId: string
  done: boolean
  messages: { sender: string; body: string }[]
  options: string[]
}

/** The event's schedule in ITS OWN timezone — "Thu, Oct 14 · 5:00–7:00 PM EDT". */
const when = (e: { startsAt: string; endsAt: string | null; timezone: string }) => {
  const tz = e.timezone || 'America/New_York'
  const day = new Date(e.startsAt).toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', timeZone: tz })
  const t = (iso: string, zone: boolean) =>
    new Date(iso).toLocaleTimeString('en-US', { timeZone: tz, hour: 'numeric', minute: '2-digit', ...(zone ? { timeZoneName: 'short' } : {}) })
  return `${day} · ${e.endsAt ? `${t(e.startsAt, false)}–${t(e.endsAt, true)}` : t(e.startsAt, true)}`
}

export default function EventRegisterPage() {
  const { eventId } = useParams<{ eventId: string }>()
  const [event, setEvent] = useState<EventInfo | null>(null)
  const [form, setForm] = useState<IntakeForm | null>(null)
  const [mode, setMode] = useState<'form' | 'chat'>('form')

  useEffect(() => {
    if (!eventId) return
    api.get<EventInfo>(`/events/${eventId}/public`).then((r) => setEvent(r.data)).catch(() => setEvent(null))
    // The event's own form (or the default), with its School question listing the schools on record.
    api.get<{ schema: string }>(`/events/${eventId}/registration-form`).then((r) => {
      try {
        setForm(normalizeIntake(JSON.parse(r.data.schema)))
      } catch {
        setForm(null)
      }
    })
  }, [eventId])

  return (
    <div style={{ minHeight: '100vh', background: 'var(--app-bg)', display: 'flex', flexDirection: 'column' }}>
      <header style={{ background: 'var(--bofa-navy, #012169)', color: '#fff', padding: '16px 22px' }}>
        <div style={{ fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: 16 }}>Bank of America · Campus</div>
        {event && (
          <div style={{ marginTop: 6, fontSize: 13, opacity: 0.9 }}>
            {event.name} · {when(event)} · {event.location}
          </div>
        )}
      </header>

      <main style={{ flex: 1, display: 'flex', justifyContent: 'center', padding: '26px 16px' }}>
        <div style={{ width: '100%', maxWidth: 620 }}>
          {event && (event.registration === 'CLOSED' || event.registration === 'FULL') ? (
            <div className="card" style={{ padding: '30px 24px', textAlign: 'center' }}>
              <div style={{ fontFamily: 'var(--font-display)', fontSize: 19, margin: '8px 0 4px' }}>
                {event.registration === 'FULL' ? 'This event is full' : 'Registration has closed'}
              </div>
              <p style={{ fontSize: 13.5, color: 'var(--ink-3)', margin: '0 0 14px' }}>
                {event.registration === 'FULL'
                  ? 'Every seat for this event has been taken.'
                  : 'This event is no longer taking registrations.'}{' '}
                Have a look at our other events.
              </p>
              <a className="btn btn--outline btn--sm" href="/careers/events">See all events</a>
            </div>
          ) : (
            <>
              <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 24, margin: '0 0 4px' }}>
                {event?.registration === 'WAITLIST' ? 'Join the waitlist' : 'Register for this event'}
              </h1>
              <p style={{ margin: '0 0 14px', fontSize: 13.5, color: 'var(--ink-4)' }}>
                Fill in the form, or use the chat. The questions are the same.
              </p>
              {event && seatsLine(event) && (
                <p style={{ margin: '0 0 14px', fontSize: 13, color: 'var(--ink-2)', fontWeight: 600 }}>{seatsLine(event)}.</p>
              )}

              <div className="segmented" role="group" aria-label="Mode" style={{ marginBottom: 16 }}>
                <button aria-pressed={mode === 'form'} onClick={() => setMode('form')}>Form</button>
                <button aria-pressed={mode === 'chat'} onClick={() => setMode('chat')}>Chat</button>
              </div>

              {mode === 'form'
                ? (eventId && form && event ? <RegisterForm eventId={eventId} form={form} event={event} /> : <p className="muted">Loading…</p>)
                : (eventId ? <AriaRegisterChat eventId={eventId} /> : null)}
            </>
          )}
        </div>
      </main>
    </div>
  )
}

/* ── Web-form renderer: rules mirrored client-side, enforced server-side ──── */
function RegisterForm({ eventId, form, event }: { eventId: string; form: IntakeForm; event: EventInfo }) {
  const [answers, setAnswers] = useState<Record<string, Answer>>({})
  const [busy, setBusy] = useState(false)
  // Nothing is sent, and nothing is kept, until the student has agreed.
  const [consent, setConsent] = useState(false)
  const [outcome, setOutcome] = useState<{ kind: 'ok' | 'wait' | 'dup' | 'gone' | 'err'; message: string } | null>(null)

  // An answer counts as the form counts it: one its question no longer offers (the country was
  // changed after the school was picked) is no answer. Rules can also make a question required.
  const raw = (id: string) => answerText(answers[id])
  const val = (id: string) => effectiveValue(form, id, raw)
  const isVisible = (f: IntakeField) => fieldVisible(form, f.id, val)
  const visibleFields = flattenIntake(form).filter(isVisible).map((f) => resolveField(form, f, val))
  const valid = visibleFields.every((f) => DISPLAY_TYPES.includes(f.type) || !f.required || val(f.id).trim() !== '')

  const submit = () => {
    setBusy(true)
    const payload = visibleFields
      .filter((f) => !DISPLAY_TYPES.includes(f.type))
      .map((f) => ({ fieldId: f.id, label: f.label || 'Untitled', value: val(f.id) }))
      .filter((a) => a.value.trim() !== '')
    api.post<{ status: string; waitlistPosition: number | null }>(`/events/${eventId}/register-form`, { answers: JSON.stringify(payload), consent })
      .then((r) =>
        setOutcome(
          r.data.status === 'WAITLISTED'
            ? { kind: 'wait', message: `Every seat is taken for now, so you are number ${r.data.waitlistPosition ?? 1} on the waitlist. If a seat comes free it is yours, and our team will be in touch.` }
            : { kind: 'ok', message: "You're registered. Come and say hello at the booth." },
        ),
      )
      .catch((e: { response?: { status?: number; data?: { message?: string } } }) => {
        if (e.response?.status === 410) setOutcome({ kind: 'gone', message: `${e.response?.data?.message ?? 'Registration has closed'}. Nothing was saved.` })
        else if (e.response?.status === 409) setOutcome({ kind: 'dup', message: "You're already registered for this event — no need to do anything else." })
        else setOutcome({ kind: 'err', message: e.response?.data?.message ?? 'Something went wrong — please try again.' })
      })
      .finally(() => setBusy(false))
  }

  if (outcome && outcome.kind !== 'err') {
    return (
      <div className="card" style={{ padding: '30px 24px', textAlign: 'center' }}>
        <div style={{ fontFamily: 'var(--font-display)', fontSize: 19, margin: '8px 0 4px' }}>
          {outcome.kind === 'ok' ? 'See you there' : outcome.kind === 'wait' ? "You're on the waitlist" : outcome.kind === 'gone' ? 'Sorry, you just missed it' : 'Already on the list'}
        </div>
        <p style={{ fontSize: 13.5, color: 'var(--ink-3)', margin: 0 }}>{outcome.message}</p>
      </div>
    )
  }

  return (
    <div className="card" style={{ padding: '20px 22px' }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        {form.rows.map((row) => {
          const shown = row.slots.filter((f): f is IntakeField => f !== null && isVisible(f))
          if (row.slots.some((f) => f !== null) && shown.length === 0) return null
          return (
            <div key={row.id} style={{ display: 'grid', gridTemplateColumns: `repeat(${Math.max(shown.length, 1)}, minmax(0, 1fr))`, gap: 12 }}>
              {shown.map((f) => (
                <IntakeInput key={f.id} f={resolveField(form, f, val)} value={val(f.id) === '' && raw(f.id) !== '' ? undefined : answers[f.id]} onChange={(v) => setAnswers((prev) => ({ ...prev, [f.id]: v }))} />
              ))}
            </div>
          )
        })}
      </div>
      {outcome?.kind === 'err' && (
        <p style={{ fontSize: 12.5, color: 'var(--bofa-red, #c41230)', margin: '12px 0 0' }}>{outcome.message}</p>
      )}
      <label style={{ display: 'flex', alignItems: 'flex-start', gap: 10, marginTop: 16, fontSize: 12.5, color: 'var(--ink-2)', lineHeight: 1.5 }}>
        <input type="checkbox" checked={consent} style={{ marginTop: 3 }} onChange={(e) => setConsent(e.target.checked)} />
        <span>{event.consentText}</span>
      </label>
      <button className="btn btn--primary" style={{ marginTop: 14, width: '100%' }} disabled={!valid || !consent || busy} onClick={submit}>
        {busy ? 'Registering…' : event.registration === 'WAITLIST' ? 'Join the waitlist' : 'Register'}
      </button>
    </div>
  )
}

/* ── Aria renderer: same definition, same rules, conversational ───────────── */
function AriaRegisterChat({ eventId }: { eventId: string }) {
  const [turn, setTurn] = useState<ChatTurn | null>(null)
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const scrollRef = useRef<HTMLDivElement>(null)
  const started = useRef(false)

  useEffect(() => {
    if (started.current) return
    started.current = true
    api.post<ChatTurn>(`/aria/events/${eventId}/registration-chat`).then((r) => setTurn(r.data))
  }, [eventId])

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight })
  }, [turn])

  const send = (message: string) => {
    if (!turn || !message.trim() || busy) return
    setBusy(true)
    setText('')
    api.post<ChatTurn>(`/aria/registration-chat/${turn.conversationId}`, { text: message })
      .then((r) => setTurn(r.data))
      .finally(() => setBusy(false))
  }

  const bubbles = useMemo(() => turn?.messages ?? [], [turn])

  return (
    <div className="card" style={{ display: 'flex', flexDirection: 'column', height: 480, overflow: 'hidden' }}>
      <div ref={scrollRef} style={{ flex: 1, overflowY: 'auto', padding: '16px 16px 8px', display: 'flex', flexDirection: 'column', gap: 10 }}>
        {bubbles.map((m, i) => (
          <div key={i} style={{ display: 'flex', justifyContent: m.sender === 'ARIA' ? 'flex-start' : 'flex-end' }}>
            <div style={{
              maxWidth: '82%', padding: '9px 13px', fontSize: 13.5, lineHeight: 1.5, whiteSpace: 'pre-line',
              borderRadius: m.sender === 'ARIA' ? '4px 14px 14px 14px' : '14px 4px 14px 14px',
              background: m.sender === 'ARIA' ? 'var(--app-sunken)' : 'var(--bofa-navy, #012169)',
              color: m.sender === 'ARIA' ? 'var(--ink-1)' : '#fff',
            }}>
              {m.body}
            </div>
          </div>
        ))}
      </div>
      {turn && turn.options.length > 0 && !turn.done && (
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', padding: '6px 16px' }}>
          {turn.options.map((o) => (
            <button key={o} className="btn btn--outline btn--sm" style={{ fontSize: 12 }} disabled={busy} onClick={() => send(o)}>
              {o}
            </button>
          ))}
        </div>
      )}
      {!turn?.done && (
        <div style={{ display: 'flex', gap: 8, padding: '10px 16px 14px' }}>
          <input
            className="input"
            placeholder="Type your answer…"
            value={text}
            disabled={busy || !turn}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && send(text)}
          />
          <button className="btn btn--primary" disabled={busy || !text.trim()} onClick={() => send(text)}>Send</button>
        </div>
      )}
    </div>
  )
}
