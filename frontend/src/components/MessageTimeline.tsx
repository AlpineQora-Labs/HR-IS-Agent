import { useMemo, useState } from 'react'
import { useCandidateMessages, useJobInterviews, useSendReminder } from '@/api/hooks'
import type { MessageRow, ReminderResult } from '@/api/types'
import '@/styles/messages.css'

/* The record of what was said to a candidate and what they said back: every
   text and email, newest first. What a message says, whether it went, and why
   it did not are the server's words — this only lays them out. */

type Show = 'all' | 'SMS' | 'EMAIL'

const dayOf = (iso: string) =>
  new Date(iso).toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' })

const timeOf = (iso: string) => new Date(iso).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })

function stateClass(status: string) {
  switch (status) {
    case 'SENT':
      return 'badge--ok'
    case 'QUEUED':
    case 'SENDING':
      return 'badge--info'
    case 'SUPPRESSED':
      return 'badge--warn'
    case 'FAILED':
      return 'badge--danger'
    default:
      return ''
  }
}

function what(m: MessageRow) {
  const kind = m.channel === 'SMS' ? 'Text' : 'Email'
  return m.direction === 'INBOUND' ? `${kind} from candidate` : `${kind} to candidate`
}

function Row({ m }: { m: MessageRow }) {
  const [open, setOpen] = useState(false)
  const long = m.channel === 'EMAIL' && m.text.split('\n').length > 3
  return (
    <li className={`msg ${m.direction === 'INBOUND' ? 'msg--in' : 'msg--out'}`}>
      <div className="msg__time">{timeOf(m.createdAt)}</div>
      <div style={{ minWidth: 0 }}>
        <div className="msg__head">
          <span className="msg__what">{what(m)}</span>
          {m.sentWhen ? <span className="msg__when">{m.sentWhen}</span> : null}
          <span className={`badge ${stateClass(m.status)} msg__state`}>{m.statusText}</span>
        </div>
        {m.subject ? <div className="msg__subject">{m.subject}</div> : null}
        {m.text ? (
          <div className={`msg__text${long && !open ? ' msg__text--clipped' : ''}`}>{m.text}</div>
        ) : null}
        {long ? (
          <button type="button" className="msg__more" onClick={() => setOpen((o) => !o)}>
            {open ? 'Show less' : 'Show the whole email'}
          </button>
        ) : null}
        {m.reason ? (
          <div className={`msg__reason${m.status === 'FAILED' ? ' msg__reason--failed' : ''}`}>{m.reason}</div>
        ) : null}
        {m.direction === 'OUTBOUND' && (m.templateName || m.address || m.parts) ? (
          <div className="msg__meta">
            {m.templateName ? <span>Wording: {m.templateName}</span> : null}
            {m.address ? <span>{m.address}</span> : null}
            {m.parts && m.parts > 1 ? <span>{m.parts} text parts</span> : null}
          </div>
        ) : null}
      </div>
    </li>
  )
}

