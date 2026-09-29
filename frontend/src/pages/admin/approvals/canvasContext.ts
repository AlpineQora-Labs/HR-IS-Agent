import { createContext, useContext } from 'react'
import type { Answer } from './graphModel'

/** Where a new step is being added: on a line, or after a step that has nothing following it. */
export type AddTarget =
  | { kind: 'edge'; edgeId: string }
  | { kind: 'open'; nodeId: string; answer: Answer | null }

/** What can be added from the "What happens next?" menu. */
export type StepKind = 'condition' | 'approval' | 'email' | 'sms' | 'step' | 'exception'

/** Which answers / ends of a step have nothing leaving them yet. */
export interface OpenEnds {
  yes: boolean
  no: boolean
  next: boolean
}

export interface CanvasActions {
  /** Open the add menu at a screen position. */
  openMenu: (at: { x: number; y: number }, target: AddTarget) => void
  openEnds: Map<string, OpenEnds>
}

export const CanvasCtx = createContext<CanvasActions | null>(null)

export const useCanvas = () => useContext(CanvasCtx)
