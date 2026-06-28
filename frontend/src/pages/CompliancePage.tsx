import { useBias } from '@/api/hooks'
import type { BiasRow } from '@/api/types'
import { percent } from '@/lib/format'

// impactRatio is the 4/5ths-rule ratio (group pass rate ÷ favored-group pass rate).
// Below 0.80 → potential adverse impact.
const FOUR_FIFTHS = 0.8

function ratioClass(r: number) {
  if (r >= FOUR_FIFTHS) return 'badge--ok'
  if (r >= 0.7) return 'badge--warn'
  return 'badge--danger'
}

export default function CompliancePage() {
  const { data, isLoading, isError, refetch } = useBias()

  const rows: BiasRow[] = data ?? []
  const flaggedCount = rows.filter((r) => r.flagged).length
  const monitored = rows.length

  return (
    <div>
      <div className="page-head" style={{ marginBottom: 18 }}>
        <div className="crumb">
          <span className="dot" />
          Responsible AI · Bias &amp; Compliance
        </div>
        <div className="page-head__row">
          <div>
            <h1 style={{ fontSize: 28 }}>Bias &amp; Compliance</h1>
            <p className="sub">
              Continuous adverse-impact monitoring across every automated stage. Selection rates are compared by protected
              dimension using the EEOC four-fifths (80%) rule; any group falling below an 0.80 impact ratio is flagged for
              human review. All scoring decisions are written to an immutable audit trail.
            </p>
          </div>
        </div>
      </div>

      <div className="grid-stats" style={{ marginBottom: 18 }}>
        <div className="stat">
          <div className="stat__label">Checks monitored</div>
          <div className="stat__value" style={{ fontSize: 30 }}>
            {monitored}
          </div>
        </div>
        <div className="stat">
          <div className="stat__label">Flagged (4/5ths)</div>
          <div className="stat__value" style={{ fontSize: 30, color: flaggedCount ? 'var(--bofa-red)' : 'var(--ink-0)' }}>
            {flaggedCount}
          </div>
        </div>
        <div className="stat">
          <div className="stat__label">Adverse-impact threshold</div>
          <div className="stat__value" style={{ fontSize: 30 }}>
            0.80
          </div>
        </div>
        <div className="stat">
          <div className="stat__label">Audit trail</div>
          <div className="stat__value" style={{ fontSize: 30 }}>
            Active
          </div>
        </div>
      </div>

      {isLoading ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
            Running bias analysis…
          </div>
        </div>
      ) : isError ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center' }}>
            <div style={{ fontFamily: 'var(--font-display)', fontSize: 20, color: 'var(--ink-0)' }}>Couldn’t load compliance data.</div>
            <div style={{ fontSize: 13.5, color: 'var(--ink-4)', margin: '6px 0 16px' }}>The compliance API didn’t respond.</div>
            <button className="btn btn--outline btn--sm" onClick={() => refetch()}>
              Retry
            </button>
          </div>
        </div>
      ) : rows.length === 0 ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
            No bias checks recorded yet.
          </div>
        </div>
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Job</th>
                <th>Stage</th>
                <th>Dimension</th>
                <th>Group</th>
                <th className="t-right">Pass rate</th>
                <th className="t-right">Impact ratio</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr
                  key={r.id}
                  style={r.flagged ? { background: 'var(--danger-bg)' } : undefined}
                >
                  <td className="t-strong">{r.jobTitle}</td>
                  <td className="t-muted">{r.stage}</td>
                  <td>{r.dimension}</td>
                  <td>{r.groupLabel}</td>
                  <td className="t-right t-num">{percent(r.passRate)}</td>
                  <td className="t-right">
                    <span className={`badge ${ratioClass(r.impactRatio)}`}>{r.impactRatio.toFixed(2)}</span>
                  </td>
                  <td>
                    {r.flagged ? (
                      <span className="status" style={{ color: 'var(--danger-fg)', fontWeight: 600 }}>
                        <span className="dot dot--danger" />
                        Flagged · review
                      </span>
                    ) : (
                      <span className="status">
                        <span className="dot dot--ok" />
                        Within tolerance
                      </span>
                    )}
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
