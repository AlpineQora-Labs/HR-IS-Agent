import { useIntegrations } from '@/api/hooks'
import type { Integration } from '@/api/types'
import { relativeTime } from '@/lib/format'

function statusBadge(status: string) {
  const s = status.toUpperCase()
  if (s === 'CONNECTED') return { cls: 'badge--ok', label: 'Connected' }
  if (s === 'ERROR') return { cls: 'badge--danger', label: 'Error' }
  return { cls: 'badge--info', label: 'Available' }
}

function groupByCategory(items: Integration[]) {
  const map = new Map<string, Integration[]>()
  for (const it of items) {
    const key = it.category || 'Other'
    const arr = map.get(key)
    if (arr) arr.push(it)
    else map.set(key, [it])
  }
  return Array.from(map.entries()).sort((a, b) => a[0].localeCompare(b[0]))
}

function IntegrationCard({ it }: { it: Integration }) {
  const badge = statusBadge(it.status)
  const connected = it.status.toUpperCase() === 'CONNECTED'
  const error = it.status.toUpperCase() === 'ERROR'
  return (
    <div className="card">
      <div className="card__body" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div
              className="avatar"
              style={{ borderRadius: 10, background: 'var(--app-sunken)', color: 'var(--ink-2)', fontFamily: 'var(--font-display)' }}
            >
              {it.name.slice(0, 2).toUpperCase()}
            </div>
            <div>
              <div style={{ fontWeight: 600, color: 'var(--ink-0)', fontSize: 15 }}>{it.name}</div>
              <div style={{ fontSize: 12, color: 'var(--ink-4)' }}>{it.category}</div>
            </div>
          </div>
          <span className={`badge ${badge.cls}`}>{badge.label}</span>
        </div>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 8,
            paddingTop: 12,
            borderTop: '1px solid var(--line)',
          }}
        >
          <span style={{ fontSize: 12, color: error ? 'var(--danger-fg)' : 'var(--ink-4)', fontFamily: 'var(--font-mono)' }}>
            {error ? 'Sync failed' : it.lastSyncAt ? `Synced ${relativeTime(it.lastSyncAt)}` : 'Never synced'}
          </span>
          <button className={`btn btn--sm ${connected ? 'btn--ghost' : 'btn--outline'}`}>
            {connected ? 'Manage' : error ? 'Reconnect' : 'Connect'}
          </button>
        </div>
      </div>
    </div>
  )
}

export default function IntegrationsPage() {
  const { data, isLoading, isError, refetch } = useIntegrations()
  const groups = data ? groupByCategory(data) : []
  const connected = data?.filter((i) => i.status.toUpperCase() === 'CONNECTED').length ?? 0

  return (
    <div>
      <div className="page-head" style={{ marginBottom: 18 }}>
        <div className="crumb">
          <span className="dot" />
          Platform · Integrations
        </div>
        <div className="page-head__row">
          <div>
            <h1 style={{ fontSize: 28 }}>Integrations</h1>
            <p className="sub">
              The platform speaks to your stack through an open API. Connect your HRIS, calendars, assessments, and messaging
              channels — data syncs both ways.
            </p>
          </div>
          {data && (
            <span className="badge badge--ok" style={{ alignSelf: 'center' }}>
              {connected} connected
            </span>
          )}
        </div>
      </div>

      {isLoading ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
            Loading integrations…
          </div>
        </div>
      ) : isError ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center' }}>
            <div style={{ fontFamily: 'var(--font-display)', fontSize: 20, color: 'var(--ink-0)' }}>Couldn’t load integrations.</div>
            <div style={{ fontSize: 13.5, color: 'var(--ink-4)', margin: '6px 0 16px' }}>The integrations API didn’t respond.</div>
            <button className="btn btn--outline btn--sm" onClick={() => refetch()}>
              Retry
            </button>
          </div>
        </div>
      ) : !data || data.length === 0 ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
            No integrations available.
          </div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 28 }}>
          {groups.map(([category, items]) => (
            <div key={category}>
              <div
                className="eyebrow"
                style={{ marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}
              >
                {category}
                <span style={{ color: 'var(--ink-5)' }}>· {items.length}</span>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 16 }}>
                {items.map((it) => (
                  <IntegrationCard key={it.id} it={it} />
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
