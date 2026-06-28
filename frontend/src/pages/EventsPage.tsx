import { useEvents } from '@/api/hooks'
import { date } from '@/lib/format'

// Paradox-inspired recruiting events: campus drives, career fairs and hiring
// events, with funnel read-outs from registration through to hire.

function typeBadge(type: string) {
  const key = type.toUpperCase()
  if (key === 'CAMPUS') return 'badge--purple'
  if (key === 'CAREER_FAIR' || key === 'FAIR') return 'badge--info'
  if (key === 'HIRING_EVENT' || key === 'HIRING') return 'badge--ok'
  if (key === 'WEBINAR' || key === 'VIRTUAL') return 'badge--warn'
  return 'badge'
}

export default function EventsPage() {
  const { data, isLoading } = useEvents()

  return (
    <div>
      <div className="page-head">
        <div className="crumb">
          <span className="dot" />
          Engagement · Events &amp; Campus
        </div>
        <div className="page-head__row">
          <div>
            <h1>Events &amp; Campus</h1>
            <p className="sub">
              Run campus drives and hiring events end to end — from registration through attendance to offers, with the
              conversion funnel in one view.
            </p>
          </div>
          <button className="btn btn--primary">New event</button>
        </div>
      </div>

      {isLoading ? (
        <div className="card">
          <div style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>Loading events…</div>
        </div>
      ) : !data || data.length === 0 ? (
        <div className="card">
          <div style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>No recruiting events yet.</div>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 16 }}>
          {data.map((e) => (
            <div key={e.id} className="card" style={{ padding: 20 }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
                <h3 style={{ margin: 0, fontSize: 17 }}>{e.name}</h3>
                <span className={`badge ${typeBadge(e.type)}`}>{e.type}</span>
              </div>
              <div className="muted" style={{ marginTop: 8 }}>
                {e.location} · {date(e.startsAt)}
              </div>
              <div
                style={{
                  marginTop: 16,
                  display: 'grid',
                  gridTemplateColumns: 'repeat(3, 1fr)',
                  gap: 12,
                  borderTop: '1px solid var(--line)',
                  paddingTop: 16,
                }}
              >
                {[
                  { label: 'Registered', value: e.registrations },
                  { label: 'Attended', value: e.attended },
                  { label: 'Hires', value: e.hires },
                ].map((m) => (
                  <div key={m.label}>
                    <div
                      className="t-num"
                      style={{ fontFamily: 'var(--font-display)', fontWeight: 600, fontSize: 22, color: 'var(--ink-0)' }}
                    >
                      {m.value}
                    </div>
                    <div className="eyebrow" style={{ fontSize: 9, marginTop: 4 }}>{m.label}</div>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
