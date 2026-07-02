import { useMemo, useState } from 'react'
import { rowAction } from '@/lib/a11y'
import { Link } from 'react-router-dom'
import SlideOver from '../components/SlideOver'
import { useCandidate, useCandidates, useJobInterviews } from '../api/hooks'
import type { CandidateSummary } from '../api/types'
import { date, humanize, initials } from '../lib/format'

const ivTime = (iso: string | null | undefined) => {
  if (!iso) return ''
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '' : d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })
}

function ivStatusBadge(status: string) {
  const s = (status || '').toUpperCase()
  if (s === 'COMPLETED' || s === 'DONE') return 'badge--ok'
  if (s === 'SCHEDULED' || s === 'CONFIRMED') return 'badge--info'
  if (s === 'CANCELLED' || s === 'NO_SHOW') return 'badge--danger'
  return 'badge--neutral'
}

function lifecycleClass(lifecycle: string) {
  const s = lifecycle.toLowerCase()
  if (s.includes('hire') || s === 'active') return 'badge--ok'
  if (s.includes('lead') || s.includes('prospect') || s.includes('new')) return 'badge--info'
  if (s.includes('reject') || s.includes('declin') || s.includes('withdraw')) return 'badge--neutral'
  if (s.includes('nurtur') || s.includes('passive')) return 'badge--purple'
  return 'badge--neutral'
}

function fitClass(score: number) {
  if (score >= 80) return 'badge--ok'
  if (score >= 60) return 'badge--info'
  if (score >= 40) return 'badge--warn'
  return 'badge--danger'
}

function stageClass(stage: string) {
  const s = stage.toLowerCase()
  if (s.includes('hire') || s.includes('offer')) return 'badge--ok'
  if (s.includes('reject') || s.includes('declin') || s.includes('withdraw')) return 'badge--neutral'
  if (s.includes('interview') || s.includes('screen')) return 'badge--info'
  return 'badge--neutral'
}

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, padding: '7px 0', fontSize: 13 }}>
      <span style={{ color: 'var(--ink-4)' }}>{label}</span>
      <span style={{ color: 'var(--ink-1)', textAlign: 'right' }}>{children}</span>
    </div>
  )
}

type Tab = 'overview' | 'applications' | 'interviews'

