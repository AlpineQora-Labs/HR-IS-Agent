import { useMemo, useState } from 'react'
import ConfirmButton from '@/components/ConfirmButton'
import { useStore } from '@/state/store'
import { usePersistentState, uid } from './builderStore'

// Survey studio — a port of the Arya "Teammate Voices" survey editor design
// (REBUILD-SURVEY-EDITOR.md) onto Olivia's design system: a survey library in
// front of a five-tab authoring workspace (Details · Form Builder · Form
// Viewer · Configuration · Settings), multi-page surveys, ten question types,
// and a Draft → Published → Clone lifecycle with an edit lock.
// Phase 1 ships the shell + Details + Builder + Viewer + lifecycle;
// Configuration (logic rules) and Settings (email dispatch) are Phases 2–3.

// ── Model ───────────────────────────────────────────────────────────────────
export type QuestionType =
  | 'SINGLE_SELECT' | 'MULTIPLE_SELECT' | 'RATING_SCALE' | 'GRID_RATING'
  | 'RADIO' | 'CHECKBOXES' | 'SINGLE_LINE' | 'TEXTAREA' | 'SLIDING_SCALE' | 'STATIC_TEXT'

export interface Option { text: string; value: number }

export interface Question {
  id: string
  text: string
  type: QuestionType
  label: string
  required: boolean
  options?: Option[]
  gridRows?: string[]
  min?: number
  max?: number
  step?: number
}

export interface Page {
  id: string // PG-XXX
  label: string
  title: string
  description: string
  showDescription: boolean
  questions: Question[]
}

export interface SurveyV2 {
  id: string
  name: string
  summary: string
  status: 'DRAFT' | 'ACTIVE' | 'CLOSED'
  touchpoint: string
  participantType: 'ALL' | 'CANDIDATES' | 'NEW_HIRES' | 'EMPLOYEES'
  audienceSource: string
  cycle: string
  startDate: string
  endDate: string
  isAnonymous: boolean
  pages: Page[]
  updatedAt: string
}

export const SURVEYS_V2_KEY = 'olivia.surveysV2'

const TOUCHPOINTS = ['Application', 'Post-interview', 'Offer', 'Onboarding', 'Candidate withdrew', 'Not selected']

const TYPE_OPTIONS: { value: QuestionType; label: string }[] = [
  { value: 'SINGLE_SELECT', label: 'Single Select' },
  { value: 'MULTIPLE_SELECT', label: 'Multiple Select' },
  { value: 'RATING_SCALE', label: 'Rating Scale' },
  { value: 'GRID_RATING', label: 'Grid Rating Scale' },
  { value: 'RADIO', label: 'Radio Buttons' },
  { value: 'CHECKBOXES', label: 'Checkboxes' },
  { value: 'SINGLE_LINE', label: 'Single-line Input' },
  { value: 'TEXTAREA', label: 'Text Area' },
  { value: 'SLIDING_SCALE', label: 'Sliding Scale' },
  { value: 'STATIC_TEXT', label: 'Static Text' },
]

const RATING_DEFAULTS: Option[] = [
  { value: 1, text: 'Strongly Disagree' },
  { value: 2, text: 'Disagree' },
  { value: 3, text: 'Neutral' },
  { value: 4, text: 'Agree' },
  { value: 5, text: 'Strongly Agree' },
]

const hasOptions = (t: QuestionType) =>
  ['SINGLE_SELECT', 'MULTIPLE_SELECT', 'RATING_SCALE', 'GRID_RATING', 'RADIO', 'CHECKBOXES'].includes(t)

function newQuestion(type: QuestionType): Question {
  const q: Question = { id: uid(), text: '', type, label: '', required: false }
  if (type === 'RATING_SCALE' || type === 'GRID_RATING') q.options = RATING_DEFAULTS.map((o) => ({ ...o }))
  else if (hasOptions(type)) q.options = [{ text: 'Option 1', value: 1 }, { text: 'Option 2', value: 2 }]
  if (type === 'GRID_RATING') q.gridRows = ['Row 1', 'Row 2']
  if (type === 'SLIDING_SCALE') { q.min = 1; q.max = 10; q.step = 1 }
  return q
}

function pageIdFrom(label: string): string {
  const slug = label.trim().toUpperCase().replace(/[^A-Z0-9]+/g, '').slice(0, 8)
  return `PG-${slug || 'PAGE'}`
}

function newPage(label: string): Page {
  return { id: pageIdFrom(label), label, title: label, description: '', showDescription: false, questions: [] }
}

