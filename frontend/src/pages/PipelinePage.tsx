import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useJobs, usePipeline } from '../api/hooks'
import type { ApplicationRow } from '../api/types'

function fitClass(score: number) {
  if (score >= 80) return 'badge--ok'
  if (score >= 60) return 'badge--info'
  if (score >= 40) return 'badge--warn'
  return 'badge--danger'
}

function isOpen(status: string) {
  const s = status.toLowerCase()
  return s === 'open' || s === 'active' || s === 'published'
}

function CandidateCard({ app }: { app: ApplicationRow }) {
  return (
    <Link to={`/candidates/${app.candidateId}`} style={{ textDecoration: 'none', display: 'block' }}>
      <div className="card" style={{ padding: '12px 14px' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
          <span style={{ fontWeight: 600, fontSize: 13.5, color: 'var(--ink-0)' }}>{app.candidateName}</span>
          <span className={`badge ${fitClass(app.fitScore)}`}>{Math.round(app.fitScore)}</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 8 }}>
          <span style={{ fontSize: 12, color: 'var(--ink-4)' }}>{app.source}</span>
          {app.knockoutPassed ? null : <span className="badge badge--danger">Knockout</span>}
        </div>
      </div>
    </Link>
  )
}

export default function PipelinePage() {
  const { data: jobs, isLoading: jobsLoading } = useJobs()
  const [jobId, setJobId] = useState<string | undefined>(undefined)

  useEffect(() => {
    if (jobId || !jobs || jobs.length === 0) return
    const firstOpen = jobs.find((j) => isOpen(j.status)) ?? jobs[0]
    setJobId(firstOpen.id)
  }, [jobs, jobId])

  const { data: columns, isLoading, isError, refetch } = usePipeline(jobId)
  const selectedJob = jobs?.find((j) => j.id === jobId)

  return (
    <div>
      <div className="page-head" style={{ marginBottom: 18 }}>
        <div className="crumb">
          <span className="dot" />
          Recruiting · Pipeline
        </div>
        <div className="page-head__row">
          <div>
            <h1 style={{ fontSize: 28 }}>Pipeline</h1>
            <p className="sub">Candidates by stage for the selected requisition.</p>
          </div>
          <div className="field" style={{ minWidth: 280 }}>
            <select
              className="select"
              value={jobId ?? ''}
              onChange={(e) => setJobId(e.target.value || undefined)}
              disabled={jobsLoading || !jobs || jobs.length === 0}
            >
              {jobsLoading ? (
                <option>Loading jobs…</option>
              ) : !jobs || jobs.length === 0 ? (
                <option>No jobs</option>
              ) : (
                jobs.map((j) => (
                  <option key={j.id} value={j.id}>
                    {j.title} · {j.department}
                  </option>
                ))
              )}
            </select>
          </div>
        </div>
      </div>

      {!jobId ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
            {jobsLoading ? 'Loading…' : 'Select a job to view its pipeline.'}
          </div>
        </div>
      ) : isLoading ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
            Loading pipeline…
          </div>
        </div>
      ) : isError ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center' }}>
            <div style={{ fontFamily: 'var(--font-display)', fontSize: 20, color: 'var(--ink-0)' }}>
              Couldn’t load the pipeline.
            </div>
            <button className="btn btn--outline btn--sm" style={{ marginTop: 14 }} onClick={() => refetch()}>
              Retry
            </button>
          </div>
        </div>
      ) : !columns || columns.length === 0 ? (
        <div className="card">
          <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--ink-4)' }}>
            No candidates in the pipeline {selectedJob ? `for ${selectedJob.title}` : ''} yet.
          </div>
        </div>
      ) : (
        <div style={{ display: 'flex', gap: 16, overflowX: 'auto', paddingBottom: 8, alignItems: 'flex-start' }}>
          {columns.map((col) => (
            <div
              key={col.stage}
              style={{
                flex: '0 0 280px',
                width: 280,
                background: 'var(--app-sunken)',
                borderRadius: 'var(--ra-3)',
                padding: 12,
              }}
            >
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '4px 6px 12px',
                }}
              >
                <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>{col.stage}</span>
                <span
                  className="t-num"
                  style={{
                    fontSize: 12,
                    fontWeight: 600,
                    color: 'var(--ink-4)',
                    background: 'var(--app-panel)',
                    borderRadius: 999,
                    padding: '1px 9px',
                  }}
                >
                  {col.count}
                </span>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {col.cards.length === 0 ? (
                  <div style={{ fontSize: 12.5, color: 'var(--ink-5)', textAlign: 'center', padding: '16px 0' }}>
                    Empty
                  </div>
                ) : (
                  col.cards.map((app) => <CandidateCard key={app.id} app={app} />)
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
