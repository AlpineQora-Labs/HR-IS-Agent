/* Reading a workflow drawing: which steps sit on each side of a rule, where
   the sides rejoin, how the whole flow reads in words, and which lane each
   step belongs in. Pure functions — no React, no server calls.

   What a rule MEANS and whether a workflow is sound is decided by the server
   (ApprovalRouteEngine). Nothing here evaluates a request. */

export type WfData = {
  label?: string
  approverRole?: string
  condition?: string
  lit?: boolean
  emailAttached?: boolean
  emailTemplateId?: string
  emailTemplateName?: string
  emailTrigger?: string
  smsTemplateId?: string
  smsTemplateName?: string
  smsTrigger?: string
  /** Candidate journey: the point this message is sent at (the server's key for it). */
  journeyPoint?: string
  /** Exception: who reviews it, and why requests land here. */
  reviewerRole?: string
  reason?: string
}

export interface GNode {
  id: string
  type?: string
  data: Record<string, unknown>
}

export interface GEdge {
  id: string
  source: string
  target: string
  sourceHandle?: string | null
}

export type Answer = 'yes' | 'no'

/** The trigger of the workflow that draws the candidate journey. */
export const JOURNEY = 'Candidate journey'

/** The rules the engine can check. `value` is the stored key — never reword it.
    A journey's rules are things a candidate does; a request's rules are facts about it. */
export const RULES: { value: string; sentence: string; journey?: boolean }[] = [
  { value: 'Flagged critical', sentence: 'the request is flagged critical' },
  { value: 'Short notice (under 14 days)', sentence: 'notice is under 14 days' },
  { value: 'In-person event', sentence: 'the event is in person' },
  { value: 'Virtual event', sentence: 'the event is virtual' },
  { value: 'Candidate asks for status', sentence: 'the candidate asks for their status', journey: true },
  { value: 'Candidate asks to reschedule', sentence: 'the candidate asks to reschedule', journey: true },
]

/** The rules a workflow of this trigger can be drawn with. */
export function rulesFor(trigger: string | undefined) {
  const journey = trigger === JOURNEY
  return RULES.filter((r) => !!r.journey === journey)
}

const START = 'trigger'
const d = (n: GNode | undefined) => (n?.data ?? {}) as WfData

/** "the request is flagged critical"; null when nothing is chosen yet. */
export function ruleSentence(condition: string | undefined): string | null {
  if (condition === undefined || condition === 'Always') return 'always'
  if (condition.trim() === '') return null
  return RULES.find((r) => r.value === condition)?.sentence ?? condition
}

/** What the rule card says: the question, as a sentence. */
export function ruleTitle(condition: string | undefined): string {
  const s = ruleSentence(condition)
  if (s === null) return 'Choose what to check'
  return s === 'always' ? 'Always' : `If ${s}`
}

const text = (v: string | undefined) => (v && v.trim()) || ''

/** What a person would call this step. */
export function stepName(n: GNode | undefined): string {
  if (!n) return 'a missing step'
  const data = d(n)
  switch (n.type) {
    case 'trigger':
      return text(data.label) || 'Start'
    case 'approval':
      return text(data.label) || 'Approval'
    case 'condition':
      return ruleTitle(data.condition)
    case 'email':
      return text(data.emailTemplateName) || 'Email'
    case 'sms':
      return text(data.smsTemplateName) || 'Text message'
    case 'exception':
      return text(data.label) || 'Exception'
    case 'end':
      return text(data.label) || 'Approved'
    case 'policy':
      return 'Auto-approve'
    default:
      return text(data.label) || 'Step'
  }
}

export const isOutcome = (n: GNode | undefined) => n?.type === 'end' || n?.type === 'exception'
const isException = (n: GNode | undefined) => n?.type === 'exception'

/** The step the flow starts from: the trigger, by type, else by its legacy id. */
export function startOf(nodes: GNode[]): string | null {
  return nodes.find((n) => n.type === 'trigger')?.id ?? (nodes.some((n) => n.id === START) ? START : null)
}