function freshSurvey(): SurveyV2 {
  const page = newPage('Welcome')
  page.questions = [{ ...newQuestion('RATING_SCALE'), text: 'How would you rate your experience so far?', label: 'overall' }]
  return {
    id: uid(), name: 'Untitled survey', summary: '', status: 'DRAFT',
    touchpoint: 'Post-interview', participantType: 'CANDIDATES', audienceSource: '', cycle: '',
    startDate: '', endDate: '', isAnonymous: true,
    pages: [page], updatedAt: new Date().toISOString(),
  }
}

// One-time migration: old single-page builder surveys → V2 shape.
function migrateOld(): SurveyV2[] {
  try {
    const raw = localStorage.getItem('olivia.surveys')
    if (!raw) return [freshSurvey()]
    const olds = JSON.parse(raw)
    if (!Array.isArray(olds) || olds.length === 0) return [freshSurvey()]
    const mapType = (t: string): QuestionType =>
      t === 'rating' ? 'RATING_SCALE' : t === 'nps' ? 'SLIDING_SCALE' : t === 'single' ? 'SINGLE_SELECT'
      : t === 'multi' ? 'MULTIPLE_SELECT' : t === 'yesno' ? 'RADIO' : t === 'long' ? 'TEXTAREA' : 'SINGLE_LINE'
    return olds.map((o: { id?: string; name?: string; stage?: string; intro?: string; questions?: { type: string; title: string; required?: boolean; options?: string[] }[] }) => {
      const page = newPage('Main')
      page.description = o.intro ?? ''
      page.showDescription = !!o.intro
      page.questions = (o.questions ?? []).map((q) => {
        const nq = newQuestion(mapType(q.type))
        nq.text = q.title
        nq.required = !!q.required
        if (q.type === 'nps') { nq.min = 0; nq.max = 10; nq.step = 1 }
        if (q.options && hasOptions(nq.type)) nq.options = q.options.map((t: string, i: number) => ({ text: t, value: i + 1 }))
        if (q.type === 'yesno') nq.options = [{ text: 'Yes', value: 1 }, { text: 'No', value: 0 }]
        return nq
      })
      return {
        id: o.id ?? uid(), name: o.name ?? 'Untitled survey', summary: '', status: 'DRAFT' as const,
        touchpoint: o.stage ?? 'Post-interview', participantType: 'CANDIDATES' as const,
        audienceSource: '', cycle: '', startDate: '', endDate: '', isAnonymous: true,
        pages: [page], updatedAt: new Date().toISOString(),
      }
    })
  } catch {
    return [freshSurvey()]
  }
}

const questionCount = (s: SurveyV2) => s.pages.reduce((n, p) => n + p.questions.length, 0)

function StatusPill({ status }: { status: SurveyV2['status'] }) {
  const cls = status === 'ACTIVE' ? 'badge--ok' : status === 'DRAFT' ? 'badge--warn' : 'badge--neutral'
  const label = status === 'ACTIVE' ? 'Published' : status === 'DRAFT' ? 'Draft' : 'Closed'
  return <span className={`badge ${cls}`}>{label}</span>
}

