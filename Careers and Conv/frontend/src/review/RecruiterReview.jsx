import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  listApplications, getApplication, advanceApplication,
  proposeSlots, getBooking, bookSlot, cancelBooking, getMessages,
  getInterviewKit, getScorecards, submitScorecard, getIdentityRisk,
  getRediscovery, reEngageCandidate,
} from '../api/adminClient.js';
import { getPublishedFlows } from '../api/client.js';

const fmt = (iso) => {
  try {
    return new Date(iso).toLocaleString([], {
      weekday: 'short', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit',
    });
  } catch {
    return iso;
  }
};

// Recruiter review surface. Lists applications, shows each candidate's explainable screening trail,
// and lets a recruiter advance to the next step or decline — a human decision (audited server-side).
export default function RecruiterReview() {
  const [token, setToken] = useState(() => localStorage.getItem('tb_recruiter_token') || '');
  const [recruiterName, setRecruiterName] = useState(() => localStorage.getItem('tb_recruiter_name') || '');
  const [authed, setAuthed] = useState(false);
  const [apps, setApps] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [detail, setDetail] = useState(null);
  const [note, setNote] = useState('');
  const [error, setError] = useState(null);
  const [deciding, setDeciding] = useState(false);
  const [tab, setTab] = useState('pipeline');

  const load = async (tok = token) => {
    setError(null);
    try {
      const list = await listApplications(tok);
      setApps(list);
      setAuthed(true);
      localStorage.setItem('tb_recruiter_token', tok);
      localStorage.setItem('tb_recruiter_name', recruiterName);
    } catch (e) {
      setAuthed(false);
      setError(e.message);
    }
  };

  // Try to auto-load if a token was remembered.
  useEffect(() => { if (token) load(token); /* eslint-disable-next-line */ }, []);

  const select = async (id) => {
    setSelectedId(id);
    setNote('');
    setError(null);
    try {
      setDetail(await getApplication(id, token));
    } catch (e) {
      setError(e.message);
    }
  };

  const decide = async (decision) => {
    if (!selectedId) return;
    setDeciding(true);
    setError(null);
    try {
      await advanceApplication(selectedId, decision, note, token, recruiterName);
      await load();
      await select(selectedId);
    } catch (e) {
      setError(e.message);
    } finally {
      setDeciding(false);
    }
  };

  const statusChip = (s) => `chip status-${(s || '').toLowerCase()}`;

  return (
    <div className="app">
      <header className="app-head">
        <div className="mark">BofA</div>
        <div>
          <h1>Recruiter Review</h1>
          <div className="sub">Candidate pipeline · screening is explainable · you make the call</div>
        </div>
        <Link className="nav-link" to="/analytics" style={{ marginLeft: 'auto' }}>Analytics →</Link>
        <Link className="nav-link" to="/" style={{ marginLeft: 10 }}>← Candidate view</Link>
      </header>

      {!authed ? (
        <div className="landing">
          <h2>Recruiter sign-in</h2>
          <p className="lead">Enter the recruiter token to review applications. (POC stub — the dev
            token is <code>dev-admin-token</code>.)</p>
          <div className="field">
            <label>Your name</label>
            <input value={recruiterName} placeholder="e.g. Dana K." onChange={(e) => setRecruiterName(e.target.value)} />
          </div>
          <div className="field">
            <label>Recruiter token</label>
            <input value={token} placeholder="dev-admin-token" onChange={(e) => setToken(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') load(); }} />
          </div>
          {error && <div className="error">⚠ {error}</div>}
          <button className="apply-btn" onClick={() => load()}>Load pipeline</button>
        </div>
      ) : (
        <>
        <div className="tabs">
          <button className={tab === 'pipeline' ? 'active' : ''} onClick={() => setTab('pipeline')}>Pipeline</button>
          <button className={tab === 'rediscover' ? 'active' : ''} onClick={() => setTab('rediscover')}>Rediscover</button>
        </div>
        {tab === 'rediscover' ? <Rediscover token={token} /> : (
        <div className="review">
          <div className="panel review-list">
            <div className="trail-head">
              <h3>Applications ({apps.length})</h3>
              <p>Newest first. Select one to review the screening.</p>
            </div>
            <div className="review-list-body">
              {apps.length === 0 && <div className="trail-empty">No applications yet.</div>}
              {apps.map((a) => (
                <button key={a.id} className={`review-row ${selectedId === a.id ? 'sel' : ''}`} onClick={() => select(a.id)}>
                  <div className="review-row-top">
                    <span className="cand">{a.candidateName || 'Candidate'}</span>
                    <span className={statusChip(a.status)}>{a.status}</span>
                  </div>
                  <div className="review-row-meta">{a.jobTitle}</div>
                  <div className="review-row-chips">
                    <span className={`chip ${a.resultStatus === 'PASSED' ? 'pass' : 'review'}`}>{a.resultStatus}</span>
                    {a.knockoutFailed && <span className="chip fail">knockout</span>}
                  </div>
                </button>
              ))}
            </div>
          </div>

          <div className="panel review-detail">
            {!detail ? (
              <div className="trail-empty" style={{ padding: 24 }}>Select an application to review.</div>
            ) : (
              <ReviewDetail detail={detail} note={note} setNote={setNote} deciding={deciding}
                onDecide={decide} statusChip={statusChip} token={token} recruiterName={recruiterName} />
            )}
            {error && <div className="error">⚠ {error}</div>}
          </div>
        </div>
        )}
        </>
      )}
    </div>
  );
}

const RD_BUCKET = {
  QUALIFIED_WAITING: { label: 'Qualified · waiting', cls: 'pass' },
  TRANSFERABLE: { label: 'Transferable skills', cls: 'kind' },
  GHOSTED: { label: 'Ghosted', cls: 'review' },
};

// Talent rediscovery: pick a role, surface re-engageable past/ghosted candidates, re-engage in a click.
function Rediscover({ token }) {
  const [flows, setFlows] = useState([]);
  const [flowKey, setFlowKey] = useState('');
  const [matches, setMatches] = useState([]);
  const [reEngaged, setReEngaged] = useState({});
  const [error, setError] = useState(null);

  useEffect(() => { getPublishedFlows().then(setFlows).catch(() => {}); }, []);

  const load = (fk = flowKey) => {
    setError(null);
    getRediscovery(fk, token).then(setMatches).catch((e) => setError(e.message));
  };
  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);

  const onPickRole = (fk) => { setFlowKey(fk); load(fk); };

  const reengage = async (m) => {
    try {
      await reEngageCandidate(m.sessionId, flowKey || null, token);
      setReEngaged((r) => ({ ...r, [m.sessionId]: true }));
    } catch (e) {
      setError(e.message);
    }
  };

  return (
    <div className="panel" style={{ padding: '18px 20px' }}>
      <div className="flow-group-label">Re-engage your warm pipeline</div>
      <p className="muted" style={{ fontSize: 12, margin: '4px 0 14px' }}>
        Past candidates worth another look — qualified but never advanced, ghosted mid-screening, or a
        skills match from another role. Matching is deterministic and explained.
      </p>
      <div className="field" style={{ maxWidth: 360 }}>
        <label>Target role</label>
        <select value={flowKey} onChange={(e) => onPickRole(e.target.value)}>
          <option value="">All open roles</option>
          {flows.map((f) => <option key={f.id} value={f.flowKey}>{f.name}</option>)}
        </select>
      </div>
      {error && <div className="error">⚠ {error}</div>}
      {matches.length === 0 && <div className="trail-empty">No re-engageable candidates yet.</div>}
      <div className="rd-list">
        {matches.map((m) => {
          const b = RD_BUCKET[m.bucket] || { label: m.bucket, cls: 'kind' };
          const done = reEngaged[m.sessionId];
          return (
            <div className="rd-card" key={m.sessionId}>
              <div className="rd-top">
                <span className="rd-name">{m.candidateName || 'Candidate'}</span>
                <span className={`chip ${b.cls}`}>{b.label}</span>
                <span className="rd-score">match {m.matchScore}</span>
              </div>
              <div className="rd-reason">{m.matchReason}</div>
              <div className="rd-actions">
                <span className="muted" style={{ fontSize: 11 }}>{m.daysAgo}d ago · {m.lastRole}</span>
                {done ? (
                  <span className="chip pass">✓ Re-engaged</span>
                ) : (
                  <button className="btn-secondary" onClick={() => reengage(m)}>Re-engage</button>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function ReviewDetail({ detail, note, setNote, deciding, onDecide, statusChip, token, recruiterName }) {
  const a = detail.application;
  const decided = a.status !== 'APPLIED';
  return (
    <>
      <div className="trail-head">
        <h3>{a.candidateName} · {a.jobTitle}</h3>
        <p>
          {a.candidateEmail || 'no email'} · screening&nbsp;
          <span className={`chip ${a.resultStatus === 'PASSED' ? 'pass' : 'review'}`}>{a.resultStatus}</span>
          {detail.passedCount != null && <> · {detail.passedCount}/{detail.totalCount} rules passed</>}
        </p>
      </div>

      <div className="trail-body">
        {detail.trail.map((t) => (
          <div className="trail-item" key={t.ruleKey + t.result}>
            <div className="row">
              <span className="label">{t.label}</span>
              <span className="chip kind">{t.kind}</span>
            </div>
            <div className="row" style={{ marginTop: 6 }}>
              <span className={`chip ${t.result === 'PASS' ? 'pass' : 'fail'}`}>{t.result}</span>
              <span className="muted" style={{ fontSize: 11 }}>#{t.ruleKey}</span>
            </div>
            {t.reason && <div className="reason">{t.reason}</div>}
          </div>
        ))}

        <IdentityRisk applicationId={a.id} token={token} />

        {decided ? (
          <>
            <div className={`decision-made ${a.status.toLowerCase()}`}>
              <div className="title">
                <span className={statusChip(a.status)}>{a.status}</span> by {a.decidedBy || 'recruiter'}
              </div>
              {a.recruiterNote && <div className="note">“{a.recruiterNote}”</div>}
            </div>
            {a.status === 'ADVANCED' && (
              <Scheduling applicationId={a.id} token={token} recruiterName={recruiterName} />
            )}
            {a.status === 'ADVANCED' && (
              <InterviewPanel applicationId={a.id} token={token} recruiterName={recruiterName} />
            )}
          </>
        ) : (
          <div className="decision-box">
            <div className="decision-hint">
              Screening never auto-decides. Advance the candidate to the next step, or decline — your call.
            </div>
            <textarea
              className="decision-note"
              placeholder="Optional note (recorded with your decision)…"
              value={note}
              onChange={(e) => setNote(e.target.value)}
            />
            <div className="decision-actions">
              <button className="btn-advance" disabled={deciding} onClick={() => onDecide('ADVANCED')}>
                Advance to next step
              </button>
              <button className="btn-decline" disabled={deciding} onClick={() => onDecide('DECLINED')}>
                Decline
              </button>
            </div>
          </div>
        )}

        <Communications applicationId={a.id} token={token} />
      </div>
    </>
  );
}

const REC_OPTIONS = [
  { v: 'STRONG_YES', label: 'Strong yes' },
  { v: 'YES', label: 'Yes' },
  { v: 'NO', label: 'No' },
  { v: 'STRONG_NO', label: 'Strong no' },
];

// Structured interview kit (generated from the role) + a scorecard the interviewer submits.
function InterviewPanel({ applicationId, token }) {
  const [kit, setKit] = useState(null);
  const [cards, setCards] = useState([]);
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [ratings, setRatings] = useState({});
  const [overall, setOverall] = useState('');
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const reload = () => {
    getInterviewKit(applicationId, token).then(setKit).catch((e) => setError(e.message));
    getScorecards(applicationId, token).then(setCards).catch(() => {});
  };
  useEffect(() => { reload(); /* eslint-disable-next-line */ }, [applicationId, token]);

  const setRating = (key, field, value) =>
    setRatings((r) => ({ ...r, [key]: { ...r[key], [field]: value } }));

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      const payload = {
        interviewerName: name || 'Interviewer',
        overallRecommendation: overall || null,
        notes,
        ratings: (kit?.competencies || [])
          .filter((c) => ratings[c.key]?.score)
          .map((c) => ({
            competency: c.name,
            score: Number(ratings[c.key].score),
            comment: ratings[c.key].comment || '',
          })),
      };
      await submitScorecard(applicationId, payload, token);
      setOpen(false); setName(''); setRatings({}); setOverall(''); setNotes('');
      reload();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };

  if (!kit) return null;
  return (
    <div className="scheduling">
      <div className="flow-group-label">Interview kit &amp; scorecards</div>

      {cards.length > 0 && cards.map((c) => (
        <div className="comm-row" key={c.id}>
          <div className="comm-head">
            <span className={`chip ${c.overallRecommendation?.includes('YES') ? 'pass' : 'fail'}`}>
              {(c.overallRecommendation || '—').replace('_', ' ')}
            </span>
            <span className="comm-subject">{c.interviewerName}</span>
          </div>
          <div className="comm-body">
            {c.ratings.map((r) => `${r.competency}: ${r.score}/4`).join(' · ')}
            {c.notes ? ` — ${c.notes}` : ''}
          </div>
        </div>
      ))}

      {!open && (
        <button className="btn-secondary" onClick={() => setOpen(true)}>
          + Fill out a scorecard
        </button>
      )}

      {open && (
        <div className="scorecard">
          <div className="muted" style={{ fontSize: 12, marginBottom: 8 }}>{kit.scale}</div>
          <input className="sc-name" placeholder="Interviewer name" value={name}
            onChange={(e) => setName(e.target.value)} />
          {kit.competencies.map((c) => (
            <div className="sc-comp" key={c.key}>
              <div className="sc-comp-head">
                <span className="sc-comp-name">{c.name}</span>
                <span className="chip kind">{c.source}</span>
              </div>
              <div className="sc-q">{c.questions[0]}</div>
              <div className="sc-row">
                <select value={ratings[c.key]?.score || ''} onChange={(e) => setRating(c.key, 'score', e.target.value)}>
                  <option value="">Rate 1–4</option>
                  <option value="1">1 · Poor</option><option value="2">2 · Mixed</option>
                  <option value="3">3 · Strong</option><option value="4">4 · Excellent</option>
                </select>
                <input placeholder="Comment (optional)" value={ratings[c.key]?.comment || ''}
                  onChange={(e) => setRating(c.key, 'comment', e.target.value)} />
              </div>
            </div>
          ))}
          <div className="sc-overall">
            <span>Overall:</span>
            {REC_OPTIONS.map((o) => (
              <button key={o.v} className={`rec-btn ${overall === o.v ? 'active' : ''}`}
                onClick={() => setOverall(o.v)}>{o.label}</button>
            ))}
          </div>
          <textarea placeholder="Summary notes" value={notes} onChange={(e) => setNotes(e.target.value)} />
          {error && <div className="error">⚠ {error}</div>}
          <div className="sc-actions">
            <button className="btn-advance" disabled={busy} onClick={submit}>Submit scorecard</button>
            <button className="btn-secondary" onClick={() => setOpen(false)}>Cancel</button>
          </div>
        </div>
      )}
    </div>
  );
}

// Identity-proofing status + deterministic fraud-risk signals — shown before the recruiter decides.
function IdentityRisk({ applicationId, token }) {
  const [data, setData] = useState(null);
  useEffect(() => {
    let active = true;
    getIdentityRisk(applicationId, token).then((d) => { if (active) setData(d); }).catch(() => {});
    return () => { active = false; };
  }, [applicationId, token]);

  if (!data) return null;
  const level = (data.risk?.level || 'LOW').toLowerCase();
  const idStatus = data.identity?.status || 'NOT_STARTED';
  return (
    <div className="scheduling">
      <div className="flow-group-label">Identity &amp; fraud check</div>
      <div className={`risk-banner ${level}`}>
        <span>Risk: {data.risk?.level}</span>
        <span className="muted" style={{ fontWeight: 400 }}>· identity {idStatus.replace(/_/g, ' ').toLowerCase()}</span>
      </div>
      {(data.risk?.signals || []).length === 0 && (
        <div className="trail-empty">No risk signals — identity verified, no anomalies.</div>
      )}
      {(data.risk?.signals || []).map((s) => (
        <div className="risk-signal" key={s.code}>
          <span className={`sev ${s.severity.toLowerCase()}`}>{s.severity}</span>
          <span className="t"><b>{s.label}</b> — {s.detail}</span>
        </div>
      ))}
    </div>
  );
}

function Communications({ applicationId, token }) {
  const [messages, setMessages] = useState([]);

  useEffect(() => {
    let active = true;
    getMessages(applicationId, token).then((m) => { if (active) setMessages(m || []); }).catch(() => {});
    return () => { active = false; };
  }, [applicationId, token]);

  if (messages.length === 0) return null;
  return (
    <div className="scheduling">
      <div className="flow-group-label">Communications sent</div>
      {messages.map((m) => (
        <div className="comm-row" key={m.id}>
          <div className="comm-head">
            <span className="chip status-applied">{m.channel}</span>
            <span className="comm-subject">{m.subject}</span>
            <span className={`chip ${m.status === 'SENT' ? 'pass' : 'review'}`}>{m.status}</span>
          </div>
          <div className="comm-body">{m.body}</div>
        </div>
      ))}
    </div>
  );
}

function Scheduling({ applicationId, token, recruiterName }) {
  const [booking, setBooking] = useState(null);
  const [slots, setSlots] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const loadBooking = async () => {
    try {
      setBooking(await getBooking(applicationId, token));
    } catch (e) {
      setError(e.message);
    }
  };

  useEffect(() => { setSlots(null); setError(null); loadBooking(); /* eslint-disable-next-line */ }, [applicationId]);

  const propose = async () => {
    setBusy(true); setError(null);
    try {
      setSlots(await proposeSlots(applicationId, token));
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };

  const book = async (slotId) => {
    setBusy(true); setError(null);
    try {
      await bookSlot(applicationId, slotId, token, recruiterName);
      setSlots(null);
      await loadBooking();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };

  const cancel = async () => {
    if (!booking) return;
    setBusy(true); setError(null);
    try {
      await cancelBooking(booking.id, token, recruiterName);
      setBooking(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="scheduling">
      <div className="flow-group-label">Interview</div>
      {error && <div className="error">⚠ {error}</div>}

      {booking ? (
        <div className="booking-card">
          <div className="booking-when">📅 {fmt(booking.startTs)}</div>
          <div className="booking-who">{booking.interviewerName}{booking.interviewerTitle ? ` · ${booking.interviewerTitle}` : ''}</div>
          <div className="booking-meta">Confirmed by {booking.createdBy}</div>
          <button className="btn-ghost sm danger" disabled={busy} onClick={cancel} style={{ marginTop: 8 }}>
            Cancel interview
          </button>
        </div>
      ) : slots ? (
        <div className="slot-list">
          {slots.length === 0 && <div className="trail-empty">No open slots available.</div>}
          {slots.map((s) => (
            <button key={s.slotId} className="slot" disabled={busy} onClick={() => book(s.slotId)}>
              <div className="slot-when">{fmt(s.startTs)}</div>
              <div className="slot-who">
                {s.interviewerName} · {s.department}
                {s.matchesDepartment && <span className="slot-match">★ role match</span>}
              </div>
            </button>
          ))}
          <button className="btn-ghost sm" onClick={() => setSlots(null)}>Cancel</button>
        </div>
      ) : (
        <button className="btn-advance" disabled={busy} onClick={propose}>
          {busy ? 'Finding slots…' : '📅 Propose interview slots'}
        </button>
      )}
    </div>
  );
}
