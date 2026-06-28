import { useMobility } from '@/api/hooks'

// Eightfold-inspired internal talent marketplace: match current employees to
// open roles by skills adjacency, with an explainable rationale per move.

function fitBadge(score: number) {
  if (score >= 80) return 'badge--ok'
  if (score >= 60) return 'badge--info'
  return 'badge--warn'
}

export default function MobilityPage() {
  const { data, isLoading } = useMobility()

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

      {isLoading ? (
        <div className="card">
          <div style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>Finding internal moves…</div>
        </div>
      ) : !data || data.length === 0 ? (
        <div className="card">
          <div style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
            No internal mobility matches yet.
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
