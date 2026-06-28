import { useState } from 'react'
import { useCopilot, useGenerateCopilot, useJobs } from '@/api/hooks'
import { relativeTime } from '@/lib/format'

// Sense-inspired recruiting copilot: generate JDs, outreach, interview kits and
// summaries, with every artifact saved to a running history.

const KINDS = [
  { value: 'JD', label: 'Job description' },
  { value: 'OUTREACH', label: 'Outreach message' },
  { value: 'INTERVIEW_QUESTIONS', label: 'Interview questions' },
  { value: 'SUMMARY', label: 'Candidate summary' },
  { value: 'REWRITE', label: 'Rewrite / polish' },
] as const

function kindBadge(kind: string) {
  const key = kind.toUpperCase()
  if (key === 'JD') return 'badge--info'
  if (key === 'OUTREACH') return 'badge--purple'
  if (key === 'INTERVIEW_QUESTIONS') return 'badge--warn'
  if (key === 'SUMMARY') return 'badge--ok'
  if (key === 'REWRITE') return 'badge'
  return 'badge'
}

function kindLabel(kind: string) {
  return KINDS.find((k) => k.value === kind.toUpperCase())?.label ?? kind
}

export default function CopilotPage() {
  const { data: artifacts, isLoading } = useCopilot()
  const { data: jobs } = useJobs()
  const generate = useGenerateCopilot()

  const [kind, setKind] = useState<string>('JD')
  const [jobId, setJobId] = useState<string>('')
  const [prompt, setPrompt] = useState<string>('')

  function onGenerate() {
    if (generate.isPending) return
    generate.mutate({ kind, jobId: jobId || undefined, prompt: prompt || undefined })
  }

  const result = generate.data

  return (
    <div>
      <div className="page-head">
        <div className="crumb">
          <span className="dot" />
          Recruiting · Copilot
        </div>
        <h1>AI Copilot</h1>
        <p className="sub">
          Your recruiting copilot. Draft job descriptions, outreach, interview kits and candidate summaries in seconds —
          grounded in the role you pick.
        </p>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) minmax(0, 1.1fr)', gap: 20, alignItems: 'start' }}>
        {/* Generator */}
        <div className="card">
          <div className="card__head">
            <h3>Generate</h3>
          </div>
          <div className="card__body" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div className="field">
              <label className="field__label" htmlFor="cp-kind">Artifact</label>
              <select id="cp-kind" className="select" value={kind} onChange={(e) => setKind(e.target.value)}>
                {KINDS.map((k) => (
                  <option key={k.value} value={k.value}>{k.label}</option>
                ))}
              </select>
            </div>

            <div className="field">
              <label className="field__label" htmlFor="cp-job">
                Role <span className="opt">optional</span>
              </label>
              <select id="cp-job" className="select" value={jobId} onChange={(e) => setJobId(e.target.value)}>
                <option value="">No specific role</option>
                {jobs?.map((j) => (
                  <option key={j.id} value={j.id}>{j.title} — {j.department}</option>
                ))}
              </select>
            </div>

            <div className="field">
              <label className="field__label" htmlFor="cp-prompt">
                Prompt <span className="opt">optional</span>
              </label>
              <textarea
                id="cp-prompt"
                className="textarea"
                placeholder="Add context, tone, or specifics for the assistant to work from…"
                value={prompt}
                onChange={(e) => setPrompt(e.target.value)}
              />
            </div>

            <div>
              <button className="btn btn--primary" onClick={onGenerate} disabled={generate.isPending}>
                {generate.isPending ? 'Generating…' : 'Generate'}
              </button>
            </div>

            {generate.isError && (
              <p className="muted" style={{ color: 'var(--danger-fg)' }}>Couldn’t generate that artifact. Try again.</p>
            )}

            {result && (
              <div className="card" style={{ background: 'var(--app-sunken)', boxShadow: 'none' }}>
                <div className="card__head" style={{ borderColor: 'var(--line)' }}>
                  <span className={`badge ${kindBadge(result.kind)}`}>{kindLabel(result.kind)}</span>
                  <span className="muted" style={{ margin: 0 }}>{relativeTime(result.createdAt)}</span>
                </div>
                <div className="card__body" style={{ whiteSpace: 'pre-wrap', fontSize: 13.5, lineHeight: 1.55, color: 'var(--ink-1)' }}>
                  {result.content}
                </div>
              </div>
            )}
          </div>
        </div>

        {/* History */}
        <div className="card">
          <div className="card__head">
            <h3>Recent artifacts</h3>
            <span className="muted" style={{ margin: 0 }}>{artifacts?.length ?? 0}</span>
          </div>
          <div className="card__body">
            {isLoading ? (
              <div style={{ padding: '32px 0', textAlign: 'center', color: 'var(--ink-4)' }}>Loading artifacts…</div>
            ) : !artifacts || artifacts.length === 0 ? (
              <div style={{ padding: '32px 0', textAlign: 'center', color: 'var(--ink-4)' }}>
                Nothing generated yet. Your copilot history will appear here.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                {artifacts.map((a) => (
                  <div key={a.id} style={{ borderBottom: '1px solid var(--line)', paddingBottom: 12 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6 }}>
                      <span className={`badge ${kindBadge(a.kind)}`}>{kindLabel(a.kind)}</span>
                      {a.jobTitle && <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>{a.jobTitle}</span>}
                      <span className="muted" style={{ margin: '0 0 0 auto' }}>{relativeTime(a.createdAt)}</span>
                    </div>
                    <p
                      style={{
                        margin: 0,
                        fontSize: 13,
                        color: 'var(--ink-3)',
                        lineHeight: 1.5,
                        display: '-webkit-box',
                        WebkitLineClamp: 3,
                        WebkitBoxOrient: 'vertical',
                        overflow: 'hidden',
                      }}
                    >
                      {a.content}
                    </p>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
