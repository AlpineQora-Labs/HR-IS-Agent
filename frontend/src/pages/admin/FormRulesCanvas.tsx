import { useMemo, useState } from 'react'
import {
  Background,
  BackgroundVariant,
  Handle,
  MarkerType,
  Panel,
  Position,
  ReactFlow,
  useReactFlow,
  useViewport,
  type Edge,
  type Node,
  type NodeProps,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
// The same canvas as the approval workflows and the candidate journey: its surface, its cards
// and its floating toolbar, so a form's rules read like every other drawing in the product.
import '@/styles/canvas.css'
import type { FormChoice, FormCondition, FormRule, IntakeField } from './FormBuilder'

/* The form's rules as a drawing. A box is a question; an arrow runs from the question whose
   answer is looked at to the question it acts on, and says what the answer must be and what
   then happens. Questions are laid out left to right in the order the answers flow: a question
   sits one column to the right of the last question it depends on.

   It only draws what the rules say. Rules are changed in the pop-up a box opens. */

const OP: Record<FormCondition['op'], string> = { equals: 'is', not_equals: 'is not', contains: 'contains' }
// The workflow canvas's own colours: green for a yes path, red for a no path, navy, amber.
const ACTION: Record<FormRule['action'], { word: string; color: string }> = {
  show: { word: 'Show', color: '#1a9d55' },
  hide: { word: 'Hide', color: '#e31837' },
  require: { word: 'Require', color: '#012169' },
}
const FOLLOW = '#c98a00'

type BoxData = { label: string; kind: string; acts: string[]; looksAt: number; onOpen: () => void }

/** A question that decides others is drawn as a decision; one that is only acted on, as a step. */
const GLYPH = {
  decides: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 3l9 9-9 9-9-9z" /></svg>
  ),
  acted: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="4" y="5" width="16" height="14" rx="3" /><path d="M8 10h8M8 14h5" /></svg>
  ),
}

function QuestionBox({ data }: NodeProps<Node<BoxData>>) {
  const decides = data.looksAt > 0
  const tone = decides ? { bg: '#e8edf8', fg: '#012169' } : { bg: '#fdf3dc', fg: '#8a5d00' }
  return (
    <div
      className="wfc-node"
      style={{ width: 236, cursor: 'pointer' }}
      role="button"
      tabIndex={0}
      title={`Rules for ${data.label}`}
      onClick={data.onOpen}
      onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && data.onOpen()}
    >
      <Handle type="target" position={Position.Left} />
      <div className="wfc-node__inner">
        <span className="wfc-node__icon" style={{ background: tone.bg, color: tone.fg }}>{decides ? GLYPH.decides : GLYPH.acted}</span>
        <div style={{ minWidth: 0 }}>
          <div className="wfc-node__kicker" style={{ color: tone.fg }}>{data.kind}</div>
          <div className="wfc-node__title">{data.label}</div>
          <div className="wfc-node__sub">
            {[...data.acts, decides ? `decides ${data.looksAt} ${data.looksAt === 1 ? 'question' : 'questions'}` : null].filter(Boolean).join(' · ')}
          </div>
        </div>
      </div>
      <Handle type="source" position={Position.Right} />
    </div>
  )
}

/* The workflow canvas's floating toolbar: zoom, fit, and put the boxes back where the rules lay them. */
function Toolbar({ onArrange }: { onArrange: () => void }) {
  const { zoomIn, zoomOut, fitView } = useReactFlow()
  const { zoom } = useViewport()
  return (
    <Panel position="bottom-center" className="wfc-toolbar">
      <button onClick={() => zoomOut()} aria-label="Zoom out">−</button>
      <span className="wfc-toolbar__zoom">{Math.round(zoom * 100)}%</span>
      <button onClick={() => zoomIn()} aria-label="Zoom in">+</button>
      <span className="wfc-toolbar__sep" />
      <button onClick={() => fitView({ padding: 0.18, maxZoom: 1, duration: 250 })}>Fit</button>
      <button onClick={onArrange} title="Put the boxes back in order, left to right">Arrange</button>
    </Panel>
  )
}

