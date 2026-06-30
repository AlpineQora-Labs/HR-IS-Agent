import { useMemo, useRef, useState } from 'react'
import { usePersistentState, uid } from './builderStore'

// ── Survey model ───────────────────────────────────────────────────────────
type QType = 'short' | 'long' | 'single' | 'multi' | 'rating' | 'nps' | 'yesno'

interface Question {
  id: string
  type: QType
  title: string
  required: boolean
  options?: string[]
}

interface Survey {
  id: string
  name: string
  stage: string
  intro: string
  questions: Question[]
  updatedAt: string
}

const STAGES = ['Application', 'Post-interview', 'Offer', 'Onboarding', 'Candidate withdrew', 'Not selected']

const PALETTE: { type: QType; label: string; hint: string; glyph: string }[] = [
  { type: 'rating', label: 'Star rating', hint: '1–5 stars', glyph: '★' },
  { type: 'nps', label: 'NPS', hint: '0–10 scale', glyph: '⛯' },
  { type: 'single', label: 'Single choice', hint: 'Pick one', glyph: '◉' },
  { type: 'multi', label: 'Multiple choice', hint: 'Pick many', glyph: '☑' },
  { type: 'yesno', label: 'Yes / No', hint: 'Boolean', glyph: '⇄' },
  { type: 'short', label: 'Short text', hint: 'One line', glyph: '—' },
  { type: 'long', label: 'Long text', hint: 'Paragraph', glyph: '¶' },
]

const TYPE_LABEL: Record<QType, string> = {
  short: 'Short text', long: 'Long text', single: 'Single choice', multi: 'Multiple choice',
  rating: 'Star rating', nps: 'NPS', yesno: 'Yes / No',
}

function defaultTitle(t: QType): string {
  switch (t) {
    case 'rating': return 'How would you rate your experience?'
    case 'nps': return 'How likely are you to recommend us to a friend?'
    case 'single': return 'Which best describes you?'
    case 'multi': return 'Select all that apply'
    case 'yesno': return 'Did you get the information you needed?'
    case 'short': return 'Untitled question'
    case 'long': return 'Tell us more'
  }
}

function newQuestion(t: QType): Question {
  const q: Question = { id: uid(), type: t, title: defaultTitle(t), required: false }
  if (t === 'single' || t === 'multi') q.options = ['Option 1', 'Option 2']
  return q
}

function freshSurvey(): Survey {
  return {
    id: uid(),
    name: 'Untitled survey',
    stage: 'Post-interview',
    intro: 'Your feedback takes about a minute and helps us improve the experience for every candidate.',
    questions: [newQuestion('rating'), newQuestion('nps')],
    updatedAt: new Date().toISOString(),
  }
}

// ── Live preview controls (interactive but non-persisting) ──────────────────
function Stars() {
  const [v, setV] = useState(0)
  return (
    <div style={{ display: 'flex', gap: 6 }}>
      {[1, 2, 3, 4, 5].map((n) => (
        <button key={n} type="button" onClick={() => setV(n)}
          style={{ border: 0, background: 'none', cursor: 'pointer', fontSize: 26, lineHeight: 1, color: n <= v ? 'var(--c-amber)' : 'var(--ink-5)' }}>★</button>
      ))}
    </div>
  )
}

function NpsRow() {
  const [v, setV] = useState<number | null>(null)
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
      {Array.from({ length: 11 }, (_, n) => (
        <button key={n} type="button" onClick={() => setV(n)}
          style={{
            width: 34, height: 34, borderRadius: 'var(--ra-2)', cursor: 'pointer', fontSize: 13, fontWeight: 600,
            border: '1px solid var(--line)',
            background: v === n ? 'var(--bofa-navy)' : 'var(--app-panel)',
            color: v === n ? '#fff' : 'var(--ink-2)',
          }}>{n}</button>
      ))}
    </div>
  )
}

