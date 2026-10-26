import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import SlideOver, { useSlideOverClose } from '@/components/SlideOver'
import { useBeginInterview, useBookInterviewTime, useInterviewOpenings, useProposeTimes, useRescheduleInterview, useTransitionInterview } from '@/api/hooks'
import type { AttentionItem, InterviewerLoad } from '@/api/types'
import { initials } from '@/lib/format'
import { useAssistantFocus } from '@/lib/assistantFocus'

/* The scheduling drawer of the interview desk: it slides in from the right when a candidate is
   to be scheduled. In it: pick a candidate who has no time yet, choose who
   interviews them, see which days the panel is free, and offer those times. The candidate picks
   the time; this panel decides who is on the panel and shows what is open. */

const DOW = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
/** A day as the server names it: 2026-10-15. */
const key = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const clock = (iso: string) => new Date(iso).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })

/** The drawer: slides in over the desk, and closes with its X, Escape or a click outside. */
export default function InterviewScheduleDrawer(props: PanelProps & { fresh: Fresh | null; onClose: () => void }) {
  // The portal assistant answers about the candidate this drawer is on.
  const shown = props.fresh ?? props.others.find((w) => w.interviewId === props.picked)
    ?? props.waiting.find((w) => w.interviewId === props.picked) ?? props.waiting[0] ?? null
  useAssistantFocus(shown ? { applicationId: shown.applicationId, label: shown.jobTitle ? `${shown.candidateName}, ${shown.jobTitle}` : shown.candidateName } : null)
  return (
    <SlideOver label="Schedule an interview" onClose={props.onClose} head={false}>
      {props.fresh ? <FreshPanel fresh={props.fresh} onPick={props.onPick} /> : <Panel {...props} />}
    </SlideOver>
  )
}

/** A candidate who applied but has no interview yet. */
export type Fresh = { applicationId: string; candidateId: string; jobId: string; candidateName: string; jobTitle: string; stage: string }