/** Lines that loop back to a step already on the way (found from the start). */
export function backEdges(nodes: GNode[], edges: GEdge[]): Set<string> {
  const out = new Map<string, GEdge[]>()
  for (const e of edges) out.set(e.source, [...(out.get(e.source) ?? []), e])
  const back = new Set<string>()
  const state = new Map<string, number>() // 1 on the way · 2 done
  const visit = (id: string) => {
    state.set(id, 1)
    for (const e of out.get(id) ?? []) {
      const st = state.get(e.target) ?? 0
      if (st === 1) back.add(e.id)
      else if (st === 0) visit(e.target)
    }
    state.set(id, 2)
  }
  const start = startOf(nodes)
  if (start) visit(start)
  // Steps not connected to the start can still loop among themselves.
  for (const n of nodes) if (!state.has(n.id)) visit(n.id)
  return back
}

/** Every step a line leads to from `from` (including `from`). */
export function reach(edges: GEdge[], from: string): Set<string> {
  const seen = new Set<string>([from])
  const todo = [from]
  while (todo.length) {
    const cur = todo.pop()!
    for (const e of edges) {
      if (e.source === cur && !seen.has(e.target)) {
        seen.add(e.target)
        todo.push(e.target)
      }
    }
  }
  return seen
}

/** Which row each step sits on: its longest distance from the start, loop-backs aside. */
export function rows(nodes: GNode[], edges: GEdge[]): Map<string, number> {
  const back = backEdges(nodes, edges)
  const fwd = edges.filter((e) => !back.has(e.id))
  const row = new Map<string, number>()
  const start = startOf(nodes)
  if (start) row.set(start, 0)
  for (let pass = 0; pass <= nodes.length; pass++) {
    let changed = false
    for (const e of fwd) {
      const from = row.get(e.source)
      if (from == null) continue
      if ((row.get(e.target) ?? -1) < from + 1) {
        row.set(e.target, from + 1)
        changed = true
      }
    }
    if (!changed) break
  }
  return row
}

export type SideState =
  /** nothing chosen: no line leaves this answer */
  | 'open'
  /** goes straight on to a step the other answer also reaches, or to the outcome */
  | 'continue'
  /** has steps of its own first */
  | 'steps'
  /** stops: sent for review */
  | 'stop'
  /** goes back to a step the request has already been through */
  | 'back'

export interface Side {
  answer: Answer
  state: SideState
  edge?: GEdge
  /** the first step this answer leads to */
  target?: GNode
  /** steps that only happen on this side, in order */
  steps: GNode[]
  /** where this side rejoins the other, or the outcome it ends at */
  join?: GNode
  /** the earlier step this side's last step loops back to */
  returnsTo?: GNode
}

/** What happens on one answer of a rule. */
export function sideOf(nodes: GNode[], edges: GEdge[], ruleId: string, answer: Answer): Side {
  const byId = new Map(nodes.map((n) => [n.id, n]))
  const edge = edges.find((e) => e.source === ruleId && e.sourceHandle === answer)
  const target = edge ? byId.get(edge.target) : undefined
  if (!edge || !target) return { answer, state: 'open', steps: [] }
  if (target.type === 'exception') return { answer, state: 'stop', edge, target, steps: [], join: target }

  const back = backEdges(nodes, edges)
  if (back.has(edge.id)) return { answer, state: 'back', edge, target, steps: [], returnsTo: target }
  const fwd = edges.filter((e) => !back.has(e.id))
  const other = fwd.find((e) => e.source === ruleId && e.sourceHandle === (answer === 'yes' ? 'no' : 'yes'))
  const otherReach = other ? reach(fwd, other.target) : new Set<string>()

  const steps: GNode[] = []
  const seen = new Set<string>()
  let cur: GNode | undefined = target
  let join: GNode | undefined
  let returnsTo: GNode | undefined
  while (cur) {
    if (otherReach.has(cur.id) || isOutcome(cur)) {
      join = cur
      break
    }
    if (seen.has(cur.id)) break
    seen.add(cur.id)
    steps.push(cur)
    if (cur.type === 'condition') break // a rule inside a side: its own panel tells the rest
    const curId: string = cur.id
    const next = fwd.find((e) => e.source === curId && byId.get(e.target)?.type !== 'policy')
    if (!next) {
      const loop = edges.find((e) => e.source === curId && back.has(e.id))
      returnsTo = loop ? byId.get(loop.target) : undefined
    }
    cur = next ? byId.get(next.target) : undefined
  }
  return { answer, state: steps.length ? 'steps' : 'continue', edge, target, steps, join, returnsTo }
}

/**
 * Does this answer lead on to the rest of the flow? Not when it is unset,
 * stops for review (at once or after its own steps), or loops back.
 */
export function carriesOn(side: Side): boolean {
  if (side.state !== 'continue' && side.state !== 'steps') return false
  if (isException(side.join)) return false
  return !(side.state === 'steps' && !side.join && side.returnsTo)
}

