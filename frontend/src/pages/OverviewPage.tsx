import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { useAllSlots, useAnalyticsSummary, useJobs } from '../api/hooks'
import { date, percent } from '../lib/format'
import type { FunnelStage, JobSummary } from '../api/types'

function StatTile({ label, value, meta }: { label: string; value: string | number; meta?: string }) {
  return (
    <div className="stat">
      <div className="stat__label">{label}</div>
      <div className="stat__value">{value}</div>
      {meta ? <div className="stat__meta">{meta}</div> : null}
    </div>
  )
}

const STATUS_LABEL: Record<string, string> = {
  OPEN: 'Open',
  PAUSED: 'On hold',
  FILLED: 'Filled',
  DRAFT: 'Draft',
  CLOSED: 'Closed',
  ARCHIVED: 'Archived',
}
const STATUS_COLORS: Record<string, string> = {
  OPEN: '#1D9E75',
  PAUSED: '#EF9F27',
  FILLED: '#185FA5',
  DRAFT: '#888780',
  CLOSED: '#A32D2D',
  ARCHIVED: '#534AB7',
}
const FALLBACK_COLORS = ['#1D9E75', '#185FA5', '#EF9F27', '#534AB7', '#D4537E', '#888780']

function JobSummaryDonut({ jobs }: { jobs: JobSummary[] }) {
  const segs = useMemo(() => {
    const counts: Record<string, number> = {}
    jobs.forEach((j) => {
      const s = (j.status || 'OTHER').toUpperCase()
      counts[s] = (counts[s] || 0) + 1
    })
    return Object.entries(counts).sort((a, b) => b[1] - a[1])
  }, [jobs])

  const total = jobs.length
  const r = 66
  const C = 2 * Math.PI * r
  let offset = 0

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 22, flexWrap: 'wrap' }}>
      <div style={{ position: 'relative', width: 168, height: 168, flexShrink: 0 }}>
        <svg width={168} height={168} viewBox="0 0 168 168" style={{ transform: 'rotate(-90deg)' }}>
          <circle cx={84} cy={84} r={r} fill="none" stroke="var(--app-sunken)" strokeWidth={20} />
          {segs.map(([status, count], i) => {
            const frac = total > 0 ? count / total : 0
            const len = frac * C
            const color = STATUS_COLORS[status] ?? FALLBACK_COLORS[i % FALLBACK_COLORS.length]
            const circle = (
              <circle
                key={status}
                cx={84}
                cy={84}
                r={r}
                fill="none"
                stroke={color}
                strokeWidth={20}
                strokeDasharray={`${len} ${C - len}`}
                strokeDashoffset={-offset}
              />
            )
            offset += len
            return circle
          })}
        </svg>
        <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
          <div className="t-num" style={{ fontSize: 34, fontWeight: 700, color: 'var(--ink-0)', lineHeight: 1 }}>{total}</div>
          <div style={{ fontSize: 12, color: 'var(--ink-4)', marginTop: 2 }}>Total jobs</div>
        </div>
      </div>
      <div style={{ flex: 1, minWidth: 150, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px 16px' }}>
        {segs.map(([status, count], i) => (
          <div key={status} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
            <span style={{ width: 10, height: 10, borderRadius: 3, background: STATUS_COLORS[status] ?? FALLBACK_COLORS[i % FALLBACK_COLORS.length], flexShrink: 0 }} />
            <span style={{ color: 'var(--ink-3)' }}>{STATUS_LABEL[status] ?? status}</span>
            <span className="t-num" style={{ marginLeft: 'auto', fontWeight: 600, color: 'var(--ink-0)' }}>{count}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

const ivTime = (iso: string) => {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '' : d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })
}

function ScheduleTimeline({ jobs }: { jobs: JobSummary[] }) {
  const jobIds = useMemo(() => jobs.map((j) => j.id), [jobs])
  const titleById = useMemo(() => Object.fromEntries(jobs.map((j) => [j.id, j.title])), [jobs])
  const { slots, isLoading } = useAllSlots(jobIds)

  const upcoming = useMemo(() => {
    const now = Date.now()
    return [...slots]
      .filter((s) => !Number.isNaN(new Date(s.startsAt).getTime()))
      .sort((a, b) => new Date(a.startsAt).getTime() - new Date(b.startsAt).getTime())
      .filter((s) => new Date(s.endsAt || s.startsAt).getTime() >= now - 86_400_000)
      .slice(0, 6)
  }, [slots])

  if (isLoading) return <div style={{ padding: '20px', color: 'var(--ink-4)', fontSize: 13 }}>Loading schedule…</div>
  if (upcoming.length === 0) return <div style={{ padding: '20px', color: 'var(--ink-4)', fontSize: 13 }}>No upcoming interview slots.</div>

  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      {upcoming.map((s) => (
        <div
          key={s.id}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 14,
            padding: '11px 14px',
            borderBottom: '1px solid var(--line)',
            borderLeft: `3px solid ${s.booked ? 'var(--bofa-navy)' : '#1D9E75'}`,
          }}
        >
          <div style={{ width: 116, flexShrink: 0 }}>
            <div className="t-num" style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--ink-1)', whiteSpace: 'nowrap' }}>
              {/* Drop the first meridiem when both ends share it: "10:30 – 11:00 AM". */}
              {ivTime(s.startsAt).endsWith(ivTime(s.endsAt).slice(-2))
                ? `${ivTime(s.startsAt).replace(/ (AM|PM)$/, '')} – ${ivTime(s.endsAt)}`
                : `${ivTime(s.startsAt)} – ${ivTime(s.endsAt)}`}
            </div>
            <div style={{ fontSize: 11, color: 'var(--ink-4)' }}>{date(s.startsAt)}</div>
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 13, color: 'var(--ink-0)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {titleById[s.jobId] || 'Interview'}
            </div>
            <div style={{ fontSize: 11.5, color: 'var(--ink-4)' }}>{s.interviewerName || 'Unassigned'}</div>
          </div>
          <span className={`badge ${s.booked ? 'badge--info' : 'badge--ok'}`}>{s.booked ? 'Booked' : 'Open'}</span>
        </div>
      ))}
    </div>
  )
}

