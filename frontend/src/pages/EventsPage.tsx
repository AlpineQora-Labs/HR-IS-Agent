import { useMemo, useState } from 'react'
import { useStore } from '@/state/store'
import SlideOver from '@/components/SlideOver'
import { useCreateEvent, useEvents } from '@/api/hooks'
import { date } from '@/lib/format'
import type { EventRow } from '@/api/types'

// Campus & hiring events, modeled on how Handshake / Yello / RippleMatch run them:
// a conversion funnel (registered → attended → hires), event formats, and a
// stepped creation flow (basics → sessions → registration → review).

const TYPE_OPTIONS = [
  { value: 'CAMPUS', label: 'Campus drive' },
  { value: 'CAREER_FAIR', label: 'Career fair' },
  { value: 'HIRING_EVENT', label: 'Hiring event' },
  { value: 'WEBINAR', label: 'Webinar / info session' },
]

function typeBadge(type: string) {
  const key = type.toUpperCase()
  if (key === 'CAMPUS') return 'badge--purple'
  if (key === 'CAREER_FAIR' || key === 'FAIR') return 'badge--info'
  if (key === 'HIRING_EVENT' || key === 'HIRING') return 'badge--ok'
  if (key === 'WEBINAR' || key === 'VIRTUAL') return 'badge--warn'
  return 'badge'
}

function typeLabel(type: string) {
  return TYPE_OPTIONS.find((t) => t.value === type.toUpperCase())?.label ?? type
}

function formatOf(e: EventRow) {
  const t = e.type.toUpperCase()
  const loc = (e.location || '').toLowerCase()
  if (t === 'WEBINAR' || loc.includes('virtual') || loc.includes('online') || loc.includes('zoom')) return 'Virtual'
  return 'In-person'
}

const rate = (num: number, den: number) => (den > 0 ? Math.round((num / den) * 100) : 0)
const isUpcoming = (iso: string) => new Date(iso).getTime() >= Date.now()

function StatTile({ label, value, meta }: { label: string; value: string | number; meta?: string }) {
  return (
    <div className="stat">
      <div className="stat__label">{label}</div>
      <div className="stat__value">{value}</div>
      {meta ? <div className="stat__meta">{meta}</div> : null}
    </div>
  )
}

// Registered → Attended → Hires conversion bar.
function FunnelBar({ e }: { e: EventRow }) {
  const att = rate(e.attended, e.registrations)
  const hire = rate(e.hires, e.registrations)
  return (
    <div style={{ marginTop: 14, borderTop: '1px solid var(--line)', paddingTop: 14 }}>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12 }}>
        {[
          { label: 'Registered', value: e.registrations },
          { label: 'Attended', value: e.attended },
          { label: 'Hires', value: e.hires },
        ].map((m) => (
          <div key={m.label}>
            <div className="t-num" style={{ fontFamily: 'var(--font-display)', fontWeight: 600, fontSize: 22, color: 'var(--ink-0)' }}>
              {m.value}
            </div>
            <div className="eyebrow" style={{ fontSize: 9, marginTop: 4 }}>{m.label}</div>
          </div>
        ))}
      </div>
      <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
        <Meter label="Attendance" pct={att} color="var(--bofa-navy)" />
        <Meter label="Hire rate" pct={hire} color="#1D9E75" />
      </div>
    </div>
  )
}

function Meter({ label, pct, color }: { label: string; pct: number; color: string }) {
  return (
    <div style={{ flex: 1 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 10.5, color: 'var(--ink-4)', marginBottom: 3 }}>
        <span>{label}</span>
        <span className="t-num" style={{ color: 'var(--ink-2)', fontWeight: 600 }}>{pct}%</span>
      </div>
      <div style={{ height: 5, background: 'var(--app-sunken)', borderRadius: 3, overflow: 'hidden' }}>
        <div style={{ width: `${pct}%`, height: '100%', background: color }} />
      </div>
    </div>
  )
}

