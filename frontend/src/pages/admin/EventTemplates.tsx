import { useState } from 'react'
import SlideOver from '@/components/SlideOver'
import { useDeleteEventTemplate, useEventTemplates, useFormsList, useSaveEventTemplate } from '@/api/hooks'
import type { EventTemplate } from '@/api/types'

/* Event templates: everything that is the same for a kind of event, kept once. A new event
   starts from one and takes its settings. Changing a template changes only events created
   afterwards; an event keeps what it was created with. */

export const EVENT_TYPES = [
  { value: 'CAMPUS', label: 'Campus drive' },
  { value: 'CAREER_FAIR', label: 'Career fair' },
  { value: 'HIRING_EVENT', label: 'Hiring event' },
  { value: 'WEBINAR', label: 'Webinar / info session' },
]
export const EVENT_FORMATS = [
  { value: 'IN_PERSON', label: 'In-person' },
  { value: 'VIRTUAL', label: 'Virtual' },
  { value: 'HYBRID', label: 'Hybrid' },
]
export const EVENT_COUNTRIES = [
  { code: 'US', name: 'United States' },
  { code: 'IN', name: 'India' },
  { code: 'GB', name: 'United Kingdom' },
  { code: 'IE', name: 'Ireland' },
  { code: 'DE', name: 'Germany' },
  { code: 'FR', name: 'France' },
  { code: 'SG', name: 'Singapore' },
  { code: 'HK', name: 'Hong Kong' },
  { code: 'JP', name: 'Japan' },
  { code: 'AU', name: 'Australia' },
  { code: 'CA', name: 'Canada' },
  { code: 'AE', name: 'United Arab Emirates' },
]
/** Event timezones (IANA zone → label). */
export const EVENT_TIMEZONES: { value: string; label: string }[] = [
  { value: 'America/New_York', label: 'US Eastern (ET)' },
  { value: 'America/Chicago', label: 'US Central (CT)' },
  { value: 'America/Denver', label: 'US Mountain (MT)' },
  { value: 'America/Los_Angeles', label: 'US Pacific (PT)' },
  { value: 'America/Toronto', label: 'Toronto (ET)' },
  { value: 'Europe/London', label: 'UK (GMT/BST)' },
  { value: 'Europe/Dublin', label: 'Ireland (GMT/IST)' },
  { value: 'Europe/Berlin', label: 'Central Europe (CET)' },
  { value: 'Asia/Dubai', label: 'Gulf (GST)' },
  { value: 'Asia/Kolkata', label: 'India (IST)' },
  { value: 'Asia/Singapore', label: 'Singapore (SGT)' },
  { value: 'Asia/Hong_Kong', label: 'Hong Kong (HKT)' },
  { value: 'Asia/Tokyo', label: 'Japan (JST)' },
  { value: 'Australia/Sydney', label: 'Sydney (AET)' },
  { value: 'UTC', label: 'UTC' },
]

export const countryName = (code: string | null) => (code ? (EVENT_COUNTRIES.find((c) => c.code === code)?.name ?? code) : null)
const typeName = (v: string) => EVENT_TYPES.find((t) => t.value === v)?.label ?? v
const formatName = (v: string) => EVENT_FORMATS.find((t) => t.value === v)?.label ?? v
const days = (n: number | null, none: string, each: (n: number) => string) => (n === null ? none : each(n))

/** What a template sets, in a line each: shown on the template's card and when one is picked for a new event. */
export function templateFacts(t: EventTemplate): string[] {
  return [
    [typeName(t.eventType), formatName(t.format), countryName(t.country)].filter(Boolean).join(' · '),
    t.capacity === null ? 'No seat limit' : `${t.capacity} seats${t.waitlistEnabled ? ', then a waitlist' : ', no waitlist'}`,
    days(t.closeDaysBefore, 'Registration open until the event starts', (n) => (n === 0 ? 'Registration closes when the event starts' : `Registration closes ${n} ${n === 1 ? 'day' : 'days'} before`)),
    days(t.publishDaysBefore, 'On the careers site as soon as approved', (n) => `On the careers site ${n} ${n === 1 ? 'day' : 'days'} before, once approved`),
  ]
}

const BLANK: EventTemplate = {
  id: '',
  name: '',
  eventType: 'CAMPUS',
  format: 'IN_PERSON',
  country: null,
  timezone: 'America/New_York',
  intakeFormId: null,
  registrationFormId: null,
  capacity: null,
  waitlistEnabled: true,
  closeDaysBefore: 2,
  publishDaysBefore: 30,
  consentText: null,
  active: true,
  eventsMade: 0,
}