function Funnel({ stages }: { stages: FunnelStage[] }) {
  const max = Math.max(1, ...stages.map((s) => s.count))
  const top = stages[0]?.count ?? 0
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      {stages.map((s) => {
        const width = `${Math.max(4, Math.round((s.count / max) * 100))}%`
        const conv = top > 0 ? Math.round((s.count / top) * 100) : 0
        return (
          <div key={s.stage}>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'baseline',
                marginBottom: 6,
              }}
            >
              <span style={{ fontSize: 13.5, fontWeight: 600, color: 'var(--ink-1)' }}>{s.stage}</span>
              <span style={{ fontSize: 12.5, color: 'var(--ink-4)' }}>
                <span className="t-num" style={{ color: 'var(--ink-1)', fontWeight: 600 }}>
                  {s.count.toLocaleString()}
                </span>{' '}
                · {conv}%
              </span>
            </div>
            <div
              style={{
                height: 26,
                background: 'var(--app-sunken)',
                borderRadius: 'var(--ra-1)',
                overflow: 'hidden',
              }}
            >
              <div
                style={{
                  width,
                  height: '100%',
                  background: 'var(--bofa-navy)',
                  borderRadius: 'var(--ra-1)',
                  transition: 'width 0.3s ease',
                }}
              />
            </div>
          </div>
        )
      })}
    </div>
  )
}

