import { Link, useParams } from 'react-router-dom'
import { useCareerJob } from '@/api/hooks'
import type { JobDetail } from '@/api/types'
import { money } from '@/lib/format'

function payLabel(j: JobDetail) {
  if (!j.payMin && !j.payMax) return 'Competitive'
  const period = j.payPeriod ? `/${j.payPeriod.toLowerCase()}` : ''
  if (j.payMin && j.payMax) return `${money(j.payMin)} – ${money(j.payMax)}${period}`
  return `${money(j.payMax || j.payMin)}${period}`
}

export default function JobPreviewPage() {
  const { id } = useParams()
  const { data: j, isLoading, isError, refetch } = useCareerJob(id)

  if (isLoading) {
    return (
      <div className="careers app-surface">
        <div className="careers__inner">
          <div className="card">
            <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
              Loading role…
            </div>
          </div>
        </div>
      </div>
    )
  }

  if (isError || !j) {
    return (
      <div className="careers app-surface">
        <div className="careers__inner">
          <div className="card">
            <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center' }}>
              <div style={{ fontFamily: 'var(--font-display)', fontSize: 20, color: 'var(--ink-0)' }}>
                We couldn’t load this role.
              </div>
              <div style={{ fontSize: 13.5, color: 'var(--ink-4)', margin: '6px 0 16px' }}>It may have been filled or removed.</div>
              <div style={{ display: 'flex', gap: 10, justifyContent: 'center' }}>
                <button className="btn btn--outline btn--sm" onClick={() => refetch()}>
                  Retry
                </button>
                <Link to="/careers" className="btn btn--ghost btn--sm">
                  Back to all roles
                </Link>
              </div>
            </div>
          </div>
        </div>
      </div>
    )
  }

  const preview = j.preview

  return (
    <div className="careers app-surface">
      {/* Hero */}
      <div style={{ background: 'var(--bofa-navy)', color: '#fff' }}>
        <div className="careers__inner" style={{ paddingTop: 40, paddingBottom: 44, maxWidth: 840 }}>
          <Link
            to="/careers"
            style={{
              color: 'rgba(255,255,255,0.72)',
              fontSize: 13,
              fontWeight: 500,
              borderBottom: 0,
              display: 'inline-block',
              marginBottom: 22,
            }}
          >
            ← All roles
          </Link>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 16 }}>
            {j.family && <span className="badge badge--solid-navy" style={{ background: 'rgba(255,255,255,0.14)', borderColor: 'transparent', color: '#fff' }}>{j.family}</span>}
            {j.workMode && <span className="badge" style={{ background: 'rgba(255,255,255,0.14)', borderColor: 'transparent', color: '#fff' }}>{j.workMode}</span>}
            {j.employmentType && <span className="badge" style={{ background: 'rgba(255,255,255,0.14)', borderColor: 'transparent', color: '#fff' }}>{j.employmentType}</span>}
          </div>
          <h1 style={{ color: '#fff', fontSize: 40, lineHeight: 1.08, margin: 0 }}>{j.title}</h1>
          <div style={{ color: 'rgba(255,255,255,0.82)', fontSize: 15, marginTop: 12 }}>
            {j.location}
            {j.department ? ` · ${j.department}` : ''} · {payLabel(j)}
          </div>
          {preview?.headline && (
            <p style={{ color: 'rgba(255,255,255,0.9)', fontSize: 18, lineHeight: 1.5, maxWidth: '50ch', margin: '20px 0 0' }}>
              {preview.headline}
            </p>
          )}
          <div style={{ marginTop: 26 }}>
            <Link to={`/careers/${j.id}/apply`} className="btn btn--accent btn--lg">
              Apply with Olivia →
            </Link>
          </div>
        </div>
      </div>

      <div className="careers__inner" style={{ maxWidth: 840 }}>
        {/* Immersive preview — day in the life */}
        {preview?.dayInLife && (
          <div className="card card--strip s-red" style={{ marginBottom: 20 }}>
            <div className="card__head">
              <h3>A day in the life</h3>
              <span className="eyebrow eyebrow-red">Immersive preview</span>
            </div>
            <div className="card__body">
              <p style={{ fontSize: 15.5, lineHeight: 1.65, color: 'var(--ink-1)', margin: 0, whiteSpace: 'pre-line' }}>
                {preview.dayInLife}
              </p>
            </div>
          </div>
        )}

        {/* Description */}
        {j.description && (
          <div className="card" style={{ marginBottom: 20 }}>
            <div className="card__head">
              <h3>About the role</h3>
            </div>
            <div className="card__body">
              <p style={{ fontSize: 15, lineHeight: 1.65, color: 'var(--ink-1)', margin: 0, whiteSpace: 'pre-line' }}>
                {j.description}
              </p>
            </div>
          </div>
        )}

        {/* Skills */}
        {j.skills?.length > 0 && (
          <div className="card" style={{ marginBottom: 20 }}>
            <div className="card__head">
              <h3>What you’ll bring</h3>
            </div>
            <div className="card__body">
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                {j.skills.map((s) => (
                  <span key={s.id} className={`chip ${s.required ? 'chip--active' : ''}`} style={{ cursor: 'default' }}>
                    {s.name}
                    {s.required ? ' ·  required' : ''}
                  </span>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* Apply CTA footer */}
        <div
          className="card"
          style={{ textAlign: 'center', marginBottom: 8 }}
        >
          <div className="card__body" style={{ padding: '32px 20px' }}>
            <h3 style={{ margin: '0 0 6px' }}>Ready to apply?</h3>
            <p style={{ fontSize: 14, color: 'var(--ink-3)', margin: '0 0 18px' }}>
              Olivia will walk you through a few quick questions — no long forms.
            </p>
            <Link to={`/careers/${j.id}/apply`} className="btn btn--primary btn--lg">
              Apply with Olivia →
            </Link>
          </div>
        </div>
      </div>
    </div>
  )
}