const errorOf = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Could not save that. Try again.'

export default function EventTemplates() {
  const { data, isLoading } = useEventTemplates()
  const [editing, setEditing] = useState<EventTemplate | null>(null)
  const list = data ?? []

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16, marginBottom: 16 }}>
        <p className="sub" style={{ margin: 0, fontSize: 13.5, maxWidth: '70ch' }}>
          A template holds what a kind of event always has. A new event starts from one and takes its settings; changing a template
          later changes only the events created afterwards.
        </p>
        <button className="btn btn--primary" onClick={() => setEditing({ ...BLANK })}>New template</button>
      </div>

      {isLoading ? (
        <div className="card"><div style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>Loading templates…</div></div>
      ) : list.length === 0 ? (
        <div className="card"><div style={{ padding: '44px 20px', textAlign: 'center', color: 'var(--ink-4)', fontSize: 13.5 }}>No templates yet. Events can still be created blank.</div></div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 16 }}>
          {list.map((t) => (
            <button key={t.id} className="card" onClick={() => setEditing(t)}
              style={{ padding: 20, textAlign: 'left', cursor: 'pointer', font: 'inherit', border: '1px solid var(--line)', background: 'var(--app-panel)', opacity: t.active ? 1 : 0.6 }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 10 }}>
                <h3 style={{ margin: 0, fontSize: 17 }}>{t.name}</h3>
                {!t.active && <span className="badge">Switched off</span>}
              </div>
              <ul style={{ margin: '12px 0 0', padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 5, fontSize: 12.5, color: 'var(--ink-3)' }}>
                {templateFacts(t).map((f) => <li key={f}>{f}</li>)}
              </ul>
              <div className="eyebrow" style={{ marginTop: 14, paddingTop: 10, borderTop: '1px solid var(--line)' }}>
                {t.eventsMade === 0 ? 'No events yet' : `${t.eventsMade} ${t.eventsMade === 1 ? 'event' : 'events'} created from it`}
              </div>
            </button>
          ))}
        </div>
      )}

      {editing && <TemplateEditor key={editing.id || 'new'} template={editing} onClose={() => setEditing(null)} />}
    </div>
  )
}