// ── Library ─────────────────────────────────────────────────────────────────
function Library({ surveys, onEdit, onNew, onClone, onDelete }: {
  surveys: SurveyV2[]
  onEdit: (id: string) => void
  onNew: () => void
  onClone: (id: string) => void
  onDelete: (id: string) => void
}) {
  return (
    <div className="card">
      <div className="card__head">
        <h3 style={{ fontSize: 15 }}>Survey library</h3>
        <button className="btn btn--primary btn--sm" onClick={onNew}>New survey</button>
      </div>
      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>Survey</th>
              <th>Status</th>
              <th>Touchpoint</th>
              <th className="t-right">Pages</th>
              <th className="t-right">Questions</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {surveys.map((s) => (
              <tr key={s.id}>
                <td>
                  <div className="who__name">{s.name}</div>
                  {s.summary ? <div className="who__sub">{s.summary}</div> : null}
                </td>
                <td><StatusPill status={s.status} /></td>
                <td className="t-muted">{s.touchpoint}</td>
                <td className="t-right t-num">{s.pages.length}</td>
                <td className="t-right t-num">{questionCount(s)}</td>
                <td className="t-right">
                  <div style={{ display: 'inline-flex', gap: 6 }}>
                    <button className="btn btn--outline btn--sm" onClick={() => onEdit(s.id)}>{s.status === 'DRAFT' ? 'Edit' : 'Open'}</button>
                    <button className="btn btn--ghost btn--sm" onClick={() => onClone(s.id)}>Clone</button>
                    {s.status === 'DRAFT' && (
                      <ConfirmButton label="Delete" confirmLabel="Delete survey?" onConfirm={() => onDelete(s.id)} disabled={surveys.length <= 1} />
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

// ── Details tab ─────────────────────────────────────────────────────────────
function Row({ children }: { children: React.ReactNode }) {
  return <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 14 }}>{children}</div>
}

function L({ label, help, required, children }: { label: string; help?: string; required?: boolean; children: React.ReactNode }) {
  return (
    <label style={{ display: 'block' }}>
      <div className="eyebrow" style={{ marginBottom: 6 }}>{label}{required ? <span style={{ color: 'var(--bofa-red)' }}> *</span> : null}</div>
      {children}
      {help ? <div style={{ fontSize: 11, color: 'var(--ink-5)', marginTop: 4 }}>{help}</div> : null}
    </label>
  )
}

function Toggle({ on, onChange, disabled, labels = ['On', 'Off'] }: { on: boolean; onChange: (v: boolean) => void; disabled?: boolean; labels?: [string, string] | string[] }) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={() => onChange(!on)}
      aria-pressed={on}
      style={{
        display: 'inline-flex', alignItems: 'center', gap: 8, border: '1px solid var(--line)',
        background: 'var(--app-panel)', borderRadius: 999, padding: '5px 10px 5px 6px', cursor: disabled ? 'default' : 'pointer', font: 'inherit', fontSize: 12.5,
      }}
    >
      <span style={{ width: 30, height: 16, borderRadius: 999, background: on ? 'var(--c-green)' : 'var(--ink-5)', position: 'relative', transition: 'background .15s ease' }}>
        <span style={{ position: 'absolute', top: 2, left: on ? 16 : 2, width: 12, height: 12, borderRadius: '50%', background: '#fff', transition: 'left .15s ease' }} />
      </span>
      {on ? labels[0] : labels[1]}
    </button>
  )
}

function DetailsTab({ s, patch, locked, onSaved, onNext }: {
  s: SurveyV2
  patch: (p: Partial<SurveyV2>) => void
  locked: boolean
  onSaved: () => void
  onNext: () => void
}) {
  return (
    <div className="card">
      <div className="card__body" style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
        <Row>
          <L label="Survey name" help="Create a name for the survey." required>
            <input className="input" value={s.name} disabled={locked} onChange={(e) => patch({ name: e.target.value })} />
          </L>
          <L label="Summary" help="Add a short description of the survey.">
            <input className="input" value={s.summary} disabled={locked} onChange={(e) => patch({ summary: e.target.value })} />
          </L>
          <L label="Survey status">
            <Toggle on={s.status === 'ACTIVE'} disabled={locked} labels={['Active', 'Draft']} onChange={(v) => patch({ status: v ? 'ACTIVE' : 'DRAFT' })} />
          </L>
        </Row>
        <div style={{ borderTop: '1px solid var(--line)' }} />
        <Row>
          <L label="Touchpoint" help="Where in the candidate journey this survey is sent." required>
            <select className="select" value={s.touchpoint} disabled={locked} onChange={(e) => patch({ touchpoint: e.target.value })}>
              {TOUCHPOINTS.map((t) => <option key={t} value={t}>{t}</option>)}
            </select>
          </L>
          <L label="Participant type">
            <select className="select" value={s.participantType} disabled={locked} onChange={(e) => patch({ participantType: e.target.value as SurveyV2['participantType'] })}>
              <option value="ALL">All</option>
              <option value="CANDIDATES">Candidates</option>
              <option value="NEW_HIRES">New hires</option>
              <option value="EMPLOYEES">Employees</option>
            </select>
          </L>
          <L label="Audience source">
            <input className="input" value={s.audienceSource} disabled={locked} onChange={(e) => patch({ audienceSource: e.target.value })} placeholder="Pipeline stage, pool, CSV…" />
          </L>
          <L label="Cycle">
            <input className="input" value={s.cycle} disabled={locked} onChange={(e) => patch({ cycle: e.target.value })} placeholder="2026-Q3" />
          </L>
          <L label="Start date">
            <input className="input" type="date" value={s.startDate} disabled={locked} onChange={(e) => patch({ startDate: e.target.value })} />
          </L>
          <L label="End date">
            <input className="input" type="date" value={s.endDate} disabled={locked} onChange={(e) => patch({ endDate: e.target.value })} />
          </L>
        </Row>
        <div>
          <L label="Anonymous responses">
            <Toggle on={s.isAnonymous} disabled={locked} labels={['On', 'Off']} onChange={(v) => patch({ isAnonymous: v })} />
          </L>
        </div>
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <button className="btn btn--outline btn--sm" onClick={onSaved} disabled={locked}>Save</button>
          <button className="btn btn--primary btn--sm" onClick={onNext}>Next</button>
        </div>
      </div>
    </div>
  )
}

// ── Form Builder tab ────────────────────────────────────────────────────────
function OptionsEditor({ q, onChange, locked }: { q: Question; onChange: (q: Question) => void; locked: boolean }) {
  const options = q.options ?? []
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <div className="eyebrow">Options</div>
      {options.map((o, i) => (
        <div key={i} style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <input className="input" value={o.text} disabled={locked} style={{ flex: 1 }}
            onChange={(e) => onChange({ ...q, options: options.map((x, j) => (j === i ? { ...x, text: e.target.value } : x)) })} />
          <input className="input t-num" type="number" value={o.value} disabled={locked} style={{ width: 74 }} aria-label="Option value"
            onChange={(e) => onChange({ ...q, options: options.map((x, j) => (j === i ? { ...x, value: Number(e.target.value) } : x)) })} />
          <button className="btn btn--ghost btn--sm" disabled={locked || options.length <= 1} aria-label="Remove option"
            onClick={() => onChange({ ...q, options: options.filter((_, j) => j !== i) })}>×</button>
        </div>
      ))}
      <button className="btn btn--outline btn--sm" style={{ alignSelf: 'flex-start' }} disabled={locked}
        onClick={() => onChange({ ...q, options: [...options, { text: `Option ${options.length + 1}`, value: options.length + 1 }] })}>
        + Add option
      </button>
    </div>
  )
}

function GridRowsEditor({ q, onChange, locked }: { q: Question; onChange: (q: Question) => void; locked: boolean }) {
  const rows = q.gridRows ?? []
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <div className="eyebrow">Rows (sub-questions)</div>
      {rows.map((r, i) => (
        <div key={i} style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <input className="input" value={r} disabled={locked} style={{ flex: 1 }}
            onChange={(e) => onChange({ ...q, gridRows: rows.map((x, j) => (j === i ? e.target.value : x)) })} />
          <button className="btn btn--ghost btn--sm" disabled={locked || rows.length <= 1} aria-label="Remove row"
            onClick={() => onChange({ ...q, gridRows: rows.filter((_, j) => j !== i) })}>×</button>
        </div>
      ))}
      <button className="btn btn--outline btn--sm" style={{ alignSelf: 'flex-start' }} disabled={locked}
        onClick={() => onChange({ ...q, gridRows: [...rows, `Row ${rows.length + 1}`] })}>
        + Add row
      </button>
    </div>
  )
}

function QuestionCard({ q, index, locked, onChange, onRemove }: {
  q: Question
  index: number
  locked: boolean
  onChange: (q: Question) => void
  onRemove: () => void
}) {
  return (
    <div className="card" style={{ marginBottom: 12 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '9px 14px', borderBottom: '1px solid var(--line)' }}>
        <span className="t-num" style={{ width: 30, height: 22, borderRadius: 999, background: 'var(--navy-050)', color: 'var(--bofa-navy)', fontSize: 11.5, fontWeight: 700, display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>
          Q{index + 1}
        </span>
        <select className="select" value={q.type} disabled={locked} style={{ width: 190, height: 30, padding: '2px 8px', fontSize: 12.5 }}
          onChange={(e) => {
            const t = e.target.value as QuestionType
            const base = newQuestion(t)
            onChange({ ...base, id: q.id, text: q.text, label: q.label, required: q.required })
          }}>
          {TYPE_OPTIONS.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
        </select>
        <div style={{ flex: 1 }} />
        <button
          type="button"
          className="badge"
          aria-pressed={q.required}
          disabled={locked}
          onClick={() => onChange({ ...q, required: !q.required })}
          style={{ cursor: locked ? 'default' : 'pointer', border: '1px solid var(--line)', background: q.required ? 'var(--navy-050)' : 'var(--app-panel)', color: q.required ? 'var(--bofa-navy)' : 'var(--ink-4)', fontWeight: 600 }}
        >
          Required
        </button>
        <button className="btn btn--ghost btn--sm" disabled={locked} onClick={onRemove} aria-label="Delete question">×</button>
      </div>
      <div className="card__body" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {q.type === 'STATIC_TEXT' ? (
          <textarea className="input" rows={2} value={q.text} disabled={locked} placeholder="Instructional text shown to respondents"
            onChange={(e) => onChange({ ...q, text: e.target.value })} style={{ resize: 'vertical' }} />
        ) : (
          <input className="input" value={q.text} disabled={locked} placeholder="Question text"
            onChange={(e) => onChange({ ...q, text: e.target.value })} style={{ fontSize: 14, fontWeight: 500 }} />
        )}
        {q.type !== 'STATIC_TEXT' && (
          <input className="input" value={q.label} disabled={locked} placeholder="Question label (short identifier, e.g. mgr_support)"
            onChange={(e) => onChange({ ...q, label: e.target.value })} style={{ fontSize: 12.5, width: 320 }} />
        )}
        {q.type === 'GRID_RATING' && <GridRowsEditor q={q} onChange={onChange} locked={locked} />}
        {hasOptions(q.type) && <OptionsEditor q={q} onChange={onChange} locked={locked} />}
        {q.type === 'SLIDING_SCALE' && (
          <div style={{ display: 'flex', gap: 12 }}>
            <L label="Min"><input className="input t-num" type="number" value={q.min ?? 1} disabled={locked} style={{ width: 90 }} onChange={(e) => onChange({ ...q, min: Number(e.target.value) })} /></L>
            <L label="Max"><input className="input t-num" type="number" value={q.max ?? 10} disabled={locked} style={{ width: 90 }} onChange={(e) => onChange({ ...q, max: Number(e.target.value) })} /></L>
            <L label="Step"><input className="input t-num" type="number" value={q.step ?? 1} disabled={locked} style={{ width: 90 }} onChange={(e) => onChange({ ...q, step: Number(e.target.value) })} /></L>
          </div>
        )}
      </div>
    </div>
  )
}

function BuilderTab({ s, patch, locked }: { s: SurveyV2; patch: (p: Partial<SurveyV2>) => void; locked: boolean }) {
  const [pageIdx, setPageIdx] = useState(0)
  const [addType, setAddType] = useState<QuestionType>('RATING_SCALE')
  const page = s.pages[Math.min(pageIdx, s.pages.length - 1)]

  function patchPage(p: Partial<Page>) {
    patch({ pages: s.pages.map((x, i) => (i === pageIdx ? { ...x, ...p } : x)) })
  }
  function movePage(i: number, dir: -1 | 1) {
    const j = i + dir
    if (j < 0 || j >= s.pages.length) return
    const pages = [...s.pages]
    ;[pages[i], pages[j]] = [pages[j], pages[i]]
    patch({ pages })
    setPageIdx(j)
  }

  if (!page) return null

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '210px 1fr', gap: 16, alignItems: 'start' }}>
      {/* Page rail */}
      <div className="card" style={{ padding: 8, position: 'sticky', top: 12 }}>
        <div className="eyebrow" style={{ padding: '6px 8px' }}>Pages</div>
        {s.pages.map((p, i) => (
          <div key={p.id} style={{ display: 'flex', alignItems: 'center', gap: 4, marginBottom: 2 }}>
            <button
              onClick={() => setPageIdx(i)}
              style={{
                flex: 1, textAlign: 'left', border: 0, cursor: 'pointer', borderRadius: 'var(--ra-2)',
                padding: '8px 10px', font: 'inherit',
                background: i === pageIdx ? 'var(--navy-050)' : 'transparent',
                color: i === pageIdx ? 'var(--bofa-navy)' : 'var(--ink-1)',
              }}
            >
              <div style={{ fontSize: 13, fontWeight: i === pageIdx ? 600 : 500 }}>{p.label}</div>
              <div style={{ fontSize: 10.5, color: 'var(--ink-5)' }}>{p.id} · {p.questions.length} q</div>
            </button>
            {!locked && i === pageIdx && (
              <span style={{ display: 'inline-flex', flexDirection: 'column' }}>
                <button className="btn btn--ghost btn--sm" style={{ padding: '0 6px', height: 16, fontSize: 10 }} disabled={i === 0} onClick={() => movePage(i, -1)} aria-label="Move page up">↑</button>
                <button className="btn btn--ghost btn--sm" style={{ padding: '0 6px', height: 16, fontSize: 10 }} disabled={i === s.pages.length - 1} onClick={() => movePage(i, 1)} aria-label="Move page down">↓</button>
              </span>
            )}
          </div>
        ))}
        {!locked && (
          <button className="btn btn--outline btn--sm" style={{ width: '100%', marginTop: 8 }}
            onClick={() => { patch({ pages: [...s.pages, newPage(`Page ${s.pages.length + 1}`)] }); setPageIdx(s.pages.length) }}>
            + Add page
          </button>
        )}
      </div>

      {/* Page editor */}
      <div>
        <div className="card" style={{ marginBottom: 12 }}>
          <div className="card__body" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'flex-end' }}>
              <L label="Page label"><input className="input" value={page.label} disabled={locked} style={{ width: 200 }} onChange={(e) => patchPage({ label: e.target.value })} /></L>
              <L label="Page title"><input className="input" value={page.title} disabled={locked} style={{ width: 280 }} onChange={(e) => patchPage({ title: e.target.value })} /></L>
              <div style={{ flex: 1 }} />
              {!locked && s.pages.length > 1 && (
                <ConfirmButton label="Delete page" confirmLabel="Delete page + questions?" onConfirm={() => { patch({ pages: s.pages.filter((_, i) => i !== pageIdx) }); setPageIdx(0) }} />
              )}
            </div>
            <div style={{ display: 'flex', gap: 12, alignItems: 'flex-end' }}>
              <L label="Page description">
                <input className="input" value={page.description} disabled={locked} style={{ width: 420, maxWidth: '100%' }} onChange={(e) => patchPage({ description: e.target.value })} />
              </L>
              <L label="Show description">
                <Toggle on={page.showDescription} disabled={locked} onChange={(v) => patchPage({ showDescription: v })} />
              </L>
            </div>
          </div>
        </div>

        {page.questions.length === 0 ? (
          <div className="card" style={{ marginBottom: 12 }}>
            <div className="card__body" style={{ padding: '34px 20px', textAlign: 'center', color: 'var(--ink-4)', fontSize: 13 }}>
              No questions on this page yet — add one below.
            </div>
          </div>
        ) : (
          page.questions.map((q, i) => (
            <QuestionCard
              key={q.id}
              q={q}
              index={i}
              locked={locked}
              onChange={(nq) => patchPage({ questions: page.questions.map((x) => (x.id === q.id ? nq : x)) })}
              onRemove={() => patchPage({ questions: page.questions.filter((x) => x.id !== q.id) })}
            />
          ))
        )}

        {!locked && (
          <div className="card">
            <div className="card__body" style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
              <span className="eyebrow">Add question</span>
              <select className="select" value={addType} style={{ width: 210 }} onChange={(e) => setAddType(e.target.value as QuestionType)}>
                {TYPE_OPTIONS.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
              </select>
              <button className="btn btn--primary btn--sm" onClick={() => patchPage({ questions: [...page.questions, newQuestion(addType)] })}>
                Add question
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

// ── Form Viewer tab ─────────────────────────────────────────────────────────
function RatingTiles({ options }: { options: Option[] }) {
  const [sel, setSel] = useState<number | null>(null)
  return (
    <div style={{ display: 'grid', gridTemplateColumns: `repeat(${Math.max(options.length, 1)}, 1fr)`, gap: 8 }}>
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          onClick={() => setSel(o.value)}
          style={{
            border: sel === o.value ? '2px solid var(--c-blue)' : '1px solid var(--line)',
            background: sel === o.value ? 'var(--tint-blue)' : 'var(--app-panel)',
            borderRadius: 'var(--ra-2)', padding: '12px 8px', cursor: 'pointer', font: 'inherit',
            // padding compensates the border delta so tiles never change size
            paddingTop: sel === o.value ? 11 : 12, paddingBottom: sel === o.value ? 11 : 12,
          }}
        >
          <div className="t-num" style={{ fontSize: 16, fontWeight: 700, color: 'var(--ink-0)' }}>{o.value}</div>
          <div style={{ fontSize: 11, color: 'var(--ink-4)', marginTop: 3 }}>{o.text}</div>
        </button>
      ))}
    </div>
  )
}

function Slider({ q }: { q: Question }) {
  const [v, setV] = useState(q.min ?? 1)
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
      <input type="range" min={q.min ?? 1} max={q.max ?? 10} step={q.step ?? 1} value={v} onChange={(e) => setV(Number(e.target.value))} style={{ flex: 1 }} />
      <span className="t-num badge badge--info">{v}</span>
    </div>
  )
}

