import { usePools } from '@/api/hooks'

// Phenom-inspired talent CRM: organise prospects into nurtured talent pools.

export default function CrmPage() {
  const { data, isLoading } = usePools()

  return (
    <div>
      <div className="page-head">
        <div className="crumb">
          <span className="dot" />
          Engagement · Talent CRM
        </div>
        <div className="page-head__row">
          <div>
            <h1>Talent CRM</h1>
            <p className="sub">
              Segment prospects into living talent pools — pipelines you nurture over time so the right people are warm
              before the req opens.
            </p>
          </div>
          <button className="btn btn--primary">New pool</button>
        </div>
      </div>

      {isLoading ? (
        <div className="card">
          <div style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>Loading talent pools…</div>
        </div>
      ) : !data || data.length === 0 ? (
        <div className="card">
          <div style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
            No talent pools yet. Create one to start nurturing prospects.
          </div>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 16 }}>
          {data.map((p) => (
            <div key={p.id} className="card" style={{ display: 'flex', flexDirection: 'column', minHeight: 168 }}>
              <div style={{ padding: '20px 20px 0' }}>
                <div className="eyebrow" style={{ fontSize: 9.5 }}>Talent pool</div>
                <h3 style={{ margin: '8px 0 0', fontSize: 18 }}>{p.name}</h3>
                <p style={{ fontSize: 13.5, color: 'var(--ink-3)', margin: '8px 0 0', lineHeight: 1.5 }}>
                  {p.description}
                </p>
              </div>
              <div
                style={{
                  marginTop: 'auto',
                  padding: '14px 20px',
                  borderTop: '1px solid var(--line)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                }}
              >
                <span className="t-num t-strong" style={{ fontFamily: 'var(--font-display)', fontSize: 22 }}>
                  {p.memberCount}
                </span>
                <span className="muted" style={{ margin: 0 }}>members</span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
