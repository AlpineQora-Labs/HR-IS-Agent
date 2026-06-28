import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useCareerJobs } from '@/api/hooks'
import type { JobSummary } from '@/api/types'
import { money } from '@/lib/format'

function payLabel(j: JobSummary) {
  if (!j.payMin && !j.payMax) return null
  const period = j.payPeriod ? `/${j.payPeriod.toLowerCase()}` : ''
  if (j.payMin && j.payMax) return `${money(j.payMin)} – ${money(j.payMax)}${period}`
  return `${money(j.payMax || j.payMin)}${period}`
}

function JobCard({ j }: { j: JobSummary }) {
  const pay = payLabel(j)
  return (
    <Link
      to={`/careers/${j.id}`}
      className="card"
      style={{ display: 'block', textDecoration: 'none', color: 'inherit', height: '100%' }}
    >
      <div className="card__body" style={{ display: 'flex', flexDirection: 'column', gap: 10, height: '100%' }}>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {j.family && <span className="badge">{j.family}</span>}
          {j.workMode && <span className="badge badge--info">{j.workMode}</span>}
        </div>
        <h3 style={{ fontSize: 18, margin: 0, color: 'var(--ink-0)' }}>{j.title}</h3>
        <div style={{ fontSize: 13, color: 'var(--ink-4)' }}>
          {j.location}
          {j.department ? ` · ${j.department}` : ''}
        </div>
        {j.summary && (
          <p style={{ fontSize: 14, color: 'var(--ink-2)', lineHeight: 1.5, margin: 0 }}>{j.summary}</p>
        )}
        <div
          style={{
            marginTop: 'auto',
            paddingTop: 12,
            borderTop: '1px solid var(--line)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 8,
          }}
        >
          <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>{pay ?? 'Competitive'}</span>
          <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--bofa-navy)' }}>View role →</span>
        </div>
      </div>
    </Link>
  )
}

export default function CareerSitePage() {
  const { data, isLoading, isError, refetch } = useCareerJobs()
  const [q, setQ] = useState('')
  const [family, setFamily] = useState('')

  const families = useMemo(() => {
    const set = new Set<string>()
    for (const j of data ?? []) if (j.family) set.add(j.family)
    return Array.from(set).sort()
  }, [data])

  const filtered = useMemo(() => {
    const term = q.trim().toLowerCase()
    return (data ?? []).filter((j) => {
      if (family && j.family !== family) return false
      if (!term) return true
      return (
        j.title.toLowerCase().includes(term) ||
        j.location.toLowerCase().includes(term) ||
        (j.summary ?? '').toLowerCase().includes(term) ||
        (j.department ?? '').toLowerCase().includes(term)
      )
    })
  }, [data, q, family])

  return (
    <div className="careers app-surface">
      {/* Hero */}
      <div style={{ background: 'var(--bofa-navy)', color: '#fff' }}>
        <div className="careers__inner" style={{ paddingTop: 56, paddingBottom: 48, maxWidth: 980 }}>
          <div
            style={{
              fontFamily: 'var(--font-mono)',
              fontSize: 11,
              letterSpacing: '0.16em',
              textTransform: 'uppercase',
              color: 'rgba(255,255,255,0.66)',
              marginBottom: 16,
            }}
          >
            Careers · Bank of America
          </div>
          <h1 style={{ color: '#fff', fontSize: 46, lineHeight: 1.05, margin: 0, maxWidth: '16ch' }}>
            Find your role at the bank.
          </h1>
          <p style={{ color: 'rgba(255,255,255,0.82)', fontSize: 17, lineHeight: 1.55, maxWidth: '52ch', margin: '18px 0 0' }}>
            Build a career that grows with you. Explore open roles below, then apply in minutes with Olivia — our
            conversational hiring assistant.
          </p>
        </div>
      </div>

      <div className="careers__inner" style={{ maxWidth: 980 }}>
        {/* Search + filters */}
        <div
          className="card"
          style={{ marginTop: -28, position: 'relative', zIndex: 1, marginBottom: 24 }}
        >
          <div className="card__body" style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <input
              className="input"
              placeholder="Search roles, teams, or locations…"
              value={q}
              onChange={(e) => setQ(e.target.value)}
            />
            {families.length > 0 && (
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                <button
                  className={`chip ${family === '' ? 'chip--active' : ''}`}
                  aria-pressed={family === ''}
                  onClick={() => setFamily('')}
                >
                  All roles
                </button>
                {families.map((f) => (
                  <button
                    key={f}
                    className={`chip ${family === f ? 'chip--active' : ''}`}
                    aria-pressed={family === f}
                    onClick={() => setFamily(f)}
                  >
                    {f}
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>

        {isLoading ? (
          <div className="card">
            <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
              Loading open roles…
            </div>
          </div>
        ) : isError ? (
          <div className="card">
            <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center' }}>
              <div style={{ fontFamily: 'var(--font-display)', fontSize: 20, color: 'var(--ink-0)' }}>
                We couldn’t load open roles.
              </div>
              <div style={{ fontSize: 13.5, color: 'var(--ink-4)', margin: '6px 0 16px' }}>Please try again in a moment.</div>
              <button className="btn btn--outline btn--sm" onClick={() => refetch()}>
                Retry
              </button>
            </div>
          </div>
        ) : filtered.length === 0 ? (
          <div className="card">
            <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
              {(data ?? []).length === 0 ? 'No open roles right now — check back soon.' : 'No roles match your search.'}
            </div>
          </div>
        ) : (
          <>
            <div
              className="eyebrow"
              style={{ marginBottom: 14, color: 'var(--ink-4)' }}
            >
              {filtered.length} open {filtered.length === 1 ? 'role' : 'roles'}
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 16 }}>
              {filtered.map((j) => (
                <JobCard key={j.id} j={j} />
              ))}
            </div>
          </>
        )}
      </div>
    </div>
  )
}