function ViewerQuestion({ q, n }: { q: Question; n: number | null }) {
  return (
    <div style={{ display: 'flex', gap: 12, marginBottom: 20 }}>
      {n !== null ? (
        <span className="t-num" style={{ width: 26, height: 26, borderRadius: '50%', background: 'var(--bofa-navy)', color: '#fff', fontSize: 12, fontWeight: 700, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, marginTop: 1 }}>
          {n}
        </span>
      ) : <span style={{ width: 26, flexShrink: 0 }} />}
      <div style={{ flex: 1, minWidth: 0 }}>
        {q.type === 'STATIC_TEXT' ? (
          <div style={{ fontSize: 13.5, color: 'var(--ink-2)', lineHeight: 1.6, whiteSpace: 'pre-line' }}>{q.text || 'Static text'}</div>
        ) : (
          <>
            <div style={{ fontSize: 14, fontWeight: 500, color: 'var(--ink-0)', marginBottom: 10 }}>
              {q.text || <span style={{ color: 'var(--ink-5)' }}>Untitled question</span>}
              {q.required ? <span style={{ color: 'var(--bofa-red)' }}> *</span> : null}
            </div>
            {q.type === 'RATING_SCALE' && <RatingTiles options={q.options ?? []} />}
            {q.type === 'GRID_RATING' && (
              <div className="table-wrap">
                <table className="data-table" style={{ fontSize: 12.5 }}>
                  <thead>
                    <tr>
                      <th />
                      {(q.options ?? []).map((o) => <th key={o.value} style={{ textAlign: 'center' }}>{o.text}</th>)}
                    </tr>
                  </thead>
                  <tbody>
                    {(q.gridRows ?? []).map((r) => (
                      <tr key={r}>
                        <td className="t-strong">{r}</td>
                        {(q.options ?? []).map((o) => (
                          <td key={o.value} style={{ textAlign: 'center' }}><input type="radio" name={`${q.id}-${r}`} /></td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {(q.type === 'SINGLE_SELECT' || q.type === 'RADIO') && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
                {(q.options ?? []).map((o) => (
                  <label key={o.value} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: 'var(--ink-1)', cursor: 'pointer' }}>
                    <input type="radio" name={q.id} />{o.text}
                  </label>
                ))}
              </div>
            )}
            {(q.type === 'MULTIPLE_SELECT' || q.type === 'CHECKBOXES') && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
                {(q.options ?? []).map((o) => (
                  <label key={o.value} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: 'var(--ink-1)', cursor: 'pointer' }}>
                    <input type="checkbox" />{o.text}
                  </label>
                ))}
              </div>
            )}
            {q.type === 'SINGLE_LINE' && <input className="input" placeholder="Your answer" style={{ maxWidth: 420 }} />}
            {q.type === 'TEXTAREA' && <textarea className="input" rows={3} placeholder="Your answer" style={{ resize: 'vertical' }} />}
            {q.type === 'SLIDING_SCALE' && <Slider q={q} />}
          </>
        )}
      </div>
    </div>
  )
}

function ViewerTab({ s }: { s: SurveyV2 }) {
  let n = 0
  return (
    <div className="card" style={{ maxWidth: 760 }}>
      <div style={{ background: 'var(--bofa-navy)', color: '#fff', padding: '16px 22px' }}>
        <div style={{ fontSize: 11, opacity: 0.8, letterSpacing: '0.04em', textTransform: 'uppercase' }}>Preview · {s.touchpoint}</div>
        <div style={{ fontFamily: 'var(--font-display)', fontSize: 20, marginTop: 2 }}>{s.name}</div>
        {s.summary ? <div style={{ fontSize: 12.5, opacity: 0.85, marginTop: 4 }}>{s.summary}</div> : null}
      </div>
      <div className="card__body" style={{ padding: '20px 22px' }}>
        {s.pages.map((p) => (
          <section key={p.id} style={{ marginBottom: 26 }}>
            <h3 style={{ fontSize: 17, margin: '0 0 4px' }}>{p.title || p.label}</h3>
            {p.showDescription && p.description ? (
              <p style={{ fontSize: 12.5, color: 'var(--ink-4)', margin: '0 0 14px', lineHeight: 1.5 }}>{p.description}</p>
            ) : <div style={{ height: 10 }} />}
            {p.questions.map((q) => {
              const num = q.type === 'STATIC_TEXT' ? null : ++n
              return <ViewerQuestion key={q.id} q={q} n={num} />
            })}
            {p.questions.length === 0 && <div style={{ fontSize: 12.5, color: 'var(--ink-5)' }}>No questions on this page.</div>}
          </section>
        ))}
      </div>
    </div>
  )
}

// ── Phase 2/3 placeholders ──────────────────────────────────────────────────
function ComingSoon({ title, line }: { title: string; line: string }) {
  return (
    <div className="card">
      <div className="card__body" style={{ padding: '48px 20px', textAlign: 'center' }}>
        <div style={{ fontFamily: 'var(--font-display)', fontSize: 19, color: 'var(--ink-0)' }}>{title}</div>
        <div style={{ fontSize: 13, color: 'var(--ink-4)', margin: '8px auto 0', maxWidth: 460, lineHeight: 1.6 }}>{line}</div>
      </div>
    </div>
  )
}

// ── Editor shell ────────────────────────────────────────────────────────────
type EditorTab = 'details' | 'builder' | 'viewer' | 'logic' | 'settings'

const EDITOR_TABS: { key: EditorTab; label: string }[] = [
  { key: 'details', label: 'Details' },
  { key: 'builder', label: 'Form Builder' },
  { key: 'viewer', label: 'Form Viewer' },
  { key: 'logic', label: 'Configuration' },
  { key: 'settings', label: 'Settings' },
]

function Editor({ s, patch, onBack, onClone, onDelete }: {
  s: SurveyV2
  patch: (p: Partial<SurveyV2>) => void
  onBack: () => void
  onClone: () => void
  onDelete: () => void
}) {
  const { toastMsg } = useStore()
  const [tab, setTab] = useState<EditorTab>('details')
  const locked = s.status !== 'DRAFT'

  function publish() {
    const problems: string[] = []
    if (!s.name.trim()) problems.push('Survey needs a name.')
    if (s.pages.length === 0) problems.push('Add at least one page.')
    if (questionCount(s) === 0) problems.push('Add at least one question.')
    if (problems.length) {
      toastMsg(problems[0])
      return
    }
    patch({ status: 'ACTIVE' })
    toastMsg(`"${s.name}" published`)
  }

  return (
    <div>
      {/* Header: breadcrumb, title, status, lifecycle actions */}
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12, marginBottom: 14 }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 12, color: 'var(--ink-4)', marginBottom: 4 }}>
            <button className="link" onClick={onBack} style={{ border: 0, background: 'none', padding: 0 }}>Surveys</button>
            {' '}/ {s.name || 'Untitled'}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <h2 style={{ fontSize: 22, margin: 0 }}>{s.name || 'Untitled survey'}</h2>
            <StatusPill status={s.status} />
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
          {s.status === 'DRAFT' ? (
            <>
              <ConfirmButton label="Delete" confirmLabel="Delete survey?" onConfirm={onDelete} />
              <button className="btn btn--ghost btn--sm" onClick={onBack}>Cancel</button>
              <button className="btn btn--primary btn--sm" onClick={publish}>Publish</button>
            </>
          ) : (
            <>
              <button className="btn btn--ghost btn--sm" onClick={onBack}>Cancel</button>
              <button className="btn btn--primary btn--sm" onClick={onClone}>Clone</button>
            </>
          )}
        </div>
      </div>

      <div className="tabs" style={{ marginBottom: 18 }}>
        {EDITOR_TABS.map((t) => (
          <button key={t.key} className="tab" aria-selected={tab === t.key} onClick={() => setTab(t.key)}>
            {t.label}
          </button>
        ))}
      </div>

      {locked && tab !== 'viewer' && (
        <div style={{ fontSize: 12, color: 'var(--ink-4)', marginBottom: 12 }}>
          Published surveys are locked for editing — Clone to make changes.
        </div>
      )}

      {tab === 'details' && (
        <DetailsTab s={s} patch={patch} locked={locked} onSaved={() => toastMsg('Survey saved')} onNext={() => setTab('builder')} />
      )}
      {tab === 'builder' && <BuilderTab s={s} patch={patch} locked={locked} />}
      {tab === 'viewer' && <ViewerTab s={s} />}
      {tab === 'logic' && (
        <ComingSoon title="Configuration" line="Conditional logic — show/hide, require, skip-to, and text piping rules with AND/OR condition groups — lands here in the next phase." />
      )}
      {tab === 'settings' && (
        <ComingSoon title="Settings" line="Email template assignments (from Communication) for invitations, reminders, and thank-yous, plus the dispatch-readiness checklist — the phase after logic." />
      )}
    </div>
  )
}

