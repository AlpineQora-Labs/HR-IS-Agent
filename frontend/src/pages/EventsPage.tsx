import { useEffect, useMemo, useState } from 'react'
import { QRCodeSVG } from 'qrcode.react'
import { useStore } from '@/state/store'
import SlideOver from '@/components/SlideOver'
import { useCreateEvent, useEvents, useEventRoster, useRegisterAttendee, useRegistrationTransition, useSchools, useFormResponses, useSubmitFormResponse, useFormDefById, useFormsList, useEvaluateRegistration, usePutForward, useEventSeats, useJobs, useSchoolReport, useEventTemplates, useSendEventForApproval, useAddSchool } from '@/api/hooks'
import { date } from '@/lib/format'
import type { EventRow, EventRegistration } from '@/api/types'
import { DISPLAY_TYPES, effectiveValue, fieldVisible, INTAKE_KEY, defaultIntakeForm, flattenIntake, normalizeIntake, resolveField, type IntakeField, type IntakeForm } from './admin/FormBuilder'
import { IntakeInput, answerText, type Answer } from './admin/IntakeInput'
import { EVENT_COUNTRIES, EVENT_TIMEZONES, countryName, templateFacts } from './admin/EventTemplates'

// Campus & hiring events, modeled on how Handshake / Yello / RippleMatch run them:
// a conversion funnel (registered → attended → hires), event formats, and a
// stepped creation flow whose Details step renders the admin-designed intake
// form (Admin → Forms), so orgs control what gets asked at event creation.

const TYPE_OPTIONS = [
  { value: 'CAMPUS', label: 'Campus drive' },
  { value: 'CAREER_FAIR', label: 'Career fair' },
  { value: 'HIRING_EVENT', label: 'Hiring event' },
  { value: 'WEBINAR', label: 'Webinar / info session' },
]

function typeBadge(_type: string) {
  // Event type is a category, not a state — no color.
  return ''
}

function typeLabel(type: string) {
  return TYPE_OPTIONS.find((t) => t.value === type.toUpperCase())?.label ?? type
}

function formatOf(e: EventRow) {
  if (e.format) return e.format === 'VIRTUAL' ? 'Virtual' : e.format === 'HYBRID' ? 'Hybrid' : 'In-person'
  const t = e.type.toUpperCase()
  const loc = (e.location || '').toLowerCase()
  if (t === 'WEBINAR' || loc.includes('virtual') || loc.includes('online') || loc.includes('zoom')) return 'Virtual'
  return 'In-person'
}

const rate = (num: number, den: number) => (den > 0 ? Math.round((num / den) * 100) : 0)
const isUpcoming = (iso: string) => new Date(iso).getTime() >= Date.now()

/** "2:00–4:00 PM EST" in the event's own timezone. */
const timeRange = (e: Pick<EventRow, 'startsAt' | 'endsAt' | 'timezone'>) => {
  const tz = e.timezone || 'America/New_York'
  const fmt = (iso: string, withZone: boolean) =>
    new Date(iso).toLocaleTimeString('en-US', {
      timeZone: tz, hour: 'numeric', minute: '2-digit', ...(withZone ? { timeZoneName: 'short' } : {}),
    })
  return e.endsAt ? `${fmt(e.startsAt, false)}–${fmt(e.endsAt, true)}` : fmt(e.startsAt, true)
}

const approvalBadge = (s: string | null) =>
  s === 'PENDING' ? (
    <span className="badge badge--warn">Pending approval</span>
  ) : s === 'REJECTED' ? (
    <span className="badge badge--danger">Rejected</span>
  ) : s === 'NOT_SENT' ? (
    <span className="badge badge--danger">Not sent for approval</span>
  ) : null

