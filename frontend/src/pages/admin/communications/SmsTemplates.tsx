import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { commsApi, type SmsTemplate } from './commsApi'

/* SMS templates for the candidate journey — the text-message side of
   Communications. Plain text + {{merge_field}} chips, a character/segment
   counter and a live phone-bubble preview. Channel policy: web chat + SMS
   only (never WhatsApp). Used by the workflow canvas's SMS block. */

const MERGE_FIELDS = [
  'candidate_name',
  'job_title',
  'interview_type',
  'interview_time',
  'status',
  'link',
]

function statusDot(status: string) {
  return (
    <span
      style={{
        width: 7, height: 7, borderRadius: 999, display: 'inline-block', marginRight: 8,
        background: status === 'ACTIVE' ? '#1a9d55' : status === 'ARCHIVED' ? '#c9d1de' : '#c98a00',
      }}
    />
  )
}

function Editor({
  template,
  onDone,
}: {
  template: SmsTemplate | null
  onDone: () => void
}) {
  const qc = useQueryClient()
  const [name, setName] = useState(template?.name ?? '')
  const [status, setStatus] = useState<SmsTemplate['status']>(template?.status ?? 'ACTIVE')
  const [body, setBody] = useState(template?.body ?? '')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setName(template?.name ?? '')
    setStatus(template?.status ?? 'ACTIVE')
    setBody(template?.body ?? '')
    setError(null)
  }, [template])

  const save = useMutation({
    mutationFn: () =>
      template
        ? commsApi.updateSmsTemplate(template.id, { name, status, body })
        : commsApi.createSmsTemplate({ name, status, body }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sms-templates'] })
      onDone()
    },
    onError: (e) =>
      setError((e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Could not save.'),
  })

  const segments = Math.max(1, Math.ceil(body.length / 160))

  return (
    <div style={{ border: '1px solid var(--line, #e3e8f0)', borderRadius: 12, padding: 16, marginTop: 12 }}>
      <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'flex-end' }}>
        <label className="field" style={{ flex: 1, minWidth: 220 }}>
          <span className="field__label">Template name</span>
          <input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Interview reminder" />
        </label>
        <label className="field" style={{ width: 140 }}>
          <span className="field__label">Status</span>
          <select className="input" value={status} onChange={(e) => setStatus(e.target.value as SmsTemplate['status'])}>
            <option value="ACTIVE">Active</option>
            <option value="DRAFT">Draft</option>
            <option value="ARCHIVED">Archived</option>
          </select>
        </label>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 240px', gap: 16, marginTop: 14 }}>
        <div>
          <div className="field__label" style={{ marginBottom: 6 }}>Message</div>
          <textarea
            className="input"
            style={{ width: '100%', minHeight: 110, resize: 'vertical', font: 'inherit', fontSize: 13.5, lineHeight: 1.5 }}
            value={body}
            onChange={(e) => setBody(e.target.value)}
            placeholder="Hi {{candidate_name}}, …"
          />
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 8 }}>
            {MERGE_FIELDS.map((f) => (
              <button
                key={f}
                className="btn btn--outline btn--sm"
                style={{ fontSize: 11.5, padding: '3px 9px' }}
                onClick={() => setBody((b) => `${b}{{${f}}}`)}
              >
                {'{{'}{f}{'}}'}
              </button>
            ))}
          </div>
          <div style={{ fontSize: 11.5, color: body.length > 320 ? '#a33a3a' : 'var(--ink-4)', marginTop: 8 }}>
            {body.length} characters · {segments} SMS segment{segments > 1 ? 's' : ''}
            {body.length > 320 ? ' — keep candidate texts short' : ''}
          </div>
        </div>

        {/* phone preview */}
        <div>
          <div className="field__label" style={{ marginBottom: 6 }}>Preview</div>
          <div style={{ border: '1px solid var(--line, #e3e8f0)', borderRadius: 18, padding: '14px 12px', background: '#fafbfd', minHeight: 150 }}>
            <div style={{ fontSize: 10.5, color: 'var(--ink-4)', textAlign: 'center', marginBottom: 8 }}>Bank of America Careers</div>
            <div
              style={{
                background: '#e9ebef', color: 'var(--ink-1)', borderRadius: '14px 14px 14px 4px',
                padding: '9px 12px', fontSize: 12.5, lineHeight: 1.45, whiteSpace: 'pre-wrap', wordBreak: 'break-word',
              }}
            >
              {body || 'Your message appears here…'}
            </div>
          </div>
        </div>
      </div>

      {error && <div style={{ color: '#a33a3a', fontSize: 12.5, marginTop: 10 }}>{error}</div>}
      <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
        <button className="btn btn--primary btn--sm" disabled={save.isPending} onClick={() => save.mutate()}>
          {template ? 'Save changes' : 'Create template'}
        </button>
        <button className="btn btn--ghost btn--sm" onClick={onDone}>Cancel</button>
      </div>
    </div>
  )
}

export default function SmsTemplates() {
  const qc = useQueryClient()
  const { data: templates } = useQuery({ queryKey: ['sms-templates'], queryFn: commsApi.getSmsTemplates })
  const [editing, setEditing] = useState<SmsTemplate | null>(null)
  const [creating, setCreating] = useState(false)

  const remove = useMutation({
    mutationFn: (id: string) => commsApi.deleteSmsTemplate(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['sms-templates'] }),
  })

  return (
    <div className="card" style={{ marginTop: 18 }}>
      <div className="card__head" style={{ display: 'flex', alignItems: 'center' }}>
        <div style={{ marginRight: 'auto' }}>
          <h3 style={{ margin: 0 }}>SMS templates</h3>
          <div style={{ fontSize: 12, color: 'var(--ink-4)', marginTop: 2 }}>
            Candidate text messages for the journey workflow — status updates, confirmations, reminders. Web chat + SMS only.
          </div>
        </div>
        <button
          className="btn btn--primary btn--sm"
          onClick={() => {
            setCreating(true)
            setEditing(null)
          }}
        >
          New SMS template
        </button>
      </div>
      <div className="card__body">
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Template</th>
                <th>Message</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {(templates ?? []).map((t) => (
                <tr key={t.id} onClick={() => { setEditing(t); setCreating(false) }} style={{ cursor: 'pointer' }}>
                  <td className="t-strong" style={{ whiteSpace: 'nowrap' }}>{t.name}</td>
                  <td className="t-muted" style={{ maxWidth: 420, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {t.body}
                  </td>
                  <td style={{ whiteSpace: 'nowrap' }}>
                    {statusDot(t.status)}
                    <span className="t-muted" style={{ fontSize: 12.5 }}>{t.status.charAt(0) + t.status.slice(1).toLowerCase()}</span>
                  </td>
                  <td className="t-right" onClick={(e) => e.stopPropagation()}>
                    <button
                      className="btn btn--ghost btn--sm"
                      onClick={() => {
                        if (confirm(`Delete "${t.name}"? Workflows using it will need a new template.`)) remove.mutate(t.id)
                      }}
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
              {(templates ?? []).length === 0 && (
                <tr>
                  <td colSpan={4} style={{ textAlign: 'center', color: 'var(--ink-4)', padding: '26px 18px' }}>
                    No SMS templates yet.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        {(creating || editing) && (
          <Editor
            template={editing}
            onDone={() => {
              setCreating(false)
              setEditing(null)
            }}
          />
        )}
      </div>
    </div>
  )
}
