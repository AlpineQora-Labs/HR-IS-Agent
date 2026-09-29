import { Icons, SPEC } from './canvasTheme'
import {
  RULES, continueChoices, mainOf, ruleInWords, ruleSentence, sideOf, stepName,
  type Answer, type GEdge, type GNode, type Side, type WfData,
} from './graphModel'

/* A rule, written as a sentence: IF … THEN (yes) … OTHERWISE (no) …
   Both answers are always shown, so "otherwise" can never be forgotten. */

/** add: after this answer's own steps · addFirst: on the answer's own line, straight after the rule */
export type SideChoice = 'continue' | 'add' | 'addFirst' | 'stop'

type Anchor = { x: number; y: number }

const capital = (s: string) => (s ? s[0].toUpperCase() + s.slice(1) : s)

/** Step names for a list, told apart when two steps share a name. */
function named(steps: GNode[]): { id: string; name: string }[] {
  const count = new Map<string, number>()
  for (const n of steps) count.set(stepName(n), (count.get(stepName(n)) ?? 0) + 1)
  const met = new Map<string, number>()
  return steps.map((n) => {
    const name = stepName(n)
    if ((count.get(name) ?? 0) < 2) return { id: n.id, name }
    const k = (met.get(name) ?? 0) + 1
    met.set(name, k)
    return { id: n.id, name: `${name} (${k})` }
  })
}

function SideEditor({
  side,
  main,
  choices,
  onChoose,
  onSelect,
}: {
  side: Side
  /** true when the other answer does not carry on, so this answer is the rest of the flow */
  main: boolean
  /** the steps this answer could carry on to, nearest first */
  choices: GNode[]
  onChoose: (choice: SideChoice, anchor: Anchor, destId?: string) => void
  onSelect: (id: string) => void
}) {
  const yes = side.answer === 'yes'
  const word = yes ? 'YES' : 'NO'
  const at = (e: { currentTarget: Element }) => {
    const r = e.currentTarget.getBoundingClientRect()
    return { x: r.left + r.width / 2, y: r.bottom + 6 }
  }
  const stepButton = (n: GNode) => (
    <button key={n.id} className="wfc-rule__step" onClick={() => onSelect(n.id)} title="Show this step">
      <span
        className="wfc-rule__stepic"
        style={{ background: SPEC[n.type as keyof typeof SPEC]?.iconBg, color: SPEC[n.type as keyof typeof SPEC]?.iconFg }}
      >
        {Icons[n.type as keyof typeof Icons]}
      </span>
      {stepName(n)}
    </button>
  )
  // Where this answer goes now is always one of the places it can go.
  const going = side.state === 'continue' ? side.join : undefined
  const places = named(going && !choices.some((n) => n.id === going.id) ? [going, ...choices] : choices)
  const unset = side.state === 'open' || side.state === 'stop' || side.state === 'back'

  return (
    <div className="wfc-rule__side">
      <div className="wfc-rule__sidehead">
        <span className="wfc-rule__word">{yes ? 'Then' : 'Otherwise'}</span>
        <span className={`wfc-rule__pill wfc-rule__pill--${side.answer}`}>{word}</span>
      </div>

      {side.state === 'open' && <div className="wfc-rule__open">Nothing chosen yet.</div>}

      {main && (
        <div className="wfc-rule__says">
          Carries on to <b>{stepName(side.target)}</b> and the rest of the flow.
        </div>
      )}

      {!main &&
        side.state === 'continue' &&
        (places.length > 1 ? (
          <label className="wfc-rule__goto">
            Continues to
            <select
              className="select"
              value={going?.id ?? ''}
              aria-label={`Where the ${word} side continues to`}
              onChange={(e) => onChoose('continue', at(e), e.target.value)}
            >
              {places.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.name}
                </option>
              ))}
            </select>
          </label>
        ) : (
          <div className="wfc-rule__says">
            Continues to <b>{stepName(side.join)}</b>.
          </div>
        ))}

      {!main && side.state === 'steps' && (
        <>
          <div className="wfc-rule__steps">{side.steps.map(stepButton)}</div>
          {side.join && (
            <div className="wfc-rule__says">
              {side.join.type === 'exception' ? 'Then stops and is sent for review: ' : 'Then continues to '}
              <b>{stepName(side.join)}</b>.
            </div>
          )}
          {!side.join && side.returnsTo && (
            <div className="wfc-rule__says">
              Then goes back to <b>{stepName(side.returnsTo)}</b>.
            </div>
          )}
        </>
      )}

      {side.state === 'stop' && side.target && (
        <>
          <div className="wfc-rule__says">Stops here and is sent for review.</div>
          <div className="wfc-rule__steps">{stepButton(side.target)}</div>
        </>
      )}

      {side.state === 'back' && (
        <div className="wfc-rule__says">
          Goes back to <b>{stepName(side.target)}</b>.
        </div>
      )}

      <div className="wfc-rule__choices">
        {unset && places.length === 1 && (
          <button className="btn btn--outline btn--sm" onClick={(e) => onChoose('continue', at(e), places[0].id)}>
            Continue to {places[0].name}
          </button>
        )}
        {unset && places.length > 1 && (
          <select
            className="select wfc-rule__pick"
            value=""
            aria-label={`Continue the ${word} side to a step`}
            onChange={(e) => e.target.value && onChoose('continue', at(e), e.target.value)}
          >
            <option value="">Continue to…</option>
            {places.map((o) => (
              <option key={o.id} value={o.id}>
                {o.name}
              </option>
            ))}
          </select>
        )}
        {side.state !== 'stop' && (
          <button className="btn btn--outline btn--sm" onClick={(e) => onChoose(main ? 'addFirst' : 'add', at(e))}>
            {!main && side.state === 'steps' ? 'Add another step' : 'Add a step'}
          </button>
        )}
        {!main && (side.state === 'open' || side.state === 'continue' || side.state === 'back') && (
          <button className="btn btn--outline btn--sm" onClick={(e) => onChoose('stop', at(e))}>
            Stop and send for review
          </button>
        )}
      </div>
    </div>
  )
}

