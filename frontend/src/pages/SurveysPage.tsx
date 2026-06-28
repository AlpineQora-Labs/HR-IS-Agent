import { useSurveys } from '@/api/hooks'
import { percent } from '@/lib/format'

// Sense-inspired candidate experience surveys: capture sentiment at each stage,
// with a colored sentiment indicator (-1..1) and NPS.

function sentimentColor(s: number) {
  if (s >= 0.3) return 'var(--c-green)'
  if (s <= -0.3) return 'var(--bofa-red)'
  return 'var(--c-amber)'
}

function sentimentLabel(s: number) {
  if (s >= 0.3) return 'Positive'
  if (s <= -0.3) return 'Negative'
  return 'Neutral'
}

function Sentiment({ value }: { value: number }) {
  const color = sentimentColor(value)
  // map -1..1 → 0..100 for the position of the marker on the track
  const pos = Math.max(0, Math.min(100, ((value + 1) / 2) * 100))
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, justifyContent: 'flex-end' }}>
      <div style={{ position: 'relative', width: 64, height: 7, borderRadius: 999, background: 'var(--app-sunken)' }}>
        <span
          style={{
            position: 'absolute',
            top: -2,
            left: `calc(${pos}% - 5px)`,
            width: 11,
            height: 11,
            borderRadius: '50%',
            background: color,
            border: '2px solid var(--app-panel)',
          }}
        />
      </div>
      <span style={{ fontSize: 12.5, fontWeight: 600, color, minWidth: 58 }}>{sentimentLabel(value)}</span>
    </div>
  )
}

function npsBadge(nps: number) {
  if (nps >= 50) return 'badge--ok'
  if (nps >= 0) return 'badge--warn'
  return 'badge--danger'
}

export default function SurveysPage() {
  const { data, isLoading } = useSurveys()

  return (
    <div>
      <div className="page-head">
        <div className="crumb">
          <span className="dot" />
          Engagement · Experience surveys
        </div>
        <h1>Surveys</h1>
        <p className="sub">
          Listen at every stage. Pulse surveys capture candidate sentiment as they move through the funnel, so you can fix
          friction before it costs you talent.
        </p>
      </div>

      {isLoading ? (
        <div className="card">
          <div style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>Loading surveys…</div>
        </div>
      ) : !data || data.length === 0 ? (
        <div className="card">
          <div style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>No surveys yet.</div>
        </div>
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Survey</th>
                <th>Stage</th>
                <th className="t-right">Sent</th>
                <th className="t-right">Responses</th>
                <th className="t-right">Response rate</th>
                <th className="t-right">Sentiment</th>
                <th className="t-right">NPS</th>
              </tr>
            </thead>
            <tbody>
              {data.map((s) => (
                <tr key={s.id}>
                  <td className="t-strong">{s.name}</td>
                  <td className="t-muted">{s.stage}</td>
                  <td className="t-right t-num">{s.sent}</td>
                  <td className="t-right t-num">{s.responses}</td>
                  <td className="t-right t-num">{percent(s.responseRate, false)}</td>
                  <td className="t-right">
                    <Sentiment value={s.avgSentiment} />
                  </td>
                  <td className="t-right">
                    <span className={`badge ${npsBadge(s.nps)}`}>{s.nps}</span>
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
