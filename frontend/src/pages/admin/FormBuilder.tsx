import { useRef, useState } from 'react'
import ConfirmButton from '@/components/ConfirmButton'
import { usePersistentState, uid } from './builderStore'

// Event-intake form builder. Admins design the form recruiters fill when
// creating an event: system fields (what the API needs) are locked at the top;
// everything else — registration rules, audience targeting, internal ops — is
// composable from the palette, Survey-Builder style. The Events "New event"
// wizard renders whatever is designed here.

export type IntakeFieldType = 'short' | 'long' | 'single' | 'multi' | 'yesno' | 'date' | 'number'

export interface IntakeField {
  id: string
  type: IntakeFieldType
  label: string
  help?: string
  required: boolean
  options?: string[]
}

export interface IntakeForm {
  title: string
  fields: IntakeField[]
  updatedAt: string
}

export const INTAKE_KEY = 'olivia.eventIntakeForm'

// System fields — always collected, mapped straight to the events API.
export const SYSTEM_FIELDS: { label: string; hint: string }[] = [
  { label: 'Event name', hint: 'Short text' },
  { label: 'Event type', hint: 'Career fair · Campus drive · Info session · Hiring event · Webinar' },
  { label: 'Date & time', hint: 'Date + start time' },
  { label: 'Location / link', hint: 'Campus & room, or meeting URL' },
]

const PALETTE: { type: IntakeFieldType; label: string; hint: string; glyph: string }[] = [
  { type: 'number', label: 'Number', hint: 'Capacity, headcount…', glyph: '#' },
  { type: 'date', label: 'Date', hint: 'Deadlines', glyph: '▦' },
  { type: 'yesno', label: 'Yes / No', hint: 'Waitlist, approval…', glyph: '⇄' },
  { type: 'single', label: 'Single choice', hint: 'Pick one', glyph: '◉' },
  { type: 'multi', label: 'Multiple choice', hint: 'Pick many', glyph: '☑' },
  { type: 'short', label: 'Short text', hint: 'One line', glyph: '—' },
  { type: 'long', label: 'Long text', hint: 'Paragraph', glyph: '¶' },
]

const TYPE_LABEL: Record<IntakeFieldType, string> = {
  short: 'Short text', long: 'Long text', single: 'Single choice', multi: 'Multiple choice',
  yesno: 'Yes / No', date: 'Date', number: 'Number',
}

function defaultLabel(t: IntakeFieldType): string {
  switch (t) {
    case 'number': return 'Untitled number'
    case 'date': return 'Untitled date'
    case 'yesno': return 'Untitled yes/no'
    case 'single': return 'Untitled choice'
    case 'multi': return 'Select all that apply'
    case 'short': return 'Untitled question'
    case 'long': return 'Untitled question'
  }
}

function newField(t: IntakeFieldType): IntakeField {
  const f: IntakeField = { id: uid(), type: t, label: defaultLabel(t), required: false }
  if (t === 'single' || t === 'multi') f.options = ['Option 1', 'Option 2']
  return f
}

/**
 * The research-backed starter intake: registration controls (Handshake-style
 * capacity/deadline/waitlist), audience targeting, and internal ops
 * (Yello-style budget & expected attendance). All editable or removable.
 */
export function defaultIntakeForm(): IntakeForm {
  return {
    title: 'Event intake',
    updatedAt: new Date().toISOString(),
    fields: [
      { id: uid(), type: 'number', label: 'Registration capacity', help: 'Maximum sign-ups before the waitlist kicks in', required: true },
      { id: uid(), type: 'date', label: 'Registration deadline', required: false },
      { id: uid(), type: 'yesno', label: 'Enable waitlist when full?', required: false },
      { id: uid(), type: 'multi', label: 'Target audience', help: 'Who should this event reach?', required: false, options: ['Engineering & CS', 'Finance & Banking', 'Risk & Compliance', 'Early career / interns', 'MBA', 'All majors'] },
      { id: uid(), type: 'number', label: 'Expected attendance', required: false },
      { id: uid(), type: 'short', label: 'Budget code', help: 'Cost center for expenses & ROI reporting', required: false },
    ],
  }
}

