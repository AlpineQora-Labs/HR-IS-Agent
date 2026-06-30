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
      <style>{`@keyframes slideOverIn{from{transform:translateX(24px);opacity:.4}to{transform:translateX(0);opacity:1}}`}</style>
      <div onClick={onClose} style={{ position: 'fixed', inset: 0, background: 'rgba(16,22,38,0.32)', zIndex: 60 }} />
      <aside
        role="dialog"
        aria-modal="true"
        aria-label={label}
        style={{
          position: 'fixed',
          top: 0,
          right: 0,
          bottom: 0,
          width,
          maxWidth: '92vw',
          background: 'var(--app-panel)',
          borderLeft: '1px solid var(--line)',
          boxShadow: '-18px 0 48px rgba(16,22,38,0.16)',
          zIndex: 61,
          display: 'flex',
          flexDirection: 'column',
          animation: 'slideOverIn 160ms ease-out',
        }}
      >
        {children}
      </aside>
    </>
  )
}
