import { useCallback, useEffect, useMemo, useState } from 'react'
import ConfirmButton from '@/components/ConfirmButton'
import SlideOver, { useSlideOverClose } from '@/components/SlideOver'
import { Link, useParams } from 'react-router-dom'
import { api } from '@/api/client'

/* Availability: the team's week of free interview time, each person's hours and weekly
   preferences, and the interviews each job runs. A person or an interview opens in the drawer.
   Everything set here feeds the scheduling engine, so candidates are only offered times that hold. */

interface Settings {
  workStart: string
  workEnd: string
  timezone: string
  maxPerDay: number
  maxPerWeek: number
}

interface UserRow {
  id: string
  name: string
  role: string
  title: string | null
  settings: Settings
  ruleCount: number
}

interface Rule {
  id: string
  dayOfWeek: number
  startTime: string
  endTime: string
  kind: 'NO_INTERVIEWS' | 'INTERVIEW_BLOCK'
}

interface Detail {
  userId: string
  name: string
  role: string
  settings: Settings
  rules: Rule[]
}

const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
const TIMEZONES = [
  'America/New_York',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
  'Europe/London',
  'Asia/Kolkata',
]

const roleLabel = (r: string) =>
  r.replace(/_/g, ' ').toLowerCase().replace(/\b./g, (c) => c.toUpperCase())

const isoDate = (d: Date) => {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

interface PlanMember {
  userId: string
  name: string
  role: string
  notes?: string[]
}

interface PlanRound {
  id: string
  roundNo: number
  name: string
  durationMin: number
  members: PlanMember[]
}

interface Requisition {
  jobId: string
  title: string
  department: string | null
  status: string
  recruiterName: string | null
  hiringManagerName: string | null
  roundCount: number
}

const initialsOf = (name: string) =>
  name.split(/\s+/).map((w) => w[0]).slice(0, 2).join('').toUpperCase()

const totalLabel = (rounds: PlanRound[]) => {
  const min = rounds.reduce((t, r) => t + r.durationMin, 0)
  if (!min) return ''
  const h = Math.floor(min / 60)
  const m = min % 60
  return `${h ? `${h}h` : ''}${h && m ? ' ' : ''}${m ? `${m}m` : ''} of interviews`
}

interface DayOpen {
  day: string
  state: 'OPEN' | 'FULL' | 'OUTSIDE' | 'PAST'
  why: string | null
  times: string[]
}

const hourLabel = (hhmm: string) => {
  const [h, m] = hhmm.split(':').map(Number)
  const d = new Date(2000, 0, 1, h, m || 0)
  return d.toLocaleTimeString('en-US', { hour: 'numeric', minute: m ? '2-digit' : undefined })
}
const zoneLabel = (tz: string) => (tz.split('/')[1] ?? tz).replace(/_/g, ' ')

interface PersonWeek {
  userId: string
  days: DayOpen[]
}

type Plans = Record<string, PlanRound[]>

const DOW = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
const clock = (iso: string) => new Date(iso).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })
const dayLabel = (iso: string) =>
  new Date(iso + 'T12:00:00').toLocaleDateString('en-US', { weekday: 'long', month: 'short', day: 'numeric' })

/* A job's interview setup in the same drawer as interview scheduling: its interviews across the
   top, then the picked one's length, interviewers and the days and times they are all free. */
