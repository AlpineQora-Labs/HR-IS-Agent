import { useEffect, useMemo, useRef, useState } from 'react'
import { useConfig } from '@/state/config'
import { useStore } from '@/state/store'
import { IconChevronDown, IconChevronUp } from '@/components/icons'
import ApprovalCanvas, { SPEC, Icons } from './ApprovalCanvas'
import {
  useServerWorkflows, useSaveServerWorkflows, fromServerDto, toServerDto,
  type ApprovalWorkflow,
} from './approvals'
import '@/styles/canvas.css'
import '@/styles/workflow.css'

/** Inline trash glyph (the TA icon set has no IconTrash). */
function IconTrash({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round">
      <path d="M3 6h18M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2m2 0v14a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V6" />
      <path d="M10 11v6M14 11v6" />
    </svg>
  )
}

const TRIGGERS = ['Event', 'Offer', 'Requisition', 'Onboarding', 'Compliance']
const CONDITIONS = ['Always', 'Bill rate over threshold', 'Amount over threshold', 'Duration over threshold', 'Flagged critical']

export default function AdminWorkflows() {
  const { roles } = useConfig()
  const { toastMsg: flash } = useStore()
  const roleName = (key: string) => roles.find((r) => r.key === key)?.name ?? key
  const [canvasId, setCanvasId] = useState<string | null>(null)

  // Workflows live in local state, hydrated from the server and debounce-saved back.
  const [workflows, setWorkflows] = useState<ApprovalWorkflow[]>([])
  const canvasWorkflow = workflows.find((w) => w.id === canvasId) ?? null
  const { data: serverWorkflows } = useServerWorkflows()
  const saveServer = useSaveServerWorkflows()
  const hydrated = useRef(false)
  // Serialized snapshot of what the server already has: the debounce-save below
  // only pushes when the working copy actually differs. Without this, a
  // hydration echo or an HMR remount with stale preserved state re-saves and
  // can clobber newer server data (e.g. a workflow saved from the canvas).
  const lastPushed = useRef('')
  useEffect(() => {
    if (!hydrated.current && serverWorkflows && serverWorkflows.length) {
      hydrated.current = true
      lastPushed.current = JSON.stringify(serverWorkflows.map((d) => toServerDto(fromServerDto(d))))
      setWorkflows(serverWorkflows.map(fromServerDto))
    }
  }, [serverWorkflows])

  // ---- local mutators (were config-store helpers) ----
  const replaceWorkflows = (list: ApprovalWorkflow[]) => setWorkflows(list)
  const updateWorkflow = (id: string, patch: Partial<ApprovalWorkflow>) =>
    setWorkflows((ws) => ws.map((w) => (w.id === id ? { ...w, ...patch } : w)))
  const addWorkflowLevel = (id: string) =>
    setWorkflows((ws) => ws.map((w) => (w.id === id
      ? { ...w, levels: [...w.levels, { id: `l${Date.now()}`, approverRole: roles[0]?.key ?? 'admin', condition: 'Always' }] } : w)))
  const removeWorkflowLevel = (id: string, lid: string) =>
    setWorkflows((ws) => ws.map((w) => (w.id === id ? { ...w, levels: w.levels.filter((l) => l.id !== lid) } : w)))
  const updateWorkflowLevel = (id: string, lid: string, patch: Partial<ApprovalWorkflow['levels'][number]>) =>
    setWorkflows((ws) => ws.map((w) => (w.id === id
      ? { ...w, levels: w.levels.map((l) => (l.id === lid ? { ...l, ...patch } : l)) } : w)))
  const moveWorkflowLevel = (id: string, lid: string, dir: -1 | 1) =>
    setWorkflows((ws) => ws.map((w) => {
      if (w.id !== id) return w
      const i = w.levels.findIndex((l) => l.id === lid)
      const j = i + dir
      if (i < 0 || j < 0 || j >= w.levels.length) return w
      const levels = [...w.levels]
      ;[levels[i], levels[j]] = [levels[j], levels[i]]
      return { ...w, levels }
    }))
  // referenced to satisfy the linter (used via canvas onSaved below)
  void replaceWorkflows
  void useMemo
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const firstRender = useRef(true)
  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false
      return
    }
    if (!hydrated.current) return
    const payload = workflows.map(toServerDto)
    const json = JSON.stringify(payload)
    if (json === lastPushed.current) return
    if (saveTimer.current) clearTimeout(saveTimer.current)
    saveTimer.current = setTimeout(() => {
      lastPushed.current = json
      saveServer.mutate(payload)
    }, 900)
    return () => {
      if (saveTimer.current) clearTimeout(saveTimer.current)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workflows])

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
      {canvasWorkflow && (
        <ApprovalCanvas
          workflow={canvasWorkflow}
          onClose={() => setCanvasId(null)}
          onSaved={(w) => updateWorkflow(w.id, w)}
        />
      )}
      <div className="card">
        <div className="card__body" style={{ padding: '16px 20px', fontSize: 13, color: 'var(--ink-3)' }}>
          Each request type routes through an ordered chain of approval levels. Items that fall within the auto-approve policy
          skip the chain. Rules are evaluated top to bottom; a level fires only when its condition is met.
        </div>
      </div>

      {workflows.map((w) => (
        <WorkflowCard
          key={w.id}
          w={w}
          roleName={roleName}
          roles={roles}
          onToggle={() => {
            updateWorkflow(w.id, { enabled: !w.enabled })
            flash(`${w.name} ${w.enabled ? 'disabled' : 'enabled'}`)
          }}
          onThreshold={(v) => updateWorkflow(w.id, { threshold: v })}
          onTrigger={(v) => updateWorkflow(w.id, { trigger: v, graph: undefined })}
          onAuto={() => updateWorkflow(w.id, { autoApprove: !w.autoApprove, graph: undefined })}
          onAddLevel={() => addWorkflowLevel(w.id)}
          onRemoveLevel={(lid) => removeWorkflowLevel(w.id, lid)}
          onLevelRole={(lid, v) => updateWorkflowLevel(w.id, lid, { approverRole: v })}
          onLevelCond={(lid, v) => updateWorkflowLevel(w.id, lid, { condition: v })}
          onMove={(lid, dir) => moveWorkflowLevel(w.id, lid, dir)}
          onCanvas={() => setCanvasId(w.id)}
        />
      ))}
    </div>
  )
}

