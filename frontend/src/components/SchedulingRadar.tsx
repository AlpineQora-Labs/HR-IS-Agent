import { useMemo, useState } from 'react'
import SlideOver from '@/components/SlideOver'
import { useProposeTimes, useSchedulingOverview, useTransitionInterview } from '@/api/hooks'
import type { AttentionItem, InterviewerLoad } from '@/api/types'

/* The recruiter's scheduling radar — "what's stuck", across every job. Four
   attention lanes plus the interviewer-load strip. The panel picker doubles as
   the load-balancing assist: interviewers are sorted lightest-first. */

const timeShort = (iso: string | null) => {
  if (!iso) return '—'
  const d = new Date(iso)
  return d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })
}

const age = (hours: number) => (hours < 1 ? '<1h' : hours < 48 ? `${hours}h` : `${Math.round(hours / 24)}d`)

function Lane({
  title,
  hint,
  items,
  tone,
  render,
}: {
  title: string
  hint: string
  items: AttentionItem[]
  tone: 'warn' | 'danger' | 'info' | 'ok'
  render: (item: AttentionItem) => React.ReactNode
}) {
  return (
    <div className="card" style={{ flex: 1, minWidth: 220 }}>
      <div className="card__body" style={{ padding: '14px 16px' }}>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, marginBottom: 2 }}>
          <span className="eyebrow">{title}</span>
          <span className={`badge badge--${tone}`}>{items.length}</span>
        </div>
        <div className="muted" style={{ fontSize: 11.5, marginBottom: 10 }}>{hint}</div>
        {items.length === 0 ? (
          <div className="muted" style={{ fontSize: 12.5 }}>Nothing here 🎉</div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {items.slice(0, 4).map((item) => (
              <div key={item.interviewId} style={{ borderTop: '1px solid var(--line)', paddingTop: 8 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, fontSize: 13 }}>
                  <span className="t-strong">{item.candidateName}</span>
                </div>
                <div className="muted" style={{ fontSize: 11.5, margin: '1px 0 6px' }}>{item.jobTitle}</div>
                {render(item)}
              </div>
            ))}
            {items.length > 4 && (
              <div className="muted" style={{ fontSize: 11.5 }}>+{items.length - 4} more</div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

export function SchedulingRadar() {
  const { data, isLoading } = useSchedulingOverview()
  const transition = useTransitionInterview()
  const [panelFor, setPanelFor] = useState<AttentionItem | null>(null)
  const btn = { fontSize: 11.5, padding: '2px 8px' } as const

  if (isLoading || !data) return null

  return (
    <>
      <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap', alignItems: 'stretch', marginBottom: 18 }}>
        <Lane
          title="Awaiting candidate"
          hint="Offers out — candidate hasn't picked yet"
          items={data.awaitingCandidate}
          tone="warn"
          render={(item) => (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span className="muted" style={{ fontSize: 11.5 }}>waiting {age(item.waitingHours)}</span>
              <button className="btn btn--outline btn--sm" style={btn} onClick={() => setPanelFor(item)}>
                Re-propose…
              </button>
            </div>
          )}
        />
        <Lane
          title="Needs outcome"
          hint="The time has passed — record what happened"
          items={data.needsOutcome}
          tone="danger"
          render={(item) => (
            <div style={{ display: 'flex', gap: 6 }}>
              <button className="btn btn--outline btn--sm" style={btn} disabled={transition.isPending}
                onClick={() => transition.mutate({ interviewId: item.interviewId, status: 'COMPLETED' })}>
                Complete
              </button>
              <button className="btn btn--outline btn--sm" style={btn} disabled={transition.isPending}
                onClick={() => transition.mutate({ interviewId: item.interviewId, status: 'NO_SHOW' })}>
                No-show
              </button>
            </div>
          )}
        />
        <Lane
          title="No-show recovery"
          hint="Aria has re-offered times automatically"
          items={data.noShows}
          tone="info"
          render={() => <span className="muted" style={{ fontSize: 11.5 }}>Aria is on it — new offers sent</span>}
        />
        <Lane
          title="Today"
          hint="Interviews happening today (ET)"
          items={data.today}
          tone="ok"
          render={(item) => (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12 }}>
              <span>{timeShort(item.scheduledAt)}</span>
              {item.meetingLink && (
                <a href={item.meetingLink} target="_blank" rel="noreferrer" style={{ fontSize: 11.5 }}>Teams</a>
              )}
            </div>
          )}
        />
      </div>

      <LoadStrip load={data.load} />

      {panelFor && <PanelPicker item={panelFor} load={data.load} onClose={() => setPanelFor(null)} />}
    </>
  )
}

function LoadStrip({ load }: { load: InterviewerLoad[] }) {
  const max = Math.max(1, ...load.map((l) => l.next7Days))
  return (
    <div className="card" style={{ marginBottom: 18 }}>
      <div className="card__body" style={{ padding: '14px 16px' }}>
        <span className="eyebrow">Interviewer load · next 7 days</span>
        <div style={{ display: 'flex', gap: 18, flexWrap: 'wrap', marginTop: 10 }}>
          {load.map((l) => (
            <div key={l.userId} style={{ minWidth: 150 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12.5 }}>
                <span className="t-strong">{l.name}</span>
                <span className="muted">{l.next7Days} interview{l.next7Days === 1 ? '' : 's'}</span>
              </div>
              <div style={{ height: 5, borderRadius: 3, background: 'var(--app-sunken)', marginTop: 4 }}>
                <div
                  style={{
                    height: 5,
                    borderRadius: 3,
                    width: `${(l.next7Days / max) * 100}%`,
                    background: l.next7Days >= max && max > 1 ? 'var(--bofa-red, #c41230)' : 'var(--bofa-navy, #012169)',
                  }}
                />
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

/* Panel picker + load-balancing assist: choose who sits on this round, lightest
   load first, then re-propose times that fit every panelist's calendar. */
function PanelPicker({ item, load, onClose }: { item: AttentionItem; load: InterviewerLoad[]; onClose: () => void }) {
  const propose = useProposeTimes()
  const byLoad = useMemo(() => [...load].sort((a, b) => a.next7Days - b.next7Days), [load])
  const [selected, setSelected] = useState<string[]>(() =>
    load.filter((l) => item.interviewers.includes(l.name)).map((l) => l.userId))

  const toggle = (id: string) =>
    setSelected((cur) => (cur.includes(id) ? cur.filter((x) => x !== id) : [...cur, id]))

  return (
    <SlideOver label="Choose the panel and re-propose times" onClose={onClose} width={420}>
      <div style={{ padding: '18px 20px 14px', borderBottom: '1px solid var(--line)' }}>
        <div style={{ fontFamily: 'var(--font-display)', fontSize: 18 }}>Propose times</div>
        <div className="sub" style={{ marginTop: 3, fontSize: 12.5 }}>
          {item.candidateName} · {item.jobTitle}
        </div>
      </div>
      <div style={{ flex: 1, overflowY: 'auto', padding: '16px 20px' }}>
        <div className="eyebrow" style={{ marginBottom: 6 }}>Panel · lightest load first</div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {byLoad.map((l) => {
            const on = selected.includes(l.userId)
            return (
              <button
                key={l.userId}
                onClick={() => toggle(l.userId)}
                style={{
                  display: 'flex', alignItems: 'center', gap: 10, padding: '9px 12px', cursor: 'pointer',
                  borderRadius: 'var(--ra-2)', textAlign: 'left', fontSize: 13,
                  border: on ? '1.5px solid var(--bofa-navy, #012169)' : '1px solid var(--line)',
                  background: on ? 'var(--app-sunken)' : 'var(--app-panel)', color: 'var(--ink-1)',
                }}
              >
                <span className="t-strong" style={{ width: 30 }}>{l.initials}</span>
                <span style={{ flex: 1 }}>
                  {l.name}
                  <span className="muted" style={{ fontSize: 11.5 }}> · {l.role.replace('_', ' ').toLowerCase()}</span>
                </span>
                <span className={`badge ${l.next7Days >= 2 ? 'badge--warn' : 'badge--ok'}`}>
                  {l.next7Days}/7d
                </span>
              </button>
            )
          })}
        </div>
        <p className="muted" style={{ fontSize: 11.5, marginTop: 12 }}>
          Proposed times will fit every selected panelist's calendar (9–5 ET, buffers and
          daily caps applied). Leave empty to use the job's default hiring team.
        </p>
      </div>
      <div style={{ padding: '14px 20px', borderTop: '1px solid var(--line)', display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
        <button className="btn btn--outline" onClick={onClose}>Cancel</button>
        <button
          className="btn"
          disabled={propose.isPending}
          onClick={() =>
            propose.mutate(
              { interviewId: item.interviewId, interviewerUserIds: selected.length ? selected : undefined },
              { onSuccess: onClose },
            )
          }
        >
          {propose.isPending ? 'Proposing…' : 'Propose times'}
        </button>
      </div>
    </SlideOver>
  )
}