function EventDetail({ e, onClose }: { e: EventRow; onClose: () => void }) {
  return (
    <SlideOver label={`${e.name} details`} onClose={onClose} width={440}>
      <div style={{ padding: '18px 20px 16px', borderBottom: '1px solid var(--line)' }}>
        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
          <div style={{ fontFamily: 'var(--font-display)', fontSize: 19, color: 'var(--ink-0)', lineHeight: 1.2 }}>{e.name}</div>
          <button type="button" onClick={onClose} aria-label="Close" style={{ border: 0, background: 'none', cursor: 'pointer', fontSize: 20, color: 'var(--ink-4)' }}>×</button>
        </div>
        <div style={{ display: 'flex', gap: 6, marginTop: 10 }}>
          <span className={`badge ${typeBadge(e.type)}`}>{typeLabel(e.type)}</span>
          <span className="badge">{formatOf(e)}</span>
        </div>
      </div>
      <div style={{ flex: 1, overflowY: 'auto', padding: '16px 20px' }}>
        <Fact label="When">{date(e.startsAt)}</Fact>
        <Fact label={formatOf(e) === 'Virtual' ? 'Link' : 'Location'}>{e.location || '—'}</Fact>
        <Fact label="Status">{isUpcoming(e.startsAt) ? 'Upcoming' : 'Past'}</Fact>

        <div style={{ marginTop: 18 }}>
          <div className="eyebrow" style={{ marginBottom: 8 }}>Conversion funnel</div>
          <FunnelStep label="Registered" value={e.registrations} pct={100} />
          <FunnelStep label="Attended" value={e.attended} pct={rate(e.attended, e.registrations)} />
          <FunnelStep label="Hires" value={e.hires} pct={rate(e.hires, e.registrations)} />
        </div>
      </div>
      <div style={{ padding: '14px 20px', borderTop: '1px solid var(--line)', fontSize: 12, color: 'var(--ink-4)' }}>
        Sessions, registrants, and linked jobs appear here once the event runs.
      </div>
    </SlideOver>
  )
}

function FunnelStep({ label, value, pct }: { label: string; value: number; pct: number }) {
  return (
    <div style={{ marginBottom: 10 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12.5, marginBottom: 4 }}>
        <span style={{ color: 'var(--ink-2)' }}>{label}</span>
        <span className="t-num" style={{ color: 'var(--ink-4)' }}>
          <span style={{ color: 'var(--ink-1)', fontWeight: 600 }}>{value}</span> · {pct}%
        </span>
      </div>
      <div style={{ height: 8, background: 'var(--app-sunken)', borderRadius: 4, overflow: 'hidden' }}>
        <div style={{ width: `${Math.max(2, pct)}%`, height: '100%', background: 'var(--bofa-navy)' }} />
      </div>
    </div>
  )
}

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, padding: '7px 0', fontSize: 13 }}>
      <span style={{ color: 'var(--ink-4)' }}>{label}</span>
      <span style={{ color: 'var(--ink-1)', textAlign: 'right' }}>{children}</span>
    </div>
  )
}

// ── Create wizard ─────────────────────────────────────────────────────────────

type Draft = {
  name: string
  type: string
  format: 'In-person' | 'Virtual' | 'Hybrid'
  location: string
  day: string
  time: string
  groupSessions: number
  oneOnOne: boolean
  slotMins: number
  capacity: string
  qualifications: string
  reminders: boolean
}

const EMPTY_DRAFT: Draft = {
  name: '',
  type: 'CAMPUS',
  format: 'In-person',
  location: '',
  day: '',
  time: '09:00',
  groupSessions: 1,
  oneOnOne: true,
  slotMins: 15,
  capacity: '',
  qualifications: '',
  reminders: true,
}

const STEPS = ['Basics', 'Sessions', 'Registration', 'Review']

