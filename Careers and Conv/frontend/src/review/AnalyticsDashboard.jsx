import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getAnalytics, getBiasAudit } from '../api/adminClient.js';

// Recruiting analytics: the hiring funnel plus an adverse-impact (four-fifths) bias audit. Both are
// aggregated deterministically server-side; demographics never touch the screening decision.
export default function AnalyticsDashboard() {
  const [token, setToken] = useState(() => localStorage.getItem('tb_recruiter_token') || '');
  const [authed, setAuthed] = useState(false);
  const [tab, setTab] = useState('funnel');
  const [data, setData] = useState(null);
  const [bias, setBias] = useState(null);
  const [error, setError] = useState(null);

  const load = async (tok = token) => {
    setError(null);
    try {
      const [a, b] = await Promise.all([getAnalytics(tok), getBiasAudit(tok)]);
      setData(a);
      setBias(b);
      setAuthed(true);
      localStorage.setItem('tb_recruiter_token', tok);
    } catch (e) {
      setAuthed(false);
      setError(e.message);
    }
  };

  useEffect(() => { if (token) load(token); /* eslint-disable-next-line */ }, []);

  if (!authed) {
    return (
      <div className="app">
        <Header />
        <div className="landing" style={{ maxWidth: 460 }}>
          <h2>Analytics</h2>
          <p className="lead">Enter the access token to view the recruiting funnel and bias audit.</p>
          {error && <div className="error">⚠ {error}</div>}
          <div className="field">
            <label>Access token</label>
            <input value={token} onChange={(e) => setToken(e.target.value)} placeholder="dev-admin-token" />
          </div>
          <button className="apply-btn" onClick={() => load()}>View analytics</button>
        </div>
      </div>
    );
  }

  if (!data) return <div className="app"><Header /><div className="muted">Loading…</div></div>;

  return (
    <div className="app">
      <Header />
      <div className="tabs">
        <button className={tab === 'funnel' ? 'active' : ''} onClick={() => setTab('funnel')}>Funnel</button>
        <button className={tab === 'fairness' ? 'active' : ''} onClick={() => setTab('fairness')}>Fairness (bias audit)</button>
      </div>
      {tab === 'funnel' ? <FunnelView data={data} /> : <FairnessView bias={bias} />}
    </div>
  );
}

