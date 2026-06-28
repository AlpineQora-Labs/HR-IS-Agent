import { useMemo, useState } from 'react'
import { useApplications, useInterviews, useJobs, useSlots } from '@/api/hooks'
import { date } from '@/lib/format'
import type { Interview, Slot } from '@/api/types'

const statusBadge = (status: string) => {
  const s = status.toUpperCase()
  if (s === 'COMPLETED' || s === 'DONE') return 'badge--ok'
  if (s === 'SCHEDULED' || s === 'CONFIRMED') return 'badge--info'
  if (s === 'CANCELLED' || s === 'NO_SHOW') return 'badge--danger'
  if (s === 'PENDING' || s === 'REQUESTED') return 'badge--warn'
  return ''
}

const recBadge = (rec: string) => {
  const r = rec.toUpperCase()
  if (r.includes('STRONG_YES') || r === 'HIRE' || r === 'YES') return 'badge--ok'
  if (r.includes('NO')) return 'badge--danger'
  if (r.includes('LEAN') || r === 'MAYBE') return 'badge--warn'
  return ''
}

const time = (iso: string | null | undefined) => {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })
}

function Empty({ label }: { label: string }) {
  return (
    <div className="card">
      <div className="card__body" style={{ padding: '40px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
        {label}
      </div>
    </div>
  )
}

function ScheduledTable({ rows, isLoading }: { rows: Interview[] | undefined; isLoading: boolean }) {
  if (isLoading) return <Empty label="Loading interviews…" />
  if (!rows || rows.length === 0) return <Empty label="No interviews scheduled for this application." />
  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            <th>Type</th>
            <th>When</th>
            <th className="t-right">Duration</th>
            <th>Status</th>
            <th>Interviewers</th>
            <th className="t-right">Score</th>
            <th>Recommendation</th>
            <th>Summary</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((iv) => (
            <tr key={iv.id}>
              <td className="t-strong">{iv.type}</td>
              <td>
                {date(iv.scheduledAt)}
                <span className="t-muted"> · {time(iv.scheduledAt)}</span>
              </td>
              <td className="t-right t-num">{iv.durationMin ? `${iv.durationMin}m` : '—'}</td>
              <td>
                <span className={`badge ${statusBadge(iv.status)}`}>{iv.status || '—'}</span>
              </td>
              <td className="t-muted">{iv.interviewers?.length ? iv.interviewers.join(', ') : '—'}</td>
              <td className="t-right t-num">{iv.score ? iv.score.toFixed(1) : '—'}</td>
              <td>
                {iv.recommendation ? (
                  <span className={`badge ${recBadge(iv.recommendation)}`}>{iv.recommendation}</span>
                ) : (
                  <span className="t-muted">—</span>
                )}
              </td>
              <td style={{ maxWidth: 320, color: 'var(--ink-3)' }}>{iv.summary || '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function SlotsList({ slots, isLoading }: { slots: Slot[] | undefined; isLoading: boolean }) {
  if (isLoading) return <Empty label="Loading slots…" />
  if (!slots || slots.length === 0) return <Empty label="No open interview slots for this job." />
  return (
    <div className="card">
      <div className="card__body" style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {slots.map((slot) => (
          <div
            key={slot.id}
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 16,
              padding: '12px 14px',
              borderRadius: 'var(--ra-2)',
              background: slot.booked ? 'var(--app-sunken)' : 'var(--app-panel)',
              border: '1px solid var(--line)',
            }}
          >
            <div>
              <div className="t-strong" style={{ fontSize: 14 }}>
                {date(slot.startsAt)}
              </div>
              <div className="muted" style={{ margin: '2px 0 0' }}>
                {time(slot.startsAt)} – {time(slot.endsAt)} · {slot.interviewerName || 'Unassigned'}
              </div>
            </div>
            <span className={`badge ${slot.booked ? 'badge--warn' : 'badge--ok'}`}>
              {slot.booked ? 'Booked' : 'Open'}
            </span>
          </div>
        ))}
      </div>
    </div>
  )
}

export default function InterviewsPage() {
  const { data: jobs } = useJobs()
  const [jobId, setJobId] = useState<string>('')

  const effectiveJobId = jobId || jobs?.[0]?.id

  // Applications for the selected job let us scope interviews to one application.
  const { data: apps } = useApplications(effectiveJobId ? { jobId: effectiveJobId } : undefined)
  const [applicationId, setApplicationId] = useState<string>('')
  const effectiveAppId = applicationId || apps?.[0]?.id

  const { data: interviews, isLoading: ivLoading } = useInterviews(effectiveAppId)
  const { data: slots, isLoading: slotLoading } = useSlots(effectiveJobId)

  const slotsByJob = useMemo(() => slots ?? [], [slots])

  return (
    <div>
      <div className="page-head">
        <div className="crumb">
          <span className="dot" />
          Hiring · Interviews
        </div>
        <div className="page-head__row">
          <div>
            <h1>Interviews</h1>
            <p className="sub">Scheduled conversations and open interviewer availability, scoped by job and candidate.</p>
          </div>
        </div>
      </div>

      <div className="toolbar" style={{ marginBottom: 18 }}>
        <select
          className="select"
          style={{ width: 280 }}
          value={effectiveJobId ?? ''}
          onChange={(e) => {
            setJobId(e.target.value)
            setApplicationId('')
          }}
        >
          {(jobs ?? []).map((j) => (
            <option key={j.id} value={j.id}>
              {j.title}
            </option>
          ))}
        </select>
        <select
          className="select"
          style={{ width: 280 }}
          value={effectiveAppId ?? ''}
          onChange={(e) => setApplicationId(e.target.value)}
          disabled={!apps || apps.length === 0}
        >
          {(apps ?? []).length === 0 ? (
            <option value="">No applications</option>
          ) : (
            (apps ?? []).map((a) => (
              <option key={a.id} value={a.id}>
                {a.candidateName} — {a.stage}
              </option>
            ))
          )}
        </select>
      </div>

      <h3 className="h3" style={{ margin: '0 0 12px' }}>
        Scheduled interviews
      </h3>
      <ScheduledTable rows={interviews} isLoading={ivLoading && !!effectiveAppId} />

      <h3 className="h3" style={{ margin: '28px 0 12px' }}>
        Open interview slots
      </h3>
      <SlotsList slots={slotsByJob} isLoading={slotLoading && !!effectiveJobId} />
    </div>
  )
}
