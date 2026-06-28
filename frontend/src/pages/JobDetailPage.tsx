import { Link, useParams } from 'react-router-dom'
import { useJob } from '../api/hooks'
import { money } from '../lib/format'

function statusBadgeClass(status: string) {
  const s = status.toLowerCase()
  if (s === 'open' || s === 'active' || s === 'published') return 'badge--ok'
  if (s === 'draft') return 'badge--warn'
  if (s === 'closed' || s === 'filled' || s === 'archived') return 'badge--neutral'
  if (s === 'on hold' || s === 'paused') return 'badge--info'
  return 'badge--neutral'
}

export default function JobDetailPage() {
  const { id } = useParams()
  const { data: job, isLoading, isError, refetch } = useJob(id)

  if (isLoading) {
    return (
      <div className="card">
        <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
          Loading job…
        </div>
      </div>
    )
  }

  if (isError || !job) {
    return (
      <div className="card">
        <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center' }}>
          <div style={{ fontFamily: 'var(--font-display)', fontSize: 20, color: 'var(--ink-0)' }}>
            Couldn’t load this job.
          </div>
          <div style={{ fontSize: 13.5, color: 'var(--ink-4)', margin: '6px 0 16px' }}>
            It may not exist, or the API didn’t respond.
          </div>
          <div style={{ display: 'flex', gap: 10, justifyContent: 'center' }}>
            <button className="btn btn--outline btn--sm" onClick={() => refetch()}>
              Retry
            </button>
            <Link to="/jobs" className="btn btn--ghost btn--sm">
              Back to jobs
            </Link>
          </div>
        </div>
      </div>
    )
  }

  const payPer = job.payPeriod ? ` / ${job.payPeriod.toLowerCase()}` : ''
  const stageEntries = Object.entries(job.stageCounts ?? {})

  return (
    <div>
      <div className="page-head" style={{ marginBottom: 20 }}>
        <div className="crumb">
          <span className="dot" />
          <Link to="/jobs" className="link" style={{ fontSize: 11, letterSpacing: 'inherit' }}>
            Jobs
          </Link>
          <span style={{ color: 'var(--ink-5)' }}>/</span>
          {job.family || 'Requisition'}
        </div>
        <div className="page-head__row">
          <div>
            <h1 style={{ fontSize: 28 }}>{job.title}</h1>
            <p className="sub">
              {[job.department, job.location, job.workMode, job.employmentType].filter(Boolean).join(' · ')}
            </p>
            <div style={{ display: 'flex', gap: 10, alignItems: 'center', marginTop: 12, flexWrap: 'wrap' }}>
              <span className={`badge ${statusBadgeClass(job.status)}`}>{job.status}</span>
              <span className="muted" style={{ margin: 0 }}>
                {job.openings} {job.openings === 1 ? 'opening' : 'openings'} · {job.applicants} applicants ·{' '}
                {money(job.payMin)}–{money(job.payMax)}
                {payPer}
              </span>
            </div>
          </div>
          <Link to={`/careers/${job.id}`} className="btn btn--outline btn--sm">
            View on career site
          </Link>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) 340px', gap: 20, alignItems: 'start' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          {(job.summary || job.description) && (
            <div className="card">
              <div className="card__head">
                <h3>About this role</h3>
              </div>
              <div className="card__body">
                {job.summary && (
                  <p style={{ fontSize: 15, color: 'var(--ink-1)', margin: '0 0 14px', lineHeight: 1.6 }}>
                    {job.summary}
                  </p>
                )}
                {job.description && (
                  <p style={{ fontSize: 14, color: 'var(--ink-2)', margin: 0, lineHeight: 1.7, whiteSpace: 'pre-line' }}>
                    {job.description}
                  </p>
                )}
              </div>
            </div>
          )}

          {job.preview && (job.preview.headline || job.preview.dayInLife) && (
            <div className="card card--strip s-violet">
              <div className="card__head">
                <h3 style={{ fontSize: 15 }}>Immersive preview</h3>
                <span className="badge badge--purple">Day in the life</span>
              </div>
              <div className="card__body">
                {job.preview.headline && (
                  <div
                    style={{
                      fontFamily: 'var(--font-display)',
                      fontSize: 19,
                      color: 'var(--ink-0)',
                      marginBottom: 10,
                      letterSpacing: '-0.01em',
                    }}
                  >
                    {job.preview.headline}
                  </div>
                )}
                {job.preview.dayInLife && (
                  <p style={{ fontSize: 14, color: 'var(--ink-2)', margin: 0, lineHeight: 1.7 }}>
                    {job.preview.dayInLife}
                  </p>
                )}
              </div>
            </div>
          )}

          {job.skills && job.skills.length > 0 && (
            <div className="card">
              <div className="card__head">
                <h3 style={{ fontSize: 15 }}>Skills</h3>
                <span className="eyebrow">{job.skills.filter((s) => s.required).length} required</span>
              </div>
              <div className="card__body">
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                  {job.skills.map((s) => (
                    <span key={s.id} className={`chip ${s.required ? 'chip--active' : ''}`} style={{ cursor: 'default' }}>
                      {s.name}
                      {s.required ? <span style={{ color: 'var(--bofa-red)' }}>*</span> : null}
                    </span>
                  ))}
                </div>
              </div>
            </div>
          )}

          {job.knockoutQuestions && job.knockoutQuestions.length > 0 && (
            <div className="card">
              <div className="card__head">
                <h3 style={{ fontSize: 15 }}>Knockout questions</h3>
                <span className="eyebrow">{job.knockoutQuestions.length} screening</span>
              </div>
              <div className="card__body" style={{ padding: 0 }}>
                {[...job.knockoutQuestions]
                  .sort((a, b) => a.ordinal - b.ordinal)
                  .map((k, i) => (
                    <div
                      key={k.id}
                      style={{
                        display: 'flex',
                        gap: 12,
                        padding: '14px 20px',
                        borderBottom: '1px solid var(--line)',
                      }}
                    >
                      <span className="t-num" style={{ color: 'var(--ink-4)', fontWeight: 600, flex: 'none' }}>
                        {i + 1}.
                      </span>
                      <div style={{ flex: 1 }}>
                        <div style={{ fontSize: 14, color: 'var(--ink-1)' }}>{k.prompt}</div>
                        <div style={{ fontSize: 12, color: 'var(--ink-4)', marginTop: 3 }}>
                          {k.answerType}
                          {k.required ? ' · required' : ''}
                        </div>
                      </div>
                    </div>
                  ))}
              </div>
            </div>
          )}
        </div>

        <aside style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          <div className="card">
            <div className="card__head">
              <h3 style={{ fontSize: 15 }}>Pipeline</h3>
            </div>
            <div className="card__body" style={{ padding: '8px 0' }}>
              {stageEntries.length > 0 ? (
                stageEntries.map(([stage, count]) => (
                  <div
                    key={stage}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      gap: 12,
                      padding: '10px 18px',
                    }}
                  >
                    <span className="status">
                      <span className="dot dot--info" />
                      {stage}
                    </span>
                    <span className="t-num" style={{ fontWeight: 600, color: 'var(--ink-0)' }}>
                      {count}
                    </span>
                  </div>
                ))
              ) : (
                <div style={{ padding: '20px 18px', textAlign: 'center', color: 'var(--ink-4)', fontSize: 13.5 }}>
                  No candidates in the pipeline yet.
                </div>
              )}
            </div>
          </div>

          <div className="card">
            <div className="card__head">
              <h3 style={{ fontSize: 15 }}>Details</h3>
            </div>
            <div className="card__body" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {[
                ['Hiring manager', job.hiringManager],
                ['Recruiter', job.recruiter],
                ['Employment', job.employmentType],
                ['Work mode', job.workMode],
                ['Languages', job.languages?.join(', ')],
              ]
                .filter(([, v]) => v)
                .map(([label, value]) => (
                  <div key={label} style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
                    <span style={{ fontSize: 13, color: 'var(--ink-4)' }}>{label}</span>
                    <span style={{ fontSize: 13.5, color: 'var(--ink-1)', fontWeight: 500, textAlign: 'right' }}>
                      {value}
                    </span>
                  </div>
                ))}
            </div>
          </div>
        </aside>
      </div>
    </div>
  )
}
