import { useEffect } from 'react'

// Right-side slide-over chrome: dimmed backdrop, panel, Esc + click-outside to
// close, slide-in animation. Callers render their own header / body / footer
// inside (the panel is a flex column at full height).
export default function SlideOver({
  label,
  onClose,
  width = 440,
  children,
}: {
  label: string
  onClose: () => void
  width?: number
  children: React.ReactNode
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <>
      <style>{`@keyframes slideOverIn{from{transform:translateX(28px);opacity:.35}to{transform:translateX(0);opacity:1}}`}</style>
      <div onClick={onClose} style={{ position: 'fixed', inset: 0, background: 'rgba(16,22,38,0.32)', zIndex: 60 }} />
      {/* Floating drawer: inset from the viewport edges with rounded corners all
          around, so it reads as a card hovering over the app rather than a wall
          sliding in. overflow:hidden keeps scrolling content inside the radius. */}
      <aside
        role="dialog"
        aria-modal="true"
        aria-label={label}
        style={{
          position: 'fixed',
          top: 12,
          right: 12,
          bottom: 12,
          width,
          maxWidth: 'calc(92vw - 12px)',
          background: 'var(--app-panel)',
          border: '1px solid var(--line)',
          borderRadius: 16,
          overflow: 'hidden',
          boxShadow: '-24px 0 64px rgba(16,22,38,0.22), 0 8px 28px rgba(16,22,38,0.14)',
          zIndex: 61,
          display: 'flex',
          flexDirection: 'column',
          animation: 'slideOverIn 180ms cubic-bezier(0.16, 1, 0.3, 1)',
        }}
      >
        {children}
      </aside>
    </>
  )
}
