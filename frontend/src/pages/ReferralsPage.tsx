import { useReferrals } from '@/api/hooks'
import { date, money } from '@/lib/format'

// Sense-inspired employee referrals: track who referred whom, where it is in
// the pipeline, and the bonus on the line.

function statusBadge(status: string) {
  const key = status.toUpperCase()
  if (key === 'HIRED' || key === 'PAID') return 'badge--ok'
  if (key === 'INTERVIEWING' || key === 'IN_REVIEW' || key === 'SUBMITTED') return 'badge--info'
  if (key === 'PENDING' || key === 'NEW') return 'badge--warn'
  if (key === 'REJECTED' || key === 'DECLINED') return 'badge--danger'
  return 'badge'
}

export default function ReferralsPage() {
  const { data, isLoading } = useReferrals()

  return (
    <div>
      <div className="page-head">
        <div className="crumb">
          <span className="dot" />
          Engagement · Referrals
        </div>
        <h1>Referrals</h1>
        <p className="sub">
          Your employees are your best sourcers. Track every referral from submission to hire — and the bonus that comes
          with it.
        </p>
      </div>

      {isLoading ? (
        <div className="card">
          <div style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>Loading referrals…</div>
        </div>
      ) : !data || data.length === 0 ? (
        <div className="card">
          <div style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>No referrals yet.</div>
        </div>
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Referrer</th>
                <th>Candidate</th>
                <th>Role</th>
                <th>Status</th>
                <th className="t-right">Bonus</th>
                <th className="t-right">Submitted</th>
              </tr>
            </thead>
            <tbody>
              {data.map((r) => (
                <tr key={r.id}>
                  <td className="t-strong">{r.referrerName}</td>
                  <td>{r.candidateName || '—'}</td>
                  <td className="t-muted">{r.jobTitle || '—'}</td>
                  <td>
                    <span className={`badge ${statusBadge(r.status)}`}>{r.status}</span>
                  </td>
                  <td className="t-right t-num">{r.bonusAmount ? money(r.bonusAmount) : '—'}</td>
                  <td className="t-right t-muted">{date(r.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