export default function MessageTimeline({ candidateId, narrow = false, maxHeight }: {
  candidateId: string | undefined
  /** For drawers: the time sits above each message rather than beside it. */
  narrow?: boolean
  maxHeight?: number | string
}) {
  const { data, isLoading, isError, refetch } = useCandidateMessages(candidateId)
  const [show, setShow] = useState<Show>('all')

  const days = useMemo(() => {
    const rows = (data?.messages ?? []).filter((m) => show === 'all' || m.channel === show)
    const newestFirst = [...rows].reverse()
    const groups: { day: string; rows: MessageRow[] }[] = []
    for (const m of newestFirst) {
      const day = dayOf(m.createdAt)
      const last = groups[groups.length - 1]
      if (last && last.day === day) last.rows.push(m)
      else groups.push({ day, rows: [m] })
    }
    return groups
  }, [data, show])

  if (isLoading) return <div className="msgs__empty">Loading messages…</div>
  if (isError || !data) {
    return (
      <div className="msgs__empty">
        The messages could not be loaded.
        <div style={{ marginTop: 10 }}>
          <button className="btn btn--outline btn--sm" onClick={() => refetch()}>Retry</button>
        </div>
      </div>
    )
  }

  const total = data.messages.length
  return (
    <div className={`msgs${narrow ? ' msgs--narrow' : ''}`}>
      <div className="msgs__facts">
        <span>Texts to <b>{data.phone ?? 'no mobile number'}</b></span>
        <span>Email to <b>{data.email || 'no address'}</b></span>
      </div>
      {!data.canBeTexted && data.whyNot ? (
        <div className="msgs__note">Texts are held back. {data.whyNot}.</div>
      ) : null}
      {data.note ? <div className="msgs__note">{data.note}</div> : null}
      {!data.journeyOn ? (
        <div className="msgs__note">
          The candidate journey is switched off. Nothing is sent unprompted; a text from the candidate is still answered.
        </div>
      ) : null}

      {total > 0 ? (
        <div className="msgs__filter">
          <div className="segmented" role="group" aria-label="Show">
            {([['all', 'All'], ['SMS', 'Texts'], ['EMAIL', 'Emails']] as const).map(([key, label]) => (
              <button key={key} type="button" aria-pressed={show === key} onClick={() => setShow(key)}>
                {label}
              </button>
            ))}
          </div>
          <span className="eyebrow" style={{ margin: 0 }}>{total} on record</span>
        </div>
      ) : null}

      <div className="msgs__scroll" style={maxHeight ? { maxHeight } : undefined}>
        {days.map((g) => (
          <section key={g.day}>
            <div className="msgs__day">{g.day}</div>
            <ul className="msgs__list">
              {g.rows.map((m) => <Row key={m.id} m={m} />)}
            </ul>
          </section>
        ))}
        {total === 0 ? (
          <div className="msgs__empty">
            No texts or emails yet. They are recorded here as the candidate moves through the journey.
          </div>
        ) : days.length === 0 ? (
          <div className="msgs__empty">Nothing of this kind on record.</div>
        ) : null}
      </div>
    </div>
  )
}

/** Send a reminder for the candidate's next booked interview now, rather than at its hour. */
export function InterviewReminders({ applicationIds }: { applicationIds: string[] }) {
  const { interviews } = useJobInterviews(applicationIds)
  const remind = useSendReminder()
  const [outcome, setOutcome] = useState<string | null>(null)

  const next = useMemo(() => {
    const now = Date.now()
    return interviews
      .filter((i) => i.status === 'SCHEDULED' && i.scheduledAt && new Date(i.scheduledAt).getTime() > now)
      .sort((a, b) => new Date(a.scheduledAt).getTime() - new Date(b.scheduledAt).getTime())[0]
  }, [interviews])

  if (!next) return null

  const send = (which: '24h' | '1h') => {
    setOutcome(null)
    remind.mutate(
      { interviewId: next.id, which },
      {
        onSuccess: (r: ReminderResult) =>
          setOutcome(
            r.note ??
              r.sent
                .map((m) => `${m.channel === 'SMS' ? 'Text' : 'Email'}: ${m.statusText.toLowerCase()}${m.reason ? ` (${m.reason})` : ''}`)
                .join('. ') + '.',
          ),
        onError: (e) =>
          setOutcome(
            (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
              'The reminder could not be sent.',
          ),
      },
    )
  }

  return (
    <div className="remind">
      <span className="remind__label">Send a reminder now</span>
      <button className="btn btn--outline btn--sm" disabled={remind.isPending} onClick={() => send('24h')}>
        Day before
      </button>
      <button className="btn btn--outline btn--sm" disabled={remind.isPending} onClick={() => send('1h')}>
        Hour before
      </button>
      {outcome ? <div className="remind__note" role="status">{outcome}</div> : null}
    </div>
  )
}
