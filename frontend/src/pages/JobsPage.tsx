import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useJobs } from '../api/hooks'
import { money } from '../lib/format'
import type { JobSummary } from '../api/types'

function statusBadgeClass(status: string) {
  const s = status.toLowerCase()
  if (s === 'open' || s === 'active' || s === 'published') return 'badge--ok'
  if (s === 'draft') return 'badge--warn'
  if (s === 'closed' || s === 'filled' || s === 'archived') return 'badge--neutral'
  if (s === 'on hold' || s === 'paused') return 'badge--info'
  return 'badge--neutral'
}

function payRange(j: JobSummary) {
  if (!j.payMin && !j.payMax) return '—'
  const per = j.payPeriod ? ` / ${j.payPeriod.toLowerCase()}` : ''
  return `${money(j.payMin)}–${money(j.payMax)}${per}`
}

export default function JobsPage() {
  const { data, isLoading, isError, refetch } = useJobs()
  const [filter, setFilter] = useState('')

  const rows = useMemo(() => {
    if (!data) return []
    const q = filter.trim().toLowerCase()
    if (!q) return data
    return data.filter((j) =>
      [j.title, j.department, j.location, j.family, j.status].some((v) => v?.toLowerCase().includes(q)),
    )
  }, [data, filter])

  return (
    <div>
      <div className="page-head" style={{ marginBottom: 18 }}>
        <div className="crumb">
          <span className="dot" />
          Recruiting · Jobs
        </div>
        <div className="page-head__row">
          <div>
            <h1 style={{ fontSize: 28 }}>Jobs</h1>
            <p className="sub">Open requisitions across the organization.</p>
          </div>
          <div className="input-group" style={{ width: 280 }}>
            <input
              className="input"
              placeholder="Filter by title, team, location…"
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
            />
          </div>
        </div>
      </div>

      {isLoading ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
            Loading jobs…
          </div>
        </div>
      ) : isError ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center' }}>
            <div style={{ fontFamily: 'var(--font-display)', fontSize: 20, color: 'var(--ink-0)' }}>
              Couldn’t load jobs.
            </div>
            <div style={{ fontSize: 13.5, color: 'var(--ink-4)', margin: '6px 0 16px' }}>
              The API didn’t respond. Check that the backend is running.
            </div>
            <button className="btn btn--outline btn--sm" onClick={() => refetch()}>
              Retry
            </button>
          </div>
        </div>
      ) : !data || data.length === 0 ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
            No jobs posted yet.
          </div>
        </div>
      ) : rows.length === 0 ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
            No jobs match “{filter}”.
          </div>
        </div>
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Role</th>
                <th>Location</th>
                <th>Family</th>
                <th>Status</th>
                <th className="t-right">Openings</th>
                <th className="t-right">Applicants</th>
                <th className="t-right">Pay</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((j) => (
                <tr key={j.id}>
                  <td>
                    <Link to={`/jobs/${j.id}`} style={{ textDecoration: 'none' }}>
                      <div className="who__name" style={{ color: 'var(--bofa-navy)' }}>
                        {j.title}
                      </div>
                    </Link>
                    <div className="who__sub" style={{ marginTop: 2 }}>
                      {j.department}
                    </div>
                  </td>
                  <td className="t-muted">{j.location}</td>
                  <td className="t-muted">{j.family}</td>
                  <td>
                    <span className={`badge ${statusBadgeClass(j.status)}`}>{j.status}</span>
                  </td>
                  <td className="t-right t-num">{j.openings}</td>
                  <td className="t-right t-num">{j.applicants}</td>
                  <td className="t-right t-num">{payRange(j)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
