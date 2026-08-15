import { useCallback, useEffect, useMemo, useState } from 'react'
import { api } from '@/api/client'

/* Interviewer availability — per-person working window + load caps, recurring
   weekly rules ("no Monday mornings", dedicated interview blocks) and a week
   calendar that reads tentative / busy / vacation / in-office / interviews.
   Rules feed straight into the scheduling engine: proposals never land inside
   a blackout, and when someone has interview blocks, only inside them. */

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

interface CalEvent {
  id: string
  title: string
  startsAt: string
  endsAt: string
  kind: string
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

/* Week-view geometry: 8:00–18:00, 44px per hour. */
const DAY_START = 8
const DAY_END = 18
const HOUR_PX = 44

/* Calendar-status treatments — muted, no loud fills. */
const EVENT_STYLE: Record<string, React.CSSProperties> = {
  BUSY: { background: '#eceff4', border: '1px solid #d6dbe4', color: 'var(--ink-2)' },
  INTERVIEW: { background: 'var(--bofa-navy, #012169)', border: '1px solid var(--bofa-navy, #012169)', color: '#fff' },
  TENTATIVE: { background: '#fff', border: '1.5px dashed #aab2c0', color: 'var(--ink-3)' },
  VACATION: { background: 'repeating-linear-gradient(-45deg, #f6f0e2, #f6f0e2 5px, #efe6cf 5px, #efe6cf 10px)', border: '1px solid #e2d6b4', color: '#6d5c1e' },
  IN_OFFICE: { background: '#eef4ee', border: '1px solid #d3e2d3', color: '#2e5a34' },
}

const KIND_LABEL: Record<string, string> = {
  BUSY: 'Busy',
  INTERVIEW: 'Interview',
  TENTATIVE: 'Tentative',
  VACATION: 'Vacation',
  IN_OFFICE: 'In office',
}

const mondayOf = (d: Date) => {
  const out = new Date(d)
  out.setHours(0, 0, 0, 0)
  out.setDate(out.getDate() - ((out.getDay() + 6) % 7))
  return out
}

const isoDate = (d: Date) => {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

const minutesOf = (t: string) => {
  const [h, m] = t.split(':').map(Number)
  return h * 60 + m
}

interface PlanMember {
  userId: string
  name: string
  role: string
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

/* Interview plans by requisition — the intake output. Rounds in order, each
   with the interviewers the manager aligned; the scheduler proposes times from
   every round member's calendar and pings them when their calendars block. */
function InterviewPlans({ users }: { users: UserRow[] }) {
  const [reqs, setReqs] = useState<Requisition[]>([])
  const [jobId, setJobId] = useState<string | null>(null)
  const [rounds, setRounds] = useState<PlanRound[]>([])
  const [addFor, setAddFor] = useState<string | null>(null)

  const loadReqs = useCallback(() => {
    api.get<Requisition[]>('/interview-plan/requisitions').then((r) => {
      setReqs(r.data)
      setJobId((cur) => cur ?? r.data.find((x) => x.roundCount > 0)?.jobId ?? r.data[0]?.jobId ?? null)
    })
  }, [])
  useEffect(loadReqs, [loadReqs])

  const loadPlan = useCallback(() => {
    if (!jobId) return
    api.get<PlanRound[]>('/interview-plan', { params: { jobId } }).then((r) => setRounds(r.data))
  }, [jobId])
  useEffect(loadPlan, [loadPlan])

  const refresh = () => {
    loadPlan()
    loadReqs()
  }

  const addRound = async () => {
    if (!jobId) return
    await api.post(`/interview-plan/${jobId}/rounds`, {})
    refresh()
  }

  const updateRound = async (id: string, patch: { name?: string; durationMin?: number }) => {
    await api.put(`/interview-plan/rounds/${id}`, patch)
    refresh()
  }

  const removeRound = async (id: string) => {
    await api.delete(`/interview-plan/rounds/${id}`)
    refresh()
  }

  const setMembers = async (round: PlanRound, userIds: string[]) => {
    await api.put(`/interview-plan/rounds/${round.id}/members`, { userIds })
    refresh()
  }

  const selectedReq = reqs.find((r) => r.jobId === jobId)

  return (
    <div className="card" style={{ marginBottom: 16 }}>
      <div className="card__head"><h3>Interview plans</h3></div>
      <div className="card__body" style={{ display: 'grid', gridTemplateColumns: '280px 1fr', gap: 18, alignItems: 'start' }}>
        {/* requisition list */}
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          <div style={{ fontSize: 12, color: 'var(--ink-4)', marginBottom: 6 }}>
            Requisitions — click one to define its plan during intake.
          </div>
          {reqs.map((r) => (
            <button
              key={r.jobId}
              onClick={() => setJobId(r.jobId)}
              style={{
                font: 'inherit', textAlign: 'left', cursor: 'pointer', border: 'none',
                background: r.jobId === jobId ? '#f0f3f8' : 'transparent',
                borderLeft: r.jobId === jobId ? '3px solid var(--bofa-navy, #012169)' : '3px solid transparent',
                padding: '9px 12px',
              }}
            >
              <div style={{ fontSize: 13.5, fontWeight: 600, color: 'var(--ink-1)' }}>{r.title}</div>
              <div style={{ fontSize: 12, color: 'var(--ink-4)', marginTop: 1 }}>
                {[r.recruiterName && `Recruiter ${r.recruiterName}`, r.hiringManagerName && `HM ${r.hiringManagerName}`]
                  .filter(Boolean)
                  .join(' · ') || '—'}
              </div>
              <div style={{ fontSize: 12, color: r.roundCount ? 'var(--ink-3)' : 'var(--ink-4)', marginTop: 1 }}>
                {r.roundCount ? `${r.roundCount} round${r.roundCount > 1 ? 's' : ''} defined` : 'No plan yet'}
              </div>
            </button>
          ))}
        </div>

        {/* rounds editor */}
        <div style={{ minWidth: 0 }}>
          {selectedReq && (
            <div style={{ fontSize: 13, color: 'var(--ink-3)', marginBottom: 10 }}>
              Plan for <strong style={{ color: 'var(--ink-1)' }}>{selectedReq.title}</strong> — the scheduler offers
              candidates times that work for every interviewer in the round, and notifies interviewers whose calendars
              block scheduling.
            </div>
          )}
          {rounds.length === 0 && (
            <div style={{ fontSize: 13, color: 'var(--ink-4)', padding: '14px 0' }}>
              No rounds yet — add the first round to start this requisition's plan.
            </div>
          )}
          {rounds.map((round) => (
            <div key={round.id} style={{ border: '1px solid var(--line, #e5e9f0)', borderRadius: 10, padding: '12px 14px', marginBottom: 10 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                <span className="badge">Round {round.roundNo}</span>
                <input
                  className="input"
                  style={{ width: 240, fontWeight: 600 }}
                  defaultValue={round.name}
                  onBlur={(e) => e.target.value.trim() && e.target.value.trim() !== round.name && updateRound(round.id, { name: e.target.value.trim() })}
                />
                <input
                  className="input"
                  type="number"
                  min={15}
                  step={15}
                  style={{ width: 76 }}
                  defaultValue={round.durationMin}
                  onBlur={(e) => Number(e.target.value) >= 15 && Number(e.target.value) !== round.durationMin && updateRound(round.id, { durationMin: Number(e.target.value) })}
                />
                <span style={{ fontSize: 12.5, color: 'var(--ink-4)' }}>min</span>
                <button className="btn btn--ghost btn--sm" style={{ marginLeft: 'auto' }} onClick={() => removeRound(round.id)}>
                  Remove
                </button>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', marginTop: 10 }}>
                <span style={{ fontSize: 12.5, color: 'var(--ink-4)' }}>Interviewers:</span>
                {round.members.map((m) => (
                  <span key={m.userId} className="badge" style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                    {m.name}
                    <button
                      onClick={() => setMembers(round, round.members.filter((x) => x.userId !== m.userId).map((x) => x.userId))}
                      style={{ font: 'inherit', border: 'none', background: 'none', cursor: 'pointer', color: 'var(--ink-4)', padding: 0, lineHeight: 1 }}
                      aria-label={`Remove ${m.name}`}
                    >
                      ×
                    </button>
                  </span>
                ))}
                {round.members.length === 0 && <span style={{ fontSize: 12.5, color: 'var(--ink-4)' }}>none yet</span>}
                {addFor === round.id ? (
                  <select
                    className="input"
                    style={{ width: 200 }}
                    autoFocus
                    defaultValue=""
                    onBlur={() => setAddFor(null)}
                    onChange={(e) => {
                      if (e.target.value) {
                        setMembers(round, [...round.members.map((m) => m.userId), e.target.value])
                      }
                      setAddFor(null)
                    }}
                  >
                    <option value="" disabled>Add interviewer…</option>
                    {users
                      .filter((u) => !round.members.some((m) => m.userId === u.id))
                      .map((u) => (
                        <option key={u.id} value={u.id}>{u.name} — {roleLabel(u.role)}</option>
                      ))}
                  </select>
                ) : (
                  <button className="btn btn--outline btn--sm" onClick={() => setAddFor(round.id)}>+ Add</button>
                )}
              </div>
            </div>
          ))}
          <button className="btn btn--outline btn--sm" onClick={addRound}>Add round</button>
        </div>
      </div>
    </div>
  )
}

export default function AvailabilityPage() {
  const [users, setUsers] = useState<UserRow[]>([])
  const [selected, setSelected] = useState<string | null>(null)
  const [detail, setDetail] = useState<Detail | null>(null)
  const [weekStart, setWeekStart] = useState(() => mondayOf(new Date()))
  const [events, setEvents] = useState<CalEvent[]>([])
  const [form, setForm] = useState<Settings | null>(null)
  const [savedTick, setSavedTick] = useState(0)
  const [error, setError] = useState<string | null>(null)

  // add-rule row state
  const [ruleDay, setRuleDay] = useState(1)
  const [ruleFrom, setRuleFrom] = useState('09:00')
  const [ruleTo, setRuleTo] = useState('12:00')
  const [ruleKind, setRuleKind] = useState<Rule['kind']>('NO_INTERVIEWS')

  const loadUsers = useCallback(() => {
    api.get<UserRow[]>('/availability/users').then((r) => {
      setUsers(r.data)
      setSelected((cur) => cur ?? r.data[0]?.id ?? null)
    })
  }, [])
  useEffect(loadUsers, [loadUsers])

  const loadDetail = useCallback(() => {
    if (!selected) return
    api.get<Detail>(`/availability/${selected}`).then((r) => {
      setDetail(r.data)
      setForm(r.data.settings)
    })
  }, [selected])
  useEffect(loadDetail, [loadDetail])

  useEffect(() => {
    if (!selected) return
    api
      .get<CalEvent[]>(`/availability/${selected}/calendar`, { params: { weekStart: isoDate(weekStart) } })
      .then((r) => setEvents(r.data))
  }, [selected, weekStart, savedTick])

  const saveSettings = async () => {
    if (!selected || !form) return
    setError(null)
    try {
      await api.put(`/availability/${selected}/settings`, form)
      setSavedTick((t) => t + 1)
      loadUsers()
      loadDetail()
    } catch (e) {
      setError((e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Could not save settings.')
    }
  }

  const addRule = async () => {
    if (!selected) return
    setError(null)
    try {
      await api.post(`/availability/${selected}/rules`, {
        dayOfWeek: ruleDay,
        startTime: ruleFrom,
        endTime: ruleTo,
        kind: ruleKind,
      })
      loadDetail()
      loadUsers()
    } catch (e) {
      setError((e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Could not add rule.')
    }
  }

  const deleteRule = async (id: string) => {
    await api.delete(`/availability/rules/${id}`)
    loadDetail()
    loadUsers()
  }

  const tz = detail?.settings.timezone ?? 'America/New_York'

  /* Events positioned into weekday columns, clamped to the visible band. */
  const positioned = useMemo(() => {
    const cols: { ev: CalEvent; top: number; height: number; label: string }[][] = DAYS.slice(0, 5).map(() => [])
    for (const ev of events) {
      const s = new Date(ev.startsAt)
      const e = new Date(ev.endsAt)
      const parts = new Intl.DateTimeFormat('en-US', {
        timeZone: tz, weekday: 'short', hour: 'numeric', minute: 'numeric', hour12: false,
      }).formatToParts(s)
      const weekday = parts.find((p) => p.type === 'weekday')?.value ?? 'Mon'
      const col = DAYS.indexOf(weekday)
      if (col < 0 || col > 4) continue
      const hour = Number(parts.find((p) => p.type === 'hour')?.value ?? 9)
      const minute = Number(parts.find((p) => p.type === 'minute')?.value ?? 0)
      const durMin = Math.max(30, (e.getTime() - s.getTime()) / 60000)
      const startMin = Math.max(hour * 60 + minute, DAY_START * 60)
      const endMin = Math.min(hour * 60 + minute + durMin, DAY_END * 60)
      if (endMin <= DAY_START * 60 || startMin >= DAY_END * 60) continue
      const label = s.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', timeZone: tz })
      cols[col].push({
        ev,
        top: ((startMin - DAY_START * 60) / 60) * HOUR_PX,
        height: Math.max(20, ((endMin - startMin) / 60) * HOUR_PX - 2),
        label,
      })
    }
    return cols
  }, [events, tz])

  /* Weekly rules painted as background stripes per weekday column. */
  const ruleBands = useMemo(() => {
    const cols: { rule: Rule; top: number; height: number }[][] = DAYS.slice(0, 5).map(() => [])
    for (const r of detail?.rules ?? []) {
      if (r.dayOfWeek > 5) continue
      const start = Math.max(minutesOf(r.startTime), DAY_START * 60)
      const end = Math.min(minutesOf(r.endTime), DAY_END * 60)
      if (end <= start) continue
      cols[r.dayOfWeek - 1].push({
        rule: r,
        top: ((start - DAY_START * 60) / 60) * HOUR_PX,
        height: ((end - start) / 60) * HOUR_PX,
      })
    }
    return cols
  }, [detail])

  const weekLabel = useMemo(() => {
    const end = new Date(weekStart)
    end.setDate(end.getDate() + 4)
    const f = (d: Date) => d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
    return `${f(weekStart)} – ${f(end)}`
  }, [weekStart])

  const shiftWeek = (dir: number) => {
    setWeekStart((w) => {
      const n = new Date(w)
      n.setDate(n.getDate() + dir * 7)
      return n
    })
  }

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <h1 className="page__title">Availability</h1>
          <p className="page__sub">
            Working windows, interviewing preferences and calendars. Rules here shape which times the scheduler offers candidates.
          </p>
        </div>
      </div>

      <InterviewPlans users={users} />

      <div style={{ display: 'grid', gridTemplateColumns: '250px 1fr', gap: 16, alignItems: 'start' }}>
        {/* interviewer list */}
        <div className="card">
          <div className="card__head"><h3>Interviewers</h3></div>
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {users.map((u) => (
              <button
                key={u.id}
                onClick={() => setSelected(u.id)}
                style={{
                  font: 'inherit', textAlign: 'left', cursor: 'pointer', border: 'none',
                  background: u.id === selected ? '#f0f3f8' : 'transparent',
                  borderLeft: u.id === selected ? '3px solid var(--bofa-navy, #012169)' : '3px solid transparent',
                  padding: '10px 14px',
                }}
              >
                <div style={{ fontSize: 13.5, fontWeight: 600, color: 'var(--ink-1)' }}>{u.name}</div>
                <div style={{ fontSize: 12, color: 'var(--ink-4)', marginTop: 1 }}>
                  {roleLabel(u.role)}
                  {u.ruleCount > 0 ? ` · ${u.ruleCount} rule${u.ruleCount > 1 ? 's' : ''}` : ''}
                </div>
              </button>
            ))}
          </div>
        </div>

        {detail && form && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16, minWidth: 0 }}>
            {error && (
              <div style={{ border: '1px solid #ead9ac', background: '#fdf5e3', color: '#7a5c00', borderRadius: 8, padding: '8px 12px', fontSize: 13 }}>
                {error}
              </div>
            )}

            {/* settings */}
            <div className="card">
              <div className="card__head">
                <h3>Working window & load</h3>
                <button className="btn btn--primary btn--sm" onClick={saveSettings}>Save</button>
              </div>
              <div className="card__body" style={{ display: 'flex', gap: 18, flexWrap: 'wrap', alignItems: 'flex-end' }}>
                <label className="field" style={{ width: 110 }}>
                  <span className="field__label">Work start</span>
                  <input className="input" type="time" value={form.workStart} onChange={(e) => setForm({ ...form, workStart: e.target.value })} />
                </label>
                <label className="field" style={{ width: 110 }}>
                  <span className="field__label">Work end</span>
                  <input className="input" type="time" value={form.workEnd} onChange={(e) => setForm({ ...form, workEnd: e.target.value })} />
                </label>
                <label className="field" style={{ width: 190 }}>
                  <span className="field__label">Timezone</span>
                  <select className="input" value={form.timezone} onChange={(e) => setForm({ ...form, timezone: e.target.value })}>
                    {(TIMEZONES.includes(form.timezone) ? TIMEZONES : [form.timezone, ...TIMEZONES]).map((z) => (
                      <option key={z} value={z}>{z.replace('_', ' ')}</option>
                    ))}
                  </select>
                </label>
                <label className="field" style={{ width: 120 }}>
                  <span className="field__label">Max per day</span>
                  <input className="input" type="number" min={1} value={form.maxPerDay} onChange={(e) => setForm({ ...form, maxPerDay: Number(e.target.value) })} />
                </label>
                <label className="field" style={{ width: 120 }}>
                  <span className="field__label">Max per week</span>
                  <input className="input" type="number" min={1} value={form.maxPerWeek} onChange={(e) => setForm({ ...form, maxPerWeek: Number(e.target.value) })} />
                </label>
              </div>
            </div>

            {/* weekly rules */}
            <div className="card">
              <div className="card__head"><h3>Weekly preferences</h3></div>
              <div className="card__body" style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {detail.rules.length === 0 && (
                  <div style={{ fontSize: 13, color: 'var(--ink-4)' }}>
                    No rules yet — interviews can be booked anywhere inside the working window.
                  </div>
                )}
                {detail.rules.map((r) => (
                  <div key={r.id} style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: 13.5, padding: '6px 0', borderBottom: '1px solid var(--line, #edf0f4)' }}>
                    <span style={{ width: 40, fontWeight: 600 }}>{DAYS[r.dayOfWeek - 1]}</span>
                    <span style={{ width: 110, color: 'var(--ink-2)' }}>{r.startTime} – {r.endTime}</span>
                    <span className={`badge ${r.kind === 'NO_INTERVIEWS' ? 'badge--danger' : 'badge--ok'}`}>
                      {r.kind === 'NO_INTERVIEWS' ? "Can't interview" : 'Dedicated interview time'}
                    </span>
                    <button className="btn btn--ghost btn--sm" style={{ marginLeft: 'auto' }} onClick={() => deleteRule(r.id)}>
                      Remove
                    </button>
                  </div>
                ))}
                <div style={{ display: 'flex', gap: 10, alignItems: 'flex-end', flexWrap: 'wrap', paddingTop: 8 }}>
                  <label className="field" style={{ width: 90 }}>
                    <span className="field__label">Day</span>
                    <select className="input" value={ruleDay} onChange={(e) => setRuleDay(Number(e.target.value))}>
                      {DAYS.slice(0, 5).map((d, i) => <option key={d} value={i + 1}>{d}</option>)}
                    </select>
                  </label>
                  <label className="field" style={{ width: 105 }}>
                    <span className="field__label">From</span>
                    <input className="input" type="time" value={ruleFrom} onChange={(e) => setRuleFrom(e.target.value)} />
                  </label>
                  <label className="field" style={{ width: 105 }}>
                    <span className="field__label">To</span>
                    <input className="input" type="time" value={ruleTo} onChange={(e) => setRuleTo(e.target.value)} />
                  </label>
                  <label className="field" style={{ width: 210 }}>
                    <span className="field__label">Type</span>
                    <select className="input" value={ruleKind} onChange={(e) => setRuleKind(e.target.value as Rule['kind'])}>
                      <option value="NO_INTERVIEWS">Can't interview</option>
                      <option value="INTERVIEW_BLOCK">Dedicated interview time</option>
                    </select>
                  </label>
                  <button className="btn btn--outline btn--sm" onClick={addRule}>Add rule</button>
                </div>
                <div style={{ fontSize: 12, color: 'var(--ink-4)' }}>
                  "Can't interview" blocks that window every week. When someone has dedicated interview time, candidates are only offered slots inside it.
                </div>
              </div>
            </div>

            {/* week calendar */}
            <div className="card">
              <div className="card__head" style={{ display: 'flex', alignItems: 'center' }}>
                <h3>Calendar</h3>
                <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 8 }}>
                  <button className="btn btn--ghost btn--sm" onClick={() => shiftWeek(-1)}>‹</button>
                  <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-2)', minWidth: 110, textAlign: 'center' }}>{weekLabel}</span>
                  <button className="btn btn--ghost btn--sm" onClick={() => shiftWeek(1)}>›</button>
                </div>
              </div>
              <div className="card__body">
                <div style={{ display: 'grid', gridTemplateColumns: '48px repeat(5, 1fr)', gap: 0 }}>
                  <div />
                  {DAYS.slice(0, 5).map((d, i) => {
                    const day = new Date(weekStart)
                    day.setDate(day.getDate() + i)
                    return (
                      <div key={d} style={{ textAlign: 'center', fontSize: 12.5, fontWeight: 650, color: 'var(--ink-3)', paddingBottom: 6 }}>
                        {d} {day.getDate()}
                      </div>
                    )
                  })}
                  {/* hour gutter */}
                  <div style={{ position: 'relative', height: (DAY_END - DAY_START) * HOUR_PX }}>
                    {Array.from({ length: DAY_END - DAY_START }, (_, i) => (
                      <div key={i} style={{ position: 'absolute', top: i * HOUR_PX - 6, right: 8, fontSize: 10.5, color: 'var(--ink-4)' }}>
                        {DAY_START + i}:00
                      </div>
                    ))}
                  </div>
                  {DAYS.slice(0, 5).map((d, col) => (
                    <div key={d} style={{ position: 'relative', height: (DAY_END - DAY_START) * HOUR_PX, borderLeft: '1px solid var(--line, #edf0f4)' }}>
                      {Array.from({ length: DAY_END - DAY_START }, (_, i) => (
                        <div key={i} style={{ position: 'absolute', top: i * HOUR_PX, left: 0, right: 0, borderTop: '1px solid #f2f4f8' }} />
                      ))}
                      {/* rule bands under events */}
                      {ruleBands[col].map(({ rule, top, height }) => (
                        <div
                          key={rule.id}
                          title={rule.kind === 'NO_INTERVIEWS' ? "Can't interview (weekly)" : 'Dedicated interview time (weekly)'}
                          style={{
                            position: 'absolute', top, height, left: 1, right: 1, borderRadius: 4,
                            ...(rule.kind === 'NO_INTERVIEWS'
                              ? { background: 'repeating-linear-gradient(-45deg, rgba(184,74,74,0.07), rgba(184,74,74,0.07) 5px, rgba(184,74,74,0.13) 5px, rgba(184,74,74,0.13) 10px)' }
                              : { background: 'rgba(58,124,74,0.07)', border: '1px dashed rgba(58,124,74,0.45)' }),
                          }}
                        />
                      ))}
                      {positioned[col].map(({ ev, top, height, label }) => (
                        <div
                          key={ev.id}
                          title={`${KIND_LABEL[ev.kind] ?? ev.kind} · ${ev.title}`}
                          style={{
                            position: 'absolute', top, height, left: 3, right: 3, borderRadius: 6,
                            padding: '3px 7px', fontSize: 11, lineHeight: 1.25, overflow: 'hidden',
                            ...(EVENT_STYLE[ev.kind] ?? EVENT_STYLE.BUSY),
                          }}
                        >
                          <div style={{ fontWeight: 650, whiteSpace: 'nowrap', textOverflow: 'ellipsis', overflow: 'hidden' }}>{ev.title}</div>
                          {height > 34 && <div style={{ opacity: 0.75 }}>{label}</div>}
                        </div>
                      ))}
                    </div>
                  ))}
                </div>
                {/* legend */}
                <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginTop: 14, fontSize: 12, color: 'var(--ink-3)' }}>
                  {(['INTERVIEW', 'BUSY', 'TENTATIVE', 'VACATION', 'IN_OFFICE'] as const).map((k) => (
                    <span key={k} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                      <span style={{ width: 12, height: 12, borderRadius: 3, display: 'inline-block', ...(EVENT_STYLE[k]) }} />
                      {KIND_LABEL[k]}
                    </span>
                  ))}
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                    <span style={{ width: 12, height: 12, borderRadius: 3, display: 'inline-block', background: 'repeating-linear-gradient(-45deg, rgba(184,74,74,0.07), rgba(184,74,74,0.07) 3px, rgba(184,74,74,0.16) 3px, rgba(184,74,74,0.16) 6px)' }} />
                    Can't interview
                  </span>
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                    <span style={{ width: 12, height: 12, borderRadius: 3, display: 'inline-block', background: 'rgba(58,124,74,0.07)', border: '1px dashed rgba(58,124,74,0.45)' }} />
                    Dedicated interview time
                  </span>
                </div>
                <div style={{ fontSize: 12, color: 'var(--ink-4)', marginTop: 8 }}>
                  Times shown in {tz.replace('_', ' ')}. Tentative and in-office entries don't block scheduling; busy, vacation and interviews do.
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
