import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent } from 'react'
import { AgGridReact } from 'ag-grid-react'
import type { ColDef, SizeColumnsToFitGridStrategy } from 'ag-grid-community'
import { useQueryClient } from '@tanstack/react-query'
import { qk, useJourney } from '@/api/hooks'
import { commsApi, refusal, type SmsPreview, type SmsTemplate } from './commsApi'
import { Button } from './TvButton'

/* SMS templates — the text-message channel of Communications, rendered in
   the SAME table-card + grid chrome as the email list (one page, two
   channels). Editing swaps the grid for an inline editor inside the card.
   Channel policy: web chat + SMS only (never WhatsApp). */

/** Shown until the server's list of fields arrives. */
const FIELDS_MEANWHILE = ['candidate_name', 'job_title', 'interview_type', 'interview_time', 'link']

const formatDate = (iso?: string | null) => (iso ? iso.substring(0, 10) : '')

function StatusCell({ value }: { value?: string }) {
  const label = value ? value.charAt(0) + value.slice(1).toLowerCase() : 'Draft'
  return (
    <span>
      <span
        style={{
          width: 7, height: 7, borderRadius: 999, display: 'inline-block', marginRight: 7,
          background: value === 'ACTIVE' ? '#1a9d55' : value === 'ARCHIVED' ? '#c9d1de' : '#c98a00',
        }}
      />
      {label}
    </span>
  )
}