// ── Live preview controls ───────────────────────────────────────────────────
export function IntakePreviewField({ f }: { f: IntakeField }) {
  return (
    <div style={{ marginBottom: 16 }}>
      <div style={{ fontSize: 13, color: 'var(--ink-1)', fontWeight: 500, marginBottom: 6 }}>
        {f.label || <span style={{ color: 'var(--ink-5)' }}>Untitled field</span>}
        {f.required ? <span style={{ color: 'var(--bofa-red)', marginLeft: 4 }}>*</span> : null}
      </div>
      {f.help ? <div style={{ fontSize: 11.5, color: 'var(--ink-5)', margin: '-2px 0 6px' }}>{f.help}</div> : null}
      {f.type === 'short' && <input className="input" placeholder="Your answer" />}
      {f.type === 'long' && <textarea className="input" rows={2} placeholder="Your answer" style={{ resize: 'vertical' }} />}
      {f.type === 'number' && <input className="input" type="number" placeholder="0" style={{ width: 140 }} />}
      {f.type === 'date' && <input className="input" type="date" style={{ width: 180 }} />}
      {f.type === 'yesno' && (
        <div style={{ display: 'flex', gap: 8 }}>
          <label className="badge" style={{ cursor: 'pointer' }}><input type="radio" name={f.id} style={{ marginRight: 6 }} />Yes</label>
          <label className="badge" style={{ cursor: 'pointer' }}><input type="radio" name={f.id} style={{ marginRight: 6 }} />No</label>
        </div>
      )}
      {(f.type === 'single' || f.type === 'multi') && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          {(f.options ?? []).map((o, i) => (
            <label key={i} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: 'var(--ink-2)', cursor: 'pointer' }}>
              <input type={f.type === 'single' ? 'radio' : 'checkbox'} name={f.id} />
              {o || `Option ${i + 1}`}
            </label>
          ))}
        </div>
      )}
    </div>
  )
}

// ── Field editor card ───────────────────────────────────────────────────────
function FieldCard({
  f, index, total, onChange, onRemove, onMove, dragProps,
}: {
  f: IntakeField
  index: number
  total: number
  onChange: (f: IntakeField) => void
  onRemove: () => void
  onMove: (dir: -1 | 1) => void
  dragProps: React.HTMLAttributes<HTMLDivElement> & { draggable?: boolean }
}) {
  const hasOptions = f.type === 'single' || f.type === 'multi'
  return (
    <div className="card" style={{ marginBottom: 12 }} {...dragProps}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '9px 14px', borderBottom: '1px solid var(--line)' }}>
        <span title="Drag to reorder" style={{ cursor: 'grab', color: 'var(--ink-5)', fontSize: 15, userSelect: 'none' }}>⠿</span>
        <span className="badge badge--info">{TYPE_LABEL[f.type]}</span>
        <div style={{ flex: 1 }} />
        <button className="btn btn--ghost btn--sm" disabled={index === 0} onClick={() => onMove(-1)} aria-label="Move up">↑</button>
        <button className="btn btn--ghost btn--sm" disabled={index === total - 1} onClick={() => onMove(1)} aria-label="Move down">↓</button>
        <button className="btn btn--ghost btn--sm" onClick={onRemove} aria-label="Remove">×</button>
      </div>
      <div className="card__body" style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        <input className="input" value={f.label} placeholder="Field label" onChange={(e) => onChange({ ...f, label: e.target.value })} style={{ fontSize: 14, fontWeight: 500 }} />
        <input className="input" value={f.help ?? ''} placeholder="Help text (optional)" onChange={(e) => onChange({ ...f, help: e.target.value || undefined })} style={{ fontSize: 12.5 }} />
        {hasOptions && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {(f.options ?? []).map((o, i) => (
              <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ color: 'var(--ink-5)', fontSize: 13 }}>{f.type === 'single' ? '◯' : '▢'}</span>
                <input
                  className="input"
                  value={o}
                  onChange={(e) => {
                    const options = [...(f.options ?? [])]
                    options[i] = e.target.value
                    onChange({ ...f, options })
                  }}
                  style={{ flex: 1 }}
                />
                <button className="btn btn--ghost btn--sm" aria-label="Remove option" disabled={(f.options ?? []).length <= 1}
                  onClick={() => onChange({ ...f, options: (f.options ?? []).filter((_, j) => j !== i) })}>×</button>
              </div>
            ))}
            <button className="btn btn--outline btn--sm" style={{ alignSelf: 'flex-start' }}
              onClick={() => onChange({ ...f, options: [...(f.options ?? []), `Option ${(f.options ?? []).length + 1}`] })}>
              + Add option
            </button>
          </div>
        )}
        <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12.5, color: 'var(--ink-3)' }}>
          <input type="checkbox" checked={f.required} onChange={(e) => onChange({ ...f, required: e.target.checked })} />
          Required
        </label>
      </div>
    </div>
  )
}

