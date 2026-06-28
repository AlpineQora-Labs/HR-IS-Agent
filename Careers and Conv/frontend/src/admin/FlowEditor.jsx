import { useState } from 'react';
import { useAdmin } from '../store/useAdmin.js';
import RuleEditor from './RuleEditor.jsx';
import PreviewPanel from './PreviewPanel.jsx';

const NODE_TYPES = ['message', 'question', 'screening_gate', 'human_handoff', 'end'];

export default function FlowEditor() {
  const { detail, publish, exportFlow, removeNode, busy } = useAdmin();
  const [previewing, setPreviewing] = useState(false);
  const [addingNode, setAddingNode] = useState(false);
  const [exported, setExported] = useState(null);

  const { flow, nodes, rules } = detail;
  const isDraft = flow.status === 'DRAFT';
  const ruleFor = (nodeKey) => rules.find((r) => r.nodeKey === nodeKey);

  return (
    <div className="flow-editor">
      <div className="panel flow-head">
        <div>
          <div className="flow-head-name">{flow.name}</div>
          <div className="flow-head-meta">
            <span className={`chip ${isDraft ? 'status-applied' : 'status-advanced'}`}>{flow.status} v{flow.version}</span>
            <span className="muted">· key: {flow.flowKey} · job: {flow.jobRef || '—'}</span>
          </div>
        </div>
        <div className="flow-head-actions">
          <button className="btn-ghost" onClick={() => setPreviewing(true)}>▶ Preview</button>
          <button className="btn-ghost" onClick={async () => setExported(await exportFlow(flow.id))}>Export JSON</button>
          {isDraft && (
            <button className="btn-advance" disabled={busy}
              onClick={() => { if (confirm('Publish an immutable version of this flow?')) publish(flow.id); }}>
              Publish version
            </button>
          )}
        </div>
      </div>

      {!isDraft && (
        <div className="callout-immutable">This is a published, immutable version — read only. Edit the draft to make changes.</div>
      )}

      {exported && (
        <div className="panel" style={{ padding: 14 }}>
          <div className="flow-group-label">Export (§6 contract)</div>
          <pre className="export-json">{JSON.stringify(exported, null, 2)}</pre>
          <button className="btn-ghost" onClick={() => setExported(null)}>Close</button>
        </div>
      )}

      <div className="node-list">
        {nodes.map((node) => (
          <NodeCard key={node.id} node={node} rule={ruleFor(node.nodeKey)} flow={flow}
            isDraft={isDraft} onDelete={() => removeNode(flow.id, node.nodeKey)} />
        ))}
      </div>

      {isDraft && (
        addingNode
          ? <NewNodeForm flowId={flow.id} nextIndex={nodes.length} onDone={() => setAddingNode(false)} />
          : <button className="search-cta" onClick={() => setAddingNode(true)}>+ Add node</button>
      )}

      {previewing && <PreviewPanel flow={flow} onClose={() => setPreviewing(false)} />}
    </div>
  );
}