const nodeTypes = { question: QuestionBox }

export default function FormRulesCanvas({
  fields, rules, choices, labelOf, kindOf, onOpen, fill = false,
}: {
  /** Fill the space it is given (the full-screen canvas) instead of sizing itself to the drawing. */
  fill?: boolean
  fields: IntakeField[]
  rules: FormRule[]
  choices: FormChoice[]
  labelOf: (f: IntakeField) => string
  kindOf: (f: IntakeField) => string
  onOpen: (fieldId: string) => void
}) {
  // Boxes can be dragged about; Arrange lays them out afresh.
  const [arranged, setArranged] = useState(0)
  const { nodes, edges } = useMemo(() => {
    const known = new Set(fields.map((f) => f.id))
    // Only what can be judged is drawn: the same guard the form itself applies.
    const live = rules.filter((r) => r.value !== '' && known.has(r.targetId) && [r, ...(r.also ?? [])].every((c) => known.has(c.sourceId)))
    const follows = choices.filter((c) => known.has(c.targetId) && known.has(c.sourceId) && c.targetId !== c.sourceId)

    // Everything one question's answer does to another is one arrow, however many rules say it:
    // arrows drawn on top of each other cannot be read.
    type Link = { id: string; from: string; to: string; parts: string[]; colors: string[]; dashed: boolean }
    const byPair = new Map<string, Link>()
    const link = (from: string, to: string, part: string, color: string, dashed: boolean) => {
      const key = `${from}>${to}`
      const l = byPair.get(key) ?? { id: key, from, to, parts: [], colors: [], dashed }
      if (!l.parts.includes(part)) l.parts.push(part)
      l.colors.push(color)
      l.dashed = l.dashed && dashed
      byPair.set(key, l)
    }
    for (const r of live) {
      const conditions = [r, ...(r.also ?? []).filter((c) => c.sourceId && c.value !== '')]
      conditions.forEach((c) => {
        // A rule that needs several answers draws an arrow from each and says how many more it
        // needs; the sentence under the drawing names them. Long labels run into each other.
        const more = conditions.length - 1
        link(c.sourceId, r.targetId, `${OP[c.op]} ${c.value}${more > 0 ? ` (+${more} more)` : ''} → ${ACTION[r.action].word}`, ACTION[r.action].color, false)
      })
    }
    for (const c of follows) {
      link(c.sourceId, c.targetId, 'narrows the options', FOLLOW, true)
    }
    const links = [...byPair.values()].map((l) => ({
      ...l,
      label: l.parts.join('  ·  '),
      // One colour when every rule on the arrow does the same thing; neutral when they differ.
      color: new Set(l.colors).size === 1 ? l.colors[0] : '#3a4763',
    }))

    const involved = fields.filter((f) => links.some((l) => l.from === f.id || l.to === f.id))
    // Column: one to the right of the furthest question it depends on. A loop cannot push it further than the form is long.
    const depth = new Map<string, number>()
    const depthOf = (id: string, seen: Set<string>): number => {
      if (depth.has(id)) return depth.get(id)!
      if (seen.has(id)) return 0
      const next = new Set(seen).add(id)
      const from = links.filter((l) => l.to === id && l.from !== id).map((l) => l.from)
      const d = from.length === 0 ? 0 : 1 + Math.max(...from.map((s) => depthOf(s, next)))
      depth.set(id, d)
      return d
    }
    involved.forEach((f) => depthOf(f.id, new Set()))

    const rowsUsed = new Map<number, number>()
    const nodes: Node<BoxData>[] = involved.map((f) => {
      const col = depth.get(f.id) ?? 0
      const row = rowsUsed.get(col) ?? 0
      rowsUsed.set(col, row + 1)
      const acts = [
        ...new Set(live.filter((r) => r.targetId === f.id).map((r) => (r.action === 'show' ? 'shown when…' : r.action === 'hide' ? 'hidden when…' : 'required when…'))),
        ...(follows.some((c) => c.targetId === f.id) ? ['options follow…'] : []),
      ]
      return {
        id: f.id, type: 'question', position: { x: col * 520, y: row * 140 }, draggable: true,
        data: { label: labelOf(f), kind: kindOf(f), acts, looksAt: new Set(links.filter((l) => l.from === f.id && l.to !== f.id).map((l) => l.to)).size, onOpen: () => onOpen(f.id) },
      }
    })
    const edges: Edge[] = links.map((l) => ({
      id: l.id, source: l.from, target: l.to, label: l.label, type: 'default',
      style: { stroke: l.color, strokeWidth: 1.6, strokeDasharray: l.dashed ? '6 4' : undefined },
      labelStyle: { fontSize: 11, fill: l.color, fontWeight: 600 },
      labelBgStyle: { fill: '#ffffff', stroke: '#e3e8f0', strokeWidth: 1 }, labelBgPadding: [8, 4] as [number, number], labelBgBorderRadius: 999,
      markerEnd: { type: MarkerType.ArrowClosed, width: 14, height: 14, color: l.color },
    }))
    return { nodes, edges }
  }, [fields, rules, choices, labelOf, kindOf, onOpen])

  if (nodes.length === 0) {
    return (
      <div style={{ border: '2px dashed var(--line)', borderRadius: 'var(--ra-2)', padding: '40px 16px', textAlign: 'center', fontSize: 12.5, color: 'var(--ink-4)' }}>
        Nothing to draw yet. Click a question on the left and add a rule: it appears here as boxes joined by an arrow.
      </div>
    )
  }

  return (
    <div style={fill ? { display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0 } : undefined}>
      <div className="wfc" style={fill ? { flex: 1, minHeight: 0, border: '1px solid var(--line)', borderRadius: 16, overflow: 'hidden' } : { height: Math.min(600, Math.max(340, Math.max(...nodes.map((n) => n.position.y)) + 240)), border: '1px solid var(--line)', borderRadius: 16, overflow: 'hidden' }}>
        <ReactFlow
          // Redrawn from the rules each time they change, and when Arrange is pressed.
          key={`${arranged}-${nodes.length}-${edges.map((e) => e.id + e.label).join('|')}`}
          defaultNodes={nodes}
          defaultEdges={edges}
          nodeTypes={nodeTypes}
          nodesConnectable={false}
          // Full screen, the wheel zooms as on the workflow canvas. Set inside a page that scrolls,
          // the wheel scrolls the page and zoom is on the toolbar.
          zoomOnScroll={fill}
          preventScrolling={fill}
          edgesFocusable={false}
          fitView
          fitViewOptions={{ padding: 0.18, maxZoom: 1 }}
          minZoom={0.3}
          proOptions={{ hideAttribution: true }}
        >
          <Background variant={BackgroundVariant.Dots} gap={22} size={1.5} color="#d3d9e5" />
          <Toolbar onArrange={() => setArranged((n) => n + 1)} />
        </ReactFlow>
      </div>
      <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginTop: 8, fontSize: 11.5, color: 'var(--ink-4)' }}>
        {(Object.keys(ACTION) as FormRule['action'][]).map((a) => (
          <span key={a} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <span style={{ width: 18, height: 0, borderTop: `2px solid ${ACTION[a].color}` }} />
            {ACTION[a].word}
          </span>
        ))}
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          <span style={{ width: 18, height: 0, borderTop: `2px dashed ${FOLLOW}` }} />
          Narrows the options
        </span>
        <span style={{ marginLeft: 'auto' }}>An arrow runs from the answer that is looked at to the question it acts on. Click a box to change its rules.</span>
      </div>
    </div>
  )
}