function TemplateEditor({ template, onClose }: { template: EventTemplate; onClose: () => void }) {
  const save = useSaveEventTemplate()
  const remove = useDeleteEventTemplate()
  const { data: formLibrary } = useFormsList()
  const [t, setT] = useState<EventTemplate>(template)
  const set = (patch: Partial<EventTemplate>) => setT((prev) => ({ ...prev, ...patch }))
  const isNew = !template.id
  const forms = (purpose: string) => (formLibrary ?? []).filter((f) => f.purpose === purpose && !f.template && !f.defaultForKind)
  const whole = (v: string) => (v.trim() === '' ? null : Math.max(0, Math.floor(Number(v))))

  return (
    <SlideOver label={isNew ? 'New event template' : `Edit ${template.name}`} onClose={onClose} width={480}>
      <div style={{ padding: '18px 20px 14px', borderBottom: '1px solid var(--line)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ fontFamily: 'var(--font-display)', fontSize: 19, color: 'var(--ink-0)' }}>{isNew ? 'New event template' : 'Event template'}</div>
        <button type="button" onClick={onClose} aria-label="Close" style={{ border: 0, background: 'none', cursor: 'pointer', fontSize: 20, color: 'var(--ink-4)' }}>×</button>
      </div>

      <div style={{ flex: 1, overflowY: 'auto', padding: '18px 20px', display: 'flex', flexDirection: 'column', gap: 14 }}>
        <Row label="Template name">
          <input className="input" value={t.name} placeholder="Campus drive, India" onChange={(e) => set({ name: e.target.value })} />
        </Row>
        <div style={{ display: 'flex', gap: 12 }}>
          <Row label="Kind of event" style={{ flex: 1 }}>
            <select className="select" value={t.eventType} onChange={(e) => set({ eventType: e.target.value })}>
              {EVENT_TYPES.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </Row>
          <Row label="Format" style={{ flex: 1 }}>
            <select className="select" value={t.format} onChange={(e) => set({ format: e.target.value })}>
              {EVENT_FORMATS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </Row>
        </div>
        <div style={{ display: 'flex', gap: 12 }}>
          <Row label="Country" style={{ flex: 1 }}>
            <select className="select" value={t.country ?? ''} onChange={(e) => set({ country: e.target.value || null })}>
              <option value="">Any country</option>
              {EVENT_COUNTRIES.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}
            </select>
          </Row>
          <Row label="Timezone" style={{ flex: 1 }}>
            <select className="select" value={t.timezone} onChange={(e) => set({ timezone: e.target.value })}>
              {EVENT_TIMEZONES.map((z) => <option key={z.value} value={z.value}>{z.label}</option>)}
            </select>
          </Row>
        </div>

        <Row label="Intake form (the recruiter fills in)">
          <select className="select" value={t.intakeFormId ?? ''} onChange={(e) => set({ intakeFormId: e.target.value || null })}>
            <option value="">The default intake form</option>
            {forms('EVENT_INTAKE').map((f) => <option key={f.id} value={f.id}>{f.name}</option>)}
          </select>
        </Row>
        <Row label="Registration form (students fill in)">
          <select className="select" value={t.registrationFormId ?? ''} onChange={(e) => set({ registrationFormId: e.target.value || null })}>
            <option value="">The default registration form</option>
            {forms('EVENT_REGISTRATION').map((f) => <option key={f.id} value={f.id}>{f.name}</option>)}
          </select>
        </Row>

        <div style={{ display: 'flex', gap: 12 }}>
          <Row label="Seats" style={{ flex: 1 }}>
            <input className="input" type="number" min={1} placeholder="No limit" value={t.capacity ?? ''} onChange={(e) => set({ capacity: e.target.value.trim() === '' ? null : Math.max(1, Math.floor(Number(e.target.value))) })} />
          </Row>
          <Row label="Registration closes, days before" style={{ flex: 1 }}>
            <input className="input" type="number" min={0} placeholder="At the start" value={t.closeDaysBefore ?? ''} onChange={(e) => set({ closeDaysBefore: whole(e.target.value) })} />
          </Row>
        </div>
        <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: 'var(--ink-2)' }}>
          <input type="checkbox" checked={t.waitlistEnabled} onChange={(e) => set({ waitlistEnabled: e.target.checked })} />
          Keep a waitlist when every seat is taken
        </label>

        <Row label="Goes on the careers site, days before the event">
          <input className="input" type="number" min={0} placeholder="As soon as it is approved" value={t.publishDaysBefore ?? ''} onChange={(e) => set({ publishDaysBefore: whole(e.target.value) })} />
          <div className="muted" style={{ fontSize: 11.5, marginTop: 4 }}>Every event is approved first. An approved event appears on the careers site from this many days before it starts; the date and time can be changed on each event.</div>
        </Row>

        <Row label="What students agree to">
          <textarea className="input" rows={4} placeholder="Leave empty for the standard wording" value={t.consentText ?? ''} onChange={(e) => set({ consentText: e.target.value || null })} />
          <div className="muted" style={{ fontSize: 11.5, marginTop: 4 }}>Shown beside the tick box on the registration page and asked in the chat. Use the wording your privacy team approved for this country.</div>
        </Row>

        <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: 'var(--ink-2)' }}>
          <input type="checkbox" checked={t.active} onChange={(e) => set({ active: e.target.checked })} />
          Offer this template when creating an event
        </label>

        {(save.isError || remove.isError) && <div style={{ fontSize: 12.5, color: 'var(--c-red, #c41230)' }}>{errorOf(save.error ?? remove.error)}</div>}
      </div>

      <div style={{ display: 'flex', gap: 8, padding: '14px 20px', borderTop: '1px solid var(--line)' }}>
        {!isNew && template.eventsMade === 0 && (
          <button className="btn btn--ghost btn--sm" disabled={remove.isPending}
            onClick={() => { if (window.confirm(`Delete the template "${template.name}"? This cannot be undone.`)) remove.mutate(template.id, { onSuccess: onClose }) }}>
            Delete
          </button>
        )}
        <div style={{ flex: 1 }} />
        <button className="btn btn--ghost btn--sm" onClick={onClose}>Cancel</button>
        <button className="btn btn--primary btn--sm" disabled={save.isPending || t.name.trim() === ''} onClick={() => save.mutate(t, { onSuccess: onClose })}>
          {save.isPending ? 'Saving…' : 'Save template'}
        </button>
      </div>
    </SlideOver>
  )
}

function Row({ label, children, style }: { label: string; children: React.ReactNode; style?: React.CSSProperties }) {
  return (
    <label style={{ display: 'block', ...style }}>
      <div className="eyebrow" style={{ marginBottom: 6 }}>{label}</div>
      {children}
    </label>
  )
}
