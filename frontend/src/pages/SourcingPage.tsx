import { useState } from 'react'
import { useJobs, useReactivation, useSourcing } from '@/api/hooks'
import type { MatchRow, SourcingRow } from '@/api/types'

// Eightfold-inspired sourcing: reach passive talent and re-match dormant
// candidates already in the database. Lifecycle badges signal intent.

type Tab = 'passive' | 'reactivation'

function lifecycleBadge(lifecycle: string) {
  const key = lifecycle.toUpperCase()
  if (key === 'ACTIVE') return 'badge--ok'
  if (key === 'PASSIVE') return 'badge--info'
  if (key === 'DORMANT') return 'badge--warn'
  return 'badge'
}

function ScoreBar({ score }: { score: number }) {
  const pct = Math.max(0, Math.min(100, score))
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, justifyContent: 'flex-end' }}>
      <div className="progress" style={{ width: 70 }}>
        <i style={{ width: `${pct}%` }} />
      </div>
      <span className="t-num t-strong" style={{ minWidth: 24, textAlign: 'right' }}>{Math.round(score)}</span>
    </div>
  )
}

function PassiveTable({ rows }: { rows: SourcingRow[] }) {
  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            <th>Candidate</th>
            <th>Location</th>
            <th>Lifecycle</th>
            <th>Source</th>
            <th className="t-right">Fit</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.candidateId}>
              <td>
                <div className="who__name">{r.candidateName}</div>
                <div className="who__sub">{r.headline}</div>
              </td>
              <td className="t-muted">{r.location}</td>
              <td>
                <span className={`badge ${lifecycleBadge(r.lifecycle)}`}>{r.lifecycle}</span>
              </td>
              <td className="t-muted">{r.source}</td>
              <td className="t-right">
                <ScoreBar score={r.fitScore} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function ReactivationTable({ rows }: { rows: MatchRow[] }) {
  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            <th>Dormant candidate</th>
            <th>Why re-matched</th>
            <th className="t-right">Fit</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.candidateId}>
              <td>
                <div className="who__name">{r.candidateName}</div>
                <div className="who__sub">{r.headline}</div>
              </td>
              <td style={{ maxWidth: 460, color: 'var(--ink-2)' }}>{r.explanation}</td>
              <td className="t-right">
                <ScoreBar score={r.fitScore} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export default function SourcingPage() {
  const { data: jobs, isLoading: jobsLoading } = useJobs()
  const [jobId, setJobId] = useState<string>('')
  const [tab, setTab] = useState<Tab>('passive')
  const selectedJob = jobId || jobs?.[0]?.id

  const { data: passive, isLoading: passiveLoading } = useSourcing(selectedJob)
  const { data: dormant, isLoading: dormantLoading } = useReactivation()

  return (
    <div>
      <div className="page-head">
        <div className="crumb">
          <span className="dot" />
          Talent intelligence · Sourcing
        </div>
        <div className="page-head__row">
          <div>
            <h1>Sourcing</h1>
            <p className="sub">
              Reach passive talent the moment a role matches their profile, and re-engage dormant candidates already in
              your database with a fresh skills match.
            </p>
          </div>
          <div className="field" style={{ minWidth: 280 }}>
            <label className="field__label" htmlFor="src-job">Role</label>
            <select
              id="src-job"
              className="select"
              value={selectedJob ?? ''}
              onChange={(e) => setJobId(e.target.value)}
              disabled={jobsLoading || !jobs?.length}
            >
              {jobs?.map((j) => (
                <option key={j.id} value={j.id}>
                  {j.title} — {j.department}
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>

      <div className="tabs" style={{ marginBottom: 18 }}>
        <button className="tab" aria-selected={tab === 'passive'} onClick={() => setTab('passive')}>
          Passive talent
          <span className="count">{passive?.length ?? 0}</span>
        </button>
        <button className="tab" aria-selected={tab === 'reactivation'} onClick={() => setTab('reactivation')}>
          Database reactivation
          <span className="count">{dormant?.length ?? 0}</span>
        </button>
      </div>

      {tab === 'passive' ? (
        jobsLoading || passiveLoading ? (
          <div className="card">
            <div style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>Finding passive talent…</div>
          </div>
        ) : !passive || passive.length === 0 ? (
          <div className="card">
            <div style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
              No passive matches for this role yet.
            </div>
          </div>
        ) : (
          <PassiveTable rows={passive} />
        )
      ) : dormantLoading ? (
        <div className="card">
          <div style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>Re-matching dormant candidates…</div>
        </div>
      ) : !dormant || dormant.length === 0 ? (
        <div className="card">
          <div style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
            No dormant candidates to reactivate.
          </div>
        </div>
      ) : (
        <ReactivationTable rows={dormant} />
      )}
    </div>
  )
}
