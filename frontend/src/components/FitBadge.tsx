/* An application's fit score as a badge. An application that has not been
   scored yet (one made in chat, until the matching engine has run) shows a
   plain dash: no score is not a score of zero. */

function fitClass(score: number) {
  if (score >= 80) return 'badge--ok'
  if (score >= 60) return 'badge--info'
  if (score >= 40) return 'badge--warn'
  return 'badge--danger'
}

export function FitBadge({ score, label }: { score: number | null | undefined; label?: string }) {
  if (score == null) {
    return (
      <span className="badge" title="Not scored yet">
        {label ? `${label} –` : '–'}
      </span>
    )
  }
  return (
    <span className={`badge ${fitClass(score)}`}>
      {label ? `${label} ${Math.round(score)}` : Math.round(score)}
    </span>
  )
}