/** Does this answer get as far as the outcome? An unfinished side does not, nor one whose every path stops. */
function arrives(nodes: GNode[], edges: GEdge[], side: Side): boolean {
  if (!carriesOn(side) || !side.target) return false
  const byId = new Map(nodes.map((n) => [n.id, n]))
  const back = backEdges(nodes, edges)
  const fwd = edges.filter((e) => !back.has(e.id))
  return [...reach(fwd, side.target.id)].some((id) => byId.get(id)?.type === 'end')
}

/**
 * The one answer that is the rest of the flow, if there is one: the only
 * answer that reaches the outcome, or failing that the only one that carries
 * on at all. When both do (or neither), neither is — they are two sides.
 */
export function mainOf(nodes: GNode[], edges: GEdge[], ruleId: string): Answer | null {
  const yes = sideOf(nodes, edges, ruleId, 'yes')
  const no = sideOf(nodes, edges, ruleId, 'no')
  const a = arrives(nodes, edges, yes)
  const b = arrives(nodes, edges, no)
  if (a !== b) return a ? 'yes' : 'no'
  if (carriesOn(yes) !== carriesOn(no)) return carriesOn(yes) ? 'yes' : 'no'
  return null
}

/** The first step both answers lead to, for when each side ends in a rule of its own and cannot name it. */
function meetOf(nodes: GNode[], edges: GEdge[], ruleId: string): GNode | undefined {
  const back = backEdges(nodes, edges)
  const fwd = edges.filter((e) => !back.has(e.id))
  const from = (answer: Answer) => {
    const e = fwd.find((x) => x.source === ruleId && x.sourceHandle === answer)
    return e ? reach(fwd, e.target) : new Set<string>()
  }
  const yes = from('yes')
  const no = from('no')
  const row = rows(nodes, edges)
  const at = (n: GNode) => row.get(n.id) ?? Number.MAX_SAFE_INTEGER
  return nodes
    .filter((n) => yes.has(n.id) && no.has(n.id) && n.type !== 'exception' && n.type !== 'policy')
    .sort((a, b) => at(a) - at(b))[0]
}

/** Where a rule's two answers come back together; nowhere unless both are sides of their own that carry on. */
export function joinOf(nodes: GNode[], edges: GEdge[], ruleId: string): GNode | undefined {
  const yes = sideOf(nodes, edges, ruleId, 'yes')
  const no = sideOf(nodes, edges, ruleId, 'no')
  if (!carriesOn(yes) || !carriesOn(no) || mainOf(nodes, edges, ruleId)) return undefined
  return yes.join ?? no.join ?? meetOf(nodes, edges, ruleId)
}

/**
 * Where requests go once a rule is taken out: the first step of the answer
 * that is the rest of the flow, so that flow is kept whole — or, when the
 * answers are two sides, the step where they rejoin.
 */
export function afterRule(nodes: GNode[], edges: GEdge[], ruleId: string): GNode | undefined {
  const main = mainOf(nodes, edges, ruleId)
  if (main) return sideOf(nodes, edges, ruleId, main).target
  return joinOf(nodes, edges, ruleId)
}

/**
 * The steps an answer could carry on to, nearest first: each step on the other
 * answer's way and the flow after it. With nothing to follow, the outcome.
 */
export function continueChoices(nodes: GNode[], edges: GEdge[], ruleId: string, answer: Answer): GNode[] {
  const byId = new Map(nodes.map((n) => [n.id, n]))
  const back = backEdges(nodes, edges)
  const fwd = edges.filter((e) => !back.has(e.id))
  const other = fwd.find((e) => e.source === ruleId && e.sourceHandle === (answer === 'yes' ? 'no' : 'yes'))
  const found: GNode[] = []
  const seen = new Set<string>([ruleId])
  let cur = other ? byId.get(other.target) : undefined
  while (cur && !seen.has(cur.id) && found.length < 12) {
    seen.add(cur.id)
    if (cur.type === 'exception') break
    found.push(cur)
    if (cur.type === 'end') break
    const id: string = cur.id
    if (cur.type === 'condition') {
      cur = afterRule(nodes, edges, id)
    } else {
      const next = fwd.find((e) => e.source === id && byId.get(e.target)?.type !== 'policy')
      cur = next ? byId.get(next.target) : undefined
    }
  }
  if (found.length === 0) {
    const end = nodes.find((n) => n.type === 'end')
    if (end) found.push(end)
  }
  return found
}

