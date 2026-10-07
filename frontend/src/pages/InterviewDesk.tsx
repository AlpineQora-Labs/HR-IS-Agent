import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import InterviewScheduleDrawer, { type Fresh } from './InterviewSchedulePanel'
import { useApplications, useJobInterviews, useJobs, useSchedulingOverview, useTransitionInterview } from '@/api/hooks'
import type { AttentionItem } from '@/api/types'
import { initials } from '@/lib/format'

/* The interview desk: what a recruiter has to do about interviews, across every job. The
   stuck ones come first with their action on the row; then the week ahead as a plain agenda.
   The calendar is still there, as a second view of the same interviews. */

type Lane = 'outcome' | 'awaiting' | 'noshow' | 'today'

const LANES: { key: Lane; label: string; note: string }[] = [
  { key: 'outcome', label: 'Needs outcome', note: 'Happened, nothing recorded' },
  { key: 'awaiting', label: 'Awaiting candidate', note: 'Invited, no time picked' },
  { key: 'today', label: 'Today', note: 'On the calendar today' },
  { key: 'noshow', label: 'No-shows', note: 'New times already offered' },
]

const clock = (iso: string | null) =>
  iso ? new Date(iso).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' }) : ''
const day = (iso: string) => new Date(iso).toLocaleDateString('en-US', { weekday: 'long', month: 'short', day: 'numeric' })
const waited = (hours: number) => (hours < 1 ? 'under an hour' : hours < 48 ? `${hours} hours` : `${Math.round(hours / 24)} days`)
/** How an interview's time was set, in words; and whether the automation did it without the team. */
const VIA: Record<string, string> = { CHAT: 'Auto · picked in chat', TEXT: 'Auto · picked by text', WEB_PAGE: 'Auto · picked on the web page', TEAM: 'Booked by the team' }
const auto = (via: string | null | undefined) => via === 'CHAT' || via === 'TEXT' || via === 'WEB_PAGE'
const kind = (type: string) => type.replace(/_/g, ' ').toLowerCase().replace(/^./, (c) => c.toUpperCase())

