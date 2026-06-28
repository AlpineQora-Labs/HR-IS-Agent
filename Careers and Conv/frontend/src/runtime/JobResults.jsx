// Ranked job postings from a deterministic search, rendered inline in the chat. Tapping a posting
// starts the screening flow for that job (search → apply → screen is one continuous path).
export default function JobResults({ jobs, disabled, onPick }) {
  if (!jobs || jobs.length === 0) return null;
  return (
    <div className="job-results">
      {jobs.map((job) => (
        <button
          key={job.id}
          className="job-card"
          disabled={disabled}
          onClick={() => onPick(job)}
        >
          <div className="job-title">{job.title}</div>
          <div className="job-meta">{job.location} · {job.workMode} · {job.department}</div>
          {job.skills && job.skills.length > 0 && (
            <div className="job-skills">
              {job.skills.slice(0, 4).map((s) => <span key={s} className="skill">{s}</span>)}
            </div>
          )}
          <div className="job-apply">Get screened →</div>
        </button>
      ))}
    </div>
  );
}
