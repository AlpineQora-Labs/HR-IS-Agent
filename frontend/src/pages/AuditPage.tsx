import { useAudit } from '@/api/hooks'
import type { AuditRow } from '@/api/types'
import { date, initials, relativeTime } from '@/lib/format'

function sortNewest(rows: AuditRow[]) {
  return [...rows].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
}

export default function AuditPage() {
  const { data, isLoading, isError, refetch } = useAudit()
  const rows = data ? sortNewest(data) : []

  return (
    <div>
      <div className="page-head" style={{ marginBottom: 18 }}>
        <div className="crumb">
          <span className="dot" />
          Compliance · Audit log
        </div>
        <div className="page-head__row">
          <div>
            <h1 style={{ fontSize: 28 }}>Audit log</h1>
            <p className="sub">
              Every action against candidate, job, and decision data is recorded here — immutable, time-stamped, and
              attributable. Newest events first.
            </p>
          </div>
          {data && (
            <span className="badge" style={{ alignSelf: 'center' }}>
              {rows.length} events
            </span>
          )}
        </div>
      </div>

      {isLoading ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
            Loading audit log…
          </div>
        </div>
      ) : isError ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center' }}>
            <div style={{ fontFamily: 'var(--font-display)', fontSize: 20, color: 'var(--ink-0)' }}>Couldn’t load the audit log.</div>
            <div style={{ fontSize: 13.5, color: 'var(--ink-4)', margin: '6px 0 16px' }}>The audit API didn’t respond.</div>
            <button className="btn btn--outline btn--sm" onClick={() => refetch()}>
              Retry
            </button>
          </div>
        </div>
      ) : rows.length === 0 ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
            No audit events recorded yet.
          </div>
        </div>
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>When</th>
                <th>Actor</th>
                <th>Action</th>
                <th>Entity</th>
                <th>Detail</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td style={{ whiteSpace: 'nowrap' }}>
                    <div style={{ fontWeight: 600, color: 'var(--ink-0)' }}>{relativeTime(r.createdAt)}</div>
                    <div style={{ fontSize: 12, color: 'var(--ink-4)' }}>{date(r.createdAt)}</div>
                  </td>
                  <td>
                    <div className="who">
                      <div className="avatar" style={{ width: 28, height: 28, fontSize: 11 }}>
                        {initials(r.actor)}
                      </div>
                      <div className="who__name" style={{ fontSize: 13.5 }}>
                        {r.actor}
                      </div>
                    </div>
                  </td>
                  <td>
                    <span className="badge badge--info">{r.action}</span>
                  </td>
                  <td className="t-muted">{r.entityType}</td>
                  <td
                    style={{
                      fontFamily: 'var(--font-mono)',
                      fontSize: 12.5,
                      color: 'var(--ink-2)',
                      maxWidth: 480,
                    }}
                  >
                    {r.detail}
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
