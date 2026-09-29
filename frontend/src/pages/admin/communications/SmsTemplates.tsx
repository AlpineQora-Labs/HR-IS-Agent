import { useCallback, useEffect, useMemo, useState, type ChangeEvent } from 'react'
import { AgGridReact } from 'ag-grid-react'
import type { ColDef, SizeColumnsToFitGridStrategy } from 'ag-grid-community'
import { commsApi, type SmsTemplate } from './commsApi'
import { Button } from './TvButton'

/* SMS templates — the text-message channel of Communications, rendered in
   the SAME table-card + grid chrome as the email list (one page, two
   channels). Editing swaps the grid for an inline editor inside the card.
   Channel policy: web chat + SMS only (never WhatsApp). */

const MERGE_FIELDS = ['candidate_name', 'job_title', 'interview_type', 'interview_time', 'status', 'link']

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

  const segments = Math.max(1, Math.ceil(body.length / 160))

  const save = async () => {
    setBusy(true)
    setError(null)
    try {
      if (template) await commsApi.updateSmsTemplate(template.id, { name, status, body })
      else await commsApi.createSmsTemplate({ name, status, body })
      onSaved()
      onDone()
    } catch (e) {
      setError((e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Could not save.')
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

          <div style={{ fontSize: 13, fontWeight: 600, color: '#1d1d1f', marginBottom: 6 }}>Message</div>
          <textarea
            className="input"
            style={{ width: '100%', minHeight: 130, resize: 'vertical', font: 'inherit', fontSize: 13.5, lineHeight: 1.55 }}
            value={body}
            onChange={(e) => setBody(e.target.value)}
            placeholder="Hi {{candidate_name}}, …"
          />
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 10 }}>
            {MERGE_FIELDS.map((f) => (
              <button
                key={f}
                onClick={() => setBody((b) => `${b}{{${f}}}`)}
                style={{
                  font: 'inherit', fontSize: 11.5, cursor: 'pointer', padding: '4px 10px',
                  border: '1px solid #d2d2d7', borderRadius: 999, background: '#fff', color: '#1d1d1f',
                }}
              >
                {'{{'}{f}{'}}'}
              </button>
            ))}
          </div>
          <div style={{ fontSize: 12, color: body.length > 320 ? '#b3261e' : '#86868b', marginTop: 10 }}>
            {body.length} characters · {segments} SMS segment{segments > 1 ? 's' : ''}
            {body.length > 320 ? ' — keep candidate texts short' : ''}
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
              {body || 'Your message appears here…'}
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
      } catch {
        alert('Failed to delete template')
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
        cellRenderer: (params: { data: SmsTemplate }) => (
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