export default function RulePanel({
  rule,
  nodes,
  edges,
  onCondition,
  onSide,
  onSelect,
  onRemove,
}: {
  rule: GNode
  nodes: GNode[]
  edges: GEdge[]
  onCondition: (value: string) => void
  onSide: (answer: Answer, choice: SideChoice, anchor: Anchor, destId?: string) => void
  onSelect: (id: string) => void
  onRemove: () => void
}) {
  const current = (rule.data as WfData).condition
  const chosen = ruleSentence(current)
  const known = RULES.some((r) => r.value === current)
  // A rule saved before this panel existed may hold something the list no longer offers.
  const legacy = current !== undefined && current.trim() !== '' && !known && current !== 'Always' ? current : null

  return (
    <div className="wfc-rule">
      <div className="wfc-rule__sidehead">
        <span className="wfc-rule__word">If</span>
      </div>
      <div className="wfc-choice" role="radiogroup" aria-label="What this rule checks">
        {RULES.map((r) => (
          <button
            key={r.value}
            role="radio"
            aria-checked={current === r.value}
            className={`wfc-choice__row${current === r.value ? ' is-on' : ''}`}
            onClick={() => current !== r.value && onCondition(r.value)}
          >
            <span className="wfc-choice__name">{capital(r.sentence)}</span>
          </button>
        ))}
        {current === 'Always' || current === undefined ? (
          <div className="wfc-choice__row is-on" aria-disabled>
            <span className="wfc-choice__name">Always</span>
            <span className="wfc-choice__desc">Every request takes the YES side. Choose a check above to make this a real rule.</span>
          </div>
        ) : null}
        {legacy && (
          <div className="wfc-choice__row is-on wfc-choice__row--warn" aria-disabled>
            <span className="wfc-choice__name">{legacy}</span>
            <span className="wfc-choice__desc">The system can&rsquo;t check this yet. Choose a check above.</span>
          </div>
        )}
      </div>
      {chosen === null && <div className="wfc-rule__open">Choose what this rule checks.</div>}

      {(['yes', 'no'] as const).map((answer) => (
        <SideEditor
          key={answer}
          side={sideOf(nodes, edges, rule.id, answer)}
          main={mainOf(nodes, edges, rule.id) === answer}
          choices={continueChoices(nodes, edges, rule.id, answer)}
          onChoose={(choice, anchor, destId) => onSide(answer, choice, anchor, destId)}
          onSelect={onSelect}
        />
      ))}

      <div className="wfc-rule__readback">
        <div className="wfc-inspector__label">Reads as</div>
        {ruleInWords(nodes, edges, rule.id)}
      </div>
      <div className="wfc-inspector__hint">Each request takes one side only. The rule is checked once, when the request is submitted.</div>

      <button className="btn btn--danger btn--sm" onClick={onRemove}>
        Remove rule
      </button>
    </div>
  )
}