// ── Studio root ─────────────────────────────────────────────────────────────
export default function SurveyStudio() {
  const [surveys, setSurveys] = usePersistentState<SurveyV2[]>(SURVEYS_V2_KEY, migrateOld())
  const [editingId, setEditingId] = useState<string | null>(null)
  const { toastMsg } = useStore()

  const editing = useMemo(() => surveys.find((x) => x.id === editingId) ?? null, [surveys, editingId])

  function patch(id: string, p: Partial<SurveyV2>) {
    setSurveys((prev) => prev.map((x) => (x.id === id ? { ...x, ...p, updatedAt: new Date().toISOString() } : x)))
  }
  function clone(id: string) {
    const src = surveys.find((x) => x.id === id)
    if (!src) return
    const copy: SurveyV2 = {
      ...src,
      id: uid(),
      name: `${src.name} Copy`,
      status: 'DRAFT',
      pages: src.pages.map((p) => ({ ...p, questions: p.questions.map((q) => ({ ...q, id: uid(), options: q.options?.map((o) => ({ ...o })), gridRows: q.gridRows ? [...q.gridRows] : undefined })) })),
      updatedAt: new Date().toISOString(),
    }
    setSurveys((prev) => [...prev, copy])
    setEditingId(copy.id)
    toastMsg(`Cloned as "${copy.name}"`)
  }
  function remove(id: string) {
    setSurveys((prev) => {
      const next = prev.filter((x) => x.id !== id)
      return next.length ? next : [freshSurvey()]
    })
    setEditingId(null)
  }

  if (editing) {
    return (
      <Editor
        s={editing}
        patch={(p) => patch(editing.id, p)}
        onBack={() => setEditingId(null)}
        onClone={() => clone(editing.id)}
        onDelete={() => remove(editing.id)}
      />
    )
  }

  return (
    <Library
      surveys={surveys}
      onEdit={setEditingId}
      onNew={() => { const s = freshSurvey(); setSurveys((prev) => [...prev, s]); setEditingId(s.id) }}
      onClone={clone}
      onDelete={remove}
    />
  )
}
