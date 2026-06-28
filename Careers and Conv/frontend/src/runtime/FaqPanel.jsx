import { useState } from 'react';
import { askFaq } from '../api/client.js';

// Deterministic FAQ lookup (§10). Sits beside the screening flow as a help affordance; it never
// dead-ends — an unmatched query returns the topics the KB can answer.
export default function FaqPanel() {
  const [q, setQ] = useState('');
  const [result, setResult] = useState(null);

  const lookup = async (query) => {
    const text = (query ?? q).trim();
    try {
      const r = await askFaq(text);
      setResult(r);
    } catch {
      setResult({ matched: false, answer: 'FAQ is unavailable right now.', topics: [] });
    }
  };

  return (
    <details className="faq">
      <summary>Have a question? (pay, benefits, work mode, timeline, sponsorship)</summary>
      <div className="faq-row">
        <input
          value={q}
          placeholder="e.g. do you sponsor visas?"
          onChange={(e) => setQ(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') lookup(); }}
        />
        <button className="quick" onClick={() => lookup()} style={{ all: 'unset', cursor: 'pointer', color: 'var(--navy)', fontWeight: 600, padding: '0 8px' }}>Ask</button>
      </div>
      {result && (
        <>
          <div className="answer">
            {result.matched && result.topic ? <strong>{result.topic}: </strong> : null}
            {result.answer}
          </div>
          {result.topics && result.topics.length > 0 && (
            <div className="topics">
              {result.topics.map((t) => (
                <button key={t} onClick={() => { setQ(t); lookup(t); }}>{t}</button>
              ))}
            </div>
          )}
        </>
      )}
    </details>
  );
}