function FreshPanel({ fresh, onPick }: { fresh: Fresh; onPick: (interviewId: string) => void }) {
  const close = useSlideOverClose()
  const begin = useBeginInterview()
  return (
    <aside className="dash-panel sched sched--drawer">
      <section>
        <div className="sched__head">
          <div className="dash-panel__title">Interview</div>
          <button type="button" className="sched__close" aria-label="Close" onClick={() => close?.()}>×</button>
        </div>
        <div className="dash-panel__sub">No interview has been started</div>
        <div className="sched__who">
          <span className="sched__avatar">{initials(fresh.candidateName)}</span>
          <span style={{ minWidth: 0 }}>
            <strong>{fresh.candidateName}</strong>
            <small>{fresh.jobTitle}</small>
          </span>
        </div>
        <div className="sched__links">
          <Link to={`/candidates/${fresh.candidateId}`}>Candidate record →</Link>
          <Link to={`/jobs/${fresh.jobId}`}>Job →</Link>
        </div>
      </section>
      <section>
        <div className="sched__label">Where they are</div>
        <div className="sched__when">{fresh.stage.replace(/_/g, ' ').toLowerCase().replace(/^./, (c) => c.toUpperCase())}</div>
      </section>
      <section>
        <div className="sched__label">Start an interview</div>
        <button type="button" className="dash-panel__btn sched__go" style={{ marginTop: 0 }} disabled={begin.isPending}
          onClick={() => begin.mutate(fresh.applicationId, { onSuccess: (iv) => onPick((iv as { id: string }).id) })}>
          {begin.isPending ? 'Starting…' : `Invite ${fresh.candidateName.split(' ')[0]} to interview`}
        </button>
        {begin.isError && (
          <p className="sched__note" style={{ color: 'var(--bofa-red)' }}>
            {(begin.error as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'The interview could not be started.'}
          </p>
        )}
        <p className="sched__note">
          The candidate is invited and offered times with the job's hiring team. You can then change the interviewers and pick a time here. Sending is simulated in the prototype.
        </p>
      </section>
    </aside>
  )
}

type PanelProps = {
  /** Candidates invited to interview who have not picked a time. */
  waiting: AttentionItem[]
  /** Every other interview on the desk (needs an outcome, today, no-show): opened to record what happened. */
  others: AttentionItem[]
  load: InterviewerLoad[]
  /** The candidate and job behind an application, for the links to their pages. */
  appOf: (applicationId: string) => { candidateId: string; jobId: string } | undefined
  picked: string | null
  onPick: (interviewId: string) => void
}

function Panel({ waiting, others, load, appOf, picked, onPick }: PanelProps) {
  const close = useSlideOverClose()
  const propose = useProposeTimes()
  const transition = useTransitionInterview()
  const reschedule = useRescheduleInterview()
  // An interview that already has a time: the drawer shows it and records what happened, instead of scheduling.
  const held = others.find((w) => w.interviewId === picked) ?? null
  const item = held ?? waiting.find((w) => w.interviewId === picked) ?? waiting[0] ?? null
  const app = item ? appOf(item.applicationId) : undefined
  const book = useBookInterviewTime()

  // The panel: whoever is on the interview already; others are found by name and added.
  const [panel, setPanel] = useState<string[]>([])
  const [find, setFind] = useState('')
  useEffect(() => {
    setPanel(item ? load.filter((l) => item.interviewers.includes(l.name)).map((l) => l.userId) : [])
    setFind('')
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [item?.interviewId])
  const onPanel = load.filter((l) => panel.includes(l.userId))
  const found = find.trim() === '' ? [] : load.filter((l) => !panel.includes(l.userId) && l.name.toLowerCase().includes(find.trim().toLowerCase()))

  // Every time this panel could hold the interview, day by day, and why a day has none.
  const { data: openings, isFetching } = useInterviewOpenings(held ? undefined : item?.interviewId, panel)
  const byDay = useMemo(() => new Map((openings ?? []).map((d) => [d.day, d])), [openings])
  const [time, setTime] = useState<string | null>(null)
  useEffect(() => setTime(null), [item?.interviewId, openings])

  const [month, setMonth] = useState(() => { const d = new Date(); d.setDate(1); d.setHours(0, 0, 0, 0); return d })
  const [day, setDay] = useState<string | null>(null)
  const cells = useMemo(() => {
    const first = new Date(month)
    const start = new Date(first); start.setDate(1 - first.getDay())
    return Array.from({ length: 42 }, (_, i) => { const d = new Date(start); d.setDate(start.getDate() + i); return d })
  }, [month])
  const today = key(new Date())
  const firstOpen = useMemo(() => (openings ?? []).find((d) => d.state === 'OPEN')?.day ?? null, [openings])
  const shownDay = day ?? firstOpen
  const shown = shownDay ? byDay.get(shownDay) : undefined
  const times = shown?.times ?? []
  const openDays = (openings ?? []).filter((d) => d.state === 'OPEN').length
  const shift = (n: number) => setMonth((m) => { const d = new Date(m); d.setMonth(d.getMonth() + n); return d })

  return (
    <aside className="dash-panel sched sched--drawer">
      <section>
        <div className="sched__head">
          <div className="dash-panel__title">{held ? 'Interview' : 'Schedule an interview'}</div>
          <button type="button" className="sched__close" aria-label="Close" onClick={() => close?.()}>×</button>
        </div>
        <div className="dash-panel__sub">{held ? 'Record what happened, or move it' : 'Choose the interviewers, then pick a time'}</div>
        {item ? (
          <>
            <div className="sched__who">
              <span className="sched__avatar">{initials(item.candidateName)}</span>
              <span style={{ minWidth: 0 }}>
                <strong>{item.candidateName}</strong>
                <small>{item.jobTitle}</small>
              </span>
            </div>
            {app && (
              <div className="sched__links">
                <Link to={`/candidates/${app.candidateId}`}>Candidate record →</Link>
                <Link to={`/jobs/${app.jobId}`}>Job →</Link>
              </div>
            )}
          </>
        ) : (
          <div className="dash-panel__empty" style={{ marginTop: 12 }}>Every invited candidate has picked a time.</div>
        )}
      </section>

      {held && (
        <>
          <section>
            <div className="sched__label">Interviewers</div>
            {held.interviewers.length === 0 ? (
              <div className="dash-panel__empty">No interviewers are recorded on this interview.</div>
            ) : (
              <div className="sched__people">
                {held.interviewers.map((n) => (
                  <span key={n} className="sched__person" aria-pressed="true"><b>{initials(n)}</b>{n}</span>
                ))}
              </div>
            )}
          </section>
          <section>
            <div className="sched__label">When</div>
            <div className="sched__when">
              {held.scheduledAt
                ? `${new Date(held.scheduledAt).toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })} at ${clock(held.scheduledAt)}`
                : 'No time recorded'}
            </div>
            {held.meetingLink && <a className="sched__again sched__join" href={held.meetingLink} target="_blank" rel="noreferrer">Join the interview</a>}
          </section>
          <section>
            <div className="sched__label">What happened?</div>
            <button type="button" className="dash-panel__btn sched__go" style={{ marginTop: 0 }} disabled={transition.isPending}
              onClick={() => transition.mutate({ interviewId: held.interviewId, status: 'COMPLETED' }, { onSuccess: () => close?.() })}>
              It took place
            </button>
            <button type="button" className="sched__again" disabled={transition.isPending}
              onClick={() => transition.mutate({ interviewId: held.interviewId, status: 'NO_SHOW' }, { onSuccess: () => close?.() })}>
              The candidate did not attend
            </button>
            <button type="button" className="sched__again" disabled={reschedule.isPending}
              onClick={() => reschedule.mutate(held.interviewId, { onSuccess: () => onPick(held.interviewId) })}>
              {reschedule.isPending ? 'Moving…' : 'Move it: offer new times'}
            </button>
            <p className="sched__note">
              Moving it frees this time and offers the candidate new ones; it then appears under Awaiting candidate, where a time can be picked for them.
            </p>
          </section>
        </>
      )}

      {item && !held && (
        <>
          <section>
            <div className="sched__label">Interviewers</div>
            <input className="sched__find" type="search" placeholder="Search a name to add an interviewer" value={find} onChange={(e) => setFind(e.target.value)} aria-label="Search interviewers" />
            {find.trim() !== '' && (
              <div className="sched__found">
                {found.length === 0 && <span className="sched__none">No one else matches “{find.trim()}”.</span>}
                {found.map((l) => (
                  <button key={l.userId} type="button" onClick={() => { setPanel((cur) => [...cur, l.userId]); setFind(''); setDay(null) }}>
                    <b>{l.initials}</b>
                    <span>
                      <strong>{l.name}</strong>
                      <small>{l.role.replace(/_/g, ' ').toLowerCase()} · {l.next7Days} booked in the next 7 days</small>
                    </span>
                    <i>Add</i>
                  </button>
                ))}
              </div>
            )}
            <div className="sched__people">
              {onPanel.map((l) => (
                <span key={l.userId} className="sched__person" aria-pressed="true" title={`${l.name} · ${l.next7Days} booked in the next 7 days`}>
                  <b>{l.initials}</b>
                  {l.name}
                  <button type="button" className="sched__drop" aria-label={`Remove ${l.name}`} onClick={() => { setPanel((cur) => cur.filter((x) => x !== l.userId)); setDay(null) }}>×</button>
                </span>
              ))}
              {onPanel.length === 0 && <span className="sched__none">Nobody chosen: the job's hiring team is used.</span>}
            </div>
          </section>

          <section>
            <div className="sched__month">
              <span className="sched__label" style={{ margin: 0 }}>{month.toLocaleDateString('en-US', { month: 'long', year: 'numeric' })}</span>
              <span>
                <button type="button" aria-label="Previous month" onClick={() => shift(-1)}>‹</button>
                <button type="button" aria-label="Next month" onClick={() => shift(1)}>›</button>
              </span>
            </div>
            <div className="sched__grid">
              {DOW.map((d) => <span key={d} className="sched__dow">{d}</span>)}
              {cells.map((d) => {
                const k = key(d)
                const info = byDay.get(k)
                const state = info?.state === 'OPEN' ? 'open' : info?.state === 'FULL' ? 'full' : 'none'
                return (
                  <button key={k} type="button" className="sched__day" data-state={state} data-out={d.getMonth() !== month.getMonth() || undefined}
                    data-today={k === today || undefined} aria-pressed={k === shownDay} disabled={!info || info.state === 'PAST'}
                    title={info ? (info.state === 'OPEN' ? `${info.times.length} ${info.times.length === 1 ? 'time' : 'times'} free` : info.why ?? undefined) : undefined}
                    onClick={() => setDay(k)}>
                    {d.getDate()}
                  </button>
                )
              })}
            </div>
            <div className="sched__legend">
              <span data-state="open">Free times</span>
              <span data-state="full">Interviewers booked up</span>
            </div>
          </section>

          <section>
            <div className="sched__label">
              {shown
                ? `${new Date(shown.day + 'T12:00:00').toLocaleDateString('en-US', { weekday: 'long', month: 'short', day: 'numeric' })} · ${times.length} ${times.length === 1 ? 'time' : 'times'} free`
                : 'Pick a day'}
            </div>
            {times.length === 0 ? (
              <div className="dash-panel__empty">
                {isFetching ? 'Checking the calendars…' : shown?.why ?? (openDays === 0 ? 'This panel has no free time in the next 60 days. Try a different set of interviewers.' : 'Pick a highlighted day.')}
              </div>
            ) : (
              <div className="sched__times">
                {times.map((t) => (
                  <button key={t} type="button" aria-pressed={time === t} onClick={() => setTime(time === t ? null : t)}>
                    {clock(t)}
                  </button>
                ))}
              </div>
            )}
            <button type="button" className="dash-panel__btn sched__go" disabled={!time || book.isPending}
              onClick={() => time && book.mutate({ interviewId: item.interviewId, startsAt: time, interviewerUserIds: panel.length ? panel : undefined }, { onSuccess: () => close?.() })}>
              {book.isPending ? 'Booking…' : time ? `Book ${clock(time)} for ${item.candidateName.split(' ')[0]}` : 'Pick a time to book it'}
            </button>
            {book.isError && (
              <p className="sched__note" style={{ color: 'var(--bofa-red)' }}>
                {(book.error as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'That time could not be booked.'}
              </p>
            )}
            <button type="button" className="sched__again" disabled={propose.isPending}
              onClick={() => propose.mutate({ interviewId: item.interviewId, interviewerUserIds: panel.length ? panel : undefined }, { onSuccess: () => close?.() })}>
              {propose.isPending ? 'Sending…' : 'Or let the candidate choose: send them times'}
            </button>
            <p className="sched__note">
              {openDays} {openDays === 1 ? 'day has' : 'days have'} free time for this panel in the next 60 days. Every interviewer must be free, so a smaller panel usually has more. Sending is simulated in the prototype.
            </p>
          </section>
        </>
      )}
    </aside>
  )
}
