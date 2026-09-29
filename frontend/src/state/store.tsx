import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react'

/* ------------------------------------------------------------------ *
 * Tiny app-wide UI store: a transient toast + a couple of flags.
 * Trimmed from the VMS store — data lives in react-query, not here.
 * ------------------------------------------------------------------ */

interface StoreState {
  toast: string | null
  /** 'danger' for something that did not happen (a refused save, a failed request). */
  toastTone: ToastTone
  flags: string[]
  /** Show a transient toast message (auto-dismisses). */
  toastMsg: (msg: string, tone?: ToastTone) => void
}

export type ToastTone = 'ok' | 'danger'

const Ctx = createContext<StoreState | null>(null)

export function StoreProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<string | null>(null)
  const [toastTone, setToastTone] = useState<ToastTone>('ok')
  const [flags] = useState<string[]>([])
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const toastMsg = useCallback((msg: string, tone: ToastTone = 'ok') => {
    setToast(msg)
    setToastTone(tone)
    if (timer.current) clearTimeout(timer.current)
    // Something that went wrong stays long enough to be read.
    timer.current = setTimeout(() => setToast(null), tone === 'danger' ? 6000 : 2600)
  }, [])

  return <Ctx.Provider value={{ toast, toastTone, flags, toastMsg }}>{children}</Ctx.Provider>
}

export function useStore() {
  const ctx = useContext(Ctx)
  if (!ctx) throw new Error('useStore must be used within StoreProvider')
  return ctx
}
