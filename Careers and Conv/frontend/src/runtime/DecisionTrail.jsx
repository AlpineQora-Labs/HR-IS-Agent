// The live decision trail (BUILD_SPEC §7/§10): every rule that fired, with PASS/FAIL, the rule id,
// and the engine's plain-language reason — visible beside the chat during screening.
export default function DecisionTrail({ trail }) {
  return (
    <div className="panel">
      <div className="trail-head">
        <h3>Decision trail</h3>
        <p>Every screening rule, explained. No automated rejections are made here.</p>
      </div>
      <div className="trail-body">
        {(!trail || trail.length === 0) && (
          <div className="trail-empty">Answers you give will be evaluated here, one rule at a time.</div>
        )}
        {trail.map((t) => (
          <div className="trail-item" key={t.ruleKey + t.result}>
            <div className="row">
              <span className="label">{t.label}</span>
              <span className={`chip kind`}>{t.kind}</span>
            </div>
            <div className="row" style={{ marginTop: 6 }}>
              <span className={`chip ${t.result === 'PASS' ? 'pass' : 'fail'}`}>{t.result}</span>
              <span className="muted" style={{ fontSize: 11 }}>#{t.ruleKey}</span>
            </div>
            {t.reason && <div className="reason">{t.reason}</div>}
          </div>
        ))}
      </div>
    </div>
  );
}