export default function InterviewDesk() {
  const { data } = useSchedulingOverview()
  const transition = useTransitionInterview()
  const [lane, setLane] = useState<Lane | null>(null)
  // The candidate the scheduling drawer is open on; null when it is closed.
  const [picked, setPicked] = useState<string | null>(null)

  // Every application, to name the candidate and job of each interview and link to their record.
  const { data: apps } = useApplications()
  const appById = useMemo(() => new Map((apps ?? []).map((a) => [a.id, a])), [apps])
  const { interviews } = useJobInterviews(useMemo(() => (apps ?? []).map((a) => a.id), [apps]))

  // One job at a time, or all of them. With a job chosen, everyone who applied to it is listed too.
  const { data: jobs } = useJobs()
  const [jobId, setJobId] = useState('')
  const inJob = (item: AttentionItem) => jobId === '' || appById.get(item.applicationId)?.jobId === jobId
  const lanes: Record<Lane, AttentionItem[]> = {
    outcome: (data?.needsOutcome ?? []).filter(inJob),
    awaiting: (data?.awaitingCandidate ?? []).filter(inJob),
    today: (data?.today ?? []).filter(inJob),
    noshow: (data?.noShows ?? []).filter(inJob),
  }
  const onDesk = [...lanes.outcome, ...lanes.awaiting, ...lanes.today, ...lanes.noshow]

  // Everyone who applied to the chosen job, with where their interview stands.
  const roster = useMemo(() => {
    if (jobId === '') return []
    return (apps ?? [])
      .filter((a) => a.jobId === jobId)
      .map((a) => {
        const mine = interviews.filter((iv) => iv.applicationId === a.id)
        const latest = [...mine].sort((x, y) => (y.scheduledAt ?? '').localeCompare(x.scheduledAt ?? ''))[0] ?? null
        return { app: a, latest }
      })
      .sort((x, y) => x.app.candidateName.localeCompare(y.app.candidateName))
  }, [apps, interviews, jobId])
  // An interview that is not on the desk's lists (booked ahead, or finished), opened from the job's list.
  const [extra, setExtra] = useState<AttentionItem | null>(null)
  const [fresh, setFresh] = useState<Fresh | null>(null)
  const openFor = (r: (typeof roster)[number]) => {
    const item = onDesk.find((i) => i.applicationId === r.app.id)
    setFresh(null)
    setExtra(null)
    if (item) return setPicked(item.interviewId)
    if (r.latest) {
      setExtra({
        interviewId: r.latest.id, applicationId: r.app.id, candidateName: r.app.candidateName, jobTitle: r.app.jobTitle,
        status: r.latest.status, scheduledAt: r.latest.scheduledAt ?? null, waitingHours: 0,
        meetingLink: r.latest.meetingLink, interviewers: r.latest.interviewers,
      })
      return setPicked(r.latest.id)
    }
    setFresh({ applicationId: r.app.id, candidateId: r.app.candidateId, jobId: r.app.jobId, candidateName: r.app.candidateName, jobTitle: r.app.jobTitle, stage: r.app.stage })
    setPicked(`app:${r.app.id}`)
  }
  const standing = (r: (typeof roster)[number]) => {
    const lane = (Object.keys(lanes) as Lane[]).find((k) => lanes[k].some((i) => i.applicationId === r.app.id))
    if (lane === 'awaiting') return 'Invited, no time picked'
    if (lane === 'outcome') return 'Interview happened, no outcome recorded'
    if (lane === 'noshow') return 'Did not attend'
    if (lane === 'today') return 'Interview today'
    if (!r.latest) return 'No interview yet'
    const st = r.latest.status.toUpperCase()
    if (st === 'SCHEDULED' || st === 'CONFIRMED') return `Booked for ${day(r.latest.scheduledAt)}, ${clock(r.latest.scheduledAt)}`
    if (st === 'COMPLETED') return 'Interview completed'
    return kind(r.latest.status)
  }
  // What needs the recruiter, oldest problem first: an unrecorded outcome, then a quiet candidate, then a no-show.
  const stuck: { lane: Lane; item: AttentionItem }[] = (['outcome', 'awaiting', 'noshow'] as Lane[])
    .filter((k) => lane === null || lane === k)
    .flatMap((k) => lanes[k].map((item) => ({ lane: k, item })))
  const showToday = lane === 'today'

  // The next seven days, as an agenda grouped by day.
  const agenda = useMemo(() => {
    const now = Date.now()
    const rows = interviews
      .filter((iv) => iv.scheduledAt && ['SCHEDULED', 'CONFIRMED'].includes(iv.status.toUpperCase()))
      .filter((iv) => jobId === '' || appById.get(iv.applicationId)?.jobId === jobId)
      .filter((iv) => new Date(iv.scheduledAt).getTime() >= now - 3_600_000)
      .sort((a, b) => new Date(a.scheduledAt).getTime() - new Date(b.scheduledAt).getTime())
    const days = new Map<string, typeof rows>()
    for (const iv of rows) {
      const k = day(iv.scheduledAt)
      days.set(k, [...(days.get(k) ?? []), iv])
    }
    return { days: [...days.entries()], total: rows.length, auto: rows.filter((iv) => auto(iv.bookedVia)).length }
  }, [interviews, jobId, appById])


  // A candidate's name opens the drawer on their interview; the drawer links on to their record and job.
  const who = (item: AttentionItem) => (
    <button type="button" className="desk-row__name desk-row__open" onClick={() => setPicked(item.interviewId)}>{item.candidateName}</button>
  )

  // The job, as a link to its page when the application is known.
  const job = (item: AttentionItem) => {
    const app = appById.get(item.applicationId)
    return app ? <Link to={`/jobs/${app.jobId}`} className="desk-row__job">{item.jobTitle}</Link> : item.jobTitle
  }

  return (
    <div className="desk">
      <div className="desk__job">
        <label htmlFor="desk-job">Job</label>
        <select id="desk-job" className="select" value={jobId} onChange={(e) => { setJobId(e.target.value); setLane(null) }}>
          <option value="">All jobs</option>
          {(jobs ?? []).map((j) => <option key={j.id} value={j.id}>{j.jobNo ? `${j.jobNo} · ` : ''}{j.title}</option>)}
        </select>
        {jobId !== '' && <Link to={`/jobs/${jobId}`} className="link" style={{ fontSize: 13 }}>Open the job →</Link>}
      </div>

      <p className="desk__how">
        Interviews are scheduled automatically: candidates pick from times worked out from the interviewers' calendars.
        Use the desk when one needs a hand, to add interviewers or book a time yourself.
      </p>

      <div className="desk__tiles">
        <div className="card desk-tile desk-tile--still">
          <span className="desk-tile__label">Booked ahead</span>
          <span className="desk-tile__value">{agenda.total}</span>
          <span className="desk-tile__note">{agenda.auto} set automatically</span>
        </div>
        {LANES.map((l) => (
          <button key={l.key} type="button" className="card desk-tile" aria-pressed={lane === l.key}
            onClick={() => setLane(lane === l.key ? null : l.key)}>
            <span className="desk-tile__label">{l.label}</span>
            <span className="desk-tile__value">{lanes[l.key].length}</span>
            <span className="desk-tile__note">{l.note}</span>
          </button>
        ))}
      </div>

      <div className="desk__body">
        <div className="desk__main">
          <div className="card">
            <div className="card__head">
              <h3 style={{ fontSize: 15 }}>{showToday ? 'Today' : 'Needs you'}</h3>
              <span className="eyebrow">
                {showToday ? `${lanes.today.length} on the calendar` : `${stuck.length} ${stuck.length === 1 ? 'interview' : 'interviews'} ${jobId === '' ? 'across all jobs' : 'for this job'}`}
              </span>
            </div>
            <div>
              {showToday &&
                lanes.today.map((item) => (
                  <div key={item.interviewId} className="desk-row">
                    <span className="avatar desk-row__avatar">{initials(item.candidateName)}</span>
                    <span className="desk-row__main">
                      {who(item)}
                      <small>{job(item)}{item.interviewers.length > 0 ? ` · with ${item.interviewers.join(', ')}` : ''}</small>
                    </span>
                    <span className="desk-row__when">{clock(item.scheduledAt)}</span>
                    {item.meetingLink && <a className="btn btn--outline btn--sm" href={item.meetingLink} target="_blank" rel="noreferrer">Join</a>}
                  </div>
                ))}
              {!showToday &&
                stuck.map(({ lane: k, item }) => (
                  <div key={item.interviewId} className="desk-row">
                    <span className="avatar desk-row__avatar">{initials(item.candidateName)}</span>
                    <span className="desk-row__main">
                      {who(item)}
                      <small>{job(item)}{item.interviewers.length > 0 ? ` · with ${item.interviewers.join(', ')}` : ''}</small>
                    </span>
                    <span className="desk-row__why">
                      {k === 'outcome' && <>Was {item.scheduledAt ? `${day(item.scheduledAt)}, ${clock(item.scheduledAt)}` : 'scheduled'}. No outcome recorded.</>}
                      {k === 'awaiting' && <>Invited {waited(item.waitingHours)} ago. No time picked.</>}
                      {k === 'noshow' && <>Did not attend. New times were sent in chat.</>}
                    </span>
                    <span className="desk-row__actions">
                      {k === 'outcome' && (
                        <>
                          <button className="btn btn--primary btn--sm" disabled={transition.isPending}
                            onClick={() => transition.mutate({ interviewId: item.interviewId, status: 'COMPLETED' })}>
                            Took place
                          </button>
                          <button className="btn btn--outline btn--sm" disabled={transition.isPending}
                            onClick={() => transition.mutate({ interviewId: item.interviewId, status: 'NO_SHOW' })}>
                            No-show
                          </button>
                        </>
                      )}
                      {k === 'awaiting' && (
                        <button className="btn btn--outline btn--sm" aria-pressed={picked === item.interviewId} onClick={() => setPicked(item.interviewId)}>Schedule</button>
                      )}
                      {k === 'noshow' && <span className="badge badge--info">Waiting on candidate</span>}
                    </span>
                  </div>
                ))}
              {((showToday && lanes.today.length === 0) || (!showToday && stuck.length === 0)) && (
                <div className="desk-empty">{showToday ? 'No interviews today.' : 'Nothing is stuck. Every interview has a time or an outcome.'}</div>
              )}
            </div>
          </div>

          {jobId !== '' && (
            <div className="card">
              <div className="card__head">
                <h3 style={{ fontSize: 15 }}>Everyone who applied</h3>
                <span className="eyebrow">{roster.length} {roster.length === 1 ? 'candidate' : 'candidates'} for this job</span>
              </div>
              <div>
                {roster.length === 0 && <div className="desk-empty">Nobody has applied to this job yet.</div>}
                {roster.map((r) => (
                  <div key={r.app.id} className="desk-row">
                    <span className="avatar desk-row__avatar">{initials(r.app.candidateName)}</span>
                    <span className="desk-row__main">
                      <button type="button" className="desk-row__name desk-row__open" onClick={() => openFor(r)}>{r.app.candidateName}</button>
                      <small>{r.latest && r.latest.interviewers.length > 0 ? `With ${r.latest.interviewers.join(', ')}` : 'No interviewers yet'}</small>
                    </span>
                    <span className="desk-row__why">{standing(r)}</span>
                    <span className="badge">{kind(r.app.stage)}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          <div className="card">
            <div className="card__head">
              <h3 style={{ fontSize: 15 }}>Scheduled</h3>
              <span className="eyebrow">
                {agenda.total} booked ahead for {jobId === '' ? 'all jobs' : 'this job'} · {agenda.auto} automatically
              </span>
            </div>
            <div>
              {agenda.total === 0 && <div className="desk-empty">No interviews are booked ahead.</div>}
              {agenda.days.map(([label, rows]) => (
                <div key={label}>
                  <div className="desk-day">{label}</div>
                  {rows.map((iv) => {
                    const app = appById.get(iv.applicationId)
                    return (
                      <div key={iv.id} className="desk-row">
                        <span className="desk-row__time">{clock(iv.scheduledAt)}</span>
                        <span className="desk-row__main">
                          <button type="button" className="desk-row__name desk-row__open"
                          onClick={() => {
                            setFresh(null)
                            setExtra({
                              interviewId: iv.id, applicationId: iv.applicationId, candidateName: app?.candidateName ?? 'Candidate', jobTitle: app?.jobTitle ?? '',
                              status: iv.status, scheduledAt: iv.scheduledAt, waitingHours: 0, meetingLink: iv.meetingLink, interviewers: iv.interviewers,
                            })
                            setPicked(iv.id)
                          }}>
                          {app?.candidateName ?? 'Candidate'}
                        </button>
                          <small>{app ? <Link to={`/jobs/${app.jobId}`} className="desk-row__job">{app.jobTitle}</Link> : ''}{iv.interviewers.length > 0 ? ` · with ${iv.interviewers.join(', ')}` : ''}</small>
                        </span>
                        {iv.bookedVia && <span className={auto(iv.bookedVia) ? 'badge badge--ok' : 'badge'}>{VIA[iv.bookedVia]}</span>}
                        <span className="badge">{kind(iv.type)}</span>
                        <span className="desk-row__when">{iv.durationMin} min</span>
                        {iv.meetingLink && <a className="btn btn--outline btn--sm" href={iv.meetingLink} target="_blank" rel="noreferrer">Join</a>}
                      </div>
                    )
                  })}
                </div>
              ))}
            </div>
          </div>
        </div>

      </div>


      {picked && (
        <InterviewScheduleDrawer
          waiting={lanes.awaiting}
          others={[...lanes.outcome, ...lanes.today, ...lanes.noshow, ...(extra ? [extra] : [])]}
          fresh={fresh}
          load={data?.load ?? []}
          appOf={(applicationId) => appById.get(applicationId)}
          picked={picked}
          onPick={(id) => { setFresh(null); setPicked(id) }}
          onClose={() => { setPicked(null); setFresh(null); setExtra(null) }}
        />
      )}
    </div>
  )
}
