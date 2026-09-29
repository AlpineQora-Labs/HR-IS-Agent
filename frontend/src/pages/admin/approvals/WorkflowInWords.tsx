import type { CheckIssue } from './approvals'
import type { Sentence } from './graphModel'

/* What the right-hand panel shows when nothing is selected: the workflow read
   back in plain sentences, and what is left to finish. The to-finish list
   comes from the server — the canvas never decides what is valid. */

export default function WorkflowInWords({
  sentences,
  issues,
  state,
  onSelect,
}: {
  sentences: Sentence[]
  /** what the server last said; kept on screen if a later check gets no answer */
  issues: CheckIssue[]
  /** checking until the server first answers; failed when the last check got no answer */
  state: 'checking' | 'done' | 'failed'
  onSelect: (id: string) => void
}) {
  const blocking = issues.filter((i) => i.blocking)
  const advice = issues.filter((i) => !i.blocking)

  return (
    <div className="wfc-words">
      <div className="wfc-inspector__label">This workflow, in words</div>
      {sentences.length === 0 ? (
        <div className="wfc-inspector__hint">Nothing is drawn yet.</div>
      ) : (
        <ol className="wfc-words__list">
          {sentences.map((s) => (
            <li key={s.id}>
              <button onClick={() => onSelect(s.id)} title="Show this step">
                {s.text}
              </button>
            </li>
          ))}
        </ol>
      )}

      <div className="wfc-inspector__label" style={{ marginTop: 6 }}>
        To finish{issues.length ? ` · ${issues.length}` : ''}
      </div>
      {state === 'failed' && (
        <div className="wfc-todo__unknown" role="status">
          This workflow couldn&rsquo;t be checked just now{issues.length ? ', so this list may be out of date' : ''}. It is checked
          again at your next change.
        </div>
      )}
      {state === 'checking' ? (
        <div className="wfc-inspector__hint">Checking…</div>
      ) : issues.length === 0 ? (
        state === 'done' && <div className="wfc-todo__clear">Nothing left to finish.</div>
      ) : (
        <ul className="wfc-todo">
          {[...blocking, ...advice].map((i, n) => (
            <li key={`${i.code}-${i.nodeId ?? n}`} className={i.blocking ? 'is-blocking' : undefined}>
              <button disabled={!i.nodeId} onClick={() => i.nodeId && onSelect(i.nodeId)}>
                {i.message}
                {i.blocking && <span className="wfc-todo__tag">Needed before switching on</span>}
              </button>
            </li>
          ))}
        </ul>
      )}

      <div className="wfc-inspector__hint">
        Use the <b>+</b> on any line to add a rule, an approval, an action or an exception. Select a step to change it.
      </div>
    </div>
  )
}
