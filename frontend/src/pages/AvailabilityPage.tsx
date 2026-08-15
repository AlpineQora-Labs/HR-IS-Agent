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

/* Interview setup — pick a requisition from the grid, then define its
   interview WORKFLOW: a left-to-right flow of rounds (Round 1 -> Round 2 ->
   Offer), each round carrying the interviewers aligned at intake. As soon as
   interviewers are on a round, the engine reads their calendars live and shows
   whether the round is bookable and when. */

interface RoundHealth {
  nextAvailable: string | null
  openSlots: number
  blocked: boolean
}

const initialsOf = (name: string) =>
  name.split(/\s+/).map((w) => w[0]).slice(0, 2).join('').toUpperCase()

const nextLabel = (iso: string) => {
  const d = new Date(iso)
  return `${d.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' })} · ${d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })}`
}

/* Terminal node of the flow rail (Start / Offer). */
function EndCap({ label }: { label: string }) {
  return (
    <div
      style={{
        alignSelf: 'center', flexShrink: 0, padding: '8px 16px', borderRadius: 999,
        background: 'var(--bofa-navy, #012169)', color: '#fff', fontSize: 12.5, fontWeight: 650,
        letterSpacing: '0.01em', whiteSpace: 'nowrap',
      }}
    >
      {label}
    </div>
  )
}

