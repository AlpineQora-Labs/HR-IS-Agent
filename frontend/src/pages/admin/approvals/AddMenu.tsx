import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Icons } from './canvasTheme'
import type { AddTarget, StepKind } from './canvasContext'

/* "What happens next?" — the one place a step is added from. Opens on the plus
   of any line, or under a step that has nothing after it. */

const ROOT: { kind: StepKind | 'action'; icon: keyof typeof Icons; name: string; hint: string }[] = [
  { kind: 'condition', icon: 'condition', name: 'Rule', hint: 'Check something, then go one way or the other' },
  { kind: 'approval', icon: 'approval', name: 'Approval', hint: 'Ask a role to approve' },
  { kind: 'action', icon: 'action', name: 'Action', hint: 'Send an email or text, or a candidate step' },
  { kind: 'exception', icon: 'exception', name: 'Exception', hint: 'Stop here and send for review' },
]

const ACTIONS: { kind: StepKind; icon: keyof typeof Icons; name: string; hint: string }[] = [
  { kind: 'email', icon: 'email', name: 'Send an email', hint: 'From your Communications templates' },
  { kind: 'sms', icon: 'sms', name: 'Send a text message', hint: 'From your SMS templates' },
  { kind: 'step', icon: 'step', name: 'Candidate step', hint: 'Something the candidate does' },
]

export default function AddMenu({
  at,
  target,
  onPick,
  onRemoveLine,
  onClose,
}: {
  at: { x: number; y: number }
  target: AddTarget
  onPick: (kind: StepKind) => void
  onRemoveLine: () => void
  onClose: () => void
}) {
  const ref = useRef<HTMLDivElement>(null)
  // What had the keyboard when the menu opened gets it back when the menu closes.
  const opener = useRef(document.activeElement as HTMLElement | null)
  useEffect(
    () => () => {
      const el = opener.current
      const lost = !document.activeElement || document.activeElement === document.body
      if (el && document.contains(el) && lost) el.focus()
    },
    [],
  )
  const [pane, setPane] = useState<'root' | 'action'>('root')
  const [pos, setPos] = useState({ left: at.x, top: at.y })

  // Keep the menu on screen whichever line it was opened from.
  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return
    const r = el.getBoundingClientRect()
    setPos({
      left: Math.max(12, Math.min(at.x - r.width / 2, window.innerWidth - r.width - 12)),
      top: Math.max(12, Math.min(at.y, window.innerHeight - r.height - 12)),
    })
  }, [at.x, at.y, pane])

  useEffect(() => {
    ref.current?.querySelector<HTMLButtonElement>('button[data-first]')?.focus()
  }, [pane])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return
      e.stopPropagation()
      if (pane === 'action') setPane('root')
      else onClose()
    }
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) onClose()
    }
    window.addEventListener('keydown', onKey, true)
    window.addEventListener('mousedown', onDown, true)
    return () => {
      window.removeEventListener('keydown', onKey, true)
      window.removeEventListener('mousedown', onDown, true)
    }
  }, [pane, onClose])

  const rows = pane === 'root' ? ROOT : ACTIONS

  return createPortal(
    <div ref={ref} className="wfc-addmenu" role="menu" aria-label="What happens next?" style={pos}>
      <div className="wfc-addmenu__title">
        {pane === 'action' ? (
          <button className="wfc-addmenu__back" onClick={() => setPane('root')}>
            <span className="wfc-addmenu__backic">{Icons.chevron}</span>
            Back
          </button>
        ) : (
          'What happens next?'
        )}
      </div>
      {rows.map((r, i) => (
        <button
          key={r.kind}
          role="menuitem"
          className="wfc-addmenu__row"
          {...(i === 0 ? { 'data-first': true } : {})}
          onClick={() => (r.kind === 'action' ? setPane('action') : onPick(r.kind))}
        >
          <span className="wfc-addmenu__ic">{Icons[r.icon]}</span>
          <span style={{ minWidth: 0 }}>
            <span className="wfc-addmenu__name">{r.name}</span>
            <span className="wfc-addmenu__hint">{r.hint}</span>
          </span>
          {r.kind === 'action' && <span className="wfc-addmenu__more">{Icons.chevron}</span>}
        </button>
      ))}
      {pane === 'root' && target.kind === 'edge' && (
        <button role="menuitem" className="wfc-addmenu__remove" onClick={onRemoveLine}>
          Remove this connection
        </button>
      )}
    </div>,
    document.body,
  )
}
