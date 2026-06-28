// Terminal outcome (BUILD_SPEC §10). Two states only — PASSED or NEEDS_REVIEW. A failed knockout is
// never a rejection: it routes to a human. The copy reinforces that to the candidate.
export default function CompletionBanner({ resultStatus }) {
  if (resultStatus === 'PASSED') {
    return (
      <div className="completion passed">
        <div className="title">✓ You meet the baseline requirements</div>
        A recruiter will reach out to schedule next steps. Thanks for your time!
      </div>
    );
  }
  if (resultStatus === 'NEEDS_REVIEW') {
    return (
      <div className="completion review">
        <div className="title">A recruiter will review your responses</div>
        Your screening is complete. A person — not an automated system — will review your answers and
        follow up. No decision has been made yet.
      </div>
    );
  }
  return null;
}