// ── Main builder ────────────────────────────────────────────────────────────
export default function FormBuilder() {
  const [form, setForm] = usePersistentState<IntakeForm>(INTAKE_KEY, defaultIntakeForm())
  const dragFrom = useRef<number | null>(null)
  const [, setBump] = useState(0)

  function patchFields(fn: (fs: IntakeField[]) => IntakeField[]) {
    setForm((prev) => ({ ...prev, fields: fn(prev.fields), updatedAt: new Date().toISOString() }))
  }
  function move(index: number, dir: -1 | 1) {
    patchFields((fs) => {
      const next = [...fs]
      const j = index + dir
      if (j < 0 || j >= next.length) return fs
      ;[next[index], next[j]] = [next[j], next[index]]
      return next
    })
  }
  function reorder(from: number, to: number) {
    if (from === to) return
    patchFields((fs) => {
      const next = [...fs]
      const [item] = next.splice(from, 1)
      next.splice(to, 0, item)
      return next
    })
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* Toolbar */}
      <div className="card">
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '12px 14px', flexWrap: 'wrap' }}>
          <div style={{ fontFamily: 'var(--font-display)', fontSize: 16, color: 'var(--ink-0)' }}>Event intake form</div>
          <span className="eyebrow">Used by Events → New event</span>
          <div style={{ flex: 1 }} />
          <span className="eyebrow">{form.fields.length} custom fields</span>
          <span className="badge badge--ok" title="Saved automatically">Saved</span>
          <ConfirmButton label="Reset to defaults" confirmLabel="Replace all fields?" onConfirm={() => { setForm(defaultIntakeForm()); setBump((b) => b + 1) }} />
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '180px 1fr 340px', gap: 16, alignItems: 'start' }}>
        {/* Palette */}
        <div className="card" style={{ padding: 10, position: 'sticky', top: 12 }}>
          <div className="eyebrow" style={{ padding: '4px 6px 8px' }}>Add field</div>
          {PALETTE.map((p) => (
            <button key={p.type} onClick={() => patchFields((fs) => [...fs, newField(p.type)])}
              style={{ display: 'flex', alignItems: 'center', gap: 10, width: '100%', textAlign: 'left', border: '1px solid var(--line)', background: 'var(--app-panel)', cursor: 'pointer', borderRadius: 'var(--ra-2)', padding: '8px 10px', marginBottom: 6, font: 'inherit' }}>
              <span style={{ width: 20, textAlign: 'center', color: 'var(--bofa-navy)', fontSize: 14 }}>{p.glyph}</span>
              <span>
                <div style={{ fontSize: 13, color: 'var(--ink-1)', fontWeight: 500 }}>{p.label}</div>
                <div style={{ fontSize: 11, color: 'var(--ink-5)' }}>{p.hint}</div>
              </span>
            </button>
          ))}
        </div>

        {/* Canvas */}
        <div>
          {/* System fields — locked */}
          <div className="card" style={{ marginBottom: 12, background: 'var(--app-sunken)' }}>
            <div className="card__body" style={{ padding: '14px 16px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
                <span className="eyebrow">System fields</span>
                <span className="badge">🔒 always collected</span>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(210px, 1fr))', gap: 8 }}>
                {SYSTEM_FIELDS.map((sf) => (
                  <div key={sf.label} style={{ background: 'var(--app-panel)', border: '1px solid var(--line)', borderRadius: 'var(--ra-2)', padding: '8px 12px' }}>
                    <div style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--ink-1)' }}>{sf.label}</div>
                    <div style={{ fontSize: 11, color: 'var(--ink-5)', marginTop: 2 }}>{sf.hint}</div>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {form.fields.length === 0 ? (
            <div className="card">
              <div className="card__body" style={{ padding: '40px 20px', textAlign: 'center' }}>
                <div style={{ fontFamily: 'var(--font-display)', fontSize: 17, color: 'var(--ink-0)' }}>No custom fields</div>
                <div style={{ fontSize: 13, color: 'var(--ink-4)', marginTop: 6 }}>Recruiters will only fill the system fields. Add registration rules, targeting, or ops fields from the palette.</div>
              </div>
            </div>
          ) : (
            form.fields.map((f, i) => (
              <FieldCard
                key={f.id}
                f={f}
                index={i}
                total={form.fields.length}
                onChange={(nf) => patchFields((fs) => fs.map((x) => (x.id === f.id ? nf : x)))}
                onRemove={() => patchFields((fs) => fs.filter((x) => x.id !== f.id))}
                onMove={(dir) => move(i, dir)}
                dragProps={{
                  draggable: true,
                  onDragStart: () => { dragFrom.current = i },
                  onDragOver: (e) => e.preventDefault(),
                  onDrop: () => { if (dragFrom.current !== null) reorder(dragFrom.current, i); dragFrom.current = null },
                }}
              />
            ))
          )}
        </div>

        {/* Live preview */}
        <div className="card" style={{ position: 'sticky', top: 12, overflow: 'hidden' }}>
          <div style={{ background: 'var(--bofa-navy)', color: '#fff', padding: '14px 18px' }}>
            <div style={{ fontSize: 11, opacity: 0.8, letterSpacing: '0.04em', textTransform: 'uppercase' }}>Live preview · New event</div>
            <div style={{ fontFamily: 'var(--font-display)', fontSize: 18, marginTop: 2 }}>What recruiters will fill</div>
          </div>
          <div style={{ padding: '16px 18px', maxHeight: 540, overflowY: 'auto' }}>
            <div className="eyebrow" style={{ marginBottom: 10 }}>Basics (system)</div>
            <div style={{ marginBottom: 14 }}>
              <input className="input" placeholder="Event name" disabled style={{ marginBottom: 8 }} />
              <div style={{ display: 'flex', gap: 8 }}>
                <select className="select" disabled style={{ flex: 1 }}><option>Career fair</option></select>
                <input className="input" type="date" disabled style={{ flex: 1 }} />
              </div>
            </div>
            <div className="eyebrow" style={{ margin: '14px 0 10px' }}>Details (your fields)</div>
            {form.fields.length === 0 ? (
              <div style={{ fontSize: 12.5, color: 'var(--ink-5)', textAlign: 'center', padding: '14px 0' }}>Your custom fields will appear here.</div>
            ) : (
              form.fields.map((f) => <IntakePreviewField key={f.id} f={f} />)
            )}
            <button className="btn btn--primary" style={{ width: '100%', marginTop: 6 }} disabled>Create event</button>
          </div>
        </div>
      </div>
    </div>
  )
}