function PreviewField({ q }: { q: Question }) {
  return (
    <div style={{ marginBottom: 18 }}>
      <div style={{ fontSize: 13.5, color: 'var(--ink-1)', fontWeight: 500, marginBottom: 8 }}>
        {q.title || <span style={{ color: 'var(--ink-5)' }}>Untitled question</span>}
        {q.required ? <span style={{ color: 'var(--bofa-red)', marginLeft: 4 }}>*</span> : null}
      </div>
      {q.type === 'rating' && <Stars />}
      {q.type === 'nps' && <NpsRow />}
      {q.type === 'short' && <input className="input" placeholder="Your answer" />}
      {q.type === 'long' && <textarea className="input" rows={3} placeholder="Your answer" style={{ resize: 'vertical' }} />}
      {q.type === 'yesno' && (
        <div style={{ display: 'flex', gap: 8 }}>
          <label className="badge" style={{ cursor: 'pointer' }}><input type="radio" name={q.id} style={{ marginRight: 6 }} />Yes</label>
          <label className="badge" style={{ cursor: 'pointer' }}><input type="radio" name={q.id} style={{ marginRight: 6 }} />No</label>
        </div>
      )}
      {(q.type === 'single' || q.type === 'multi') && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
          {(q.options ?? []).map((o, i) => (
            <label key={i} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: 'var(--ink-2)', cursor: 'pointer' }}>
              <input type={q.type === 'single' ? 'radio' : 'checkbox'} name={q.id} />
              {o || `Option ${i + 1}`}
            </label>
          ))}
        </div>
      )}
    </div>
  )
}

// ── Question editor card ────────────────────────────────────────────────────
function QuestionCard({
  q, index, total, onChange, onRemove, onMove, onDuplicate, dragProps,
}: {
  q: Question
  index: number
  total: number
  onChange: (q: Question) => void
  onRemove: () => void
  onMove: (dir: -1 | 1) => void
  onDuplicate: () => void
  dragProps: React.HTMLAttributes<HTMLDivElement> & { draggable?: boolean }
}) {
  const hasOptions = q.type === 'single' || q.type === 'multi'
  return (
    <div className="card" style={{ marginBottom: 12 }} {...dragProps}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '10px 14px', borderBottom: '1px solid var(--line)' }}>
        <span title="Drag to reorder" style={{ cursor: 'grab', color: 'var(--ink-5)', fontSize: 16, userSelect: 'none' }}>⠿</span>
        <span className="badge badge--info">{TYPE_LABEL[q.type]}</span>
        <div style={{ flex: 1 }} />
        <button className="btn btn--ghost btn--sm" disabled={index === 0} onClick={() => onMove(-1)} aria-label="Move up">↑</button>
        <button className="btn btn--ghost btn--sm" disabled={index === total - 1} onClick={() => onMove(1)} aria-label="Move down">↓</button>
        <button className="btn btn--ghost btn--sm" onClick={onDuplicate} aria-label="Duplicate">⧉</button>
        <button className="btn btn--ghost btn--sm" onClick={onRemove} aria-label="Remove">×</button>
      </div>
      <div className="card__body" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <input
          className="input"
          value={q.title}
          placeholder="Question text"
          onChange={(e) => onChange({ ...q, title: e.target.value })}
          style={{ fontSize: 14, fontWeight: 500 }}
        />
        {hasOptions && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
            {(q.options ?? []).map((o, i) => (
              <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ color: 'var(--ink-5)', fontSize: 13 }}>{q.type === 'single' ? '◯' : '▢'}</span>
                <input
                  className="input"
                  value={o}
                  onChange={(e) => {
                    const options = [...(q.options ?? [])]
                    options[i] = e.target.value
                    onChange({ ...q, options })
                  }}
                  style={{ flex: 1 }}
                />
                <button className="btn btn--ghost btn--sm" aria-label="Remove option"
                  disabled={(q.options ?? []).length <= 1}
                  onClick={() => onChange({ ...q, options: (q.options ?? []).filter((_, j) => j !== i) })}>×</button>
              </div>
            ))}
            <button className="btn btn--outline btn--sm" style={{ alignSelf: 'flex-start' }}
              onClick={() => onChange({ ...q, options: [...(q.options ?? []), `Option ${(q.options ?? []).length + 1}`] })}>
              + Add option
            </button>
          </div>
        )}
        <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12.5, color: 'var(--ink-3)' }}>
          <input type="checkbox" checked={q.required} onChange={(e) => onChange({ ...q, required: e.target.checked })} />
          Required
        </label>
      </div>
    </div>
  )
}