function NodeCard({ node, rule, flow, isDraft, onDelete }) {
  const { saveNode } = useAdmin();
  const [editing, setEditing] = useState(false);
  const [editingRule, setEditingRule] = useState(false);
  const [form, setForm] = useState(() => toForm(node));

  const save = async () => {
    await saveNode(flow.id, fromForm(form, node), false);
    setEditing(false);
  };

  return (
    <div className="panel node-card">
      <div className="node-card-head">
        <span className="chip kind">{node.type}</span>
        <span className="node-key">{node.nodeKey}</span>
        {node.nextNodeKey && <span className="muted node-next">→ {node.nextNodeKey}</span>}
        {isDraft && (
          <span className="node-actions">
            <button className="btn-ghost sm" onClick={() => setEditing(!editing)}>{editing ? 'Cancel' : 'Edit'}</button>
            <button className="btn-ghost sm danger" onClick={onDelete}>Delete</button>
          </span>
        )}
      </div>

      {!editing ? (
        <div className="node-copy">{node.promptText || <span className="muted">(no copy)</span>}</div>
      ) : (
        <div className="node-edit">
          <label>Prompt text</label>
          <textarea value={form.promptText} onChange={(e) => setForm({ ...form, promptText: e.target.value })} />
          <label>Quick replies (comma-separated)</label>
          <input value={form.quickReplies} onChange={(e) => setForm({ ...form, quickReplies: e.target.value })} />
          <div className="node-edit-row">
            <div><label>Next node key</label>
              <input value={form.nextNodeKey} onChange={(e) => setForm({ ...form, nextNodeKey: e.target.value })} /></div>
            <div><label>Fallback text</label>
              <input value={form.fallbackText} onChange={(e) => setForm({ ...form, fallbackText: e.target.value })} /></div>
          </div>
          <label>Branches (one per line · answer=nodeKey · e.g. PASSED=end_pass)</label>
          <textarea value={form.branches} onChange={(e) => setForm({ ...form, branches: e.target.value })} />
          <button className="btn-advance" onClick={save}>Save node</button>
        </div>
      )}

      {node.type === 'QUESTION' && (
        <div className="node-rule">
          {rule ? (
            <div className="rule-summary">
              <span className={`chip ${rule.kind === 'KNOCKOUT' ? 'fail' : 'pass'}`}>{rule.kind}</span>
              <span className="muted">{rule.ruleClassName} · {rule.evaluation?.operator} {rule.evaluation?.value}</span>
              <span className="rule-label">{rule.label}</span>
              {isDraft && <button className="btn-ghost sm" onClick={() => setEditingRule(!editingRule)}>{editingRule ? 'Close' : 'Edit rule'}</button>}
            </div>
          ) : (
            isDraft && <button className="btn-ghost sm" onClick={() => setEditingRule(!editingRule)}>{editingRule ? 'Close' : '+ Attach rule'}</button>
          )}
          {editingRule && isDraft && (
            <RuleEditor flow={flow} nodeKey={node.nodeKey} rule={rule} onClose={() => setEditingRule(false)} />
          )}
        </div>
      )}
    </div>
  );
}

function NewNodeForm({ flowId, nextIndex, onDone }) {
  const { saveNode } = useAdmin();
  const [nodeKey, setNodeKey] = useState('');
  const [type, setType] = useState('message');
  const [promptText, setPromptText] = useState('');

  const create = async () => {
    await saveNode(flowId, {
      nodeKey, type, orderIndex: nextIndex, promptText,
      quickReplies: [], fallbackText: null, transitionText: null, nextNodeKey: null, branches: {},
    }, true);
    onDone();
  };

  return (
    <div className="panel new-node-form">
      <div className="flow-group-label">New node</div>
      <div className="node-edit-row">
        <div><label>Node key</label><input value={nodeKey} onChange={(e) => setNodeKey(e.target.value)} placeholder="e.g. q_skill" /></div>
        <div><label>Type</label>
          <select value={type} onChange={(e) => setType(e.target.value)}>
            {NODE_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
        </div>
      </div>
      <label>Prompt text</label>
      <textarea value={promptText} onChange={(e) => setPromptText(e.target.value)} />
      <div style={{ display: 'flex', gap: 10 }}>
        <button className="btn-advance" disabled={!nodeKey} onClick={create}>Add node</button>
        <button className="btn-ghost" onClick={onDone}>Cancel</button>
      </div>
    </div>
  );
}

function toForm(node) {
  return {
    promptText: node.promptText || '',
    quickReplies: (node.quickReplies || []).join(', '),
    fallbackText: node.fallbackText || '',
    nextNodeKey: node.nextNodeKey || '',
    branches: Object.entries(node.branches || {}).map(([k, v]) => `${k}=${v}`).join('\n'),
  };
}

function fromForm(form, node) {
  const quickReplies = form.quickReplies.split(',').map((s) => s.trim()).filter(Boolean);
  const branches = {};
  form.branches.split('\n').map((l) => l.trim()).filter(Boolean).forEach((line) => {
    const i = line.indexOf('=');
    if (i > 0) branches[line.slice(0, i).trim()] = line.slice(i + 1).trim();
  });
  return {
    nodeKey: node.nodeKey, type: node.type, orderIndex: node.orderIndex,
    promptText: form.promptText || null, quickReplies,
    fallbackText: form.fallbackText || null, transitionText: node.transitionText || null,
    nextNodeKey: form.nextNodeKey || null, branches,
  };
}
