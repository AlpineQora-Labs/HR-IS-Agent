import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAdmin } from '../store/useAdmin.js';
import FlowEditor from './FlowEditor.jsx';

export default function AdminConsole() {
  const {
    token, authed, error, flows, setToken, signIn, openFlow, detail, createDraft, busy,
  } = useAdmin();
  const [creating, setCreating] = useState(false);

  useEffect(() => { if (token) signIn(token); /* eslint-disable-next-line */ }, []);

  if (!authed) {
    return (
      <div className="app">
        <header className="app-head">
          <div className="mark">BofA</div>
          <div>
            <h1>Admin · Flow &amp; Rule Console</h1>
            <div className="sub">Author the conversation and the rules · guardrails enforced</div>
          </div>
          <Link className="nav-link" to="/">← Candidate view</Link>
        </header>
        <div className="landing">
          <h2>Admin sign-in</h2>
          <p className="lead">Enter the admin token. (POC stub — the dev token is <code>dev-admin-token</code>.)</p>
          <div className="field">
            <label>Admin token</label>
            <input value={token} placeholder="dev-admin-token"
              onChange={(e) => setToken(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') signIn(); }} />
          </div>
          {error && <div className="error">⚠ {error}</div>}
          <button className="apply-btn" onClick={() => signIn()}>Open console</button>
        </div>
      </div>
    );
  }

  const drafts = flows.filter((f) => f.status === 'DRAFT');
  const published = flows.filter((f) => f.status === 'PUBLISHED');

  return (
    <div className="app admin">
      <header className="app-head">
        <div className="mark">BofA</div>
        <div>
          <h1>Admin · Flow &amp; Rule Console</h1>
          <div className="sub">Author the conversation and the rules · guardrails enforced</div>
        </div>
        <Link className="nav-link" to="/review" style={{ marginLeft: 'auto' }}>Recruiter →</Link>
        <Link className="nav-link" to="/analytics" style={{ marginLeft: 10 }}>Analytics →</Link>
        <Link className="nav-link" to="/" style={{ marginLeft: 10 }}>Candidate →</Link>
      </header>

      {error && <div className="error">⚠ {error}</div>}

      <div className="admin-grid">
        <aside className="panel admin-sidebar">
          <div className="trail-head">
            <h3>Flows</h3>
            <p>Drafts are editable. Published versions are immutable.</p>
          </div>
          <div className="admin-flowlist">
            <button className="search-cta" style={{ marginBottom: 12 }} onClick={() => setCreating(!creating)}>
              + New draft flow
            </button>
            {creating && <NewFlowForm busy={busy} onCreate={async (b) => { await createDraft(b); setCreating(false); }} />}

            <div className="flow-group-label">Drafts</div>
            {drafts.length === 0 && <div className="trail-empty">No drafts.</div>}
            {drafts.map((f) => (
              <button key={f.id} className={`flow-item ${detail?.flow?.id === f.id ? 'sel' : ''}`} onClick={() => openFlow(f.id)}>
                <span className="flow-name">{f.name}</span>
                <span className="chip status-applied">DRAFT v{f.version}</span>
              </button>
            ))}

            <div className="flow-group-label" style={{ marginTop: 14 }}>Published</div>
            {published.map((f) => (
              <button key={f.id} className={`flow-item ${detail?.flow?.id === f.id ? 'sel' : ''}`} onClick={() => openFlow(f.id)}>
                <span className="flow-name">{f.name}</span>
                <span className="chip status-advanced">PUB v{f.version}</span>
              </button>
            ))}
          </div>
        </aside>

        <main className="admin-main">
          {detail ? <FlowEditor /> : (
            <div className="panel"><div className="trail-empty" style={{ padding: 30 }}>
              Select a flow to edit, or create a new draft.
            </div></div>
          )}
        </main>
      </div>
    </div>
  );
}

function NewFlowForm({ onCreate, busy }) {
  const [flowKey, setFlowKey] = useState('');
  const [name, setName] = useState('');
  const [jobRef, setJobRef] = useState('');
  return (
    <div className="new-flow-form">
      <input placeholder="flow key (e.g. data-analyst)" value={flowKey} onChange={(e) => setFlowKey(e.target.value)} />
      <input placeholder="Display name" value={name} onChange={(e) => setName(e.target.value)} />
      <input placeholder="Job ref (optional)" value={jobRef} onChange={(e) => setJobRef(e.target.value)} />
      <button className="btn-advance" disabled={busy || !flowKey || !name}
        onClick={() => onCreate({ flowKey, name, jobRef: jobRef || flowKey })}>Create</button>
    </div>
  );
}