export default function OverviewPage() {
  const { data, isLoading, isError, refetch } = useAnalyticsSummary()
  const { data: jobs } = useJobs()

  return (
    <div>
      <div className="page-head" style={{ marginBottom: 20 }}>
        <div className="crumb">
          <span className="dot" />
          Recruiting · Overview
        </div>
        <div className="page-head__row">
          <div>
            <h1 style={{ fontSize: 28 }}>Recruiting overview</h1>
            <p className="sub">Live snapshot of hiring activity across all open requisitions.</p>
          </div>
        </div>
      </div>

      {isLoading ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
            Loading analytics…
          </div>
        </div>
      ) : isError || !data ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center' }}>
            <div style={{ fontFamily: 'var(--font-display)', fontSize: 20, color: 'var(--ink-0)' }}>
              Couldn’t load analytics.
            </div>
            <div style={{ fontSize: 13.5, color: 'var(--ink-4)', margin: '6px 0 16px' }}>
              The API didn’t respond. Check that the backend is running.
            </div>
            <button className="btn btn--outline btn--sm" onClick={() => refetch()}>
              Retry
            </button>
          </div>
        </div>
      ) : (
        <>
          <div className="grid-stats" style={{ gridTemplateColumns: 'repeat(4,1fr)', marginBottom: 16 }}>
            <StatTile label="Open jobs" value={data.openJobs} meta="Actively recruiting" />
            <StatTile label="Active candidates" value={data.activeCandidates.toLocaleString()} meta="In pipeline" />
            <StatTile label="Avg time-to-hire" value={`${Math.round(data.avgTimeToHireDays)}d`} meta="Req to offer-accept" />
            <StatTile label="Offer-accept rate" value={percent(data.offerAcceptRate, false)} meta="Trailing 90 days" />
          </div>

          <div className="grid-stats" style={{ gridTemplateColumns: 'repeat(5,1fr)', marginBottom: 20 }}>
            <StatTile label="Applications · 30d" value={data.applications30d.toLocaleString()} />
            <StatTile label="Interviews · 30d" value={data.interviews30d.toLocaleString()} />
            <StatTile label="Offers · 30d" value={data.offers30d.toLocaleString()} />
            <StatTile label="Hires · 30d" value={data.hires30d.toLocaleString()} />
            <StatTile label="Avg fit score" value={Math.round(data.avgFitScore)} meta="Across active apps" />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) 360px', gap: 20, alignItems: 'start', marginBottom: 20 }}>
            <div className="card">
              <div className="card__head">
                <h3 style={{ fontSize: 15 }}>Job summary</h3>
                <Link to="/jobs" className="link" style={{ fontSize: 12.5 }}>View all jobs</Link>
              </div>
              <div className="card__body">
                {jobs && jobs.length > 0 ? (
                  <JobSummaryDonut jobs={jobs} />
                ) : (
                  <div style={{ padding: '24px 0', textAlign: 'center', color: 'var(--ink-4)' }}>No jobs yet.</div>
                )}
              </div>
            </div>

            <div className="card">
              <div className="card__head">
                <h3 style={{ fontSize: 15 }}>Upcoming schedule</h3>
                <Link to="/interviews" className="link" style={{ fontSize: 12.5 }}>Calendar</Link>
              </div>
              <div className="card__body" style={{ padding: 0 }}>
                {jobs && jobs.length > 0 ? (
                  <ScheduleTimeline jobs={jobs} />
                ) : (
                  <div style={{ padding: '24px 18px', textAlign: 'center', color: 'var(--ink-4)' }}>No schedule yet.</div>
                )}
              </div>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) 360px', gap: 20, alignItems: 'start' }}>
            <div className="card">
              <div className="card__head">
                <h3>Hiring funnel</h3>
                <span className="eyebrow">Conversion vs top of funnel</span>
              </div>
              <div className="card__body">
                {data.funnel && data.funnel.length > 0 ? (
                  <Funnel stages={data.funnel} />
                ) : (
                  <div style={{ padding: '24px 0', textAlign: 'center', color: 'var(--ink-4)' }}>
                    No funnel data yet.
                  </div>
                )}
              </div>
            </div>

            <div className="card">
              <div className="card__head">
                <h3 style={{ fontSize: 15 }}>Stage breakdown</h3>
                <span className="badge">Live</span>
              </div>
              <div className="card__body" style={{ padding: '8px 0' }}>
                {Object.entries(data.byStage ?? {}).length > 0 ? (
                  Object.entries(data.byStage).map(([stage, count]) => (
                    <div
                      key={stage}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        gap: 12,
                        padding: '11px 18px',
                        borderBottom: '1px solid var(--line)',
                      }}
                    >
                      <span className="status">
                        <span className="dot dot--info" />
                        {stage}
                      </span>
                      <span className="t-num" style={{ fontWeight: 600, color: 'var(--ink-0)' }}>
                        {count.toLocaleString()}
                      </span>
                    </div>
                  ))
                ) : (
                  <div style={{ padding: '24px 18px', textAlign: 'center', color: 'var(--ink-4)' }}>
                    No candidates in any stage yet.
                  </div>
                )}
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  )
}