/** A moment in a timezone, as a sentence reads it: "Nov 10, 9:00 AM IST". */
const momentText = (iso: string, tz: string) =>
  new Date(iso).toLocaleString('en-US', { timeZone: tz, month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit', timeZoneName: 'short' })
/** The same moment as a date input and a time input hold it, in that timezone. */
const partsIn = (iso: string, tz: string) => {
  const [day, time] = new Date(iso).toLocaleString('sv-SE', { timeZone: tz }).split(' ')
  return { day, time: time.slice(0, 5) }
}

/** Where an event stands on its way to the careers site: approval first, then its publishing moment. */
function publishingText(e: EventRow) {
  const tz = e.timezone || 'America/New_York'
  if (e.approvalStatus === 'PENDING') return e.publishAt ? `Waiting for approval · then on the careers site from ${momentText(e.publishAt, tz)}` : 'Waiting for approval · then on the careers site straight away'
  if (e.approvalStatus === 'REJECTED') return 'Rejected · not on the careers site'
  if (e.approvalStatus === 'NOT_SENT') return 'Not sent for approval · not on the careers site'
  if (e.published) return 'On the careers site'
  return e.publishAt ? `Approved · on the careers site from ${momentText(e.publishAt, tz)}` : 'Approved'
}

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
  const { data: serverResponses } = useFormResponses('EVENT', e.id)
  const intakeDetails = useMemo(() => {
    const fromServer = (serverResponses ?? []).flatMap((r) => {
      try {
        return JSON.parse(r.answers) as { label: string; value: string }[]
      } catch {
        return []
      }
    })
    return fromServer.length > 0 ? fromServer : readResponses(e.id)
  }, [serverResponses, e.id])
  return (
    <SlideOver label={`${e.name} details`} onClose={onClose} width={480}>
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
        <Fact label="When">{`${date(e.startsAt)} · ${timeRange(e)}`}</Fact>
        <Fact label={formatOf(e) === 'Virtual' ? 'Link' : 'Location'}>{e.location || '—'}</Fact>
        <Fact label="Status">{isUpcoming(e.startsAt) ? 'Upcoming' : 'Past'}</Fact>
        {e.country && <Fact label="Country">{countryName(e.country)}</Fact>}
        <Fact label="Careers site">{publishingText(e)}</Fact>
        <SendForApproval e={e} />

        <div style={{ marginTop: 18 }}>
          <div className="eyebrow" style={{ marginBottom: 8 }}>Conversion funnel</div>
          <FunnelStep label="Registered" value={e.registrations} pct={100} />
          <FunnelStep label="Attended" value={e.attended} pct={rate(e.attended, e.registrations)} />
          <FunnelStep label="Hires" value={e.hires} pct={rate(e.hires, e.registrations)} />
        </div>

        {intakeDetails.length > 0 && (
          <div style={{ marginTop: 18 }}>
            <div className="eyebrow" style={{ marginBottom: 4 }}>Intake details</div>
            {intakeDetails.map((r) => (
              <Fact key={r.label} label={r.label}>{r.value}</Fact>
            ))}
          </div>
        )}

        <Seats e={e} />

        <RegistrationQr eventId={e.id} />

        <Roster eventId={e.id} />
      </div>
    </SlideOver>
  )
}

/* The QR block recruiters print for the booth banner: scanning opens the
   shell-less registration page (form or chat — student's choice). The code is
   drawn here, in the browser: nothing about the event goes to another site,
   and it works with no connection. */
function RegistrationQr({ eventId }: { eventId: string }) {
  const url = `${window.location.origin}/register/${eventId}`
  const [copied, setCopied] = useState(false)
  return (
    <div style={{ marginTop: 18 }}>
      <div className="eyebrow" style={{ marginBottom: 8 }}>Registration QR · booth banner</div>
      <div style={{ display: 'flex', gap: 14, alignItems: 'center', padding: '12px 14px', border: '1px solid var(--line)', borderRadius: 'var(--ra-2)' }}>
        <QRCodeSVG value={url} size={96} marginSize={2} level="M" role="img" aria-label="Registration QR code" style={{ flexShrink: 0, borderRadius: 6, border: '1px solid var(--line)', background: '#fff' }} />
        <div style={{ minWidth: 0 }}>
          <div style={{ fontSize: 12.5, color: 'var(--ink-2)', wordBreak: 'break-all' }}>{url}</div>
          <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
            <button className="btn btn--outline btn--sm" style={{ fontSize: 11.5 }}
              onClick={() => { navigator.clipboard?.writeText(url); setCopied(true); setTimeout(() => setCopied(false), 1500) }}>
              {copied ? 'Copied!' : 'Copy link'}
            </button>
            <a className="btn btn--outline btn--sm" style={{ fontSize: 11.5 }} href={url} target="_blank" rel="noreferrer">Open</a>
          </div>
        </div>
      </div>
    </div>
  )
}

/* An event that was rejected, or created while no approval workflow was switched on, is sent
   for approval from here. It cannot go on the careers site any other way. */
function SendForApproval({ e }: { e: EventRow }) {
  const send = useSendEventForApproval()
  if (e.approvalStatus !== 'NOT_SENT' && e.approvalStatus !== 'REJECTED') return null
  return (
    <div style={{ margin: '6px 0 4px', padding: '10px 12px', border: '1px solid var(--line)', borderRadius: 'var(--ra-2)', fontSize: 12.5, color: 'var(--ink-2)', lineHeight: 1.5 }}>
      {e.approvalStatus === 'REJECTED'
        ? 'This event was rejected. Change what was asked for, then send it for approval again.'
        : 'This event has not been approved, so students cannot see it. No approval workflow was switched on for events when it was created.'}
      <div style={{ marginTop: 8 }}>
        <button className="btn btn--primary btn--sm" disabled={send.isPending} onClick={() => send.mutate(e.id)}>
          {send.isPending ? 'Sending…' : 'Send for approval'}
        </button>
      </div>
      {send.isError && (
        <div style={{ marginTop: 6, color: 'var(--c-red, #c41230)' }}>
          {(send.error as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Could not send it. Try again.'}
        </div>
      )}
    </div>
  )
}

/** The event's own day and time for a moment, as a date input and a sentence read it. */
const dayIn = (iso: string, tz: string) => new Date(iso).toLocaleDateString('en-CA', { timeZone: tz })
const closesText = (iso: string, tz: string) =>
  new Date(iso).toLocaleString('en-US', { timeZone: tz, month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit', timeZoneName: 'short' })

/* Seats: how many the event takes, when registration ends, and whether a full event keeps a
   waitlist. Registration keeps to these — the public page and the chat both ask the server. */
function Seats({ e }: { e: EventRow }) {
  const save = useEventSeats()
  const tz = e.timezone || 'America/New_York'
  const [editing, setEditing] = useState(false)
  const [capacity, setCapacity] = useState('')
  const [closeDay, setCloseDay] = useState('')
  const [waitlist, setWaitlist] = useState(false)
  const [formId, setFormId] = useState('')
  const [publishDay, setPublishDay] = useState('')
  const [publishTime, setPublishTime] = useState('09:00')
  const { data: formLibrary } = useFormsList()
  // Any Student registration form in the library can be the one this event registers with.
  const regForms = (formLibrary ?? []).filter((f) => f.purpose === 'EVENT_REGISTRATION' && !f.template)
  const formName = e.registrationFormId ? (regForms.find((f) => f.id === e.registrationFormId)?.name ?? 'A form no longer in the library') : `${regForms.find((f) => f.defaultForKind)?.name ?? 'Student registration'} (the default)`
  const seated = e.registrations - e.waitlisted
  const closed = e.registrationClosesAt ? new Date(e.registrationClosesAt).getTime() <= Date.now() : !isUpcoming(e.startsAt)
  const full = e.capacity !== null && seated >= e.capacity

  const open = () => {
    setCapacity(e.capacity === null ? '' : String(e.capacity))
    // Registration closes at the end of the chosen day, so the day shown is the one before the instant.
    setCloseDay(e.registrationClosesAt ? dayIn(new Date(new Date(e.registrationClosesAt).getTime() - 60_000).toISOString(), tz) : '')
    setWaitlist(e.waitlistEnabled)
    setFormId(e.registrationFormId ?? '')
    const at = e.publishAt ? partsIn(e.publishAt, tz) : null
    setPublishDay(at?.day ?? '')
    setPublishTime(at?.time ?? '09:00')
    setEditing(true)
  }
  const submit = () => {
    const n = capacity.trim() === '' ? null : Number(capacity)
    if (n !== null && (!Number.isInteger(n) || n < 1)) return
    let closesAt: string | null = null
    if (closeDay) {
      const next = new Date(`${closeDay}T00:00:00Z`)
      next.setUTCDate(next.getUTCDate() + 1)
      closesAt = zonedToIso(next.toISOString().slice(0, 10), '00:00', tz)
    }
    save.mutate({ eventId: e.id, settings: { capacity: n, registrationClosesAt: closesAt, waitlistEnabled: waitlist, registrationFormId: formId || null, publishAt: publishDay ? zonedToIso(publishDay, publishTime || '09:00', tz) : null } }, { onSuccess: () => setEditing(false) })
  }

  return (
    <div style={{ marginTop: 18 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
        <span className="eyebrow">Seats, registration and publishing</span>
        <button className="btn btn--outline btn--sm" style={{ fontSize: 11.5, padding: '2px 8px' }} onClick={() => (editing ? setEditing(false) : open())}>
          {editing ? 'Cancel' : 'Change'}
        </button>
      </div>
      {editing ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, padding: '10px 12px', border: '1px solid var(--line)', borderRadius: 'var(--ra-2)' }}>
          <div style={{ display: 'flex', gap: 10 }}>
            <label style={{ flex: 1, fontSize: 11.5, color: 'var(--ink-4)' }}>
              Seats
              <input className="input" type="number" min={1} placeholder="No limit" value={capacity} onChange={(ev) => setCapacity(ev.target.value)} />
            </label>
            <label style={{ flex: 1, fontSize: 11.5, color: 'var(--ink-4)' }}>
              Last day to register
              <input className="input" type="date" max={dayIn(e.startsAt, tz)} value={closeDay} onChange={(ev) => setCloseDay(ev.target.value)} />
            </label>
          </div>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12.5, color: 'var(--ink-2)' }}>
            <input type="checkbox" checked={waitlist} onChange={(ev) => setWaitlist(ev.target.checked)} />
            Keep a waitlist when every seat is taken
          </label>
          <div style={{ display: 'flex', gap: 10 }}>
            <label style={{ flex: 1, fontSize: 11.5, color: 'var(--ink-4)' }}>
              Goes on the careers site on
              <input className="input" type="date" max={dayIn(e.startsAt, tz)} value={publishDay} onChange={(ev) => setPublishDay(ev.target.value)} />
            </label>
            <label style={{ flex: 1, fontSize: 11.5, color: 'var(--ink-4)' }}>
              At (event's timezone)
              <input className="input" type="time" value={publishTime} disabled={!publishDay} onChange={(ev) => setPublishTime(ev.target.value)} />
            </label>
          </div>
          <label style={{ fontSize: 11.5, color: 'var(--ink-4)' }}>
            Registration form
            <select className="select" value={formId} onChange={(ev) => setFormId(ev.target.value)}>
              <option value="">The default form</option>
              {regForms.filter((f) => !f.defaultForKind).map((f) => (
                <option key={f.id} value={f.id}>{f.name}</option>
              ))}
            </select>
          </label>
          <div className="muted" style={{ fontSize: 11.5, lineHeight: 1.5 }}>
            Leave seats empty for no limit, and the last day empty to keep registration open until the event starts. Leave the careers-site date empty to publish as soon as the event is approved. Raising the seats moves the waitlist up.
          </div>
          <button className="btn btn--primary btn--sm" disabled={save.isPending} onClick={submit}>
            {save.isPending ? 'Saving…' : 'Save'}
          </button>
          {save.isError && (
            <span style={{ fontSize: 12, color: 'var(--c-red, #c41230)' }}>
              {(save.error as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Could not save.'}
            </span>
          )}
        </div>
      ) : (
        <>
          <Fact label="Seats">{e.capacity === null ? `${seated} taken · no limit` : `${seated} of ${e.capacity} taken`}</Fact>
          <Fact label="Waitlist">{e.waitlistEnabled ? `${e.waitlisted} waiting` : 'Not kept'}</Fact>
          <Fact label="Registration">
            {closed ? 'Closed' : full ? (e.waitlistEnabled ? 'Full · taking the waitlist' : 'Full') : 'Open'}
            {e.registrationClosesAt ? ` · ${closed ? 'closed' : 'closes'} ${closesText(e.registrationClosesAt, tz)}` : closed ? '' : ' · until the event starts'}
          </Fact>
          <Fact label="Registration form">{formName}</Fact>
          <Fact label="Publishing">{e.publishAt ? momentText(e.publishAt, tz) : 'As soon as it is approved'}</Fact>
        </>
      )}
    </div>
  )
}

const STATUS_LABEL: Record<string, string> = {
  REGISTERED: 'Registered',
  CHECKED_IN: 'Checked in',
  NO_SHOW: 'No-show',
  WAITLISTED: 'Waitlisted',
  CANCELLED: 'Cancelled',
}

/* The live roster: every student as a real row (and a real candidate) —
   register/walk-in at the booth, check in, mark no-shows, seat the waitlist, and record what
   the recruiter made of each student. The funnel above is computed from these rows. */
function Roster({ eventId }: { eventId: string }) {
  const { data: roster, isLoading } = useEventRoster(eventId)
  const { data: schools } = useSchools()
  const transition = useRegistrationTransition()
  const register = useRegisterAttendee()
  const [adding, setAdding] = useState(false)
  const [show, setShow] = useState<'all' | 'shortlisted' | 'waitlist'>('all')
  const blank = { name: '', email: '', schoolId: '', major: '', gradYear: '', consent: false }
  const [form, setForm] = useState(blank)
  const btn = { fontSize: 11.5, padding: '2px 8px' } as const

  const statusBadge = (s: string) =>
    s === 'CHECKED_IN' ? 'badge--ok' : s === 'NO_SHOW' || s === 'CANCELLED' ? 'badge--danger' : s === 'WAITLISTED' ? 'badge--warn' : 'badge--info'

  const submit = () =>
    register.mutate(
      {
        eventId,
        input: {
          name: form.name,
          email: form.email,
          schoolId: form.schoolId || undefined,
          major: form.major || undefined,
          gradYear: form.gradYear ? Number(form.gradYear) : undefined,
          walkIn: true,
          consent: form.consent,
        },
      },
      { onSuccess: () => { setAdding(false); setForm(blank) } },
    )

  const all = roster ?? []
  const shortlisted = all.filter((r) => r.shortlisted).length
  const waiting = all.filter((r) => r.status === 'WAITLISTED').length
  const rows = all.filter((r) => (show === 'shortlisted' ? r.shortlisted : show === 'waitlist' ? r.status === 'WAITLISTED' : true))

  return (
    <div style={{ marginTop: 18 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
        <span className="eyebrow">Roster{roster ? ` · ${roster.length}` : ''}</span>
        <button className="btn btn--outline btn--sm" style={btn} onClick={() => setAdding((v) => !v)}>
          {adding ? 'Cancel' : 'Add walk-in'}
        </button>
      </div>

      {adding && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, padding: '10px 12px', border: '1px solid var(--line)', borderRadius: 'var(--ra-2)', marginBottom: 10 }}>
          <input className="input" placeholder="Full name" value={form.name}
            onChange={(ev) => setForm({ ...form, name: ev.target.value })} />
          <input className="input" placeholder="Email" value={form.email}
            onChange={(ev) => setForm({ ...form, email: ev.target.value })} />
          <select className="select" value={form.schoolId}
            onChange={(ev) => setForm({ ...form, schoolId: ev.target.value })}>
            <option value="">School…</option>
            {(schools ?? []).map((sc) => (
              <option key={sc.id} value={sc.id}>{sc.name}</option>
            ))}
          </select>
          <div style={{ display: 'flex', gap: 8 }}>
            <input className="input" placeholder="Major" value={form.major}
              onChange={(ev) => setForm({ ...form, major: ev.target.value })} />
            <input className="input" placeholder="Grad year" style={{ width: 110 }} value={form.gradYear}
              onChange={(ev) => setForm({ ...form, gradYear: ev.target.value })} />
          </div>
          <label style={{ display: 'flex', alignItems: 'flex-start', gap: 8, fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.45 }}>
            <input type="checkbox" checked={form.consent} style={{ marginTop: 2 }} onChange={(ev) => setForm({ ...form, consent: ev.target.checked })} />
            The student agreed to their details being kept and to being contacted about this event and jobs.
          </label>
          <button className="btn btn--primary btn--sm" disabled={register.isPending || !form.name || !form.email || !form.consent} onClick={submit}>
            {register.isPending ? 'Adding…' : 'Add & check in'}
          </button>
          {register.isError && (
            <span style={{ fontSize: 12, color: 'var(--c-red, #c41230)' }}>
              {(register.error as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Could not add — already registered?'}
            </span>
          )}
        </div>
      )}

      {all.length > 0 && (
        <div className="segmented" role="group" aria-label="Show" style={{ marginBottom: 6 }}>
          <button aria-pressed={show === 'all'} onClick={() => setShow('all')}>All · {all.length}</button>
          <button aria-pressed={show === 'shortlisted'} onClick={() => setShow('shortlisted')}>Shortlisted · {shortlisted}</button>
          <button aria-pressed={show === 'waitlist'} onClick={() => setShow('waitlist')}>Waitlist · {waiting}</button>
        </div>
      )}

      {isLoading ? (
        <div className="muted" style={{ fontSize: 12.5 }}>Loading roster…</div>
      ) : all.length === 0 ? (
        <div className="muted" style={{ fontSize: 12.5 }}>
          No registrations yet — they'll appear here as students sign up (or add walk-ins at the booth).
        </div>
      ) : rows.length === 0 ? (
        <div className="muted" style={{ fontSize: 12.5, padding: '10px 0' }}>
          {show === 'shortlisted' ? 'Nobody is shortlisted yet. Mark a student with Shortlist.' : 'Nobody is on the waitlist.'}
        </div>
      ) : (
        rows.map((r: EventRegistration) => (
          <div key={r.id} style={{ padding: '10px 0', borderBottom: '1px solid var(--line)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 8 }}>
              <span className="t-strong" style={{ fontSize: 13 }}>{r.name}</span>
              <span className={`badge ${statusBadge(r.status)}`} style={{ flexShrink: 0 }}>
                {STATUS_LABEL[r.status] ?? r.status}
                {r.waitlistPosition ? ` · no. ${r.waitlistPosition}` : ''}
              </span>
            </div>
            <div className="muted" style={{ fontSize: 11.5, margin: '2px 0 6px' }}>
              {[r.schoolName, r.major, r.gradYear ? `'${String(r.gradYear).slice(-2)}` : null].filter(Boolean).join(' · ') || r.email}
              {r.source === 'WALK_IN' && <span> · walk-in</span>}
              {r.personalEmail && <span> · also {r.personalEmail}</span>}
            </div>
            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
              {r.status === 'REGISTERED' && (
                <>
                  <button className="btn btn--outline btn--sm" style={btn} disabled={transition.isPending}
                    onClick={() => transition.mutate({ registrationId: r.id, status: 'CHECKED_IN' })}>
                    Check in
                  </button>
                  <button className="btn btn--outline btn--sm" style={btn} disabled={transition.isPending}
                    onClick={() => transition.mutate({ registrationId: r.id, status: 'NO_SHOW' })}>
                    No-show
                  </button>
                </>
              )}
              {r.status === 'WAITLISTED' && (
                <button className="btn btn--outline btn--sm" style={btn} disabled={transition.isPending}
                  onClick={() => transition.mutate({ registrationId: r.id, status: 'REGISTERED' })}>
                  Give a seat
                </button>
              )}
              {(r.status === 'REGISTERED' || r.status === 'WAITLISTED') && (
                <button className="btn btn--ghost btn--sm" style={btn} disabled={transition.isPending}
                  onClick={() => {
                    if (window.confirm(`Cancel ${r.name}'s registration?${r.status === 'REGISTERED' ? ' Their seat goes to the next student on the waitlist.' : ''}`)) {
                      transition.mutate({ registrationId: r.id, status: 'CANCELLED' })
                    }
                  }}>
                  Cancel
                </button>
              )}
              {(r.status === 'CANCELLED' || r.status === 'NO_SHOW') && (
                <button className="btn btn--ghost btn--sm" style={btn} disabled={transition.isPending}
                  onClick={() => transition.mutate({ registrationId: r.id, status: 'REGISTERED' })}>
                  Put back as registered
                </button>
              )}
            </div>
            {r.status !== 'CANCELLED' && r.status !== 'WAITLISTED' && <Evaluation r={r} />}
          </div>
        ))
      )}
    </div>
  )
}

/* What the recruiter made of one student: a rating out of five, a note, a shortlist mark, and
   the job they are put forward for. Putting a student forward makes their application. */
function Evaluation({ r }: { r: EventRegistration }) {
  const evaluate = useEvaluateRegistration()
  const putForward = usePutForward()
  const { data: jobs } = useJobs()
  const [notes, setNotes] = useState(r.notes ?? '')
  const [writing, setWriting] = useState(false)
  const [jobId, setJobId] = useState('')
  const openJobs = (jobs ?? []).filter((j) => j.status === 'OPEN')
  const busy = evaluate.isPending || putForward.isPending
  const save = (change: { rating?: number | null; notes?: string | null; shortlisted?: boolean }) =>
    evaluate.mutate({
      registrationId: r.id,
      rating: change.rating === undefined ? r.rating : change.rating,
      notes: change.notes === undefined ? r.notes : change.notes,
      shortlisted: change.shortlisted === undefined ? r.shortlisted : change.shortlisted,
    })

  return (
    <div style={{ marginTop: 8, padding: '8px 10px', background: 'var(--app-sunken)', borderRadius: 'var(--ra-2)' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
        <div role="group" aria-label={`Rating for ${r.name}`} style={{ display: 'flex', gap: 2 }}>
          {[1, 2, 3, 4, 5].map((n) => (
            <button
              key={n}
              type="button"
              aria-label={`${n} out of 5`}
              aria-pressed={r.rating === n}
              disabled={busy}
              // Pressing the rating it already has clears it.
              onClick={() => save({ rating: r.rating === n ? null : n })}
              style={{ border: 0, background: 'none', cursor: 'pointer', padding: '0 1px', fontSize: 17, lineHeight: 1, color: r.rating !== null && n <= r.rating ? '#E0A100' : 'var(--ink-5)' }}
            >
              ★
            </button>
          ))}
        </div>
        <button className={`btn btn--sm ${r.shortlisted ? 'btn--primary' : 'btn--outline'}`} style={{ fontSize: 11.5, padding: '2px 8px' }} disabled={busy}
          aria-pressed={r.shortlisted} onClick={() => save({ shortlisted: !r.shortlisted })}>
          {r.shortlisted ? 'Shortlisted' : 'Shortlist'}
        </button>
        <button className="btn btn--ghost btn--sm" style={{ fontSize: 11.5, padding: '2px 8px' }} onClick={() => { setNotes(r.notes ?? ''); setWriting((v) => !v) }}>
          {writing ? 'Close note' : r.notes ? 'Edit note' : 'Add note'}
        </button>
      </div>

      {!writing && r.notes && <div style={{ fontSize: 12, color: 'var(--ink-2)', marginTop: 6, lineHeight: 1.45 }}>{r.notes}</div>}
      {writing && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginTop: 8 }}>
          <textarea className="input" rows={2} maxLength={2000} placeholder="What stood out, what to follow up on" value={notes} onChange={(ev) => setNotes(ev.target.value)} />
          <button className="btn btn--primary btn--sm" style={{ alignSelf: 'flex-start', fontSize: 11.5 }} disabled={busy}
            onClick={() => evaluate.mutate({ registrationId: r.id, rating: r.rating, notes: notes.trim() || null, shortlisted: r.shortlisted }, { onSuccess: () => setWriting(false) })}>
            Save note
          </button>
        </div>
      )}

      {r.applicationId ? (
        <div style={{ fontSize: 12, color: 'var(--ink-2)', marginTop: 8 }}>
          Put forward for <strong>{r.jobTitle ?? 'a job'}</strong>
          {r.applicationStage ? ` · ${r.applicationStage.charAt(0)}${r.applicationStage.slice(1).toLowerCase()}` : ''}
        </div>
      ) : (
        <div style={{ display: 'flex', gap: 6, marginTop: 8 }}>
          <select className="select" style={{ fontSize: 12, height: 30, flex: 1, minWidth: 0 }} value={jobId} aria-label={`Job to put ${r.name} forward for`} onChange={(ev) => setJobId(ev.target.value)}>
            <option value="">Put forward for a job…</option>
            {openJobs.map((j) => (
              <option key={j.id} value={j.id}>{j.title} · {j.location}</option>
            ))}
          </select>
          <button className="btn btn--outline btn--sm" style={{ fontSize: 11.5 }} disabled={!jobId || busy}
            onClick={() => putForward.mutate({ registrationId: r.id, jobId })}>
            Put forward
          </button>
        </div>
      )}
      {(evaluate.isError || putForward.isError) && (
        <div style={{ fontSize: 12, color: 'var(--c-red, #c41230)', marginTop: 6 }}>Could not save that. Try again.</div>
      )}
    </div>
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
  endTime: string
  timezone: string
  flagged: boolean
  /** The template the event starts from; '' for a blank event. */
  templateId: string
  /** When it goes on the careers site, in the event's timezone; '' leaves it to the template (or as soon as approved). */
  publishDay: string
  publishTime: string
}

/** Event timezones offered in the wizard: the same list a template chooses from. */
const TIMEZONES = EVENT_TIMEZONES

/** "day + hh:mm in IANA zone" → absolute ISO instant (offset derived per-date, DST-safe). */
function zonedToIso(day: string, time: string, tz: string): string {
  const naive = new Date(`${day}T${time}:00Z`)
  const inTz = new Date(naive.toLocaleString('en-US', { timeZone: tz }))
  const inUtc = new Date(naive.toLocaleString('en-US', { timeZone: 'UTC' }))
  return new Date(naive.getTime() + (inUtc.getTime() - inTz.getTime())).toISOString()
}

const EMPTY_DRAFT: Draft = {
  name: '',
  type: 'CAMPUS',
  format: 'In-person',
  location: '',
  day: '',
  time: '09:00',
  endTime: '11:00',
  timezone: 'America/New_York',
  flagged: false,
  templateId: '',
  publishDay: '',
  publishTime: '09:00',
}

const STEPS = ['Template', 'Basics', 'Details', 'Review']
const FORMAT_LABEL: Record<string, Draft['format']> = { IN_PERSON: 'In-person', VIRTUAL: 'Virtual', HYBRID: 'Hybrid' }
const FORMAT_CODE: Record<Draft['format'], string> = { 'In-person': 'IN_PERSON', Virtual: 'VIRTUAL', Hybrid: 'HYBRID' }

const RESPONSES_KEY = 'taportal.eventIntakeResponses'

function readIntakeForm(): IntakeForm {
  try {
    const raw = localStorage.getItem(INTAKE_KEY)
    if (raw) return normalizeIntake(JSON.parse(raw))
  } catch { /* fall through */ }
  return defaultIntakeForm()
}


/** Persist a created event's intake answers so the detail panel can show them. */
function saveResponses(eventId: string, form: IntakeForm, answers: Record<string, Answer>) {
  try {
    const raw = localStorage.getItem(RESPONSES_KEY)
    const all = raw ? JSON.parse(raw) : {}
    all[eventId] = flattenIntake(form)
      .filter((f) => !DISPLAY_TYPES.includes(f.type))
      .filter((f) => answerText(answers[f.id]).trim() !== '')
      .map((f) => ({ label: f.label, value: answerText(answers[f.id]) }))
    localStorage.setItem(RESPONSES_KEY, JSON.stringify(all))
  } catch { /* POC persistence — ignore quota errors */ }
}

function readResponses(eventId: string): { label: string; value: string }[] {
  try {
    const raw = localStorage.getItem(RESPONSES_KEY)
    if (!raw) return []
    return JSON.parse(raw)[eventId] ?? []
  } catch {
    return []
  }
}

/** One intake field as a live wizard input. */
function CreateEventWizard({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const create = useCreateEvent()
  const { toastMsg } = useStore()
  const [step, setStep] = useState(0)
  const [d, setD] = useState<Draft>(EMPTY_DRAFT)
  const set = (patch: Partial<Draft>) => setD((prev) => ({ ...prev, ...patch }))
  // The recruiter picks WHICH intake form this event uses (form library);
  // the kind's default form is preselected. localStorage remains only an
  // offline-demo fallback.
  const { data: formLibrary } = useFormsList()
  const intakeForms = useMemo(
    () => (formLibrary ?? []).filter((f) => f.purpose === 'EVENT_INTAKE' && !f.template),
    [formLibrary],
  )
  const [intakeFormId, setIntakeFormId] = useState<string | null>(null)
  // The form students will register with: the default unless another is picked (one per country, say).
  const regForms = useMemo(
    () => (formLibrary ?? []).filter((f) => f.purpose === 'EVENT_REGISTRATION' && !f.template && !f.defaultForKind),
    [formLibrary],
  )
  const [registrationFormId, setRegistrationFormId] = useState('')
  useEffect(() => {
    if (!intakeFormId && intakeForms.length > 0) {
      setIntakeFormId((intakeForms.find((f) => f.defaultForKind) ?? intakeForms[0]).id)
    }
  }, [intakeForms, intakeFormId])
  const { data: intakeDef } = useFormDefById(intakeFormId ?? undefined)
  const intake = useMemo<IntakeForm>(() => {
    if (intakeDef) {
      try {
        return normalizeIntake(JSON.parse(intakeDef.schema))
      } catch {
        /* fall through */
      }
    }
    return readIntakeForm()
  }, [intakeDef])
  const submitResponse = useSubmitFormResponse()
  const [answers, setAnswers] = useState<Record<string, Answer>>({})
  const { data: allTemplates } = useEventTemplates()
  const templates = (allTemplates ?? []).filter((t) => t.active)
  const template = templates.find((t) => t.id === d.templateId) ?? null
  // Starting from a template fills in what it holds; each can still be changed for this one event.
  const pickTemplate = (id: string) => {
    const t = templates.find((x) => x.id === id)
    if (!t) {
      set({ templateId: '' })
      return
    }
    set({ templateId: t.id, type: t.eventType, format: FORMAT_LABEL[t.format] ?? 'In-person', timezone: t.timezone })
    if (t.intakeFormId) setIntakeFormId(t.intakeFormId)
    setRegistrationFormId(t.registrationFormId ?? '')
    setAnswers({})
  }
  const publishBeforeStart = !d.publishDay || !d.day || d.publishDay <= d.day

  const endAfterStart = !d.endTime || !d.time || d.endTime > d.time
  const basicsValid = d.name.trim().length > 0 && d.day.length > 0 && endAfterStart && publishBeforeStart
  // If/then rules (Admin -> Forms -> Configuration): hidden questions are
  // neither rendered, required, nor submitted.
  // Rules can also require a question, and a choice can depend on another answer: each field is
  // taken as it stands for the answers so far.
  const rawAnswer = (id: string) => answerText(answers[id])
  const answerOf = (id: string) => effectiveValue(intake, id, rawAnswer)
  const isVisible = (f: IntakeField) => fieldVisible(intake, f.id, answerOf)
  const intakeFields = flattenIntake(intake).filter(isVisible).map((f) => resolveField(intake, f, answerOf))
  const detailsValid = intakeFields.every((f) => DISPLAY_TYPES.includes(f.type) || !f.required || answerOf(f.id).trim() !== '')
  const isLast = step === STEPS.length - 1

  function submit() {
    const startsAt = zonedToIso(d.day, d.time || '09:00', d.timezone)
    const endsAt = d.endTime ? zonedToIso(d.day, d.endTime, d.timezone) : undefined
    create.mutate(
      {
        name: d.name.trim(), type: d.type, location: d.location.trim(),
        startsAt, endsAt, timezone: d.timezone, intakeFormId: intakeFormId ?? undefined,
        registrationFormId: registrationFormId || undefined,
        templateId: d.templateId || undefined,
        format: FORMAT_CODE[d.format],
        publishAt: d.publishDay ? zonedToIso(d.publishDay, d.publishTime || '09:00', d.timezone) : undefined,
        flaggedCritical: d.flagged,
      },
      {
        onSuccess: (ev) => {
          const filled = intakeFields
            .filter((f) => !DISPLAY_TYPES.includes(f.type))
            .map((f) => ({ fieldId: f.id, label: f.label || 'Untitled', value: answerOf(f.id) }))
            .filter((r) => r.value.trim() !== '')
          if (filled.length > 0) {
            submitResponse.mutate({ purpose: 'EVENT_INTAKE', formId: intakeFormId ?? undefined, subjectType: 'EVENT', subjectId: ev.id, answers: JSON.stringify(filled) })
          }
          saveResponses(ev.id, intake, answers)
          toastMsg(`Event "${d.name.trim()}" created`)
          onCreated()
          onClose()
        },
      },
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
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <p className="sub" style={{ fontSize: 12.5, margin: '0 0 4px' }}>
              Start from a template to get its country, timezone, forms, seats, publishing and consent wording. Everything can still be changed for this event.
            </p>
            {[...templates.map((t) => ({ id: t.id, name: t.name, facts: templateFacts(t) })), { id: '', name: 'Blank event', facts: ['Nothing filled in. Default forms, no seat limit, published as soon as approved.'] }].map((t) => (
              <button key={t.id || 'blank'} type="button" aria-pressed={d.templateId === t.id} onClick={() => pickTemplate(t.id)}
                style={{ textAlign: 'left', font: 'inherit', cursor: 'pointer', padding: '12px 14px', borderRadius: 'var(--ra-2)', background: d.templateId === t.id ? 'var(--navy-050)' : 'var(--app-panel)', border: `1px solid ${d.templateId === t.id ? 'var(--bofa-navy)' : 'var(--line)'}` }}>
                <div className="t-strong" style={{ fontSize: 14, color: 'var(--ink-0)' }}>{t.name}</div>
                <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 4, lineHeight: 1.5 }}>{t.facts.join(' · ')}</div>
              </button>
            ))}
          </div>
        )}

        {step === 1 && (
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
            <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
              <Field label="Date" style={{ flex: 1 }}>
                <input className="input" type="date" value={d.day} onChange={(e) => set({ day: e.target.value })} />
              </Field>
              <Field label="Start time" style={{ flex: 1 }}>
                <input className="input" type="time" value={d.time} onChange={(e) => set({ time: e.target.value })} />
              </Field>
              <Field label="End time" style={{ flex: 1 }}>
                <input className="input" type="time" value={d.endTime} onChange={(e) => set({ endTime: e.target.value })} />
                {!endAfterStart && (
                  <div style={{ fontSize: 11.5, color: 'var(--danger-fg, #b3261e)', marginTop: 4 }}>Must be after the start time</div>
                )}
              </Field>
              <Field label="Timezone" style={{ flex: '1 1 100%' }}>
                <select className="select" value={d.timezone} onChange={(e) => set({ timezone: e.target.value })}>
                  {TIMEZONES.map((t) => (
                    <option key={t.value} value={t.value}>
                      {t.label}
                    </option>
                  ))}
                </select>
              </Field>
            </div>
            <div style={{ display: 'flex', gap: 12 }}>
              <Field label="Goes on the careers site on" style={{ flex: 1 }}>
                <input className="input" type="date" max={d.day || undefined} value={d.publishDay} onChange={(e) => set({ publishDay: e.target.value })} />
              </Field>
              <Field label="At" style={{ flex: 1 }}>
                <input className="input" type="time" value={d.publishTime} disabled={!d.publishDay} onChange={(e) => set({ publishTime: e.target.value })} />
              </Field>
            </div>
            <div style={{ fontSize: 11.5, color: publishBeforeStart ? 'var(--ink-4)' : 'var(--danger-fg, #b3261e)', marginTop: -6, lineHeight: 1.5 }}>
              {!publishBeforeStart
                ? 'The event must be published before it starts.'
                : `The event is sent for approval when you create it. Once approved, students see it from this date and time (in the event's timezone). Leave it empty ${template?.publishDaysBefore != null ? `to use the template's ${template.publishDaysBefore} days before the event` : 'to publish as soon as it is approved'}.`}
            </div>
            <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: 'var(--ink-2)' }}>
              <input type="checkbox" checked={d.flagged} onChange={(e) => set({ flagged: e.target.checked })} />
              Flag for compliance review — routes this event down the workflow's exception path
            </label>
          </div>
        )}

        {step === 2 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {regForms.length > 0 && (
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 11.5, color: 'var(--ink-4)' }}>
                Form students register with
                <select className="select" value={registrationFormId} onChange={(ev2) => setRegistrationFormId(ev2.target.value)}>
                  <option value="">The default form</option>
                  {regForms.map((f) => (
                    <option key={f.id} value={f.id}>{f.name}</option>
                  ))}
                </select>
              </label>
            )}
            {intakeFields.length === 0 ? (
              <p className="sub" style={{ fontSize: 12.5, margin: 0 }}>
                No custom intake fields are configured. Admins can design this step under Admin → Forms.
              </p>
            ) : (
              <>
              {intakeForms.length > 1 && (
                <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 11.5, color: 'var(--ink-4)', marginBottom: 4 }}>
                  Intake form
                  <select className="select" value={intakeFormId ?? ''}
                    onChange={(ev2) => { setIntakeFormId(ev2.target.value); setAnswers({}) }}>
                    {intakeForms.map((f) => (
                      <option key={f.id} value={f.id}>{f.name}{f.defaultForKind ? ' ★' : ''}</option>
                    ))}
                  </select>
                </label>
              )}
              {intake.rows.map((row) => {
                const shown = row.slots.filter((f): f is IntakeField => f !== null && isVisible(f))
                if (row.slots.some((f) => f !== null) && shown.length === 0) return null
                return (
                  <div key={row.id} style={{ display: 'grid', gridTemplateColumns: `repeat(${Math.max(shown.length, 1)}, minmax(0, 1fr))`, gap: 12 }}>
                    {shown.map((f) => (
                      <IntakeInput key={f.id} f={resolveField(intake, f, answerOf)} value={answerOf(f.id) === '' && rawAnswer(f.id) !== '' ? undefined : answers[f.id]} onChange={(v) => setAnswers((prev) => ({ ...prev, [f.id]: v }))} />
                    ))}
                  </div>
                )
              })}
              </>
            )}
          </div>
        )}

        {step === 3 && (
          <div>
            <Fact label="Template">{template?.name ?? 'Blank event'}</Fact>
            <Fact label="Name">{d.name || '—'}</Fact>
            <Fact label="Type">{typeLabel(d.type)}</Fact>
            <Fact label="Format">{d.format}</Fact>
            <Fact label={d.format === 'Virtual' ? 'Link' : 'Location'}>{d.location || '—'}</Fact>
            <Fact label="When">
              {d.day
                ? `${d.day} · ${d.time}${d.endTime ? `–${d.endTime}` : ''} ${TIMEZONES.find((t) => t.value === d.timezone)?.label ?? ''}`
                : '—'}
            </Fact>
            {intakeFields
              .filter((f) => !DISPLAY_TYPES.includes(f.type))
              .filter((f) => answerText(answers[f.id]).trim() !== '')
              .map((f) => (
                <Fact key={f.id} label={f.label}>{answerText(answers[f.id])}</Fact>
              ))}
            <Fact label="Careers site">
              {d.publishDay
                ? `${d.publishDay} · ${d.publishTime} ${TIMEZONES.find((t) => t.value === d.timezone)?.label ?? ''}, once approved`
                : template?.publishDaysBefore != null
                  ? `${template.publishDaysBefore} days before the event, once approved`
                  : 'As soon as it is approved'}
            </Fact>
            {template && templateFacts(template).slice(1, 3).map((f) => <Fact key={f} label="From the template">{f}</Fact>)}
            <div style={{ fontSize: 11.5, color: 'var(--ink-4)', marginTop: 12, lineHeight: 1.5 }}>
              Creating the event sends it for review and approval. Students cannot see it until it is approved and its careers-site date has come.
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
            disabled={(step === 1 && !basicsValid) || (step === 2 && !detailsValid)}
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

const TIER_LABEL: Record<string, string> = { CORE: 'Core', TARGET: 'Target', OPPORTUNISTIC: 'Opportunistic' }

/* Which schools are worth going back to: every school with what came of its students across
   all events. A school with nothing to show is listed too, so a quiet target school is seen. */
/* Adds a school to the list. Its country decides which students are offered it when a
   registration form asks for the country first. */
function AddSchool() {
  const add = useAddSchool()
  const blank = { name: '', location: '', tier: 'TARGET', country: '' }
  const [open, setOpen] = useState(false)
  const [f, setF] = useState(blank)
  if (!open) {
    return (
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 10 }}>
        <button className="btn btn--outline btn--sm" onClick={() => setOpen(true)}>Add a school</button>
      </div>
    )
  }
  return (
    <div className="card" style={{ padding: '14px 16px', marginBottom: 12, display: 'flex', gap: 10, alignItems: 'flex-end', flexWrap: 'wrap' }}>
      <label style={{ flex: '2 1 200px', fontSize: 11.5, color: 'var(--ink-4)' }}>
        School
        <input className="input" value={f.name} placeholder="University of Leeds" onChange={(e) => setF({ ...f, name: e.target.value })} />
      </label>
      <label style={{ flex: '1 1 150px', fontSize: 11.5, color: 'var(--ink-4)' }}>
        City
        <input className="input" value={f.location} placeholder="Leeds, UK" onChange={(e) => setF({ ...f, location: e.target.value })} />
      </label>
      <label style={{ flex: '1 1 150px', fontSize: 11.5, color: 'var(--ink-4)' }}>
        Country
        <select className="select" value={f.country} onChange={(e) => setF({ ...f, country: e.target.value })}>
          <option value="">Not said</option>
          {EVENT_COUNTRIES.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}
        </select>
      </label>
      <label style={{ flex: '1 1 130px', fontSize: 11.5, color: 'var(--ink-4)' }}>
        Tier
        <select className="select" value={f.tier} onChange={(e) => setF({ ...f, tier: e.target.value })}>
          {Object.entries(TIER_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
      </label>
      <button className="btn btn--primary btn--sm" disabled={add.isPending || f.name.trim() === ''}
        onClick={() => add.mutate({ name: f.name.trim(), location: f.location.trim() || undefined, tier: f.tier, country: f.country || undefined }, { onSuccess: () => { setF(blank); setOpen(false) } })}>
        {add.isPending ? 'Adding…' : 'Add'}
      </button>
      <button className="btn btn--ghost btn--sm" onClick={() => setOpen(false)}>Cancel</button>
      {add.isError && (
        <span style={{ flexBasis: '100%', fontSize: 12, color: 'var(--c-red, #c41230)' }}>
          {(add.error as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Could not add the school.'}
        </span>
      )}
    </div>
  )
}

function SchoolReport() {
  const { data, isLoading } = useSchoolReport()
  if (isLoading) return <div className="card"><div style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>Loading schools…</div></div>
  const rows = data ?? []
  if (rows.length === 0) {
    return <div className="card"><div style={{ padding: '44px 20px', textAlign: 'center', color: 'var(--ink-4)', fontSize: 13.5 }}>No schools are set up yet.</div></div>
  }
  const num = { textAlign: 'right' as const, fontVariantNumeric: 'tabular-nums' as const }
  return (
    <>
    <AddSchool />
    <div className="card" style={{ overflowX: 'auto' }}>
      <table className="data-table" style={{ width: '100%' }}>
        <thead>
          <tr>
            <th>School</th>
            <th>Country</th>
            <th>Tier</th>
            <th style={num}>Events</th>
            <th style={num}>Registered</th>
            <th style={num}>Attended</th>
            <th style={num}>Attendance</th>
            <th style={num}>Shortlisted</th>
            <th style={num}>Put forward</th>
            <th style={num}>Hired</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((s) => (
            <tr key={s.schoolId}>
              <td>
                <div className="t-strong">{s.name}</div>
                {s.location && <div className="muted" style={{ fontSize: 12, margin: 0 }}>{s.location}</div>}
              </td>
              <td>{countryName(s.country) ?? '—'}</td>
              <td><span className="badge">{TIER_LABEL[s.tier] ?? s.tier}</span></td>
              <td style={num}>{s.events}</td>
              <td style={num}>{s.registered}</td>
              <td style={num}>{s.attended}</td>
              <td style={num}>{s.registered > 0 ? `${rate(s.attended, s.registered)}%` : '—'}</td>
              <td style={num}>{s.shortlisted}</td>
              <td style={num}>{s.putForward}</td>
              <td style={num}>{s.hired}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="muted" style={{ fontSize: 12, padding: '10px 16px 14px' }}>
        Counts students by the school they chose when registering, across every event. Cancelled registrations are left out; a student who chose Other is not counted under any school.
      </div>
    </div>
    </>
  )
}

export default function EventsPage() {
  const { data, isLoading } = useEvents()
  const [filter, setFilter] = useState<Filter>('upcoming')
  const [view, setView] = useState<'events' | 'schools'>('events')
  // The open event is read from the list each time, so its numbers move as the roster does.
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const selected = (data ?? []).find((e) => e.id === selectedId) ?? null
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
          Engagement · Campus
        </div>
        <div className="page-head__row">
          <div>
            <h1>Campus</h1>
            <p className="sub">
              Run campus drives and hiring events end to end — registration through attendance to offers, with the conversion
              funnel in one view.
            </p>
          </div>
          <a className="btn btn--ghost" href="/careers/events" target="_blank" rel="noreferrer" style={{ marginRight: 8 }}>View public page →</a>
          <button className="btn btn--primary" onClick={() => setCreating(true)}>New event</button>
        </div>
      </div>

      <div className="grid-stats" style={{ gridTemplateColumns: 'repeat(4,1fr)', marginBottom: 18 }}>
        <StatTile label="Upcoming events" value={kpis.upcoming} meta="Scheduled ahead" />
        <StatTile label="Total registrations" value={kpis.reg.toLocaleString()} meta="Across all events" />
        <StatTile label="Avg attendance" value={`${kpis.attRate}%`} meta="Registered → attended" />
        <StatTile label="Hires from events" value={kpis.hires} meta="Event-sourced" />
      </div>

      <div style={{ display: 'flex', gap: 12, marginBottom: 16, flexWrap: 'wrap' }}>
        <div className="segmented" role="group" aria-label="View">
          <button aria-pressed={view === 'events'} onClick={() => setView('events')}>Events</button>
          <button aria-pressed={view === 'schools'} onClick={() => setView('schools')}>Schools</button>
        </div>
        {view === 'events' && (
          <div className="segmented" role="group" aria-label="Filter">
            <button aria-pressed={filter === 'upcoming'} onClick={() => setFilter('upcoming')}>Upcoming</button>
            <button aria-pressed={filter === 'past'} onClick={() => setFilter('past')}>Past</button>
            <button aria-pressed={filter === 'all'} onClick={() => setFilter('all')}>All</button>
          </div>
        )}
      </div>

      {view === 'schools' ? (
        <SchoolReport />
      ) : isLoading ? (
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
              onClick={() => setSelectedId(e.id)}
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
                <span>{date(e.startsAt)} · {timeRange(e)}</span>
                {approvalBadge(e.approvalStatus)}
                {isUpcoming(e.startsAt) && (e.approvalStatus === 'APPROVED' || e.approvalStatus === 'AUTO_APPROVED') && (
                  <span className={e.published ? 'badge badge--ok' : 'badge badge--info'}>{e.published ? 'On careers site' : 'Scheduled to publish'}</span>
                )}
                <span className="badge" style={{ marginLeft: 'auto' }}>{formatOf(e)}</span>
              </div>
              {(e.capacity !== null || e.waitlisted > 0 || e.shortlisted > 0) && (
                <div className="muted" style={{ marginTop: 8, fontSize: 12 }}>
                  {[
                    e.capacity !== null ? `${e.registrations - e.waitlisted} of ${e.capacity} seats` : null,
                    e.waitlisted > 0 ? `${e.waitlisted} waitlisted` : null,
                    e.shortlisted > 0 ? `${e.shortlisted} shortlisted` : null,
                  ].filter(Boolean).join(' · ')}
                </div>
              )}
              <FunnelBar e={e} />
            </button>
          ))}
        </div>
      )}

      {selected && <EventDetail e={selected} onClose={() => setSelectedId(null)} />}
      {creating && <CreateEventWizard onClose={() => setCreating(false)} onCreated={() => setFilter('all')} />}
    </div>
  )
}