function CreateEventWizard({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const create = useCreateEvent()
  const { toastMsg } = useStore()
  const [step, setStep] = useState(0)
  const [d, setD] = useState<Draft>(EMPTY_DRAFT)
  const set = (patch: Partial<Draft>) => setD((prev) => ({ ...prev, ...patch }))

  const basicsValid = d.name.trim().length > 0 && d.day.length > 0
  const isLast = step === STEPS.length - 1

  function submit() {
    const startsAt = new Date(`${d.day}T${d.time || '09:00'}:00`).toISOString()
    create.mutate(
      { name: d.name.trim(), type: d.type, location: d.location.trim(), startsAt },
      { onSuccess: () => { toastMsg(`Event "${d.name.trim()}" created`); onCreated(); onClose() } },
    )
  }

  return (
    <SlideOver label="New event" onClose={onClose} width={460}>
      <div style={{ padding: '18px 20px 14px', borderBottom: '1px solid var(--line)' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ fontFamily: 'var(--font-display)', fontSize: 19, color: 'var(--ink-0)' }}>New event</div>
          <button type="button" onClick={onClose} aria-label="Close" style={{ border: 0, background: 'none', cursor: 'pointer', fontSize: 20, color: 'var(--ink-4)' }}>×</button>
        </div>
        <div style={{ display: 'flex', gap: 6, marginTop: 14 }}>
          {STEPS.map((s, i) => (
            <div key={s} style={{ flex: 1 }}>
              <div style={{ height: 4, borderRadius: 2, background: i <= step ? 'var(--bofa-navy)' : 'var(--app-sunken)' }} />
              <div style={{ fontSize: 10.5, marginTop: 4, color: i === step ? 'var(--ink-1)' : 'var(--ink-4)', fontWeight: i === step ? 600 : 400 }}>{s}</div>
            </div>
          ))}
        </div>
      </div>

      <div style={{ flex: 1, overflowY: 'auto', padding: '18px 20px' }}>
        {step === 0 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <Field label="Event name">
              <input className="input" value={d.name} onChange={(e) => set({ name: e.target.value })} placeholder="MIT Fall Career Fair" />
            </Field>
            <Field label="Type">
              <select className="select" value={d.type} onChange={(e) => set({ type: e.target.value })}>
                {TYPE_OPTIONS.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
              </select>
            </Field>
            <Field label="Format">
              <div className="segmented" role="group" aria-label="Format">
                {(['In-person', 'Virtual', 'Hybrid'] as const).map((f) => (
                  <button key={f} aria-pressed={d.format === f} onClick={() => set({ format: f })}>{f}</button>
                ))}
              </div>
            </Field>
            <Field label={d.format === 'Virtual' ? 'Meeting link' : 'Location / campus'}>
              <input className="input" value={d.location} onChange={(e) => set({ location: e.target.value })} placeholder={d.format === 'Virtual' ? 'https://…' : 'Cambridge, MA'} />
            </Field>
            <div style={{ display: 'flex', gap: 12 }}>
              <Field label="Date" style={{ flex: 1 }}>
                <input className="input" type="date" value={d.day} onChange={(e) => set({ day: e.target.value })} />
              </Field>
              <Field label="Start time" style={{ flex: 1 }}>
                <input className="input" type="time" value={d.time} onChange={(e) => set({ time: e.target.value })} />
              </Field>
            </div>
          </div>
        )}

        {step === 1 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <p className="sub" style={{ fontSize: 12.5, margin: 0 }}>How will you meet candidates at this event?</p>
            <Field label="Group sessions">
              <input className="input" type="number" min={0} value={d.groupSessions} onChange={(e) => set({ groupSessions: Number(e.target.value) })} />
            </Field>
            <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13.5, color: 'var(--ink-1)' }}>
              <input type="checkbox" checked={d.oneOnOne} onChange={(e) => set({ oneOnOne: e.target.checked })} />
              Offer 1:1 sessions with recruiters
            </label>
            {d.oneOnOne && (
              <Field label="1:1 slot length">
                <select className="select" value={d.slotMins} onChange={(e) => set({ slotMins: Number(e.target.value) })}>
                  {[10, 15, 20, 30].map((m) => <option key={m} value={m}>{m} min</option>)}
                </select>
              </Field>
            )}
          </div>
        )}

        {step === 2 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <Field label="Capacity (optional)">
              <input className="input" type="number" min={0} value={d.capacity} onChange={(e) => set({ capacity: e.target.value })} placeholder="No limit" />
            </Field>
            <Field label="Qualification filters">
              <textarea className="input" rows={3} value={d.qualifications} onChange={(e) => set({ qualifications: e.target.value })} placeholder="e.g. Graduating 2026 · CS, CE majors · GPA ≥ 3.0" style={{ resize: 'vertical', fontFamily: 'inherit' }} />
            </Field>
            <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13.5, color: 'var(--ink-1)' }}>
              <input type="checkbox" checked={d.reminders} onChange={(e) => set({ reminders: e.target.checked })} />
              Send automated reminders + check-in
            </label>
          </div>
        )}

        {step === 3 && (
          <div>
            <Fact label="Name">{d.name || '—'}</Fact>
            <Fact label="Type">{typeLabel(d.type)}</Fact>
            <Fact label="Format">{d.format}</Fact>
            <Fact label={d.format === 'Virtual' ? 'Link' : 'Location'}>{d.location || '—'}</Fact>
            <Fact label="When">{d.day ? `${d.day} ${d.time}` : '—'}</Fact>
            <Fact label="Sessions">{d.groupSessions} group{d.oneOnOne ? ` · 1:1 ${d.slotMins}m` : ''}</Fact>
            <Fact label="Capacity">{d.capacity || 'No limit'}</Fact>
            <Fact label="Reminders">{d.reminders ? 'On' : 'Off'}</Fact>
            <div style={{ fontSize: 11.5, color: 'var(--ink-4)', marginTop: 12, lineHeight: 1.5 }}>
              The event is created now with its basics. Sessions, capacity, and registration rules are captured for planning and
              will sync to the event once server-side scheduling is enabled.
            </div>
          </div>
        )}
      </div>

      <div style={{ display: 'flex', gap: 8, padding: '14px 20px', borderTop: '1px solid var(--line)' }}>
        {step > 0 && (
          <button className="btn btn--ghost btn--sm" onClick={() => setStep((s) => s - 1)}>Back</button>
        )}
        <div style={{ flex: 1 }} />
        {!isLast ? (
          <button
            className="btn btn--primary btn--sm"
            disabled={step === 0 && !basicsValid}
            onClick={() => setStep((s) => s + 1)}
          >
            Next
          </button>
        ) : (
          <button className="btn btn--primary btn--sm" disabled={!basicsValid || create.isPending} onClick={submit}>
            {create.isPending ? 'Creating…' : 'Create event'}
          </button>
        )}
      </div>
    </SlideOver>
  )
}