/** Read-only mini rendering of the flow: trigger → (auto-approve policy) → levels → approved. */
function MiniFlow({ w, roleName }: { w: ApprovalWorkflow; roleName: (k: string) => string }) {
  const arrow = (
    <span className="wfs-arrow">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
        <path d="M5 12h14" />
        <path d="m13 6 6 6-6 6" />
      </svg>
    </span>
  )
  const pill = (type: keyof typeof SPEC, label: string, cond?: string) => (
    <span className="wfs-pill">
      <span className="wfs-pill__ic" style={{ background: SPEC[type].iconBg, color: SPEC[type].iconFg }}>
        {Icons[type]}
      </span>
      {label}
      {cond && cond !== 'Always' && <span className="wfs-pill__cond">◇ {cond}</span>}
    </span>
  )
  return (
    <div className="wfs-strip">
      {pill('trigger', w.trigger)}
      {w.autoApprove && (
        <>
          {arrow}
          {pill('policy', `Auto ≤ ${w.threshold}`)}
        </>
      )}
      {w.levels.map((l) => (
        <span key={l.id} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          {arrow}
          {pill('approval', roleName(l.approverRole), l.condition)}
        </span>
      ))}
      {arrow}
      {pill('end', 'Approved')}
    </div>
  )
}

function WorkflowCard({
  w,
  roles,
  roleName,
  onToggle,
  onThreshold,
  onTrigger,
  onAuto,
  onAddLevel,
  onRemoveLevel,
  onLevelRole,
  onLevelCond,
  onMove,
  onCanvas,
}: {
  w: ApprovalWorkflow
  roles: { key: string; name: string }[]
  roleName: (k: string) => string
  onToggle: () => void
  onThreshold: (v: string) => void
  onTrigger: (v: string) => void
  onAuto: () => void
  onAddLevel: () => void
  onRemoveLevel: (lid: string) => void
  onLevelRole: (lid: string, v: string) => void
  onLevelCond: (lid: string, v: string) => void
  onMove: (lid: string, dir: -1 | 1) => void
  onCanvas: () => void
}) {
  return (
    <div className="card" style={{ opacity: w.enabled ? 1 : 0.66 }}>
      <div className="card__head">
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <h3 style={{ fontSize: 16 }}>{w.name}</h3>
          <span className="badge badge--info">{w.trigger}</span>
          <span className={`badge ${w.enabled ? 'badge--ok' : ''}`}>{w.enabled ? 'Active' : 'Disabled'}</span>
          {w.graph && <span className="badge">canvas</span>}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <button className="btn btn--outline btn--sm" onClick={onCanvas}>
            Open canvas
          </button>
          <label className="switch" title="Enable workflow">
            <input type="checkbox" checked={w.enabled} onChange={onToggle} />
            <span className="track" />
          </label>
        </div>
      </div>
      <div className="card__body">
        <div style={{ display: 'flex', gap: 18, flexWrap: 'wrap', marginBottom: 18 }}>
          <div className="field" style={{ width: 200 }}>
            <span className="field__label">Trigger</span>
            <select className="select" value={w.trigger} onChange={(e) => onTrigger(e.target.value)}>
              {TRIGGERS.map((t) => (
                <option key={t}>{t}</option>
              ))}
            </select>
          </div>
          <div className="field" style={{ width: 220 }}>
            <span className="field__label">Threshold</span>
            <input className="input" value={w.threshold} onChange={(e) => onThreshold(e.target.value)} placeholder="e.g. $100,000" />
          </div>
          <div className="field" style={{ flex: 'none' }}>
            <span className="field__label">Auto-approve within policy</span>
            <label className="check" style={{ height: 40 }}>
              <input type="checkbox" checked={w.autoApprove} onChange={onAuto} />
              {w.autoApprove ? 'Enabled' : 'Disabled'}
            </label>
          </div>
        </div>

        <div style={{ marginBottom: 16 }}>
          <MiniFlow w={w} roleName={roleName} />
        </div>

        <div
          style={{
            fontFamily: 'var(--font-mono)',
            fontSize: 10.5,
            letterSpacing: '.12em',
            textTransform: 'uppercase',
            color: 'var(--ink-4)',
            marginBottom: 10,
          }}
        >
          Approval chain · {w.levels.length} {w.levels.length === 1 ? 'level' : 'levels'}
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {w.levels.map((l, i) => (
            <div
              key={l.id}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                padding: '12px 14px',
                border: '1px solid var(--line)',
                borderRadius: 'var(--ra-2)',
                background: 'var(--app-sunken)',
                flexWrap: 'wrap',
              }}
            >
              <span
                className="avatar"
                style={{ width: 26, height: 26, fontSize: 12, background: 'var(--bofa-navy)', color: '#fff', borderRadius: 7 }}
              >
                {i + 1}
              </span>
              <div className="field" style={{ width: 200 }}>
                <span className="field__label" style={{ fontSize: 11 }}>
                  Approver role
                </span>
                <select className="select" style={{ height: 34 }} value={l.approverRole} onChange={(e) => onLevelRole(l.id, e.target.value)}>
                  {roles.map((r) => (
                    <option key={r.key} value={r.key}>
                      {r.name}
                    </option>
                  ))}
                </select>
              </div>
              <div className="field" style={{ width: 240 }}>
                <span className="field__label" style={{ fontSize: 11 }}>
                  Condition
                </span>
                <select className="select" style={{ height: 34 }} value={l.condition} onChange={(e) => onLevelCond(l.id, e.target.value)}>
                  {CONDITIONS.map((c) => (
                    <option key={c}>{c}</option>
                  ))}
                </select>
              </div>
              <div style={{ flex: 1 }} />
              <div style={{ display: 'flex', gap: 4 }}>
                <button className="btn btn--ghost btn--icon btn--sm" disabled={i === 0} onClick={() => onMove(l.id, -1)} title="Move up">
                  <IconChevronUp className="ic" />
                </button>
                <button
                  className="btn btn--ghost btn--icon btn--sm"
                  disabled={i === w.levels.length - 1}
                  onClick={() => onMove(l.id, 1)}
                  title="Move down"
                >
                  <IconChevronDown className="ic" />
                </button>
                <button className="btn btn--ghost btn--icon btn--sm" onClick={() => onRemoveLevel(l.id)} title="Remove level" style={{ color: 'var(--danger-fg)' }}>
                  <IconTrash className="ic" />
                </button>
              </div>
            </div>
          ))}
          {w.levels.length === 0 && (
            <div style={{ fontSize: 13, color: 'var(--ink-4)', padding: '8px 2px' }}>
              No approval levels — requests of this type are auto-approved.
            </div>
          )}
        </div>

        <button className="btn btn--outline btn--sm" style={{ marginTop: 14 }} onClick={onAddLevel}>
          + Add approval level
        </button>

        <div style={{ marginTop: 16, fontSize: 12.5, color: 'var(--ink-4)' }}>
          Chain: {w.levels.length === 0 ? 'Auto-approved' : w.levels.map((l) => roleName(l.approverRole)).join(' → ')}
        </div>
      </div>
    </div>
  )
}