function CandidateDrawer({ summary, onClose }: { summary: CandidateSummary; onClose: () => void }) {
  const { data: c, isLoading } = useCandidate(summary.id)
  const skills = c?.skills ?? []
  const apps = c?.applications ?? []
  const appIds = useMemo(() => apps.map((a) => a.id), [apps])
  const { interviews, isLoading: ivLoading } = useJobInterviews(appIds)
  const jobByApp = useMemo(() => Object.fromEntries(apps.map((a) => [a.id, a.jobTitle])), [apps])

  const [tab, setTab] = useState<Tab>('overview')
  const [note, setNote] = useState('')

  const tabs: { key: Tab; label: string; count?: number }[] = [
    { key: 'overview', label: 'Overview' },
    { key: 'applications', label: 'Applications', count: apps.length },
    { key: 'interviews', label: 'Interviews', count: interviews.length },
  ]

  return (
    <SlideOver label={`${summary.name} details`} onClose={onClose} width={480}>
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 14, padding: '18px 20px 14px', borderBottom: '1px solid var(--line)' }}>
        <div className="avatar" style={{ width: 46, height: 46, fontSize: 16 }}>
          {initials(summary.name)}
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontFamily: 'var(--font-display)', fontSize: 19, color: 'var(--ink-0)', lineHeight: 1.2 }}>
            {summary.name}
          </div>
          {summary.headline ? (
            <div className="sub" style={{ marginTop: 3, fontSize: 12.5 }}>{summary.headline}</div>
          ) : null}
          <div style={{ display: 'flex', gap: 6, marginTop: 9 }}>
            <span className={`badge ${lifecycleClass(summary.lifecycle)}`}>{summary.lifecycle}</span>
            <span className="badge">{humanize(summary.source)}</span>
          </div>
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="Close"
          style={{ border: 0, background: 'none', cursor: 'pointer', fontSize: 20, lineHeight: 1, color: 'var(--ink-4)', padding: 2 }}
        >
          ×
        </button>
      </div>

      {/* Tab bar */}
      <div style={{ display: 'flex', gap: 2, padding: '0 12px', borderBottom: '1px solid var(--line)' }}>
        {tabs.map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            style={{
              border: 0,
              background: 'none',
              cursor: 'pointer',
              padding: '11px 12px',
              fontSize: 13,
              fontWeight: tab === t.key ? 600 : 500,
              color: tab === t.key ? 'var(--bofa-navy)' : 'var(--ink-4)',
              borderBottom: tab === t.key ? '2px solid var(--bofa-navy)' : '2px solid transparent',
              marginBottom: -1,
            }}
          >
            {t.label}
            {typeof t.count === 'number' ? <span style={{ color: 'var(--ink-5)', marginLeft: 5 }}>{t.count}</span> : null}
          </button>
        ))}
      </div>

      <div style={{ flex: 1, overflowY: 'auto', padding: '16px 20px' }}>
        {tab === 'overview' && (
          <>
            <div className="eyebrow" style={{ marginBottom: 4 }}>Personal</div>
            {summary.location ? <Fact label="Location">{summary.location}</Fact> : null}
            {summary.yearsExperience ? <Fact label="Experience">{summary.yearsExperience} yrs</Fact> : null}
            {summary.preferredLanguage ? <Fact label="Language">{summary.preferredLanguage}</Fact> : null}
            {isLoading ? (
              <Fact label="Contact">Loading…</Fact>
            ) : c ? (
              <>
                {c.email ? (
                  <Fact label="Email">
                    <a className="link" href={`mailto:${c.email}`}>{c.email}</a>
                  </Fact>
                ) : null}
                {c.phone ? <Fact label="Phone">{c.phone}</Fact> : null}
              </>
            ) : null}

            {skills.length > 0 ? (
              <div style={{ marginTop: 18 }}>
                <div className="eyebrow" style={{ marginBottom: 8 }}>Skills</div>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                  {skills.map((s) => (
                    <span
                      key={s.name}
                      className={`badge ${s.inferred ? 'badge--purple' : 'badge--neutral'}`}
                      title={`${s.proficiency}${s.years ? ` · ${s.years} yrs` : ''}${s.inferred ? ' · inferred' : ''}`}
                    >
                      {s.name}
                    </span>
                  ))}
                </div>
              </div>
            ) : null}

            <div style={{ marginTop: 18 }}>
              <div className="eyebrow" style={{ marginBottom: 8 }}>Notes</div>
              <textarea
                className="input"
                value={note}
                onChange={(e) => setNote(e.target.value)}
                placeholder="Add a private note about this candidate…"
                rows={3}
                style={{ width: '100%', resize: 'vertical', fontFamily: 'inherit' }}
              />
              <div style={{ fontSize: 11, color: 'var(--ink-5)', marginTop: 4 }}>Local to this view — not yet saved to the server.</div>
            </div>
          </>
        )}

        {tab === 'applications' && (
          isLoading ? (
            <div style={{ fontSize: 12.5, color: 'var(--ink-4)' }}>Loading…</div>
          ) : apps.length === 0 ? (
            <div style={{ fontSize: 12.5, color: 'var(--ink-4)' }}>No applications on record.</div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {apps.map((a) => (
                <Link
                  key={a.id}
                  to={`/jobs/${a.jobId}`}
                  className="card"
                  style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8, padding: '9px 12px', textDecoration: 'none', border: '1px solid var(--line)' }}
                >
                  <span style={{ fontSize: 13, color: 'var(--ink-0)', minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {a.jobTitle}
                  </span>
                  <span style={{ display: 'flex', gap: 6, flexShrink: 0 }}>
                    <span className={`badge ${stageClass(a.stage)}`}>{a.stage}</span>
                    <span className={`badge ${fitClass(a.fitScore)}`}>{Math.round(a.fitScore)}</span>
                  </span>
                </Link>
              ))}
            </div>
          )
        )}

        {tab === 'interviews' && (
          ivLoading ? (
            <div style={{ fontSize: 12.5, color: 'var(--ink-4)' }}>Loading…</div>
          ) : interviews.length === 0 ? (
            <div style={{ fontSize: 12.5, color: 'var(--ink-4)' }}>No interviews on record.</div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {[...interviews]
                .sort((a, b) => new Date(b.scheduledAt).getTime() - new Date(a.scheduledAt).getTime())
                .map((iv) => (
                  <div key={iv.id} className="card" style={{ padding: '11px 13px', border: '1px solid var(--line)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
                      <span className="t-strong" style={{ fontSize: 13.5 }}>{iv.type || 'Interview'}</span>
                      <span className={`badge ${ivStatusBadge(iv.status)}`}>{iv.status || '—'}</span>
                    </div>
                    <div style={{ fontSize: 12, color: 'var(--ink-4)', marginTop: 4 }}>
                      {date(iv.scheduledAt)} · {ivTime(iv.scheduledAt)}
                      {jobByApp[iv.applicationId] ? ` · ${jobByApp[iv.applicationId]}` : ''}
                    </div>
                    {(iv.score || iv.recommendation) && (
                      <div style={{ display: 'flex', gap: 6, marginTop: 8 }}>
                        {iv.score ? <span className="badge badge--neutral">Score {iv.score.toFixed(1)}</span> : null}
                        {iv.recommendation ? <span className="badge">{iv.recommendation}</span> : null}
                      </div>
                    )}
                    {iv.summary ? (
                      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 8, lineHeight: 1.5 }}>{iv.summary}</div>
                    ) : null}
                  </div>
                ))}
            </div>
          )
        )}
      </div>

      <div style={{ display: 'flex', gap: 8, padding: '14px 20px', borderTop: '1px solid var(--line)' }}>
        <Link to={`/candidates/${summary.id}`} className="btn btn--primary btn--sm" style={{ flex: 1, textAlign: 'center' }}>
          Open full profile
        </Link>
      </div>
    </SlideOver>
  )
}

