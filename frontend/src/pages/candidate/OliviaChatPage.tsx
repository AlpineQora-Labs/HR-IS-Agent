import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useCareerJob, useChat, useChatReply, useStartChat } from '@/api/hooks'
import type { ChatMessage, ChatState } from '@/api/types'

function isOlivia(m: ChatMessage) {
  return m.sender?.toUpperCase() !== 'CANDIDATE'
}

export default function OliviaChatPage() {
  const { id } = useParams()
  const { data: job } = useCareerJob(id)

  const startChat = useStartChat()
  const reply = useChatReply()
  const [conversationId, setConversationId] = useState<string | undefined>(undefined)
  const { data: chat } = useChat(conversationId)
  const [text, setText] = useState('')
  const transcriptRef = useRef<HTMLDivElement>(null)

  // Start a conversation for this job once, on mount.
  const startedFor = useRef<string | undefined>(undefined)
  useEffect(() => {
    if (!id || startedFor.current === id) return
    startedFor.current = id
    startChat.mutate(
      { jobId: id, channel: 'WEB', language: 'en' },
      { onSuccess: (s: ChatState) => setConversationId(s.conversationId) },
    )
  }, [id, startChat])

  // Auto-scroll to newest message.
  useEffect(() => {
    transcriptRef.current?.scrollTo({ top: transcriptRef.current.scrollHeight, behavior: 'smooth' })
  }, [chat?.messages.length, reply.isPending])

  const status = chat?.status?.toUpperCase()
  const completed = status === 'COMPLETED'
  const busy = startChat.isPending || reply.isPending
  const failed = startChat.isError

  function send(value: string) {
    const t = value.trim()
    if (!t || !conversationId || busy || completed) return
    setText('')
    reply.mutate({ conversationId, text: t })
  }

  return (
    <div className="careers app-surface" style={{ display: 'flex', flexDirection: 'column' }}>
      {/* Header */}
      <div style={{ background: 'var(--bofa-navy)', color: '#fff' }}>
        <div
          className="careers__inner"
          style={{ paddingTop: 22, paddingBottom: 22, maxWidth: 720, display: 'flex', alignItems: 'center', gap: 14 }}
        >
          <div
            style={{
              width: 40,
              height: 40,
              borderRadius: '50%',
              background: 'var(--bofa-red)',
              display: 'grid',
              placeItems: 'center',
              fontFamily: 'var(--font-display)',
              fontWeight: 600,
              fontSize: 17,
              flex: 'none',
            }}
          >
            O
          </div>
          <div style={{ flex: 1 }}>
            <div style={{ fontWeight: 600, fontSize: 17 }}>Olivia</div>
            <div style={{ fontSize: 12.5, color: 'rgba(255,255,255,0.72)' }}>
              {job ? `Applying · ${job.title}` : 'Your hiring assistant'}
            </div>
          </div>
          {chat?.step && (
            <span
              className="badge"
              style={{ background: 'rgba(255,255,255,0.14)', borderColor: 'transparent', color: '#fff' }}
            >
              {completed ? 'Complete' : chat.step}
            </span>
          )}
        </div>
      </div>

      <div className="careers__inner" style={{ maxWidth: 720, flex: 1, display: 'flex', flexDirection: 'column', paddingTop: 24 }}>
        <div className="card" style={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: 420 }}>
          {/* Transcript */}
          <div ref={transcriptRef} className="card__body" style={{ flex: 1, overflowY: 'auto' }}>
            {failed ? (
              <div style={{ textAlign: 'center', padding: '32px 8px' }}>
                <div style={{ fontFamily: 'var(--font-display)', fontSize: 18, color: 'var(--ink-0)' }}>
                  Olivia couldn’t start the conversation.
                </div>
                <div style={{ fontSize: 13.5, color: 'var(--ink-4)', margin: '6px 0 16px' }}>Please try again.</div>
                <button
                  className="btn btn--outline btn--sm"
                  onClick={() => {
                    startedFor.current = undefined
                    if (id)
                      startChat.mutate(
                        { jobId: id, channel: 'WEB', language: 'en' },
                        { onSuccess: (s) => setConversationId(s.conversationId) },
                      )
                  }}
                >
                  Retry
                </button>
              </div>
            ) : !chat ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: 'var(--ink-4)', fontSize: 14 }}>
                <span className="typing">
                  <i />
                  <i />
                  <i />
                </span>
                Olivia is getting ready…
              </div>
            ) : (
              <div className="chat">
                {chat.messages.map((m) => {
                  const olivia = isOlivia(m)
                  return (
                    <div key={m.id} className={`bubble ${olivia ? 'bubble--agent' : 'bubble--user'}`}>
                      {olivia && (
                        <span className="agent-tag">
                          <span className="dot" />
                          Olivia
                        </span>
                      )}
                      <div style={{ whiteSpace: 'pre-line' }}>{m.body}</div>
                    </div>
                  )
                })}
                {reply.isPending && (
                  <div className="bubble bubble--agent">
                    <span className="agent-tag">
                      <span className="dot" />
                      Olivia
                    </span>
                    <span className="typing">
                      <i />
                      <i />
                      <i />
                    </span>
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Completed confirmation */}
          {completed ? (
            <div
              className="card__foot"
              style={{ flexDirection: 'column', alignItems: 'stretch', gap: 10, background: 'var(--ok-bg)' }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <span
                  style={{
                    width: 26,
                    height: 26,
                    borderRadius: '50%',
                    background: 'var(--ok-fg)',
                    color: '#fff',
                    display: 'grid',
                    placeItems: 'center',
                    fontSize: 15,
                    flex: 'none',
                  }}
                >
                  ✓
                </span>
                <div>
                  <div style={{ fontWeight: 600, color: 'var(--ok-fg)' }}>Application submitted</div>
                  <div style={{ fontSize: 13, color: 'var(--ink-3)' }}>
                    Thanks! We’ll be in touch about next steps soon.
                  </div>
                </div>
              </div>
              <Link to="/careers" className="btn btn--outline btn--sm" style={{ alignSelf: 'flex-start' }}>
                Browse more roles
              </Link>
            </div>
          ) : (
            chat &&
            !failed && (
              <div style={{ borderTop: '1px solid var(--line)', padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
                {/* Quick replies */}
                {chat.options && chat.options.length > 0 && (
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                    {chat.options.map((opt) => (
                      <button key={opt} className="chip" disabled={busy} onClick={() => send(opt)}>
                        {opt}
                      </button>
                    ))}
                  </div>
                )}
                {/* Composer */}
                <div className="composer">
                  <textarea
                    placeholder="Type your reply…"
                    value={text}
                    rows={1}
                    disabled={busy}
                    onChange={(e) => setText(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' && !e.shiftKey) {
                        e.preventDefault()
                        send(text)
                      }
                    }}
                  />
                  <button className="btn btn--primary btn--sm" disabled={busy || !text.trim()} onClick={() => send(text)}>
                    Send
                  </button>
                </div>
              </div>
            )
          )}
        </div>

        <div style={{ textAlign: 'center', fontSize: 12, color: 'var(--ink-5)', margin: '16px 0 8px' }}>
          Powered by Olivia · Bank of America
        </div>
      </div>
    </div>
  )
}