// ── Main builder ────────────────────────────────────────────────────────────
export default function SurveyBuilder() {
  const [surveys, setSurveys] = usePersistentState<Survey[]>('olivia.surveys', [freshSurvey()])
  const [activeId, setActiveId] = useState<string>(() => surveys[0]?.id ?? '')
  const dragFrom = useRef<number | null>(null)

  const active = surveys.find((s) => s.id === activeId) ?? surveys[0]

  function patch(p: Partial<Survey>) {
    if (!active) return
    setSurveys((prev) => prev.map((s) => (s.id === active.id ? { ...s, ...p, updatedAt: new Date().toISOString() } : s)))
  }
  function patchQuestions(fn: (qs: Question[]) => Question[]) {
    if (!active) return
    patch({ questions: fn(active.questions) })
  }

  function addQuestion(t: QType) {
    patchQuestions((qs) => [...qs, newQuestion(t)])
  }
  function move(index: number, dir: -1 | 1) {
    patchQuestions((qs) => {
      const next = [...qs]
      const j = index + dir
      if (j < 0 || j >= next.length) return qs
      ;[next[index], next[j]] = [next[j], next[index]]
      return next
    })
  }
  function reorder(from: number, to: number) {
    if (from === to) return
    patchQuestions((qs) => {
      const next = [...qs]
      const [item] = next.splice(from, 1)
      next.splice(to, 0, item)
      return next
    })
  }

  function newSurvey() {
    const s = freshSurvey()
    setSurveys((prev) => [...prev, s])
    setActiveId(s.id)
  }
  function deleteSurvey() {
    if (!active) return
    setSurveys((prev) => {
      const next = prev.filter((s) => s.id !== active.id)
      const fallback = next[0] ?? freshSurvey()
      setActiveId(fallback.id)
      return next.length ? next : [fallback]
    })
  }
  function duplicateSurvey() {
    if (!active) return
    const copy: Survey = {
      ...active,
      id: uid(),
      name: `${active.name} (copy)`,
      questions: active.questions.map((q) => ({ ...q, id: uid() })),
      updatedAt: new Date().toISOString(),
    }
    setSurveys((prev) => [...prev, copy])
    setActiveId(copy.id)
  }

  const estMinutes = useMemo(() => Math.max(1, Math.round((active?.questions.length ?? 0) * 0.25)), [active])

  if (!active) return null

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* Toolbar */}
      <div className="card">
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '12px 14px', flexWrap: 'wrap' }}>
          <select className="select" style={{ width: 220 }} value={active.id} onChange={(e) => setActiveId(e.target.value)}>
            {surveys.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
          <button className="btn btn--outline btn--sm" onClick={newSurvey}>+ New</button>
          <button className="btn btn--ghost btn--sm" onClick={duplicateSurvey}>Duplicate</button>
          <div style={{ flex: 1 }} />
          <span className="eyebrow">{active.questions.length} questions · ~{estMinutes} min</span>
          <span className="badge badge--ok" title="Saved automatically">Saved</span>
          <button className="btn btn--ghost btn--sm" onClick={deleteSurvey} disabled={surveys.length <= 1}>Delete</button>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '180px 1fr 340px', gap: 16, alignItems: 'start' }}>
        {/* Palette */}
        <div className="card" style={{ padding: 10, position: 'sticky', top: 12 }}>
          <div className="eyebrow" style={{ padding: '4px 6px 8px' }}>Add question</div>
          {PALETTE.map((p) => (
            <button key={p.type} onClick={() => addQuestion(p.type)}
              style={{
                display: 'flex', alignItems: 'center', gap: 10, width: '100%', textAlign: 'left',
                border: '1px solid var(--line)', background: 'var(--app-panel)', cursor: 'pointer',
                borderRadius: 'var(--ra-2)', padding: '8px 10px', marginBottom: 6, font: 'inherit',
              }}>
              <span style={{ width: 22, textAlign: 'center', color: 'var(--bofa-navy)', fontSize: 15 }}>{p.glyph}</span>
              <span>
                <div style={{ fontSize: 13, color: 'var(--ink-1)', fontWeight: 500 }}>{p.label}</div>
                <div style={{ fontSize: 11, color: 'var(--ink-5)' }}>{p.hint}</div>
              </span>
            </button>
          ))}
        </div>

        {/* Canvas */}
        <div>
          <div className="card" style={{ marginBottom: 12 }}>
            <div className="card__body" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
                <label style={{ flex: 2, minWidth: 200 }}>
                  <div className="eyebrow" style={{ marginBottom: 6 }}>Survey name</div>
                  <input className="input" value={active.name} onChange={(e) => patch({ name: e.target.value })} />
                </label>
                <label style={{ flex: 1, minWidth: 160 }}>
                  <div className="eyebrow" style={{ marginBottom: 6 }}>Stage / touchpoint</div>
                  <select className="select" value={active.stage} onChange={(e) => patch({ stage: e.target.value })}>
                    {STAGES.map((s) => <option key={s} value={s}>{s}</option>)}
                  </select>
                </label>
              </div>
              <label>
                <div className="eyebrow" style={{ marginBottom: 6 }}>Intro message</div>
                <textarea className="input" rows={2} value={active.intro} onChange={(e) => patch({ intro: e.target.value })} style={{ resize: 'vertical' }} />
              </label>
            </div>
          </div>

          {active.questions.length === 0 ? (
            <div className="card">
              <div className="card__body" style={{ padding: '44px 20px', textAlign: 'center' }}>
                <div style={{ fontFamily: 'var(--font-display)', fontSize: 17, color: 'var(--ink-0)' }}>No questions yet</div>
                <div style={{ fontSize: 13, color: 'var(--ink-4)', marginTop: 6 }}>Add one from the palette on the left to start building.</div>
              </div>
            </div>
          ) : (
            active.questions.map((q, i) => (
              <QuestionCard
                key={q.id}
                q={q}
                index={i}
                total={active.questions.length}
                onChange={(nq) => patchQuestions((qs) => qs.map((x) => (x.id === q.id ? nq : x)))}
                onRemove={() => patchQuestions((qs) => qs.filter((x) => x.id !== q.id))}
                onMove={(dir) => move(i, dir)}
                onDuplicate={() => patchQuestions((qs) => {
                  const copy = { ...q, id: uid() }
                  const next = [...qs]
                  next.splice(i + 1, 0, copy)
                  return next
                })}
                dragProps={{
                  draggable: true,
                  onDragStart: () => { dragFrom.current = i },
                  onDragOver: (e) => e.preventDefault(),
                  onDrop: () => { if (dragFrom.current !== null) reorder(dragFrom.current, i); dragFrom.current = null },
                  style: { marginBottom: 12 },
                }}
              />
            ))
          )}
        </div>

        {/* Live preview */}
        <div className="card" style={{ position: 'sticky', top: 12, overflow: 'hidden' }}>
          <div style={{ background: 'var(--bofa-navy)', color: '#fff', padding: '14px 18px' }}>
            <div style={{ fontSize: 11, opacity: 0.8, letterSpacing: '0.04em', textTransform: 'uppercase' }}>Live preview · {active.stage}</div>
            <div style={{ fontFamily: 'var(--font-display)', fontSize: 18, marginTop: 2 }}>{active.name || 'Untitled survey'}</div>
          </div>
          <div style={{ padding: '16px 18px', maxHeight: 540, overflowY: 'auto' }}>
            {active.intro ? <p style={{ fontSize: 12.5, color: 'var(--ink-3)', lineHeight: 1.55, marginTop: 0 }}>{active.intro}</p> : null}
            {active.questions.length === 0 ? (
              <div style={{ fontSize: 12.5, color: 'var(--ink-5)', textAlign: 'center', padding: '24px 0' }}>Your questions will appear here.</div>
            ) : (
              active.questions.map((q) => <PreviewField key={q.id} q={q} />)
            )}
            {active.questions.length > 0 && (
              <button className="btn btn--primary" style={{ width: '100%', marginTop: 6 }} disabled>Submit feedback</button>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