function SmsEditor({
  template,
  onDone,
  onSaved,
}: {
  template: SmsTemplate | null
  onDone: () => void
  onSaved: () => void
}) {
  const [name, setName] = useState(template?.name ?? '')
  const [status, setStatus] = useState<SmsTemplate['status']>(template?.status ?? 'ACTIVE')
  const [body, setBody] = useState(template?.body ?? '')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const message = useRef<HTMLTextAreaElement>(null)
  const queryClient = useQueryClient()

  // The step of the workflow drawing this template is offered for: '' for any step its fields fit.
  // A journey point's own template already belongs to its point, so it is not asked.
  const [offeredFor, setOfferedFor] = useState(template?.journeyPoint ?? '')

  // The fields a message can carry, and what this template's point needs, are the server's.
  const { data: journey } = useJourney()
  const point = template?.templateKey
    ? journey?.points.find((p) => p.templateKey === template.templateKey)
    : journey?.points.find((p) => p.key === offeredFor)
  const steps = journey?.points.filter((p) => p.drawn && p.byText) ?? []
  // A step saved before that the list no longer holds is still shown, so what is stored can be seen and cleared.
  const savedStepUnlisted = offeredFor !== '' && !steps.some((p) => p.key === offeredFor)
  // A template for one step is offered the fields that step has values for; the server refuses the rest.
  const fields = (journey?.fields ?? FIELDS_MEANWHILE.map((name) => ({ name, holds: '' })))
    .filter((f) => !point || point.has.includes(f.name))

  // The length and parts are the server's count, the one the candidate's record shows; it is asked
  // for once typing pauses. `body` says which wording a count is for.
  const [preview, setPreview] = useState<(SmsPreview & { body: string }) | null>(null)
  const [uncounted, setUncounted] = useState(false)
  useEffect(() => {
    let live = true
    const pause = setTimeout(() => {
      commsApi.previewSmsTemplate(body)
        .then((p) => {
          if (!live) return
          setPreview({ ...p, body })
          setUncounted(false)
        })
        .catch(() => {
          if (live) setUncounted(true)
        })
    }, 300)
    return () => {
      live = false
      clearTimeout(pause)
    }
  }, [body])

  /** Put a field where the cursor is, not at the end. */
  const insert = (name: string) => {
    const token = `{{${name}}}`
    const el = message.current
    const from = el?.selectionStart ?? body.length
    const to = el?.selectionEnd ?? body.length
    setBody(body.slice(0, from) + token + body.slice(to))
    requestAnimationFrame(() => {
      el?.focus()
      el?.setSelectionRange(from + token.length, from + token.length)
    })
  }

  const save = async () => {
    setBusy(true)
    setError(null)
    try {
      const journeyPoint = template?.templateKey ? null : offeredFor || null
      if (template) await commsApi.updateSmsTemplate(template.id, { name, status, body, journeyPoint })
      else await commsApi.createSmsTemplate({ name, status, body, journeyPoint })
      // The workflow canvas offers whatever the journey says fits: ask it again, so a saved template shows there at once.
      queryClient.invalidateQueries({ queryKey: qk.journey })
      onSaved()
      onDone()
    } catch (e) {
      setError(refusal(e, 'Could not save.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div style={{ padding: '22px 24px 26px' }}>
      <button
        onClick={onDone}
        style={{ font: 'inherit', border: 'none', background: 'none', cursor: 'pointer', color: '#007aff', fontSize: 13, fontWeight: 500, padding: 0, marginBottom: 16 }}
      >
        ‹ All SMS templates
      </button>

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) 300px', gap: 28 }}>
        <div>
          <div style={{ display: 'flex', gap: 14, marginBottom: 16 }}>
            <label style={{ flex: 1 }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: '#1d1d1f', marginBottom: 6 }}>Template name</div>
              <input
                className="input"
                style={{ width: '100%' }}
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g. Interview reminder"
              />
            </label>
            <label style={{ width: 150 }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: '#1d1d1f', marginBottom: 6 }}>Status</div>
              <select className="input" style={{ width: '100%' }} value={status} onChange={(e) => setStatus(e.target.value as SmsTemplate['status'])}>
                <option value="ACTIVE">Active</option>
                <option value="DRAFT">Draft</option>
                <option value="ARCHIVED">Archived</option>
              </select>
            </label>
          </div>

          {template?.templateKey && (
            <div style={{ fontSize: 12.5, color: '#6e6e73', lineHeight: 1.5, margin: '-4px 0 16px' }}>
              Candidate journey: sent {(template.sentWhen ?? point?.sentWhen ?? '').replace(/^./, (c) => c.toLowerCase())}.
              {point && point.needs.length > 0
                ? ` It has to carry ${point.needs.map((f) => `{{${f}}}`).join(', ')}.`
                : ''}{' '}
              A journey template stays Active and cannot be deleted.
            </div>
          )}

          {!template?.templateKey && (
            <div style={{ margin: '-2px 0 16px' }}>
              <label style={{ display: 'block' }}>
                <div style={{ fontSize: 13, fontWeight: 600, color: '#1d1d1f', marginBottom: 6 }}>Offered on the workflow for</div>
                <select className="input" style={{ width: '100%' }} value={offeredFor} onChange={(e) => setOfferedFor(e.target.value)}>
                  <option value="">Any step its fields suit</option>
                  {steps.map((p) => (
                    <option key={p.key} value={p.key}>
                      {p.sentWhen}
                    </option>
                  ))}
                  {savedStepUnlisted && (
                    <option value={offeredFor}>{template?.offeredFor ?? offeredFor} (no longer on the workflow)</option>
                  )}
                </select>
              </label>
              <div style={{ fontSize: 12.5, color: '#6e6e73', lineHeight: 1.5, marginTop: 6 }}>
                {point
                  ? `A choice for that step on the workflow canvas, beside the journey's own wording.${
                      point.needs.length > 0 ? ` It has to carry ${point.needs.map((f) => `{{${f}}}`).join(', ')}.` : ''
                    } Only the fields that step has values for are offered.`
                  : 'Listed on the workflow canvas for every step whose fields it can be filled with.'}
              </div>
            </div>
          )}

          {/* The fields as a dropdown above the message: pick one and it lands where the cursor is. The
              value stays empty so the same field can be picked again, and the list reads as a menu, not a state.
              On Windows and Linux the arrow keys change a closed select's value and fire change at once, which
              would insert the first field on the first press; so they open the list instead, and only a
              committed choice inserts. Where showPicker is missing, Space or Alt+Down still opens it. */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 14, marginBottom: 6 }}>
            <div style={{ fontSize: 13, fontWeight: 600, color: '#1d1d1f' }}>Message</div>
            <label style={{ flex: '1 1 auto', display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 8, fontSize: 12.5, color: '#6e6e73', minWidth: 0 }}>
              <span style={{ whiteSpace: 'nowrap' }}>Insert a field</span>
              <select
                className="input"
                style={{ flex: '1 1 180px', minWidth: 0, maxWidth: 380, fontSize: 12.5, padding: '5px 8px' }}
                value=""
                onKeyDown={(e) => {
                  if (e.altKey || !['ArrowDown', 'ArrowUp', 'Home', 'End', 'PageDown', 'PageUp'].includes(e.key)) return
                  e.preventDefault()
                  try {
                    (e.currentTarget as HTMLSelectElement & { showPicker?: () => void }).showPicker?.()
                  } catch {
                    /* not allowed here: the list stays closed and nothing is inserted */
                  }
                }}
                onChange={(e) => {
                  if (e.target.value) insert(e.target.value)
                }}
              >
                <option value="">Choose a field to insert at the cursor</option>
                {fields.map((f) => (
                  <option key={f.name} value={f.name}>
                    {`{{${f.name}}}`}{f.holds ? ` · ${f.holds}` : ''}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <textarea
            ref={message}
            className="input"
            style={{ width: '100%', minHeight: 130, resize: 'vertical', font: 'inherit', fontSize: 13.5, lineHeight: 1.55 }}
            value={body}
            onChange={(e) => setBody(e.target.value)}
            placeholder="Hi {{candidate_name}}, …"
          />
          <div style={{ fontSize: 12, color: !uncounted && preview?.note ? '#b3261e' : '#86868b', marginTop: 10 }}>
            {uncounted
              ? 'The length could not be counted.'
              : preview
                ? `${preview.characters} characters · ${preview.parts} text part${preview.parts === 1 ? '' : 's'}${preview.note ? ` — ${preview.note}` : ''}`
                : 'Counting…'}
          </div>
          <div style={{ fontSize: 11.5, color: '#a1a1a6', marginTop: 3 }}>
            Counted on the template wording as it is sent, before fields are filled in. A field’s value changes the length.
          </div>

          {error && <div style={{ color: '#b3261e', fontSize: 13, marginTop: 12 }}>{error}</div>}
          <div style={{ display: 'flex', gap: 10, marginTop: 18 }}>
            <Button variant="primary" size="sm" onClick={save} disabled={busy}>
              {template ? 'Save changes' : 'Create template'}
            </Button>
            <Button variant="secondary" size="sm" onClick={onDone}>
              Cancel
            </Button>
          </div>
        </div>

        {/* live phone preview */}
        <div>
          <div style={{ fontSize: 13, fontWeight: 600, color: '#1d1d1f', marginBottom: 6 }}>Preview</div>
          <div style={{ border: '1px solid #e5e5ea', borderRadius: 22, padding: '16px 14px', background: '#fafafa' }}>
            <div style={{ fontSize: 11, color: '#86868b', textAlign: 'center', marginBottom: 10 }}>Bank of America Careers</div>
            <div
              style={{
                background: '#e9e9eb', color: '#1d1d1f', borderRadius: '16px 16px 16px 5px',
                padding: '10px 13px', fontSize: 13, lineHeight: 1.45, whiteSpace: 'pre-wrap', wordBreak: 'break-word',
              }}
            >
              {/* What the phone is sent once the server has made the wording plain; the typed text until then. */}
              {(preview && preview.body === body ? preview.plainText : body) || 'Your message appears here…'}
            </div>
            <div style={{ fontSize: 10.5, color: '#a1a1a6', marginTop: 8, textAlign: 'right' }}>Delivered</div>
          </div>
        </div>
      </div>
    </div>
  )
}

/** The SMS channel view — swaps in for the email grid inside the same table card. */
export default function SmsTemplatesView() {
  const [templates, setTemplates] = useState<SmsTemplate[]>([])
  const [loading, setLoading] = useState(true)
  const [quickFilterText, setQuickFilterText] = useState<string>()
  const [editing, setEditing] = useState<SmsTemplate | null>(null)
  const [creating, setCreating] = useState(false)

  const load = useCallback(() => {
    commsApi.getSmsTemplates()
      .then(setTemplates)
      .catch(() => setTemplates([]))
      .finally(() => setLoading(false))
  }, [])
  useEffect(load, [load])

  const handleDelete = useCallback(
    async (id: string) => {
      const t = templates.find((t) => t.id === id)
      if (!t || !confirm(`Delete "${t.name}"? Workflows using it will need a new template.`)) return
      try {
        await commsApi.deleteSmsTemplate(id)
        setTemplates((prev) => prev.filter((t) => t.id !== id))
      } catch (e) {
        alert(refusal(e, 'The template could not be deleted.'))
      }
    },
    [templates],
  )

  const columnDefs = useMemo(
    (): ColDef[] => [
      {
        field: 'name',
        headerName: 'Template Name',
        flex: 1.2,
        minWidth: 180,
        onCellClicked: (params) => {
          if (params.data) {
            setEditing(params.data as SmsTemplate)
            setCreating(false)
          }
        },
        cellStyle: { color: '#007aff', cursor: 'pointer', fontWeight: 500 },
      },
      {
        field: 'body',
        headerName: 'Message',
        flex: 2.4,
        minWidth: 260,
        cellStyle: { color: '#6e6e73' },
      },
      {
        field: 'sentWhen',
        headerName: 'Sent',
        flex: 1.4,
        minWidth: 200,
        cellStyle: { color: '#6e6e73' },
        // A journey point's own wording says when it is sent; another wording says which step it is a choice for.
        valueGetter: (params) => {
          const t = params.data as SmsTemplate | undefined
          return t?.sentWhen ?? (t?.offeredFor ? `A choice: ${t.offeredFor.replace(/^./, (c) => c.toLowerCase())}` : '')
        },
      },
      {
        field: 'updatedAt',
        headerName: 'Last Updated',
        width: 140,
        valueFormatter: (params) => formatDate(params.value),
      },
      {
        field: 'status',
        headerName: 'Status',
        width: 120,
        cellRenderer: (params: { value?: string }) => <StatusCell value={params.value} />,
      },
      {
        headerName: 'Actions',
        colId: 'actions',
        width: 100,
        sortable: false,
        filter: false,
        resizable: false,
        // A journey's own template cannot be deleted, so the action is not offered.
        cellRenderer: (params: { data: SmsTemplate }) =>
          params.data.templateKey ? null : (
            <button
              onClick={() => handleDelete(params.data.id)}
              style={{ font: 'inherit', fontSize: 12.5, border: 'none', background: 'none', cursor: 'pointer', color: '#b3261e' }}
            >
              Delete
            </button>
          ),
      },
    ],
    [handleDelete],
  )

  const defaultColDef = useMemo<ColDef>(() => ({ sortable: true, filter: false, resizable: true }), [])
  const autoSizeStrategy = useMemo<SizeColumnsToFitGridStrategy>(() => ({ type: 'fitGridWidth' }), [])

  const onFilterTextBoxChanged = useCallback(
    ({ target: { value } }: ChangeEvent<HTMLInputElement>) => setQuickFilterText(value),
    [],
  )

  if (creating || editing) {
    return (
      <div className="comms-library__table-card">
        <SmsEditor
          template={editing}
          onDone={() => {
            setCreating(false)
            setEditing(null)
          }}
          onSaved={load}
        />
      </div>
    )
  }

  return (
    <div className="comms-library__table-card">
      <div className="comms-library__toolbar">
        <div style={{ fontSize: 13, color: '#86868b' }}>
          Candidate text messages for the journey workflow. Web chat + SMS only.
        </div>
        <div className="comms-library__toolbar-right">
          <div className="comms-library__search-wrap">
            <svg className="comms-library__search-icon" width="16" height="16" viewBox="0 0 16 16" fill="none">
              <path fillRule="evenodd" clipRule="evenodd" d="M11.5 7a4.5 4.5 0 1 1-9 0 4.5 4.5 0 0 1 9 0Zm-.82 4.74a6 6 0 1 1 1.06-1.06l2.79 2.79a.75.75 0 1 1-1.06 1.06l-2.79-2.79Z" fill="currentColor" />
            </svg>
            <input
              type="text"
              placeholder="Search templates..."
              onInput={onFilterTextBoxChanged}
              className="comms-library__search-input"
            />
          </div>
          <Button
            variant="primary"
            size="sm"
            onClick={() => {
              setCreating(true)
              setEditing(null)
            }}
          >
            + Create Template
          </Button>
        </div>
      </div>

      {loading ? (
        <p style={{ textAlign: 'center', padding: 60, color: '#86868b' }}>Loading templates...</p>
      ) : (
        <div className="ag-theme-quartz comms-library__grid">
          <AgGridReact
            rowData={templates}
            columnDefs={columnDefs}
            defaultColDef={defaultColDef}
            autoSizeStrategy={autoSizeStrategy}
            pagination
            paginationPageSize={10}
            paginationPageSizeSelector={[10, 25, 50]}
            quickFilterText={quickFilterText}
            domLayout="autoHeight"
            rowHeight={40}
            getRowId={(params) => String((params.data as SmsTemplate).id)}
            overlayNoRowsTemplate="<span style='padding:40px;color:#86868b;font-size:14px'>No SMS templates yet</span>"
          />
        </div>
      )}
    </div>
  )
}