/* ------------------------------------------------------------ in words */

export interface Sentence {
  /** the step to select when this sentence is clicked */
  id: string
  text: string
}

function sideInWords(side: Side): string {
  switch (side.state) {
    case 'open':
      return 'nothing is chosen yet'
    case 'stop':
      return `it stops and is sent for review (${stepName(side.target)})`
    case 'back':
      return `it goes back to ${stepName(side.target)}`
    case 'continue':
      return `it continues to ${stepName(side.join)}`
    case 'steps': {
      const chain = side.steps.map(stepInWords).join(', then ')
      if (isException(side.join)) return `${chain}, then it stops and is sent for review (${stepName(side.join)})`
      if (side.join) return `${chain}, then it continues to ${stepName(side.join)}`
      return side.returnsTo ? `${chain}, then it goes back to ${stepName(side.returnsTo)}` : chain
    }
  }
}

function stepInWords(n: GNode): string {
  const data = d(n)
  switch (n.type) {
    case 'approval':
      return `${text(data.approverRole) || 'someone'} must approve`
    case 'email':
      return text(data.emailTemplateName) ? `the email “${text(data.emailTemplateName)}” is sent` : 'an email is sent'
    case 'sms':
      return text(data.smsTemplateName) ? `the text message “${text(data.smsTemplateName)}” is sent` : 'a text message is sent'
    case 'condition':
      return `another rule is checked (${lowerFirst(ruleTitle(data.condition))})`
    default:
      return lowerFirst(stepName(n))
  }
}

const capital = (s: string) => (s ? s[0].toUpperCase() + s.slice(1) : s)

/** Lowercase a leading capital for use mid-sentence, leaving acronyms (IDV, SMS) alone. */
const lowerFirst = (s: string) => (s.length > 1 && /[a-z]/.test(s[1]) ? s[0].toLowerCase() + s.slice(1) : s)

/** One rule as a sentence: what it checks, and what each answer does. */
export function ruleInWords(nodes: GNode[], edges: GEdge[], ruleId: string): string {
  const rule = nodes.find((n) => n.id === ruleId)
  const yes = sideOf(nodes, edges, ruleId, 'yes')
  const no = sideOf(nodes, edges, ruleId, 'no')
  const s = ruleSentence(d(rule).condition)
  const lead = s === null ? 'A rule with nothing to check yet' : s === 'always' ? 'Always' : `If ${s}`
  const main = mainOf(nodes, edges, ruleId)
  const words = (x: Side) => (x.answer === main ? 'it carries on' : sideInWords(x))
  return `${lead}: ${words(yes)}. Otherwise ${words(no)}.`
}

/** The whole workflow as numbered sentences, in the order a request meets them. */
export function describe(nodes: GNode[], edges: GEdge[]): Sentence[] {
  const byId = new Map(nodes.map((n) => [n.id, n]))
  const back = backEdges(nodes, edges)
  const fwd = edges.filter((e) => !back.has(e.id))
  const out: Sentence[] = []
  const seen = new Set<string>()

  const say = (n: GNode): string => {
    if (n.type === 'trigger') return `Starts when: ${stepName(n)}`
    if (n.type === 'exception') return `Stops and is sent for review: ${stepName(n)}`
    if (n.type === 'end') return `Outcome: ${stepName(n)}`
    return capital(stepInWords(n))
  }

  /** Tell the flow from one step on, stopping short of anything in `stop`. */
  const walk = (from: GNode | undefined, stop: Set<string>) => {
    let cur = from
    while (cur && !stop.has(cur.id) && !seen.has(cur.id)) {
      seen.add(cur.id)
      const id: string = cur.id
      if (cur.type !== 'condition') {
        out.push({ id, text: say(cur) })
        const next = fwd.find((e) => e.source === id && byId.get(e.target)?.type !== 'policy')
        cur = next ? byId.get(next.target) : undefined
        continue
      }
      out.push({ id, text: ruleInWords(nodes, edges, id) })
      const yes = sideOf(nodes, edges, id, 'yes')
      const no = sideOf(nodes, edges, id, 'no')
      const main = mainOf(nodes, edges, id)
      // Where the flow picks up after this rule. Whatever a side holds is told
      // before that point, and never runs past it.
      const next = afterRule(nodes, edges, id)
      const beyond = new Set(stop)
      if (next) for (const x of reach(fwd, next.id)) beyond.add(x)
      for (const side of [yes, no]) {
        if (side.answer === main) continue
        for (const n of side.steps) {
          if (n.type === 'condition') walk(n, beyond) // a rule inside a side tells its own story
          else if (!beyond.has(n.id)) seen.add(n.id) // a step of the flow outside this rule keeps its own sentence
        }
      }
      cur = next
    }
  }

  const start = startOf(nodes)
  walk(start ? byId.get(start) : undefined, new Set())
  return out
}