function Field({ label, children, style }: { label: string; children: React.ReactNode; style?: React.CSSProperties }) {
  return (
    <label style={{ display: 'block', ...style }}>
      <div className="eyebrow" style={{ marginBottom: 6 }}>{label}</div>
      {children}
    </label>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────

type Filter = 'upcoming' | 'past' | 'all'

export default function EventsPage() {
  const { data, isLoading } = useEvents()
  const [filter, setFilter] = useState<Filter>('upcoming')
  const [selected, setSelected] = useState<EventRow | null>(null)
  const [creating, setCreating] = useState(false)

  const kpis = useMemo(() => {
    const list = data ?? []
    const upcoming = list.filter((e) => isUpcoming(e.startsAt)).length
    const reg = list.reduce((n, e) => n + e.registrations, 0)
    const att = list.reduce((n, e) => n + e.attended, 0)
    const hires = list.reduce((n, e) => n + e.hires, 0)
    return { upcoming, reg, attRate: rate(att, reg), hires }
  }, [data])

  const rows = useMemo(() => {
    const list = data ?? []
    const f = list.filter((e) =>
      filter === 'all' ? true : filter === 'upcoming' ? isUpcoming(e.startsAt) : !isUpcoming(e.startsAt),
    )
    return [...f].sort((a, b) => new Date(a.startsAt).getTime() - new Date(b.startsAt).getTime())
  }, [data, filter])

  return (
    <div>
      <div className="page-head">
        <div className="crumb">
          <span className="dot" />
          Engagement · Events &amp; Campus
        </div>
        <div className="page-head__row">
          <div>
            <h1>Events &amp; Campus</h1>
            <p className="sub">
              Run campus drives and hiring events end to end — registration through attendance to offers, with the conversion
              funnel in one view.
            </p>
          </div>
          <button className="btn btn--primary" onClick={() => setCreating(true)}>New event</button>
        </div>
      </div>

      <div className="grid-stats" style={{ gridTemplateColumns: 'repeat(4,1fr)', marginBottom: 18 }}>
        <StatTile label="Upcoming events" value={kpis.upcoming} meta="Scheduled ahead" />
        <StatTile label="Total registrations" value={kpis.reg.toLocaleString()} meta="Across all events" />
        <StatTile label="Avg attendance" value={`${kpis.attRate}%`} meta="Registered → attended" />
        <StatTile label="Hires from events" value={kpis.hires} meta="Event-sourced" />
      </div>

      <div className="segmented" role="group" aria-label="Filter" style={{ marginBottom: 16 }}>
        <button aria-pressed={filter === 'upcoming'} onClick={() => setFilter('upcoming')}>Upcoming</button>
        <button aria-pressed={filter === 'past'} onClick={() => setFilter('past')}>Past</button>
        <button aria-pressed={filter === 'all'} onClick={() => setFilter('all')}>All</button>
      </div>

      {isLoading ? (
        <div className="card"><div style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>Loading events…</div></div>
      ) : rows.length === 0 ? (
        <div className="card">
          <div style={{ padding: '44px 20px', textAlign: 'center' }}>
            <div style={{ fontFamily: 'var(--font-display)', fontSize: 18, color: 'var(--ink-0)' }}>
              {filter === 'upcoming' ? 'No upcoming events' : filter === 'past' ? 'No past events' : 'No events yet'}
            </div>
            <div style={{ fontSize: 13, color: 'var(--ink-4)', margin: '6px 0 16px' }}>Schedule a campus drive, career fair, or hiring event.</div>
            <button className="btn btn--primary btn--sm" onClick={() => setCreating(true)}>New event</button>
          </div>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 16 }}>
          {rows.map((e) => (
            <button
              key={e.id}
              onClick={() => setSelected(e)}
              className="card"
              style={{ padding: 20, display: 'block', width: '100%', textAlign: 'left', cursor: 'pointer', border: '1px solid var(--line)', background: 'var(--app-panel)', font: 'inherit' }}
            >
              <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
                <h3 style={{ margin: 0, fontSize: 17 }}>{e.name}</h3>
                <span className={`badge ${typeBadge(e.type)}`}>{typeLabel(e.type)}</span>
              </div>
              <div className="muted" style={{ marginTop: 8, display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
                <span>{e.location || 'TBD'}</span>
                <span style={{ color: 'var(--ink-5)' }}>·</span>
                <span>{date(e.startsAt)}</span>
                <span className="badge" style={{ marginLeft: 'auto' }}>{formatOf(e)}</span>
              </div>
              <FunnelBar e={e} />
            </button>
          ))}
        </div>
      )}

      {selected && <EventDetail e={selected} onClose={() => setSelected(null)} />}
      {creating && <CreateEventWizard onClose={() => setCreating(false)} onCreated={() => setFilter('all')} />}
    </div>
  )
}
