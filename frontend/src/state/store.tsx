import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react'

/* ------------------------------------------------------------------ *
 * Tiny app-wide UI store: a transient toast + a couple of flags.
 * Trimmed from the VMS store — data lives in react-query, not here.
 * ------------------------------------------------------------------ */

interface StoreState {
  toast: string | null
  flags: string[]
  /** Show a transient toast message (auto-dismisses). */
  toastMsg: (msg: string) => void
}

const Ctx = createContext<StoreState | null>(null)

export function StoreProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<string | null>(null)
  const [flags] = useState<string[]>([])
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const toastMsg = useCallback((msg: string) => {
    setToast(msg)
    if (timer.current) clearTimeout(timer.current)
    timer.current = setTimeout(() => setToast(null), 2600)
  }, [])

  return <Ctx.Provider value={{ toast, flags, toastMsg }}>{children}</Ctx.Provider>
}

export function useStore() {
  const ctx = useContext(Ctx)
  if (!ctx) throw new Error('useStore must be used within StoreProvider')
  return ctx
}