/* ------------------------------------------------------------ what a change cuts off */

/** Steps that could be reached before and could not be after `nextEdges` replace the lines. */
export function cutOff(nodes: GNode[], edges: GEdge[], nextEdges: GEdge[]): GNode[] {
  const start = startOf(nodes)
  if (!start) return []
  const before = reach(edges, start)
  const after = reach(nextEdges, start)
  return nodes.filter((n) => before.has(n.id) && !after.has(n.id))
}

/* ------------------------------------------------------------ layout lanes */

/**
 * Which column each step sits in, relative to the main flow (0). Steps that
 * only happen on a rule's YES side move left, NO-only steps right, so a side's
 * steps never sit under the other side's line. When only one answer leads on,
 * that answer IS the main flow and keeps the rule's column.
 *
 * A side moves far enough to clear whatever it holds: a rule inside a side
 * spreads its own sides around it, and the outer side steps out by that much
 * more, so a side's steps do not drift back into the main column. Steps that
 * several rules lead to can still share a lane on a row; the layout sets those
 * side by side, outward of the main flow.
 */
export function lanes(nodes: GNode[], edges: GEdge[]): Map<string, number> {
  const back = backEdges(nodes, edges)
  const fwd = edges.filter((e) => !back.has(e.id))
  const row = rows(nodes, edges)
  const rowOf = (id: string) => row.get(id) ?? Number.MAX_SAFE_INTEGER

  /** Per rule: the steps each moved side holds, and how many lanes it moves (signed). */
  const moves = new Map<string, { only: Set<string>; by: number }[]>()

  /** Where a step sits relative to a column, counting only the rules inside `within`. */
  const offset = (id: string, within: Set<string>) => {
    let sum = 0
    for (const [ruleId, sides] of moves) {
      if (!within.has(ruleId)) continue
      for (const side of sides) if (side.only.has(id)) sum += side.by
    }
    return sum
  }
  /** How far a set of steps spreads to one side of its own column, on the rows given. */
  const spread = (set: Set<string>, dir: 1 | -1, onRows?: [number, number]) => {
    let most = 0
    for (const id of set) {
      const r = rowOf(id)
      if (onRows && (r < onRows[0] || r > onRows[1])) continue
      most = Math.max(most, dir * offset(id, set))
    }
    return most
  }
  const span = (set: Set<string>): [number, number] => {
    const rs = [...set].map(rowOf)
    return [Math.min(...rs), Math.max(...rs)]
  }

  // Inner rules first: how far a side moves depends on what is inside it.
  const rules = nodes.filter((n) => n.type === 'condition').sort((a, b) => rowOf(b.id) - rowOf(a.id))
  for (const rule of rules) {
    const yes = fwd.find((e) => e.source === rule.id && e.sourceHandle === 'yes')
    const no = fwd.find((e) => e.source === rule.id && e.sourceHandle === 'no')
    const a = yes ? reach(fwd, yes.target) : new Set<string>()
    const b = no ? reach(fwd, no.target) : new Set<string>()
    const aOnly = new Set([...a].filter((x) => !b.has(x)))
    const bOnly = new Set([...b].filter((x) => !a.has(x)))
    const main = mainOf(nodes, edges, rule.id)
    const aMain = main === 'yes'
    const bMain = main === 'no'
    const sides: { only: Set<string>; by: number }[] = []
    if (!aMain && aOnly.size) {
      sides.push({ only: aOnly, by: -(1 + spread(aOnly, 1) + (bMain ? spread(bOnly, -1, span(aOnly)) : 0)) })
    }
    if (!bMain && bOnly.size) {
      sides.push({ only: bOnly, by: 1 + spread(bOnly, -1) + (aMain ? spread(aOnly, 1, span(bOnly)) : 0) })
    }
    moves.set(rule.id, sides)
  }

  const all = new Set(nodes.map((n) => n.id))
  return new Map(nodes.map((n) => [n.id, offset(n.id, all)]))
}
