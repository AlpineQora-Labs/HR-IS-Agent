import { useEffect } from 'react';
import { useSession } from '../store/useSession.js';
import FaqPanel from './FaqPanel.jsx';

// Landing view (BUILD_SPEC §7 welcome: short, friendly, states purpose + value, offers tasks).
export default function RolePicker() {
  const {
    flows, loadFlows, candidateName, setCandidateName, begin, startSearch, error,
    demographics, setDemographic,
  } = useSession();

  useEffect(() => { loadFlows(); }, [loadFlows]);

  return (
    <div className="landing">
      <h2>Let’s find your fit</h2>
      <p className="lead">
        Welcome to Bank of America Careers. I can screen you for an open role right here in chat —
        it takes about two minutes, and a recruiter reviews every result personally.
      </p>

      <div className="field">
        <label htmlFor="name">Your name (optional)</label>
        <input
          id="name"
          value={candidateName}
          placeholder="e.g. Jordan Lee"
          onChange={(e) => setCandidateName(e.target.value)}
        />
      </div>

      <details className="self-id">
        <summary>Voluntary self-identification (optional)</summary>
        <p className="self-id-note">
          Completely voluntary and <strong>never used to screen you</strong> — it's kept separate from
          the hiring decision and used only in aggregate to monitor the process for fairness. You can
          skip any of these.
        </p>
        <div className="self-id-grid">
          <label>
            Gender
            <select value={demographics.gender} onChange={(e) => setDemographic('gender', e.target.value)}>
              <option value="">Prefer not to say</option>
              <option>Female</option><option>Male</option><option>Non-binary</option>
            </select>
          </label>
          <label>
            Race / ethnicity
            <select value={demographics.raceEthnicity} onChange={(e) => setDemographic('raceEthnicity', e.target.value)}>
              <option value="">Prefer not to say</option>
              <option>Hispanic or Latino</option>
              <option>White</option>
              <option>Black or African American</option>
              <option>Asian</option>
              <option>Native American or Alaska Native</option>
              <option>Native Hawaiian or Pacific Islander</option>
              <option>Two or more races</option>
            </select>
          </label>
          <label>
            Age
            <select value={demographics.ageBand} onChange={(e) => setDemographic('ageBand', e.target.value)}>
              <option value="">Prefer not to say</option>
              <option>Under 40</option><option>40 or older</option>
            </select>
          </label>
        </div>
      </details>

      <div className="field">
        <label>Search openings by chat</label>
        <button className="search-cta" onClick={startSearch}>
          🔎 Search for a job — by title, skill, or location
        </button>
      </div>

      <div className="or-divider"><span>or browse roles</span></div>

      <div className="field">
        <label>Choose a role to get started</label>
        {error && <div className="error">⚠ {error}</div>}
        {flows.length === 0 && !error && <div className="muted">Loading open roles…</div>}
        <div className="roles">
          {flows.map((f) => (
            <button className="role-card" key={f.id} onClick={() => begin(f)}>
              <div className="name">{f.name}</div>
              <div className="meta">Screening · published v{f.version}</div>
            </button>
          ))}
        </div>
      </div>

      <FaqPanel />
    </div>
  );
}