/* Connector arrow between flow nodes. */
function FlowArrow() {
  return (
    <svg width="34" height="12" viewBox="0 0 34 12" style={{ alignSelf: 'center', flexShrink: 0 }} aria-hidden>
      <line x1="0" y1="6" x2="26" y2="6" stroke="#b7c0cf" strokeWidth="1.5" />
      <path d="M25 1.5 L32 6 L25 10.5" fill="none" stroke="#b7c0cf" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function RoundNode({
  round,
  health,
  users,
  onRename,
  onDuration,
  onRemove,
  onMembers,
}: {
  round: PlanRound
  health: RoundHealth | undefined
  users: UserRow[]
  onRename: (name: string) => void
  onDuration: (min: number) => void
  onRemove: () => void
  onMembers: (userIds: string[]) => void
}) {
  const [adding, setAdding] = useState(false)
  const hasPeople = round.members.length > 0
  return (
    <div
      style={{
        width: 250, flexShrink: 0, background: '#fff', border: '1px solid var(--line, #dfe4ec)',
        borderRadius: 12, boxShadow: '0 1px 2px rgba(16,22,38,0.05)', display: 'flex', flexDirection: 'column',
      }}
    >
      <div style={{ padding: '10px 14px 8px', borderBottom: '1px solid var(--line, #eef1f6)', display: 'flex', alignItems: 'center', gap: 8 }}>
        <span style={{ fontSize: 10.5, fontWeight: 700, letterSpacing: '0.08em', color: 'var(--ink-4)' }}>
          ROUND {round.roundNo}
        </span>
        <button
          className="btn btn--ghost btn--sm"
          style={{ marginLeft: 'auto', padding: '2px 8px' }}
          onClick={onRemove}
          aria-label={`Remove round ${round.roundNo}`}
        >
          ×
        </button>
      </div>
      <div style={{ padding: '10px 14px 12px', display: 'flex', flexDirection: 'column', gap: 10, flex: 1 }}>
        <input
          className="input"
          style={{ fontWeight: 650, fontSize: 13.5 }}
          defaultValue={round.name}
          onBlur={(e) => {
            const v = e.target.value.trim()
            if (v && v !== round.name) onRename(v)
          }}
        />
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <input
            className="input"
            type="number"
            min={15}
            step={15}
            style={{ width: 68 }}
            defaultValue={round.durationMin}
            onBlur={(e) => {
              const v = Number(e.target.value)
              if (v >= 15 && v !== round.durationMin) onDuration(v)
            }}
          />
          <span style={{ fontSize: 12, color: 'var(--ink-4)' }}>minutes</span>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          {round.members.map((m) => (
            <div key={m.userId} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span
                className="avatar"
                style={{ width: 26, height: 26, fontSize: 10, flexShrink: 0 }}
              >
                {initialsOf(m.name)}
              </span>
              <span style={{ minWidth: 0, flex: 1 }}>
                <span style={{ display: 'block', fontSize: 12.5, fontWeight: 600, color: 'var(--ink-1)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {m.name}
                </span>
                <span style={{ display: 'block', fontSize: 11, color: 'var(--ink-4)' }}>{roleLabel(m.role)}</span>
              </span>
              <button
                onClick={() => onMembers(round.members.filter((x) => x.userId !== m.userId).map((x) => x.userId))}
                style={{ font: 'inherit', border: 'none', background: 'none', cursor: 'pointer', color: 'var(--ink-4)', padding: 2, lineHeight: 1 }}
                aria-label={`Remove ${m.name}`}
              >
                ×
              </button>
            </div>
          ))}
          {adding ? (
            <select
              className="input"
              autoFocus
              defaultValue=""
              onBlur={() => setAdding(false)}
              onChange={(e) => {
                if (e.target.value) onMembers([...round.members.map((m) => m.userId), e.target.value])
                setAdding(false)
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
            <button className="btn btn--outline btn--sm" style={{ alignSelf: 'flex-start' }} onClick={() => setAdding(true)}>
              + Interviewer
            </button>
          )}
        </div>

        {/* the engine's live calendar read for this lineup */}
        <div style={{ marginTop: 'auto', paddingTop: 8, borderTop: '1px solid var(--line, #eef1f6)', fontSize: 11.5 }}>
          {!hasPeople ? (
            <span style={{ color: 'var(--ink-4)' }}>Add interviewers to check calendars</span>
          ) : !health ? (
            <span style={{ color: 'var(--ink-4)' }}>Checking calendars…</span>
          ) : health.blocked ? (
            <span style={{ color: '#a33a3a', fontWeight: 600 }}>● Calendars blocked — no open times</span>
          ) : (
            <span style={{ color: 'var(--ink-3)' }}>
              <span style={{ color: '#2e7d43' }}>●</span> Next open: {health.nextAvailable ? nextLabel(health.nextAvailable) : '—'}
            </span>
          )}
        </div>
      </div>
    </div>
  )
}

function InterviewPlans({ users }: { users: UserRow[] }) {
  const [reqs, setReqs] = useState<Requisition[]>([])
  const [openId, setOpenId] = useState<string | null>(null)
  const [rounds, setRounds] = useState<PlanRound[]>([])
  const [health, setHealth] = useState<Record<string, RoundHealth>>({})

  const loadReqs = useCallback(() => {
    api.get<Requisition[]>('/interview-plan/requisitions').then((r) => setReqs(r.data))
  }, [])
  useEffect(loadReqs, [loadReqs])

  const loadPlan = useCallback(() => {
    if (!openId) return
    api.get<PlanRound[]>('/interview-plan', { params: { jobId: openId } }).then((r) => {
      setRounds(r.data)
      for (const round of r.data) {
        if (round.members.length === 0) continue
        api.get<RoundHealth>(`/interview-plan/rounds/${round.id}/health`)
          .then((h) => setHealth((cur) => ({ ...cur, [round.id]: h.data })))
          .catch(() => {})
      }
    })
  }, [openId])
  useEffect(loadPlan, [loadPlan])

  const refresh = () => {
    loadPlan()
    loadReqs()
  }

  const open = reqs.find((r) => r.jobId === openId) ?? null

  if (open) {
    return (
      <div className="card" style={{ marginBottom: 16 }}>
        <div className="card__head" style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <button className="btn btn--outline btn--sm" onClick={() => setOpenId(null)}>‹ Requisitions</button>
          <h3 style={{ margin: 0 }}>{open.title} — interview workflow</h3>
          <span style={{ fontSize: 12.5, color: 'var(--ink-4)', marginLeft: 'auto' }}>
            {[open.recruiterName && `Recruiter ${open.recruiterName}`, open.hiringManagerName && `HM ${open.hiringManagerName}`]
              .filter(Boolean)
              .join(' · ')}
          </span>
        </div>
        <div className="card__body">
          <div style={{ display: 'flex', gap: 0, alignItems: 'stretch', overflowX: 'auto', padding: '6px 2px 10px' }}>
            <EndCap label="Candidate ready" />
            <FlowArrow />
            {rounds.map((round) => (
              <div key={round.id} style={{ display: 'flex', alignItems: 'stretch' }}>
                <RoundNode
                  round={round}
                  health={health[round.id]}
                  users={users}
                  onRename={(name) => api.put(`/interview-plan/rounds/${round.id}`, { name }).then(refresh)}
                  onDuration={(durationMin) => api.put(`/interview-plan/rounds/${round.id}`, { durationMin }).then(refresh)}
                  onRemove={() => api.delete(`/interview-plan/rounds/${round.id}`).then(refresh)}
                  onMembers={(userIds) => api.put(`/interview-plan/rounds/${round.id}/members`, { userIds }).then(refresh)}
                />
                <FlowArrow />
              </div>
            ))}
            <button
              onClick={() => api.post(`/interview-plan/${open.jobId}/rounds`, {}).then(refresh)}
              style={{
                width: 150, flexShrink: 0, alignSelf: 'stretch', minHeight: 150, cursor: 'pointer',
                border: '1.5px dashed #b7c0cf', borderRadius: 12, background: 'transparent',
                font: 'inherit', fontSize: 13, fontWeight: 600, color: 'var(--ink-3)',
              }}
            >
              + Add round
            </button>
            <FlowArrow />
            <EndCap label="Advance to offer" />
          </div>
          <p style={{ fontSize: 12, color: 'var(--ink-4)', margin: '10px 2px 0' }}>
            Each round is scheduled against every listed interviewer's calendar — working windows, weekly preferences,
            vacations and load caps all apply. Blocked rounds notify their interviewers automatically.
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="card" style={{ marginBottom: 16 }}>
      <div className="card__head"><h3>Interview setup</h3></div>
      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>Requisition</th>
              <th>Recruiter</th>
              <th>Hiring manager</th>
              <th className="t-right">Rounds</th>
              <th>Plan</th>
            </tr>
          </thead>
          <tbody>
            {reqs.length === 0 ? (
              <tr>
                <td colSpan={5} style={{ textAlign: 'center', color: 'var(--ink-4)', padding: '30px 18px' }}>
                  No open requisitions.
                </td>
              </tr>
            ) : (
              reqs.map((r) => (
                <tr key={r.jobId} onClick={() => setOpenId(r.jobId)} style={{ cursor: 'pointer' }}>
                  <td>
                    <span className="t-strong" style={{ display: 'block' }}>{r.title}</span>
                    <span className="t-muted" style={{ display: 'block', fontSize: 12 }}>{r.department || '—'}</span>
                  </td>
                  <td className="t-muted">{r.recruiterName || '—'}</td>
                  <td className="t-muted">{r.hiringManagerName || '—'}</td>
                  <td className="t-num t-right">{r.roundCount || '—'}</td>
                  <td>
                    {r.roundCount > 0
                      ? <span className="badge badge--ok">Configured</span>
                      : <span className="badge badge--warn">Not set</span>}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
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
