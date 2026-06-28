import { useCampaigns } from '@/api/hooks'
import { percent } from '@/lib/format'

// Sense/Phenom-inspired nurture campaigns: multi-channel drip with
// engagement read-outs (open & reply rates) per audience.

function channelBadge(channel: string) {
  const key = channel.toUpperCase()
  if (key === 'EMAIL') return 'badge--info'
  if (key === 'SMS' || key === 'TEXT') return 'badge--purple'
  if (key === 'WHATSAPP') return 'badge--ok'
  return 'badge'
}

function statusBadge(status: string) {
  const key = status.toUpperCase()
  if (key === 'ACTIVE' || key === 'RUNNING' || key === 'SENDING') return 'badge--ok'
  if (key === 'SCHEDULED' || key === 'DRAFT') return 'badge--warn'
  if (key === 'COMPLETED' || key === 'PAUSED') return 'badge'
  return 'badge'
}

export default function CampaignsPage() {
  const { data, isLoading } = useCampaigns()

  return (
    <div>
      <div className="page-head">
        <div className="crumb">
          <span className="dot" />
          Engagement · Nurture
        </div>
        <div className="page-head__row">
          <div>
            <h1>Nurture</h1>
            <p className="sub">
              Automated, multi-channel drip campaigns that keep talent warm — with open and reply rates so you see what
              lands.
            </p>
          </div>
          <button className="btn btn--primary">New campaign</button>
        </div>
      </div>

      {isLoading ? (
        <div className="card">
          <div style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>Loading campaigns…</div>
        </div>
      ) : !data || data.length === 0 ? (
        <div className="card">
          <div style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>No nurture campaigns yet.</div>
        </div>
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Campaign</th>
                <th>Audience</th>
                <th>Channel</th>
                <th>Status</th>
                <th className="t-right">Sent</th>
                <th className="t-right">Open rate</th>
                <th className="t-right">Reply rate</th>
              </tr>
            </thead>
            <tbody>
              {data.map((c) => (
                <tr key={c.id}>
                  <td className="t-strong">{c.name}</td>
                  <td className="t-muted">{c.audience}</td>
                  <td>
                    <span className={`badge ${channelBadge(c.channel)}`}>{c.channel}</span>
                  </td>
                  <td>
                    <span className={`badge ${statusBadge(c.status)}`}>{c.status}</span>
                  </td>
                  <td className="t-right t-num">{c.sent}</td>
                  <td className="t-right t-num">{percent(c.openRate, false)}</td>
                  <td className="t-right t-num">{percent(c.replyRate, false)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
