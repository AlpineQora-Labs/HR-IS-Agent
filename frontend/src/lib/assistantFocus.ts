import { useEffect, useSyncExternalStore } from 'react'

/* What the portal assistant should answer about: whatever the recruiter has open. Pages and drawers
   say what they show with useAssistantFocus; the widget reads the most specific of them with
   useFocusOnScreen. The server decides what a briefing on it holds; this only says what is open. */

export type Focus = {
  candidateId?: string
  applicationId?: string
  jobId?: string
  /** How the widget names it before the server has answered, for example the candidate's name. */
  label?: string
}

type Entry = Focus & { owner: symbol }

let entries: Entry[] = []
let drawers = 0
const listeners = new Set<() => void>()
const emit = () => listeners.forEach((l) => l())
const subscribe = (l: () => void) => {
  listeners.add(l)
  return () => listeners.delete(l)
}

/** How specific a focus is: an application beats a candidate, which beats a job. */
const rank = (f: Focus) => (f.applicationId ? 3 : f.candidateId ? 2 : f.jobId ? 1 : 0)

/** The most specific thing open on screen; the latest of equals. Null when nothing is. */
function mostSpecific(): Focus | null {
  let best: Entry | null = null
  for (const e of entries) if (!best || rank(e) >= rank(best)) best = e
  return best && rank(best) > 0 ? best : null
}

let snapshot: Focus | null = null
function refresh() {
  const next = mostSpecific()
  const same =
    next?.applicationId === snapshot?.applicationId &&
    next?.candidateId === snapshot?.candidateId &&
    next?.jobId === snapshot?.jobId &&
    next?.label === snapshot?.label
  if (!same) snapshot = next ? { candidateId: next.candidateId, applicationId: next.applicationId, jobId: next.jobId, label: next.label } : null
  emit()
}

/** Say what this page or drawer shows, for as long as it is mounted. Null says nothing. */
export function useAssistantFocus(focus: Focus | null) {
  const key = focus ? `${focus.applicationId ?? ''}|${focus.candidateId ?? ''}|${focus.jobId ?? ''}|${focus.label ?? ''}` : ''
  useEffect(() => {
    if (!focus) return
    const owner = Symbol('focus')
    entries = [...entries, { ...focus, owner }]
    refresh()
    return () => {
      entries = entries.filter((e) => e.owner !== owner)
      refresh()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])
}

/** The most specific thing pages and drawers say is open, or null. */
export function useFocusOnScreen(): Focus | null {
  return useSyncExternalStore(subscribe, () => snapshot)
}

/** Called by every drawer while it is open, so the widget can step out of its way. */
export function useDrawerPresence() {
  useEffect(() => {
    drawers += 1
    emit()
    return () => {
      drawers -= 1
      emit()
    }
  }, [])
}

/** Whether a drawer is open. */
export function useDrawerOpen(): boolean {
  return useSyncExternalStore(subscribe, () => drawers > 0)
}
