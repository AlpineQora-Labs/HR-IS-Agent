import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { useCandidateMessages, useTextAsCandidate } from '@/api/hooks'
import type { MessageRow } from '@/api/types'
import '@/styles/messages.css'

/* The candidate's phone, for showing the journey without a handset. A text
   typed here is handled by the server exactly as one from a carrier is; this
   shows what reached the phone and what was sent from it. */

const KEYS = ['STATUS', '1', '2', '3', 'MORE', 'RESCHEDULE', 'HELP', 'STOP', 'START']

/** Only what was handed to the carrier reaches a phone. */
const reached = (m: MessageRow) =>
  m.channel === 'SMS' && (m.direction === 'INBOUND' || m.status === 'SENT' || m.status === 'SENDING' || m.status === 'QUEUED')

const stampOf = (iso: string) =>
  new Date(iso).toLocaleString('en-US', { weekday: 'short', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' })

const HALF_HOUR = 30 * 60_000

export default function PhoneSimulator({ candidateId, tall = false, from = 'Bank of America Careers' }: {
  candidateId: string | undefined
  tall?: boolean
  /** The name texts arrive under. */
  from?: string
}) {
  const { data, isLoading, isError } = useCandidateMessages(candidateId)
  const send = useTextAsCandidate(candidateId)
  const [draft, setDraft] = useState('')
  const [read, setRead] = useState<{ text: string; error?: boolean } | null>(null)
  const thread = useRef<HTMLDivElement>(null)

  const rows = useMemo(() => (data?.messages ?? []).filter(reached), [data])
  const sending = send.isPending ? send.variables : null

  useEffect(() => {
    const el = thread.current
    if (el) el.scrollTop = el.scrollHeight
  }, [rows.length, sending])

  const text = (body: string) => {
    const t = body.trim()
    if (!t || send.isPending || !data?.phone) return
    setRead(null)
    setDraft('')
    send.mutate(t, {
      onSuccess: (r) =>
        setRead({
          text: r.duplicate ? 'Delivered twice; handled once.' : r.understoodAs,
        }),
      onError: (e) =>
        setRead({
          error: true,
          text:
            (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
            'The text could not be delivered.',
        }),
    })
  }

  const submit = (e: FormEvent) => {
    e.preventDefault()
    text(draft)
  }

  const noPhone = !!data && !data.phone
  const off = isLoading || isError || noPhone || send.isPending

  let last = 0
  return (
    <div className={`phone${tall ? ' phone--tall' : ''}`}>
      <div className="phone__bar">
        <div className="phone__from">{from}</div>
        <div className="phone__number">{data?.phone ? `Candidate's number ${data.phone}` : 'Text message'}</div>
      </div>

      <div className="phone__thread" ref={thread} aria-live="polite">
        {isLoading ? <div className="phone__quiet">Loading…</div> : null}
        {isError ? <div className="phone__quiet">The messages could not be loaded.</div> : null}
        {noPhone ? (
          <div className="phone__quiet">This candidate has no mobile number, so no text can reach them or come from them.</div>
        ) : null}
        {!isLoading && !isError && !noPhone && rows.length === 0 && !sending ? (
          <div className="phone__quiet">No texts yet. Text STATUS to ask where the application stands.</div>
        ) : null}
        {rows.map((m) => {
          const at = new Date(m.createdAt).getTime()
          const stamp = at - last > HALF_HOUR
          last = at
          return (
            <div key={m.id} style={{ display: 'contents' }}>
              {stamp ? <div className="phone__stamp">{stampOf(m.createdAt)}</div> : null}
              <div className={`phone__bubble ${m.direction === 'INBOUND' ? 'phone__bubble--me' : 'phone__bubble--them'}`}>
                {m.text}
              </div>
            </div>
          )
        })}
        {sending ? <div className="phone__bubble phone__bubble--me phone__bubble--unsent">{sending}</div> : null}
      </div>

      <div className={`phone__read${read?.error ? ' phone__read--error' : ''}`} role="status">
        {read ? (
          read.error ? read.text : <>Read as: <b>{read.text}</b></>
        ) : data && !data.canBeTexted && data.whyNot && !noPhone ? (
          <>Texts to this number are held back. {data.whyNot}.</>
        ) : null}
      </div>

      <div className="phone__keys" role="group" aria-label="Quick replies">
        {KEYS.map((k) => (
          <button key={k} type="button" className="phone__key" disabled={off} onClick={() => text(k)}>
            {k}
          </button>
        ))}
      </div>
      <form className="phone__compose" onSubmit={submit}>
        <input
          className="phone__input"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="Text message"
          aria-label="Text message"
          maxLength={1600}
          disabled={isLoading || isError || noPhone}
        />
        <button type="submit" className="btn btn--primary btn--sm" disabled={off || !draft.trim()}>
          Send
        </button>
      </form>
    </div>
  )
}
