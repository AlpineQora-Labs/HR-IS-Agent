import { Fragment, useState } from 'react'
import { useOffers } from '@/api/hooks'
import { date, money } from '@/lib/format'

const statusBadge = (status: string) => {
  const s = status.toUpperCase()
  if (s === 'ACCEPTED') return 'badge--ok'
  if (s === 'SENT') return 'badge--info'
  if (s === 'DECLINED') return 'badge--danger'
  return '' // DRAFT → neutral default
}

const comp = (base: number, period: string) => {
  if (!base) return '—'
  const suffix = period ? `/${period.toLowerCase()}` : ''
  return `${money(base)}${suffix}`
}

function Empty({ label }: { label: string }) {
  return (
    <div className="card">
      <div className="card__body" style={{ padding: '40px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
        {label}
      </div>
    </div>
  )
}

export default function OffersPage() {
  const { data, isLoading } = useOffers()
  const [expanded, setExpanded] = useState<string | null>(null)

  return (
    <div>
      <div className="page-head">
        <div className="crumb">
          <span className="dot" />
          Hiring · Offers
        </div>
        <div className="page-head__row">
          <div>
            <h1>Offers</h1>
            <p className="sub">Compensation packages from draft through acceptance, with the full offer letter on hand.</p>
          </div>
        </div>
      </div>

      {isLoading ? (
        <Empty label="Loading offers…" />
      ) : !data || data.length === 0 ? (
        <Empty label="No offers extended yet." />
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Candidate</th>
                <th>Role</th>
                <th>Title</th>
                <th className="t-right">Base</th>
                <th className="t-right">Bonus</th>
                <th>Equity</th>
                <th>Start date</th>
                <th>Status</th>
                <th>Sent</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {data.map((o) => {
                const open = expanded === o.id
                return (
                  <Fragment key={o.id}>
                    <tr>
                      <td className="t-strong">{o.candidateName || '—'}</td>
                      <td className="t-muted">{o.jobTitle || '—'}</td>
                      <td>{o.title || '—'}</td>
                      <td className="t-right t-num">{comp(o.compBase, o.compPeriod)}</td>
                      <td className="t-right t-num">{o.compBonus ? money(o.compBonus) : '—'}</td>
                      <td className="t-muted">{o.equity || '—'}</td>
                      <td className="t-muted">{date(o.startDate)}</td>
                      <td>
                        <span className={`badge ${statusBadge(o.status)}`}>{o.status || '—'}</span>
                      </td>
                      <td className="t-muted">{date(o.sentAt)}</td>
                      <td className="t-right">
                        <button className="link" onClick={() => setExpanded(open ? null : o.id)}>
                          {open ? 'Hide letter' : 'View letter'}
                        </button>
                      </td>
                    </tr>
                    {open && (
                      <tr>
                        <td colSpan={10} style={{ background: 'var(--app-sunken)' }}>
                          <div style={{ padding: '4px 4px 8px' }}>
                            <p className="eyebrow" style={{ margin: '0 0 10px' }}>
                              Offer letter
                            </p>
                            <div
                              style={{
                                whiteSpace: 'pre-wrap',
                                fontSize: 14,
                                lineHeight: 1.6,
                                color: 'var(--ink-1)',
                                maxWidth: '70ch',
                              }}
                            >
                              {o.letterBody || 'No letter body on file.'}
                            </div>
                          </div>
                        </td>
                      </tr>
                    )}
                  </Fragment>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
