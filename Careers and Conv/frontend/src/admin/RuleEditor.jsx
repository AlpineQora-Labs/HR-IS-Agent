import { useEffect, useState } from 'react';
import { useAdmin } from '../store/useAdmin.js';

const ANSWER_TYPES = ['choice', 'number', 'text'];
const OPERATORS = ['equals', 'notEquals', 'gte', 'lte', 'contains'];

// The regulated surface (BUILD_SPEC §6/§8). Every edit is linted against the engine guardrail live,
// so an author literally cannot save a knockout the engine would reject.
export default function RuleEditor({ flow, nodeKey, rule, onClose }) {
  const { ruleClasses, lint, saveRule } = useAdmin();
  const [f, setF] = useState(() => ({
    ruleKey: rule?.ruleKey || '',
    label: rule?.label || '',
    questionText: rule?.questionText || '',
    answerType: (rule?.answerType || 'choice').toLowerCase(),
    options: (rule?.options || []).join(', '),
    kind: rule?.kind || 'SCORED',
    // Raw Rule entities serialize the class as `ruleClassName`; the write contract uses `ruleClass`.
    ruleClass: rule?.ruleClassName || rule?.ruleClass || (ruleClasses[0]?.name ?? ''),
    operator: rule?.evaluation?.operator || 'equals',
    value: rule?.evaluation?.value || '',
    weight: rule?.weight ?? 1,
    jobRelatedness: rule?.jobRelatedness || '',
  }));
  const [violations, setViolations] = useState([]);
  const [saveError, setSaveError] = useState(null);
  const [saving, setSaving] = useState(false);

  const payload = () => ({
    flowId: flow.id, nodeKey, id: rule?.id,
    ruleKey: f.ruleKey, label: f.label, questionText: f.questionText,
    answerType: f.answerType,
    options: f.options.split(',').map((s) => s.trim()).filter(Boolean),
    kind: f.kind, weight: f.kind === 'SCORED' ? Number(f.weight) || null : null,
    ruleClass: f.ruleClass, evaluation: { operator: f.operator, value: f.value },
    jobRelatedness: f.jobRelatedness,
  });

  // Live guardrail lint whenever a governed field changes.
  useEffect(() => {
    let active = true;
    if (!f.ruleKey || !f.label) { setViolations([]); return; }
    lint(payload()).then((v) => { if (active) setViolations(v || []); }).catch(() => {});
    return () => { active = false; };
    // eslint-disable-next-line
  }, [f.ruleKey, f.label, f.answerType, f.kind, f.ruleClass, f.operator, f.value, f.jobRelatedness]);

  const selectedClass = ruleClasses.find((c) => c.name === f.ruleClass);
  const blocked = violations.length > 0 || !f.ruleKey || !f.label;

  const onSave = async () => {
    setSaving(true);
    setSaveError(null);
    try {
      await saveRule(payload(), !rule);
      onClose();
    } catch (e) {
      setSaveError(e.message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="rule-editor">
      <div className="rule-grid">
        <div><label>Rule key</label><input value={f.ruleKey} disabled={!!rule}
          onChange={(e) => setF({ ...f, ruleKey: e.target.value })} placeholder="e.g. auth" /></div>
        <div><label>Label</label><input value={f.label}
          onChange={(e) => setF({ ...f, label: e.target.value })} placeholder="Work authorization" /></div>
      </div>

      <label>Question text</label>
      <textarea value={f.questionText} onChange={(e) => setF({ ...f, questionText: e.target.value })} />

      <div className="rule-grid">
        <div><label>Answer type</label>
          <select value={f.answerType} onChange={(e) => setF({ ...f, answerType: e.target.value })}>
            {ANSWER_TYPES.map((t) => <option key={t}>{t}</option>)}
          </select>
        </div>
        <div><label>Options (comma-separated, for choice)</label>
          <input value={f.options} onChange={(e) => setF({ ...f, options: e.target.value })} placeholder="Yes, No" /></div>
      </div>

      <div className="rule-grid">
        <div><label>Kind</label>
          <select value={f.kind} onChange={(e) => setF({ ...f, kind: e.target.value })}>
            <option value="SCORED">SCORED</option>
            <option value="KNOCKOUT">KNOCKOUT</option>
          </select>
        </div>
        <div><label>Rule class</label>
          <select value={f.ruleClass} onChange={(e) => setF({ ...f, ruleClass: e.target.value })}>
            {ruleClasses.map((c) => (
              <option key={c.name} value={c.name}>{c.name}{c.knockoutAllowed ? '' : ' (scored only)'}</option>
            ))}
          </select>
        </div>
      </div>

      {selectedClass && !selectedClass.knockoutAllowed && (
        <div className="class-hint">“{selectedClass.name}” is <b>scored-only</b> — it cannot be a knockout (screens out qualified candidates).</div>
      )}

      <div className="rule-grid">
        <div><label>Operator</label>
          <select value={f.operator} onChange={(e) => setF({ ...f, operator: e.target.value })}>
            {OPERATORS.map((o) => <option key={o}>{o}</option>)}
          </select>
        </div>
        <div><label>Value</label><input value={f.value} onChange={(e) => setF({ ...f, value: e.target.value })} placeholder="Yes / 5" /></div>
      </div>

      {f.kind === 'SCORED' && (
        <div className="rule-grid">
          <div><label>Weight</label><input type="number" value={f.weight} onChange={(e) => setF({ ...f, weight: e.target.value })} /></div>
          <div />
        </div>
      )}

      {f.kind === 'KNOCKOUT' && (
        <>
          <label>BFOQ job-relatedness justification (required for knockout)</label>
          <textarea value={f.jobRelatedness} onChange={(e) => setF({ ...f, jobRelatedness: e.target.value })}
            placeholder="Why this is a bona-fide, non-negotiable requirement for the role…" />
        </>
      )}

      {violations.length > 0 && (
        <div className="guardrail-violations">
          <div className="gv-title">⚠ Guardrail — cannot save:</div>
          <ul>{violations.map((v, i) => <li key={i}>{v}</li>)}</ul>
        </div>
      )}
      {saveError && <div className="error">⚠ {saveError}</div>}

      <div className="rule-actions">
        <button className="btn-advance" disabled={blocked || saving} onClick={onSave}>
          {saving ? 'Saving…' : (rule ? 'Save rule' : 'Attach rule')}
        </button>
        <button className="btn-ghost" onClick={onClose}>Cancel</button>
      </div>
    </div>
  );
}
