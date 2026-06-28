import { useEffect, useRef, useState } from 'react';
import { useAdmin } from '../store/useAdmin.js';
import Composer from '../runtime/Composer.jsx';

let seq = 0;
const bot = (text) => ({ id: `pb${seq++}`, who: 'bot', text });
const user = (text) => ({ id: `pu${seq++}`, who: 'user', text });

// Preview / simulate (§8.7): runs the draft as a test candidate via the real engine. No session,
// answer, result, or audit rows are written — it never touches the real pipeline.
export default function PreviewPanel({ flow, onClose }) {
  const { simulate } = useAdmin();
  const [log, setLog] = useState([]);
  const [awaiting, setAwaiting] = useState(null);
  const [trail, setTrail] = useState([]);
  const [result, setResult] = useState(null);
  const [ended, setEnded] = useState(false);
  const [answers, setAnswers] = useState([]);
  const [error, setError] = useState(null);
  const bodyRef = useRef(null);

  const ingest = (step, { userBubble, replace } = {}) => {
    setLog((prev) => {
      const next = replace ? [] : [...prev]; // replace makes the initial load idempotent
      if (userBubble) next.push(user(userBubble));
      (step.messages || []).forEach((m) => { if (m.text) next.push(bot(m.text)); });
      if (step.awaiting && step.awaiting.text) next.push(bot(step.awaiting.text));
      return next;
    });
    setAwaiting(step.awaiting || null);
    setTrail(step.trail || []);
    setResult(step.resultStatus || null);
    setEnded(step.sessionStatus === 'COMPLETED');
  };

  const run = async (nextAnswers, opts) => {
    try {
      const step = await simulate(flow.id, nextAnswers);
      ingest(step, opts);
    } catch (e) {
      setError(e.message);
    }
  };

  const restart = () => {
    setAwaiting(null); setTrail([]); setResult(null); setEnded(false);
    setAnswers([]); setError(null);
    run([], { replace: true });
  };

  useEffect(() => { restart(); /* eslint-disable-next-line */ }, [flow.id]);
  useEffect(() => { const el = bodyRef.current; if (el) el.scrollTop = el.scrollHeight; }, [log]);

  const onSend = (value) => {
    if (!awaiting) return;
    const next = [...answers, { nodeKey: awaiting.nodeKey, answer: String(value) }];
    setAnswers(next);
    run(next, { userBubble: String(value) });
  };

  return (
    <div className="preview-overlay" onClick={onClose}>
      <div className="preview-modal" onClick={(e) => e.stopPropagation()}>
        <div className="chat-head">
          <div className="avatar">▶</div>
          <div>
            <div className="who">Preview · {flow.name}</div>
            <div className="stat">Simulated · nothing is saved to the pipeline</div>
          </div>
          <button className="restart" onClick={restart}>Restart</button>
          <button className="restart" style={{ marginLeft: 8 }} onClick={onClose}>✕ Close</button>
        </div>

        <div className="preview-body">
          <div className="chat-body" ref={bodyRef}>
            {log.map((b) => (
              <div key={b.id} className={`bubble ${b.who === 'user' ? 'user' : ''}`}>{b.text}</div>
            ))}
            {error && <div className="error">⚠ {error}</div>}
            {ended && (
              <div className={`completion ${result === 'PASSED' ? 'passed' : 'review'}`}>
                <div className="title">Result: {result}</div>
                {result === 'NEEDS_REVIEW' ? 'Routed to a human — never auto-rejected.' : 'Meets the baseline requirements.'}
              </div>
            )}
            {!ended && <Composer awaiting={awaiting} disabled={!awaiting} onSend={onSend} />}
          </div>

          <div className="preview-trail">
            <div className="flow-group-label">Decision trail</div>
            {trail.length === 0 && <div className="trail-empty">No rules evaluated yet.</div>}
            {trail.map((t) => (
              <div className="trail-item" key={t.ruleKey + t.result}>
                <div className="row">
                  <span className={`chip ${t.result === 'PASS' ? 'pass' : 'fail'}`}>{t.result}</span>
                  <span className="label" style={{ fontSize: 12 }}>{t.label}</span>
                </div>
                {t.reason && <div className="reason">{t.reason}</div>}
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
