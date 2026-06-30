import { useMemo } from 'react'
import { useMobility } from '@/api/hooks'

// Eightfold-inspired internal talent marketplace: match current employees to
// open roles by skills adjacency, with an explainable rationale per move.

function fitBadge(score: number) {
  if (score >= 80) return 'badge--ok'
  if (score >= 60) return 'badge--info'
  return 'badge--warn'
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

export default function MobilityPage() {
  const { data, isLoading } = useMobility()

  const kpis = useMemo(() => {
    const list = data ?? []
    const avg = list.length ? Math.round(list.reduce((n, r) => n + r.fitScore, 0) / list.length) : 0
    const strong = list.filter((r) => r.fitScore >= 80).length
    const employees = new Set(list.map((r) => r.employeeId)).size
    return { total: list.length, avg, strong, employees }
  }, [data])

  return (
    <div>
      <div className="page-head">
        <div className="crumb">
          <span className="dot" />
          Talent intelligence · Internal mobility
        </div>
        <h1>Internal Mobility</h1>
        <p className="sub">
          An internal talent marketplace that matches your own people to open roles — turning skills adjacency into
          retention and a faster, cheaper path to fill.
        </p>
      </div>

      {data && data.length > 0 ? (
        <div className="grid-stats" style={{ gridTemplateColumns: 'repeat(4,1fr)', marginBottom: 18 }}>
          <StatTile label="Suggested moves" value={kpis.total} />
          <StatTile label="Employees matched" value={kpis.employees} />
          <StatTile label="Avg fit" value={kpis.avg} />
          <StatTile label="Strong matches" value={kpis.strong} meta="Fit ≥ 80" />
        </div>
      ) : null}

      {isLoading ? (
        <div className="card">
          <div style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>Finding internal moves…</div>
        </div>
      ) : !data || data.length === 0 ? (
        <div className="card">
          <div className="card__body" style={{ padding: '44px 20px', textAlign: 'center' }}>
            <div style={{ fontFamily: 'var(--font-display)', fontSize: 18, color: 'var(--ink-0)' }}>No internal moves yet</div>
            <div style={{ fontSize: 13, color: 'var(--ink-4)', marginTop: 6 }}>As employee skills and open roles are indexed, suggested moves appear here.</div>
          </div>
        </div>
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Employee</th>
                <th>Current role</th>
                <th>Suggested move</th>
                <th>Rationale</th>
                <th className="t-right">Fit</th>
              </tr>
            </thead>
            <tbody>
              {data.map((r) => (
                <tr key={`${r.employeeId}-${r.jobId}`}>
                  <td className="t-strong">{r.employeeName}</td>
                  <td className="t-muted">{r.currentRole}</td>
                  <td>
                    <span style={{ color: 'var(--ink-4)', marginRight: 6 }}>→</span>
                    <span className="t-strong">{r.jobTitle}</span>
                  </td>
                  <td style={{ maxWidth: 420, color: 'var(--ink-2)' }}>{r.rationale}</td>
                  <td className="t-right">
                    <span className={`badge ${fitBadge(r.fitScore)}`}>{Math.round(r.fitScore)}</span>
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
