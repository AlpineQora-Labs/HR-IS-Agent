import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useCandidates } from '../api/hooks'
import { initials } from '../lib/format'

function lifecycleClass(lifecycle: string) {
  const s = lifecycle.toLowerCase()
  if (s.includes('hire') || s === 'active') return 'badge--ok'
  if (s.includes('lead') || s.includes('prospect') || s.includes('new')) return 'badge--info'
  if (s.includes('reject') || s.includes('declin') || s.includes('withdraw')) return 'badge--neutral'
  if (s.includes('nurtur') || s.includes('passive')) return 'badge--purple'
  return 'badge--neutral'
}

export default function CandidatesPage() {
  const { data, isLoading, isError, refetch } = useCandidates()
  const [filter, setFilter] = useState('')

  const rows = useMemo(() => {
    if (!data) return []
    const q = filter.trim().toLowerCase()
    if (!q) return data
    return data.filter((c) =>
      [c.name, c.headline, c.location, c.source, c.lifecycle, ...(c.topSkills ?? [])].some((v) =>
        v?.toLowerCase().includes(q),
      ),
    )
  }, [data, filter])

  return (
    <div>
      <div className="page-head" style={{ marginBottom: 18 }}>
        <div className="crumb">
          <span className="dot" />
          Recruiting · Candidates
        </div>
        <div className="page-head__row">
          <div>
            <h1 style={{ fontSize: 28 }}>Candidates</h1>
            <p className="sub">Everyone in the talent pool, across sources and lifecycle stages.</p>
          </div>
          <div className="input-group" style={{ width: 280 }}>
            <input
              className="input"
              placeholder="Filter by name, skill, source…"
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
            />
          </div>
        </div>
      </div>

      {isLoading ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
            Loading candidates…
          </div>
        </div>
      ) : isError ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center' }}>
            <div style={{ fontFamily: 'var(--font-display)', fontSize: 20, color: 'var(--ink-0)' }}>
              Couldn’t load candidates.
            </div>
            <button className="btn btn--outline btn--sm" style={{ marginTop: 14 }} onClick={() => refetch()}>
              Retry
            </button>
          </div>
        </div>
      ) : !data || data.length === 0 ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
            No candidates in the pool yet.
          </div>
        </div>
      ) : rows.length === 0 ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
            No candidates match “{filter}”.
          </div>
        </div>
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Candidate</th>
                <th>Location</th>
                <th>Source</th>
                <th>Lifecycle</th>
                <th className="t-right">Apps</th>
                <th>Top skills</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((c) => (
                <tr key={c.id}>
                  <td>
                    <Link to={`/candidates/${c.id}`} style={{ textDecoration: 'none' }}>
                      <div className="who">
                        <div className="avatar">{initials(c.name)}</div>
                        <div>
                          <div className="who__name" style={{ color: 'var(--bofa-navy)' }}>
                            {c.name}
                          </div>
                          {c.headline ? <div className="who__sub">{c.headline}</div> : null}
                        </div>
                      </div>
                    </Link>
                  </td>
                  <td className="t-muted">{c.location || '—'}</td>
                  <td className="t-muted">{c.source || '—'}</td>
                  <td>
                    <span className={`badge ${lifecycleClass(c.lifecycle)}`}>{c.lifecycle}</span>
                  </td>
                  <td className="t-right t-num">{c.applicationCount}</td>
                  <td>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                      {(c.topSkills ?? []).slice(0, 4).map((s) => (
                        <span key={s} className="chip" style={{ cursor: 'default', padding: '4px 10px', fontSize: 12 }}>
                          {s}
                        </span>
                      ))}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
