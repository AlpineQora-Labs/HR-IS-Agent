import { useEffect, useRef, useState } from 'react';
import { useSession } from '../store/useSession.js';
import Composer from './Composer.jsx';
import CompletionBanner from './CompletionBanner.jsx';
import JobResults from './JobResults.jsx';

export default function ChatWindow() {
  const {
    mode, currentFlow, log, awaiting, typing, error, sessionStatus, resultStatus,
    searchResults, send, pickJob, reset, apply, applied, applying,
    interviewSlots, interviewBooking, scheduling, proposeInterview, bookInterview,
    identity, identityBusy, loadIdentity, giveConsent, verifyIdentity, declineIdentity,
  } = useSession();

  const bodyRef = useRef(null);
  useEffect(() => {
    const el = bodyRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [log, typing, resultStatus, searchResults]);

  const ended = sessionStatus === 'COMPLETED';
  const status = mode === 'search'
    ? 'Job search'
    : `${currentFlow ? currentFlow.name : 'Screening'} · ${ended ? 'Complete' : 'Screening in progress'}`;

  return (
    <div className="panel chat">
      <div className="chat-head">
        <div className="avatar">💬</div>
        <div>
          <div className="who">Careers Assistant</div>
          <div className="stat">{status}</div>
        </div>
        <button className="restart" onClick={reset}>Start over</button>
      </div>

      <div className="chat-body" ref={bodyRef}>
        {log.map((b) => (
          <div key={b.id} className={`bubble ${b.who === 'user' ? 'user' : ''}`}>{b.text}</div>
        ))}
        {mode === 'search' && (
          <JobResults jobs={searchResults} disabled={typing} onPick={pickJob} />
        )}
        {typing && (
          <div className="bubble" style={{ width: 'fit-content' }}>
            <div className="typing"><span /><span /><span /></div>
          </div>
        )}
        {error && <div className="error">⚠ {error}</div>}
        {ended && <CompletionBanner resultStatus={resultStatus} />}
        {ended && !applied && (
          <div className="apply-cta">
            <div className="apply-role">{currentFlow ? currentFlow.name : 'this position'}</div>
            <button className="apply-btn" disabled={applying} onClick={apply}>
              {applying ? 'Submitting…' : 'Apply to this position'}
            </button>
          </div>
        )}
        {applied && (
          <div className="completion passed">
            <div className="title">✓ Application submitted</div>
            We’ve emailed you a confirmation.{resultStatus !== 'PASSED' && ' A recruiter will review your screening and follow up.'}
          </div>
        )}
        {applied && resultStatus === 'PASSED' && (
          identity?.status === 'VERIFIED' ? (
            <SelfSchedule
              booking={interviewBooking} slots={interviewSlots} busy={scheduling}
              onPropose={proposeInterview} onBook={bookInterview} />
          ) : (
            <IdentityGate
              identity={identity} busy={identityBusy} onLoad={loadIdentity}
              onConsent={giveConsent} onVerify={verifyIdentity} onDecline={declineIdentity} />
          )
        )}
      </div>

      {!ended && <Composer awaiting={awaiting} disabled={typing || !awaiting} onSend={send} />}
    </div>
  );
}

const fmtSlot = (iso) => {
  try {
    return new Date(iso).toLocaleString([], {
      weekday: 'short', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit',
    });
  } catch {
    return iso;
  }
};

// Identity proofing before scheduling (the Identity Agent slice): consent → one-time code → verified,
// or decline → in-person fallback. Guards against proxy/deepfake candidates.
function IdentityGate({ identity, busy, onLoad, onConsent, onVerify, onDecline }) {
  const [code, setCode] = useState('');
  useEffect(() => { if (!identity) onLoad(); }, [identity, onLoad]);

  const status = identity?.status || 'NOT_STARTED';

  if (status === 'IN_PERSON_REQUIRED') {
    return (
      <div className="completion review" style={{ marginTop: 8 }}>
        <div className="title">🏢 In-person verification</div>
        {identity?.message || "We’ll verify your identity at a nearby branch — a recruiter will share times."}
      </div>
    );
  }

  if (status === 'PENDING') {
    return (
      <div className="apply-cta" style={{ flexDirection: 'column', alignItems: 'stretch', gap: 10 }}>
        <div className="apply-role">🔐 Verify it’s really you</div>
        <div className="id-demo">Demo: your one-time code is <strong>{identity?.challengeCodeHint}</strong> (a real system texts this).</div>
        <div className="entry">
          <input value={code} placeholder="Enter 6-digit code" inputMode="numeric"
            onChange={(e) => setCode(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') onVerify(code); }} />
          <button disabled={busy || !code.trim()} onClick={() => onVerify(code)}>Verify</button>
        </div>
        {identity?.message && <div className="muted" style={{ fontSize: 12 }}>{identity.message}</div>}
        <button className="link-btn" onClick={onDecline}>I’d rather verify in person</button>
      </div>
    );
  }

  return (
    <div className="apply-cta" style={{ flexDirection: 'column', alignItems: 'stretch', gap: 8 }}>
      <div className="apply-role">🔐 Verify your identity to book an interview</div>
      <div className="muted" style={{ fontSize: 12 }}>
        Banking roles require identity verification. With your consent we’ll send a one-time code to
        confirm it’s really you.
      </div>
      <button className="apply-btn" disabled={busy} onClick={onConsent}>
        {busy ? 'Starting…' : 'Consent & verify my identity'}
      </button>
      <button className="link-btn" onClick={onDecline}>I’d rather verify in person</button>
    </div>
  );
}

// Paradox-style candidate self-scheduling: a passed candidate books their own interview in chat.
function SelfSchedule({ booking, slots, busy, onPropose, onBook }) {
  if (booking) {
    return (
      <div className="completion passed" style={{ marginTop: 8 }}>
        <div className="title">📅 Interview booked — {fmtSlot(booking.startTs)}</div>
        With {booking.interviewerName}{booking.interviewerTitle ? ` · ${booking.interviewerTitle}` : ''}.
        We’ve sent a confirmation and will text you a reminder.
      </div>
    );
  }
  if (slots) {
    return (
      <div className="apply-cta" style={{ flexDirection: 'column', alignItems: 'stretch', gap: 10 }}>
        <div className="apply-role">Pick an interview time:</div>
        <div className="slot-list">
          {slots.length === 0 && <div className="trail-empty">No open times right now — we’ll reach out.</div>}
          {slots.map((s) => (
            <button key={s.slotId} className="slot" disabled={busy} onClick={() => onBook(s.slotId)}>
              <div className="slot-when">{fmtSlot(s.startTs)}</div>
              <div className="slot-who">{s.interviewerName} · {s.interviewerTitle || s.department}</div>
            </button>
          ))}
        </div>
      </div>
    );
  }
  return (
    <div className="apply-cta">
      <div className="apply-role">You’re through to the interview — pick a time now.</div>
      <button className="apply-btn" disabled={busy} onClick={onPropose}>
        {busy ? 'Finding times…' : '📅 Schedule your interview'}
      </button>
    </div>
  );
}
