import { useAnalyticsSummary } from '../api/hooks'
import { percent } from '../lib/format'
import type { FunnelStage } from '../api/types'

function StatTile({ label, value, meta }: { label: string; value: string | number; meta?: string }) {
  return (
    <div className="stat">
      <div className="stat__label">{label}</div>
      <div className="stat__value">{value}</div>
      {meta ? <div className="stat__meta">{meta}</div> : null}
    </div>
  )
}

function Funnel({ stages }: { stages: FunnelStage[] }) {
  const max = Math.max(1, ...stages.map((s) => s.count))
  const top = stages[0]?.count ?? 0
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      {stages.map((s) => {
        const width = `${Math.max(4, Math.round((s.count / max) * 100))}%`
        const conv = top > 0 ? Math.round((s.count / top) * 100) : 0
        return (
          <div key={s.stage}>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'baseline',
                marginBottom: 6,
              }}
            >
              <span style={{ fontSize: 13.5, fontWeight: 600, color: 'var(--ink-1)' }}>{s.stage}</span>
              <span style={{ fontSize: 12.5, color: 'var(--ink-4)' }}>
                <span className="t-num" style={{ color: 'var(--ink-1)', fontWeight: 600 }}>
                  {s.count.toLocaleString()}
                </span>{' '}
                · {conv}%
              </span>
            </div>
            <div
              style={{
                height: 26,
                background: 'var(--app-sunken)',
                borderRadius: 'var(--ra-1)',
                overflow: 'hidden',
              }}
            >
              <div
                style={{
                  width,
                  height: '100%',
                  background: 'var(--bofa-navy)',
                  borderRadius: 'var(--ra-1)',
                  transition: 'width 0.3s ease',
                }}
              />
            </div>
          </div>
        )
      })}
    </div>
  )
}

export default function OverviewPage() {
  const { data, isLoading, isError, refetch } = useAnalyticsSummary()

  return (
    <div>
      <div className="page-head" style={{ marginBottom: 20 }}>
        <div className="crumb">
          <span className="dot" />
          Recruiting · Overview
        </div>
        <div className="page-head__row">
          <div>
            <h1 style={{ fontSize: 28 }}>Recruiting overview</h1>
            <p className="sub">Live snapshot of hiring activity across all open requisitions.</p>
          </div>
        </div>
      </div>

      {isLoading ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
            Loading analytics…
          </div>
        </div>
      ) : isError || !data ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center' }}>
            <div style={{ fontFamily: 'var(--font-display)', fontSize: 20, color: 'var(--ink-0)' }}>
              Couldn’t load analytics.
            </div>
            <div style={{ fontSize: 13.5, color: 'var(--ink-4)', margin: '6px 0 16px' }}>
              The API didn’t respond. Check that the backend is running.
            </div>
            <button className="btn btn--outline btn--sm" onClick={() => refetch()}>
              Retry
            </button>
          </div>
        </div>
      ) : (
        <>
          <div className="grid-stats" style={{ gridTemplateColumns: 'repeat(4,1fr)', marginBottom: 16 }}>
            <StatTile label="Open jobs" value={data.openJobs} meta="Actively recruiting" />
            <StatTile label="Active candidates" value={data.activeCandidates.toLocaleString()} meta="In pipeline" />
            <StatTile label="Avg time-to-hire" value={`${Math.round(data.avgTimeToHireDays)}d`} meta="Req to offer-accept" />
            <StatTile label="Offer-accept rate" value={percent(data.offerAcceptRate, false)} meta="Trailing 90 days" />
          </div>

          <div className="grid-stats" style={{ gridTemplateColumns: 'repeat(5,1fr)', marginBottom: 20 }}>
            <StatTile label="Applications · 30d" value={data.applications30d.toLocaleString()} />
            <StatTile label="Interviews · 30d" value={data.interviews30d.toLocaleString()} />
            <StatTile label="Offers · 30d" value={data.offers30d.toLocaleString()} />
            <StatTile label="Hires · 30d" value={data.hires30d.toLocaleString()} />
            <StatTile label="Avg fit score" value={Math.round(data.avgFitScore)} meta="Across active apps" />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) 360px', gap: 20, alignItems: 'start' }}>
            <div className="card">
              <div className="card__head">
                <h3>Hiring funnel</h3>
                <span className="eyebrow">Conversion vs top of funnel</span>
              </div>
              <div className="card__body">
                {data.funnel && data.funnel.length > 0 ? (
                  <Funnel stages={data.funnel} />
                ) : (
                  <div style={{ padding: '24px 0', textAlign: 'center', color: 'var(--ink-4)' }}>
                    No funnel data yet.
                  </div>
                )}
              </div>
            </div>

            <div className="card">
              <div className="card__head">
                <h3 style={{ fontSize: 15 }}>Stage breakdown</h3>
                <span className="badge">Live</span>
              </div>
              <div className="card__body" style={{ padding: '8px 0' }}>
                {Object.entries(data.byStage ?? {}).length > 0 ? (
                  Object.entries(data.byStage).map(([stage, count]) => (
                    <div
                      key={stage}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        gap: 12,
                        padding: '11px 18px',
                        borderBottom: '1px solid var(--line)',
                      }}
                    >
                      <span className="status">
                        <span className="dot dot--info" />
                        {stage}
                      </span>
                      <span className="t-num" style={{ fontWeight: 600, color: 'var(--ink-0)' }}>
                        {count.toLocaleString()}
                      </span>
                    </div>
                  ))
                ) : (
                  <div style={{ padding: '24px 18px', textAlign: 'center', color: 'var(--ink-4)' }}>
                    No candidates in any stage yet.
                  </div>
                )}
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  )
}