function FunnelView({ data }) {
  const f = data.funnel;
  const max = Math.max(f.sessions, 1);
  const stages = [
    { key: 'sessions', label: 'Screenings started', v: f.sessions },
    { key: 'completed', label: 'Completed', v: f.completed },
    { key: 'passed', label: 'Passed screening', v: f.passed },
    { key: 'applied', label: 'Applied', v: f.applied },
    { key: 'advanced', label: 'Advanced by recruiter', v: f.advanced },
    { key: 'booked', label: 'Interview booked', v: f.booked },
  ];
  return (
    <div className="analytics-grid">
      <div className="panel a-funnel">
        <div className="flow-group-label">Hiring funnel</div>
        {stages.map((s) => (
          <div className="funnel-row" key={s.key}>
            <div className="funnel-label">{s.label}</div>
            <div className="funnel-bar-wrap">
              <div className={`funnel-bar ${s.key}`} style={{ width: `${Math.round((s.v / max) * 100)}%` }}>
                <span>{s.v}</span>
              </div>
            </div>
          </div>
        ))}
        <div className="needs-review-note">
          {f.needsReview} routed to recruiter review (NEEDS_REVIEW) · {f.declined} declined
        </div>
      </div>
      <div className="panel a-side">
        <div className="flow-group-label">Stage conversion</div>
        <div className="conv-grid">
          <Conv label="Complete" v={data.conversion.completionRate} />
          <Conv label="Pass" v={data.conversion.passRate} />
          <Conv label="Apply" v={data.conversion.applyRate} />
          <Conv label="Advance" v={data.conversion.advanceRate} />
          <Conv label="Book" v={data.conversion.bookRate} />
        </div>
        <div className="flow-group-label" style={{ marginTop: 18 }}>Top knockout reasons</div>
        {(!data.topKnockouts || data.topKnockouts.length === 0) && (
          <div className="trail-empty">No knockout failures yet.</div>
        )}
        {data.topKnockouts.map((k) => (
          <div className="ko-row" key={k.label}>
            <span className="chip fail">{k.fails}</span><span>{k.label}</span>
          </div>
        ))}
      </div>
      <div className="panel a-roles">
        <div className="flow-group-label">By role</div>
        <table className="role-table">
          <thead><tr><th>Role</th><th>Applied</th><th>Passed</th><th>Advanced</th><th>Booked</th></tr></thead>
          <tbody>
            {data.byRole.map((r) => (
              <tr key={r.jobTitle}>
                <td>{r.jobTitle}</td><td>{r.applications}</td><td>{r.passed}</td>
                <td>{r.advanced}</td><td>{r.booked}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function FairnessView({ bias }) {
  if (!bias) return <div className="muted">Loading…</div>;
  return (
    <div className="panel">
      <div className={`audit-banner ${bias.anyAdverse ? 'flag' : 'ok'}`}>
        {bias.anyAdverse
          ? '⚠ Potential adverse impact detected — one or more groups fall below the four-fifths (0.80) threshold. Review the flagged rows before relying on the automated screen.'
          : '✓ No adverse impact detected — all sufficiently-sized groups are at or above the four-fifths (0.80) threshold.'}
      </div>
      <div className="muted" style={{ fontSize: 12, marginBottom: 14 }}>
        Basis: <strong>{bias.basis}</strong> · {bias.totalScored} candidates scored · four-fifths rule
      </div>

      {bias.dimensions.map((dim) => (
        <div className="audit-dim panel" key={dim.name} style={{ padding: '14px 16px' }}>
          <div className="flow-group-label">
            {dim.name}
            {dim.anyAdverse
              ? <span className="chip fail">adverse impact</span>
              : <span className="chip pass">within 4/5</span>}
          </div>
          <table className="audit-table">
            <thead>
              <tr>
                <th>Group</th><th className="num">n</th><th className="num">Selected</th>
                <th className="num">Selection rate</th><th className="num">Impact ratio</th>
              </tr>
            </thead>
            <tbody>
              {dim.groups.map((g) => (
                <tr key={g.label} className={g.adverseImpact ? 'adverse' : ''}>
                  <td>
                    {g.label}
                    {g.reference && <span className="muted"> · reference</span>}
                    {!g.sufficientData && <span className="muted"> · small n</span>}
                  </td>
                  <td className="num">{g.total}</td>
                  <td className="num">{g.selected}</td>
                  <td className="num">{g.selectionRate}%</td>
                  <td className="num">
                    {g.impactRatio == null ? '—' : (
                      <span className={`ratio-pill ${g.adverseImpact ? 'bad' : (g.reference ? '' : 'good')}`}>
                        {g.impactRatio.toFixed(2)}
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {dim.notDisclosed > 0 && (
            <div className="muted" style={{ fontSize: 11.5, marginTop: 8 }}>
              {dim.notDisclosed} chose not to disclose (excluded from rates).
            </div>
          )}
        </div>
      ))}
      <div className="audit-note">{bias.note}</div>
    </div>
  );
}

function Conv({ label, v }) {
  return (
    <div className="conv-cell">
      <div className="conv-val">{v}%</div>
      <div className="conv-label">{label}</div>
    </div>
  );
}

function Header() {
  return (
    <header className="app-head">
      <div className="mark">BofA</div>
      <div style={{ flex: 1 }}>
        <h1>Recruiting Analytics</h1>
        <div className="sub">Funnel, conversion &amp; adverse-impact audit · deterministic</div>
      </div>
      <Link className="nav-link" to="/review" style={{ marginLeft: 'auto' }}>Recruiter →</Link>
      <Link className="nav-link" to="/admin" style={{ marginLeft: 10 }}>Admin →</Link>
    </header>
  );
}
