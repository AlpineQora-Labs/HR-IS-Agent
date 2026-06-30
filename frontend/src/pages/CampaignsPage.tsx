import { useMemo, useState } from 'react'
import SlideOver from '@/components/SlideOver'
import { useCampaigns } from '@/api/hooks'
import { percent } from '@/lib/format'
import type { Campaign } from '@/api/types'

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

function StatTile({ label, value, meta }: { label: string; value: string | number; meta?: string }) {
  return (
    <div className="stat">
      <div className="stat__label">{label}</div>
      <div className="stat__value">{value}</div>
      {meta ? <div className="stat__meta">{meta}</div> : null}
    </div>
  )
}

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, padding: '7px 0', fontSize: 13 }}>
      <span style={{ color: 'var(--ink-4)' }}>{label}</span>
      <span style={{ color: 'var(--ink-1)', textAlign: 'right' }}>{children}</span>
    </div>
  )
}

function FunnelStep({ label, value, pct }: { label: string; value: number; pct: number }) {
  return (
    <div style={{ marginBottom: 10 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12.5, marginBottom: 4 }}>
        <span style={{ color: 'var(--ink-2)' }}>{label}</span>
        <span className="t-num" style={{ color: 'var(--ink-4)' }}>
          <span style={{ color: 'var(--ink-1)', fontWeight: 600 }}>{value.toLocaleString()}</span> · {pct}%
        </span>
      </div>
      <div style={{ height: 8, background: 'var(--app-sunken)', borderRadius: 4, overflow: 'hidden' }}>
        <div style={{ width: `${Math.max(2, pct)}%`, height: '100%', background: 'var(--bofa-navy)' }} />
      </div>
    </div>
  )
}

function CampaignDetail({ c, onClose }: { c: Campaign; onClose: () => void }) {
  const openPct = c.sent ? Math.round((c.opened / c.sent) * 100) : 0
  const replyPct = c.sent ? Math.round((c.replied / c.sent) * 100) : 0
  return (
    <SlideOver label={`${c.name} campaign`} onClose={onClose} width={440}>
      <div style={{ padding: '18px 20px 16px', borderBottom: '1px solid var(--line)' }}>
        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
          <div style={{ fontFamily: 'var(--font-display)', fontSize: 19, color: 'var(--ink-0)', lineHeight: 1.2 }}>{c.name}</div>
          <button type="button" onClick={onClose} aria-label="Close" style={{ border: 0, background: 'none', cursor: 'pointer', fontSize: 20, color: 'var(--ink-4)' }}>×</button>
        </div>
        <div style={{ display: 'flex', gap: 6, marginTop: 10 }}>
          <span className={`badge ${channelBadge(c.channel)}`}>{c.channel}</span>
          <span className={`badge ${statusBadge(c.status)}`}>{c.status}</span>
        </div>
      </div>
      <div style={{ flex: 1, overflowY: 'auto', padding: '16px 20px' }}>
        <Fact label="Audience">{c.audience}</Fact>
        <div style={{ marginTop: 18 }}>
          <div className="eyebrow" style={{ marginBottom: 8 }}>Engagement funnel</div>
          <FunnelStep label="Sent" value={c.sent} pct={100} />
          <FunnelStep label="Opened" value={c.opened} pct={openPct} />
          <FunnelStep label="Replied" value={c.replied} pct={replyPct} />
        </div>
      </div>
    </SlideOver>
  )
}

export default function CampaignsPage() {
  const { data, isLoading } = useCampaigns()
  const [selected, setSelected] = useState<Campaign | null>(null)

  const kpis = useMemo(() => {
    const list = data ?? []
    const active = list.filter((c) => ['ACTIVE', 'RUNNING', 'SENDING'].includes(c.status.toUpperCase())).length
    const open = list.length ? Math.round(list.reduce((n, c) => n + c.openRate, 0) / list.length) : 0
    const reply = list.length ? Math.round(list.reduce((n, c) => n + c.replyRate, 0) / list.length) : 0
    return { total: list.length, active, open, reply }
  }, [data])

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

      {data && data.length > 0 ? (
        <div className="grid-stats" style={{ gridTemplateColumns: 'repeat(4,1fr)', marginBottom: 18 }}>
          <StatTile label="Campaigns" value={kpis.total} />
          <StatTile label="Active" value={kpis.active} />
          <StatTile label="Avg open rate" value={percent(kpis.open, false)} />
          <StatTile label="Avg reply rate" value={percent(kpis.reply, false)} />
        </div>
      ) : null}

      {isLoading ? (
        <div className="card">
          <div style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>Loading campaigns…</div>
        </div>
      ) : !data || data.length === 0 ? (
        <div className="card">
          <div className="card__body" style={{ padding: '44px 20px', textAlign: 'center' }}>
            <div style={{ fontFamily: 'var(--font-display)', fontSize: 18, color: 'var(--ink-0)' }}>No nurture campaigns yet</div>
            <div style={{ fontSize: 13, color: 'var(--ink-4)', marginTop: 6 }}>Drip campaigns keep passive talent engaged until the right role opens.</div>
          </div>
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
                <tr key={c.id} onClick={() => setSelected(c)} style={{ cursor: 'pointer' }}>
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

      {selected && <CampaignDetail c={selected} onClose={() => setSelected(null)} />}
    </div>
  )
}