function JobDrawer({
  job,
  rounds,
  users,
  refresh,
  onPerson,
}: {
  job: Requisition
  rounds: PlanRound[]
  users: UserRow[]
  refresh: () => void
  onPerson: (userId: string) => void
}) {
  const close = useSlideOverClose()
  const [picked, setPicked] = useState<string | null>(null)
  const round = rounds.find((r) => r.id === picked) ?? rounds[0] ?? null
  const [find, setFind] = useState('')
  const [openings, setOpenings] = useState<DayOpen[] | null>(null)
  const [month, setMonth] = useState(() => { const d = new Date(); d.setDate(1); d.setHours(0, 0, 0, 0); return d })
  const [day, setDay] = useState<string | null>(null)

  const memberKey = round ? round.members.map((m) => m.userId).join(',') : ''
  useEffect(() => {
    setOpenings(null)
    setDay(null)
    if (!round || round.members.length === 0) return
    let live = true
    api.get<DayOpen[]>(`/interview-plan/rounds/${round.id}/openings`, { params: { days: 60 } })
      .then((r) => { if (live) setOpenings(r.data) })
      .catch(() => { if (live) setOpenings([]) })
    return () => { live = false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [round?.id, round?.durationMin, memberKey])
  useEffect(() => setFind(''), [round?.id])

  const byDay = useMemo(() => new Map((openings ?? []).map((d) => [d.day, d])), [openings])
  const cells = useMemo(() => {
    const start = new Date(month)
    start.setDate(1 - month.getDay())
    return Array.from({ length: 42 }, (_, i) => { const d = new Date(start); d.setDate(start.getDate() + i); return d })
  }, [month])
  const today = isoDate(new Date())
  const shownDay = day ?? (openings ?? []).find((d) => d.times.length > 0)?.day ?? null
  const shown = shownDay ? byDay.get(shownDay) : undefined
  const times = shown?.times ?? []
  const openDays = (openings ?? []).filter((d) => d.times.length > 0).length
  const shift = (n: number) => setMonth((m) => { const d = new Date(m); d.setMonth(d.getMonth() + n); return d })

  const put = (body: object) => round && api.put(`/interview-plan/rounds/${round.id}`, body).then(refresh)
  const setMembers = (userIds: string[]) => round && api.put(`/interview-plan/rounds/${round.id}/members`, { userIds }).then(refresh)
  const lengths = round ? [...new Set([30, 45, 60, 90, round.durationMin])].sort((x, y) => x - y) : []
  const q = find.trim().toLowerCase()
  const found = q && round ? users.filter((u) => !round.members.some((m) => m.userId === u.id) && u.name.toLowerCase().includes(q)).slice(0, 6) : []

  return (
    <aside className="dash-panel sched sched--drawer">
      <section>
        <div className="sched__head">
          <div className="dash-panel__title">Interview setup</div>
          <button type="button" className="sched__close" aria-label="Close" onClick={() => close?.()}>×</button>
        </div>
        <div className="dash-panel__sub">Who interviews for this job, and when they are free</div>
        <div className="sched__who">
          <span className="sched__avatar">{initialsOf(job.title)}</span>
          <span style={{ minWidth: 0 }}>
            <strong>{job.title}</strong>
            <small>{[job.department, totalLabel(rounds)].filter(Boolean).join(' · ') || 'No interviews yet'}</small>
          </span>
        </div>
        <div className="sched__links">
          <Link to={`/jobs/${job.jobId}`}>Job →</Link>
        </div>
      </section>

      <section>
        <div className="sched__label">Interviews for this job</div>
        <div className="sched__people">
          {rounds.map((r) => (
            <button key={r.id} type="button" className="sched__person avd__tab" aria-pressed={round?.id === r.id} onClick={() => setPicked(r.id)}>
              {r.name}
            </button>
          ))}
          <button type="button" className="avd__add"
            onClick={() => api.post<PlanRound>(`/interview-plan/${job.jobId}/rounds`, {}).then((r) => { refresh(); if (r.data?.id) setPicked(r.data.id) })}>
            + Add
          </button>
        </div>
        {!round && <p className="sched__note">This job has no interviews yet. Add the first one.</p>}
      </section>

      {round && (
        <>
          <section key={`set-${round.id}`}>
            <div className="sched__label">Name</div>
            <input className="sched__find" defaultValue={round.name} placeholder="Name this interview" aria-label="Interview name"
              onBlur={(e) => { const v = e.target.value.trim(); if (v && v !== round.name) put({ name: v }) }}
              onKeyDown={(e) => { if (e.key === 'Enter') e.currentTarget.blur() }} />
            <div className="sched__label" style={{ marginTop: 16 }}>Length</div>
            <div className="plan-step__lengths" role="group" aria-label="Length">
              {lengths.map((m) => (
                <button key={m} type="button" aria-pressed={round.durationMin === m} onClick={() => m !== round.durationMin && put({ durationMin: m })}>
                  {m} min
                </button>
              ))}
            </div>
          </section>

          <section>
            <div className="sched__label">Interviewers</div>
            <input className="sched__find" type="search" placeholder="Search a name to add an interviewer" value={find} onChange={(e) => setFind(e.target.value)} aria-label="Search interviewers" />
            {q !== '' && (
              <div className="sched__found">
                {found.length === 0 && <span className="sched__none">No one else matches “{find.trim()}”.</span>}
                {found.map((u) => (
                  <button key={u.id} type="button" onClick={() => { setMembers([...round.members.map((m) => m.userId), u.id]); setFind('') }}>
                    <b>{initialsOf(u.name)}</b>
                    <span>
                      <strong>{u.name}</strong>
                      <small>{roleLabel(u.role)} · {hourLabel(u.settings.workStart)} to {hourLabel(u.settings.workEnd)}, {zoneLabel(u.settings.timezone)}</small>
                    </span>
                    <i>Add</i>
                  </button>
                ))}
              </div>
            )}
            <div className="sched__people">
              {round.members.map((m) => (
                <span key={m.userId} className="sched__person" aria-pressed="true">
                  <b>{initialsOf(m.name)}</b>
                  {m.name}
                  <button type="button" className="sched__drop" aria-label={`Remove ${m.name}`}
                    onClick={() => setMembers(round.members.filter((x) => x.userId !== m.userId).map((x) => x.userId))}>×</button>
                </span>
              ))}
              {round.members.length === 0 && <span className="sched__none">Nobody yet. Add the people who take this interview.</span>}
            </div>
            {round.members.map((m) => {
              const u = users.find((x) => x.id === m.userId)
              return (
                <div key={m.userId} className="avd__hours">
                  <span>
                    {m.name.split(' ')[0]}: {u ? `${hourLabel(u.settings.workStart)} to ${hourLabel(u.settings.workEnd)}, ${zoneLabel(u.settings.timezone)}, up to ${u.settings.maxPerDay} a day` : 'hours not set'}
                  </span>
                  <button type="button" onClick={() => onPerson(m.userId)}>Change</button>
                </div>
              )
            })}
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
                const k = isoDate(d)
                const info = byDay.get(k)
                const state = info && info.times.length > 0 ? 'open' : info?.state === 'FULL' ? 'full' : 'none'
                return (
                  <button key={k} type="button" className="sched__day" data-state={state} data-out={d.getMonth() !== month.getMonth() || undefined}
                    data-today={k === today || undefined} aria-pressed={k === shownDay} disabled={!info || info.state === 'PAST'}
                    title={info ? (info.times.length > 0 ? `${info.times.length} ${info.times.length === 1 ? 'time' : 'times'} free` : info.why ?? undefined) : undefined}
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
              {shownDay ? `${dayLabel(shownDay)} · ${times.length} ${times.length === 1 ? 'time' : 'times'} free` : 'Free times'}
            </div>
            {times.length === 0 ? (
              <div className="dash-panel__empty">
                {round.members.length === 0
                  ? 'Add an interviewer to see when this interview can happen.'
                  : openings === null
                    ? 'Checking the calendars…'
                    : shown?.why ?? (openDays === 0 ? 'These interviewers have no free time in the next 60 days.' : 'Pick a highlighted day.')}
              </div>
            ) : (
              <div className="sched__times avd__times">
                {times.map((t) => <span key={t}>{clock(t)}</span>)}
              </div>
            )}
            <p className="sched__note">
              {round.members.length > 0 && openings !== null ? `${openDays} ${openDays === 1 ? 'day has' : 'days have'} free time in the next 60 days. ` : ''}
              These are the times candidates are offered. Every interviewer listed must be free.
            </p>
            <ConfirmButton label={`Remove “${round.name}”`} confirmLabel="Remove it?" className="sched__again"
              onConfirm={() => api.delete(`/interview-plan/rounds/${round.id}`).then(() => { setPicked(null); refresh() })} />
          </section>
        </>
      )}
    </aside>
  )
}

const RULE_LABEL: Record<Rule['kind'], string> = {
  NO_INTERVIEWS: "Can't interview",
  INTERVIEW_BLOCK: 'Interview time',
}

/* One interviewer in the drawer: working hours, limits, weekly preferences and their free time. */
function PersonDrawer({
  user,
  week,
  takes,
  onSaved,
}: {
  user: UserRow
  week: DayOpen[]
  takes: string[]
  onSaved: () => void
}) {
  const close = useSlideOverClose()
  const [form, setForm] = useState<Settings>(user.settings)
  const [rules, setRules] = useState<Rule[]>([])
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [rule, setRule] = useState({ dayOfWeek: 1, startTime: '09:00', endTime: '12:00', kind: 'NO_INTERVIEWS' as Rule['kind'] })

  const load = useCallback(() => {
    api.get<Detail>(`/availability/${user.id}`).then((r) => setRules(r.data.rules))
  }, [user.id])
  useEffect(load, [load])

  const fail = (e: unknown, fallback: string) =>
    setError((e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback)
  const dirty = JSON.stringify(form) !== JSON.stringify(user.settings)
  const save = async () => {
    setError(null)
    setSaving(true)
    try {
      await api.put(`/availability/${user.id}/settings`, form)
      onSaved()
    } catch (e) {
      fail(e, 'Could not save the hours.')
    } finally {
      setSaving(false)
    }
  }
  const addRule = async () => {
    setError(null)
    try {
      await api.post(`/availability/${user.id}/rules`, rule)
      load()
      onSaved()
    } catch (e) {
      fail(e, 'Could not add that.')
    }
  }
  const openDays = week.filter((d) => d.times.length > 0)

  return (
    <aside className="dash-panel sched sched--drawer">
      <section>
        <div className="sched__head">
          <div className="dash-panel__title">Interviewer</div>
          <button type="button" className="sched__close" aria-label="Close" onClick={() => close?.()}>×</button>
        </div>
        <div className="dash-panel__sub">Hours, limits and weekly preferences</div>
        <div className="sched__who">
          <span className="sched__avatar">{initialsOf(user.name)}</span>
          <span style={{ minWidth: 0 }}>
            <strong>{user.name}</strong>
            <small>{user.title || roleLabel(user.role)}</small>
          </span>
        </div>
      </section>

      <section>
        <div className="sched__label">Working hours</div>
        <div className="av__fields">
          <label>
            <span>From</span>
            <input className="sched__find" type="time" value={form.workStart} onChange={(e) => setForm({ ...form, workStart: e.target.value })} />
          </label>
          <label>
            <span>To</span>
            <input className="sched__find" type="time" value={form.workEnd} onChange={(e) => setForm({ ...form, workEnd: e.target.value })} />
          </label>
          <label className="av__wide">
            <span>Time zone</span>
            <select className="sched__find" value={form.timezone} onChange={(e) => setForm({ ...form, timezone: e.target.value })}>
              {(TIMEZONES.includes(form.timezone) ? TIMEZONES : [form.timezone, ...TIMEZONES]).map((z) => (
                <option key={z} value={z}>{zoneLabel(z)}</option>
              ))}
            </select>
          </label>
          <label>
            <span>Most interviews a day</span>
            <input className="sched__find" type="number" min={1} value={form.maxPerDay} onChange={(e) => setForm({ ...form, maxPerDay: Number(e.target.value) })} />
          </label>
          <label>
            <span>Most a week</span>
            <input className="sched__find" type="number" min={1} value={form.maxPerWeek} onChange={(e) => setForm({ ...form, maxPerWeek: Number(e.target.value) })} />
          </label>
        </div>
        <button type="button" className="dash-panel__btn sched__go" disabled={!dirty || saving} onClick={save}>
          {saving ? 'Saving…' : dirty ? 'Save hours' : 'Hours are saved'}
        </button>
        {error && <p className="sched__note" style={{ color: 'var(--bofa-red)' }}>{error}</p>}
      </section>

      <section>
        <div className="sched__label">Every week</div>
        {rules.length === 0 && <div className="sched__none">Nothing set: interviews can fall anywhere in the working hours.</div>}
        <div className="av__rules">
          {rules.map((r) => (
            <div key={r.id} className="av__rule" data-kind={r.kind}>
              <b>{DAYS[r.dayOfWeek - 1]}</b>
              <span>{hourLabel(r.startTime)} to {hourLabel(r.endTime)}</span>
              <em>{RULE_LABEL[r.kind]}</em>
              <button type="button" className="sched__drop" aria-label="Remove"
                onClick={() => api.delete(`/availability/rules/${r.id}`).then(() => { load(); onSaved() })}>×</button>
            </div>
          ))}
        </div>
        <div className="av__fields av__fields--rule">
          <label>
            <span>Day</span>
            <select className="sched__find" value={rule.dayOfWeek} onChange={(e) => setRule({ ...rule, dayOfWeek: Number(e.target.value) })}>
              {DAYS.slice(0, 5).map((d, i) => <option key={d} value={i + 1}>{d}</option>)}
            </select>
          </label>
          <label>
            <span>From</span>
            <input className="sched__find" type="time" value={rule.startTime} onChange={(e) => setRule({ ...rule, startTime: e.target.value })} />
          </label>
          <label>
            <span>To</span>
            <input className="sched__find" type="time" value={rule.endTime} onChange={(e) => setRule({ ...rule, endTime: e.target.value })} />
          </label>
          <label className="av__wide">
            <span>What it is</span>
            <select className="sched__find" value={rule.kind} onChange={(e) => setRule({ ...rule, kind: e.target.value as Rule['kind'] })}>
              <option value="NO_INTERVIEWS">Can't interview then</option>
              <option value="INTERVIEW_BLOCK">Time kept for interviews</option>
            </select>
          </label>
        </div>
        <button type="button" className="sched__again" onClick={addRule}>Add to every week</button>
        <p className="sched__note">
          When someone keeps time for interviews, candidates are only offered times inside it.
        </p>
      </section>

      <section>
        <div className="sched__label">Takes these interviews</div>
        {takes.length === 0
          ? <div className="sched__none">None yet. Add them to an interview under Jobs.</div>
          : <div className="av__takes">{takes.map((t) => <span key={t}>{t}</span>)}</div>}
      </section>

      <section>
        <div className="sched__label">Free in the next 7 days</div>
        {openDays.length === 0 ? (
          <div className="dash-panel__empty">No time is free in the next 7 days.</div>
        ) : (
          <div className="plan__days">
            {openDays.map((d) => (
              <div key={d.day} className="plan__dayrow">
                <b>{new Date(d.day + 'T12:00:00').toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' })}</b>
                <div className="plan__times">
                  {d.times.slice(0, 8).map((t) => (
                    <span key={t}>{new Date(t).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })}</span>
                  ))}
                  {d.times.length > 8 && <em>+{d.times.length - 8} more</em>}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </aside>
  )
}

export default function AvailabilityPage() {
  const { jobId } = useParams()
  const [users, setUsers] = useState<UserRow[]>([])
  const [weeks, setWeeks] = useState<PersonWeek[] | null>(null)
  const [reqs, setReqs] = useState<Requisition[]>([])
  const [plans, setPlans] = useState<Plans>({})
  const [person, setPerson] = useState<string | null>(null)
  const [openJob, setOpenJob] = useState<string | null>(jobId ?? null)

  const loadPeople = useCallback(() => {
    api.get<UserRow[]>('/availability/users').then((r) => setUsers(r.data))
    api.get<PersonWeek[]>('/availability/week', { params: { days: 7 } }).then((r) => setWeeks(r.data)).catch(() => setWeeks([]))
  }, [])
  const loadPlans = useCallback(() => {
    api.get<Requisition[]>('/interview-plan/requisitions').then((r) => {
      setReqs(r.data)
      for (const req of r.data) {
        api.get<PlanRound[]>('/interview-plan', { params: { jobId: req.jobId } })
          .then((p) => setPlans((cur) => ({ ...cur, [req.jobId]: p.data })))
          .catch(() => {})
      }
    })
  }, [])
  useEffect(loadPeople, [loadPeople])
  useEffect(loadPlans, [loadPlans])

  // The interviews each person takes, as "Recruiter screen · Personal Banker".
  const takes: Record<string, string[]> = {}
  for (const req of reqs) {
    for (const r of plans[req.jobId] ?? []) {
      for (const m of r.members) (takes[m.userId] ??= []).push(`${r.name} · ${req.title}`)
    }
  }

  const job = reqs.find((r) => r.jobId === openJob) ?? null
  const openUser = users.find((u) => u.id === person) ?? null
  const nextFree = (userId: string) => {
    const first = weeks?.find((w) => w.userId === userId)?.days.find((d) => d.times.length > 0)
    return first ? `Next free ${new Date(first.times[0]).toLocaleDateString('en-US', { weekday: 'short' })}, ${clock(first.times[0])}` : 'No free time this week'
  }

  return (
    <div className="page">
      <div className="page-head">
        <div className="crumb">
          <span className="dot" />
          Hiring · Availability
        </div>
        <div className="page-head__row">
          <div>
            <h1>Availability</h1>
            <p className="sub">Who interviews for each job, and when they are free. Pick a job or a person to set it up.</p>
          </div>
        </div>
      </div>

      <div className="avp">
        <div className="card">
          <div className="card__head">
            <h3 style={{ fontSize: 15 }}>Jobs</h3>
            <span className="eyebrow">{reqs.length} open</span>
          </div>
          <div>
            {reqs.length === 0 && <div className="desk-empty">No open jobs.</div>}
            {reqs.map((r) => {
              const rounds = plans[r.jobId] ?? []
              const missing = rounds.filter((x) => x.members.length === 0).length
              return (
                <div key={r.jobId} className="desk-row">
                  <span className="avatar desk-row__avatar">{initialsOf(r.title)}</span>
                  <span className="desk-row__main">
                    <button type="button" className="desk-row__name desk-row__open" onClick={() => setOpenJob(r.jobId)}>{r.title}</button>
                    <small>{rounds.length === 0 ? r.department || 'No interviews yet' : rounds.map((x) => x.name).join(', then ')}</small>
                  </span>
                  {rounds.length === 0
                    ? <span className="badge badge--warn">Not set up</span>
                    : missing > 0
                      ? <span className="badge badge--warn">{missing} without interviewers</span>
                      : <span className="badge badge--ok">{totalLabel(rounds)}</span>}
                </div>
              )
            })}
          </div>
        </div>

        <div className="card">
          <div className="card__head">
            <h3 style={{ fontSize: 15 }}>Interviewers</h3>
            <span className="eyebrow">{users.length} people</span>
          </div>
          <div>
            {users.map((u) => (
              <div key={u.id} className="desk-row">
                <span className="avatar desk-row__avatar">{initialsOf(u.name)}</span>
                <span className="desk-row__main">
                  <button type="button" className="desk-row__name desk-row__open" onClick={() => setPerson(u.id)}>{u.name}</button>
                  <small>{hourLabel(u.settings.workStart)} to {hourLabel(u.settings.workEnd)}, {zoneLabel(u.settings.timezone)} · up to {u.settings.maxPerDay} a day</small>
                </span>
                <span className="desk-row__when">{weeks === null ? '' : nextFree(u.id)}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {job && !openUser && (
        <SlideOver label={job.title} onClose={() => setOpenJob(null)} head={false}>
          <JobDrawer key={job.jobId} job={job} rounds={plans[job.jobId] ?? []} users={users}
            refresh={() => { loadPlans(); loadPeople() }} onPerson={setPerson} />
        </SlideOver>
      )}
      {openUser && (
        <SlideOver label={openUser.name} onClose={() => setPerson(null)} head={false}>
          <PersonDrawer key={openUser.id} user={openUser} takes={takes[openUser.id] ?? []}
            week={weeks?.find((w) => w.userId === openUser.id)?.days ?? []} onSaved={loadPeople} />
        </SlideOver>
      )}
    </div>
  )
}