export default function CandidatesPage() {
  const { data, isLoading, isError, refetch } = useCandidates()
  const [filter, setFilter] = useState('')
  const [selected, setSelected] = useState<CandidateSummary | null>(null)

  const rows = useMemo(() => {
    if (!data) return []
    const q = filter.trim().toLowerCase()
    if (!q) return data
    return data.filter((c) =>
      [c.name, c.headline, c.location, c.source, c.lifecycle, ...(c.topSkills ?? [])].some((v) =>
        v?.toLowerCase().includes(q),
      ),
    )
  }, [data, filter])

  return (
    <div>
      <div className="page-head" style={{ marginBottom: 18 }}>
        <div className="crumb">
          <span className="dot" />
          Recruiting · Candidates
        </div>
        <div className="page-head__row">
          <div>
            <h1 style={{ fontSize: 28 }}>Candidates</h1>
            <p className="sub">Everyone in the talent pool, across sources and lifecycle stages.</p>
          </div>
          <div className="input-group" style={{ width: 280 }}>
            <input
              className="input"
              placeholder="Filter by name, skill, source…"
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
            />
          </div>
        </div>
      </div>

      {isLoading ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
            Loading candidates…
          </div>
        </div>
      ) : isError ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center' }}>
            <div style={{ fontFamily: 'var(--font-display)', fontSize: 20, color: 'var(--ink-0)' }}>
              Couldn’t load candidates.
            </div>
            <button className="btn btn--outline btn--sm" style={{ marginTop: 14 }} onClick={() => refetch()}>
              Retry
            </button>
          </div>
        </div>
      ) : !data || data.length === 0 ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
            No candidates in the pool yet.
          </div>
        </div>
      ) : rows.length === 0 ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
            No candidates match “{filter}”.
          </div>
        </div>
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Candidate</th>
                <th>Location</th>
                <th>Source</th>
                <th>Lifecycle</th>
                <th className="t-right">Apps</th>
                <th>Top skills</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((c) => (
                <tr key={c.id} {...rowAction(() => setSelected(c))}>
                  <td>
                    <div className="who">
                      <div className="avatar">{initials(c.name)}</div>
                      <div>
                        <div className="who__name" style={{ color: 'var(--bofa-navy)' }}>
                          {c.name}
                        </div>
                        {c.headline ? <div className="who__sub">{c.headline}</div> : null}
                      </div>
                    </div>
                  </td>
                  <td className="t-muted">{c.location || '—'}</td>
                  <td className="t-muted">{humanize(c.source)}</td>
                  <td>
                    <span className={`badge ${lifecycleClass(c.lifecycle)}`}>{c.lifecycle}</span>
                  </td>
                  <td className="t-right t-num">{c.applicationCount}</td>
                  <td>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                      {(c.topSkills ?? []).slice(0, 4).map((s) => (
                        <span key={s} className="chip" style={{ cursor: 'default', padding: '4px 10px', fontSize: 12 }}>
                          {s}
                        </span>
                      ))}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {selected && <CandidateDrawer summary={selected} onClose={() => setSelected(null)} />}
    </div>
  )
}
