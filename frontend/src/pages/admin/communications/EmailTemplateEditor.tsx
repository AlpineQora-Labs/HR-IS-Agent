import { useState, useEffect, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useEditor, EditorContent } from '@tiptap/react'
import { Node, mergeAttributes } from '@tiptap/core'
import StarterKit from '@tiptap/starter-kit'
import Underline from '@tiptap/extension-underline'
import TextAlign from '@tiptap/extension-text-align'
import Link from '@tiptap/extension-link'
import { Button } from './TvButton'
import Breadcrumb from './Breadcrumb'
import TabBar from './TabBar'
import FormField from './FormField'
import ToggleSwitch from './ToggleSwitch'
import { commsApi as api } from './commsApi'
import type { EmailTemplate, EmailCategory, EmailTemplateAssignment } from './emailTemplate'
import '@/styles/tv-comms.css'
import { EMAIL_CATEGORIES, TRIGGER_TYPES } from './emailTemplate'
import type { Survey } from './commsApi'

// ─── Custom TipTap Extensions ────────────────────────────────────────────────

// Inline non-editable merge field chip (e.g. {{participant_name}})
const MergeFieldExtension = Node.create({
  name: 'mergeField',
  group: 'inline',
  inline: true,
  atom: true,
  addAttributes() {
    return { field: { default: null } }
  },
  parseHTML() {
    return [{ tag: 'span[data-field]', getAttrs: el => ({ field: (el as HTMLElement).getAttribute('data-field') }) }]
  },
  renderHTML({ node, HTMLAttributes }) {
    return ['span', mergeAttributes(HTMLAttributes, { class: 'merge-field-chip', 'data-field': node.attrs.field, contenteditable: 'false' }), node.attrs.field]
  },
})

// Block card container
const CardBlockExtension = Node.create({
  name: 'cardBlock',
  group: 'block',
  content: 'block+',
  defining: true,
  parseHTML() {
    return [{ tag: 'div.email-card-block' }]
  },
  renderHTML({ HTMLAttributes }) {
    return ['div', mergeAttributes(HTMLAttributes, { class: 'email-card-block' }), 0]
  },
})

// Block CTA button (atomic — stores text+href as attributes)
const CtaButtonExtension = Node.create({
  name: 'ctaButton',
  group: 'block',
  atom: true,
  addAttributes() {
    return {
      text: { default: 'Click Here' },
      href: { default: '#' },
    }
  },
  parseHTML() {
    return [{ tag: 'a.email-cta-button', getAttrs: el => ({ text: (el as HTMLElement).textContent, href: (el as HTMLElement).getAttribute('href') }) }]
  },
  renderHTML({ node, HTMLAttributes }) {
    return ['a', mergeAttributes(HTMLAttributes, { class: 'email-cta-button', href: node.attrs.href, target: '_blank' }), node.attrs.text]
  },
})

// ─── Static data ─────────────────────────────────────────────────────────────

type EditorTab = 'details' | 'builder' | 'preview' | 'settings'

const TABS: { key: EditorTab; label: string }[] = [
  { key: 'details',  label: 'Details' },
  { key: 'builder',  label: 'Template Builder' },
  { key: 'preview',  label: 'Preview' },
  { key: 'settings', label: 'Settings' },
]

const MERGE_FIELDS: Record<string, { field: string; label: string }[]> = {
  Participant: [
    { field: '{{participant_name}}', label: 'Name' },
    { field: '{{email}}',            label: 'Email' },
    { field: '{{person_number}}',    label: 'Person Number' },
    { field: '{{standard_id}}',      label: 'Standard ID' },
    { field: '{{manager_name}}',     label: 'Manager' },
    { field: '{{cohort}}',           label: 'Cohort' },
  ],
  Survey: [
    { field: '{{survey_title}}',    label: 'Survey Title' },
    { field: '{{survey_link}}',     label: 'Survey Link' },
    { field: '{{survey_due_date}}', label: 'Due Date' },
  ],
  Program: [
    { field: '{{program_name}}',  label: 'Program Name' },
    { field: '{{company_name}}',  label: 'Company' },
  ],
  Sender: [
    { field: '{{sender_name}}',  label: 'Sender Name' },
    { field: '{{sender_email}}', label: 'Sender Email' },
  ],
}

const SAMPLE_DATA: Record<string, string> = {
  '{{participant_name}}': 'Jane Smith',
  '{{email}}':            'jane.smith@example.com',
  '{{person_number}}':    'P00123',
  '{{standard_id}}':      'STD-4567',
  '{{manager_name}}':     'Robert Johnson',
  '{{cohort}}':           'Spring 2026',
  '{{participant_type}}': 'NEW_HIRE',
  '{{survey_title}}':     'Employee Voice Survey 2026',
  '{{survey_link}}':      'https://surveys.example.com/respond/abc123',
  '{{survey_due_date}}':  'April 15, 2026',
  '{{survey_description}}': 'Annual engagement and sentiment survey',
  '{{program_name}}':     'Teammate Voices',
  '{{program_description}}': 'Employee feedback program',
  '{{company_name}}':     'Acme Corporation',
  '{{sender_name}}':      'HR Team',
  '{{sender_title}}':     'People & Culture',
  '{{sender_email}}':     'hr@acme.com',
}

function replaceMergeFields(html: string): string {
  let result = html
  for (const [field, value] of Object.entries(SAMPLE_DATA)) {
    result = result.split(field).join(value)
  }
  result = result.replace(/\{\{#if[^}]*\}\}/g, '').replace(/\{\{\/if\}\}/g, '')
  return result
}

// ─── Component ───────────────────────────────────────────────────────────────

export default function EmailTemplateEditor() {
  const { templateId } = useParams<{ templateId: string }>()
  const navigate = useNavigate()
  const isEditMode = !!templateId

  const [activeTab, setActiveTab] = useState<EditorTab>('details')
  const [loading, setLoading] = useState(isEditMode)
  const [saving, setSaving] = useState(false)
  const [saveMessage, setSaveMessage] = useState('')

  // Template state
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [category, setCategory] = useState<EmailCategory>('CUSTOM')
  const [subject, setSubject] = useState('')
  const [fromName, setFromName] = useState('Bank of America Careers')
  const [bodyHtml, setBodyHtml] = useState('<p>Start typing your email content here...</p>')
  const [status, setStatus] = useState<'DRAFT' | 'ACTIVE'>('DRAFT')
  const [previewMode, setPreviewMode] = useState<'desktop' | 'mobile'>('desktop')

  // Toolbar popover state
  const [mergeDropdownOpen, setMergeDropdownOpen] = useState(false)
  const [linkInputOpen, setLinkInputOpen] = useState(false)
  const [linkUrl, setLinkUrl] = useState('')
  const [ctaFormOpen, setCtaFormOpen] = useState(false)
  const [ctaText, setCtaText] = useState('')
  const [ctaUrl, setCtaUrl] = useState('')

  // Assignment state (Settings tab)
  const [assignments, setAssignments] = useState<EmailTemplateAssignment[]>([])
  const [surveys, setSurveys] = useState<Survey[]>([])
  const [newTrigger, setNewTrigger] = useState('')
  const [newSurveyId, setNewSurveyId] = useState<string>('')
  const [newDelayDays, setNewDelayDays] = useState(0)

  // Pending content to sync into editor after it initialises
  const pendingContent = useRef<string | null>(null)

  // ─── TipTap editor ─────────────────────────────────────────────────────────

  const editor = useEditor({
    extensions: [
      StarterKit,
      Underline,
      TextAlign.configure({ types: ['heading', 'paragraph'] }),
      Link.configure({ openOnClick: false, autolink: false }),
      MergeFieldExtension,
      CardBlockExtension,
      CtaButtonExtension,
    ],
    content: '<p>Start typing your email content here...</p>',
    onUpdate: ({ editor }) => setBodyHtml(editor.getHTML()),
    editorProps: {
      attributes: { class: 'wysiwyg-editor' },
    },
  })

  // When editor becomes available, apply any pending content from API load
  useEffect(() => {
    if (editor && pendingContent.current !== null) {
      editor.commands.setContent(pendingContent.current, false)
      pendingContent.current = null
    }
  }, [editor])

  // ─── Data loading ───────────────────────────────────────────────────────────

  useEffect(() => {
    api.getSurveys().then(setSurveys).catch(() => {})
  }, [])

  useEffect(() => {
    if (!templateId) return
    api.getTemplateAssignments(templateId).then(setAssignments).catch(() => {})
  }, [templateId])

  useEffect(() => {
    if (!templateId) return
    api.getEmailTemplate(templateId)
      .then(t => {
        setName(t.name)
        setDescription(t.description || '')
        setCategory(t.category)
        setSubject(t.subject)
        setFromName(t.fromName || 'Teammate Voices')
        setBodyHtml(t.bodyHtml)
        setStatus(t.status === 'ACTIVE' ? 'ACTIVE' : 'DRAFT')
        // Push content to editor — if editor isn't ready yet, pendingContent ref handles it
        if (editor) {
          editor.commands.setContent(t.bodyHtml, false)
        } else {
          pendingContent.current = t.bodyHtml
        }
      })
      .catch(() => navigate('/admin/communications'))
      .finally(() => setLoading(false))
  }, [templateId, navigate]) // eslint-disable-line react-hooks/exhaustive-deps

  // ─── Assignments ────────────────────────────────────────────────────────────

  const handleAddAssignment = async () => {
    if (!templateId || !newTrigger || !newSurveyId) {
      alert('Please select both a trigger type and a survey.')
      return
    }
    try {
      const saved = await api.saveTemplateAssignment(templateId!, {
        triggerType: newTrigger,
        surveyId: newSurveyId,
        sendDelayDays: newDelayDays,
        isActive: true,
      })
      setAssignments(prev => [...prev.filter(a => a.assignmentId !== saved.assignmentId), saved])
      setNewTrigger('')
      setNewSurveyId('')
      setNewDelayDays(0)
    } catch {
      alert('Failed to save assignment.')
    }
  }

  const handleDeleteAssignment = async (assignmentId: string) => {
    try {
      await api.deleteTemplateAssignment(assignmentId)
      setAssignments(prev => prev.filter(a => a.assignmentId !== assignmentId))
    } catch {
      alert('Failed to delete assignment.')
    }
  }

  // ─── Save ───────────────────────────────────────────────────────────────────

  const handleSave = async () => {
    if (!name.trim() || !subject.trim()) {
      alert('Template name and subject line are required.')
      return
    }
    setSaving(true)
    setSaveMessage('')
    try {
      const payload: Partial<EmailTemplate> = {
        name: name.trim(),
        description: description.trim(),
        category,
        subject: subject.trim(),
        fromName: fromName.trim(),
        bodyHtml,
        status,
      }
      if (isEditMode) {
        await api.updateEmailTemplate(templateId!, payload)
        setSaveMessage('Template saved!')
      } else {
        const created = await api.createEmailTemplate(payload)
        setSaveMessage('Template created!')
        setTimeout(() => navigate(`/admin/communications/${created.templateId}/edit`), 1500)
      }
      setTimeout(() => setSaveMessage(''), 3000)
    } catch {
      setSaveMessage('Failed to save.')
    } finally {
      setSaving(false)
    }
  }

  // ─── Toolbar helpers ────────────────────────────────────────────────────────

  const insertLink = () => {
    if (!linkUrl.trim()) return
    editor?.chain().focus().setLink({ href: linkUrl.trim() }).run()
    setLinkUrl('')
    setLinkInputOpen(false)
  }

  const insertCtaButton = () => {
    if (!ctaText.trim() || !ctaUrl.trim()) return
    editor?.chain().focus().insertContent({
      type: 'ctaButton',
      attrs: { text: ctaText.trim(), href: ctaUrl.trim() },
    }).run()
    setCtaText('')
    setCtaUrl('')
    setCtaFormOpen(false)
  }

  const insertMergeField = (field: string) => {
    editor?.chain().focus().insertContent({ type: 'mergeField', attrs: { field } }).run()
    setMergeDropdownOpen(false)
  }

  // ─── Render ─────────────────────────────────────────────────────────────────

  if (loading) {
    return <p style={{ textAlign: 'center', padding: 60, color: '#86868b' }}>Loading template...</p>
  }

  return (
    <div className="email-editor">
      <Breadcrumb items={[
        { label: 'Administration', path: '/admin' },
        { label: 'Email Templates', path: '/admin/communications' },
        { label: isEditMode ? name || 'Edit template' : 'Create template' },
      ]} />

      <div className="email-editor__header">
        <div>
          <h1 className="email-editor__title">{isEditMode ? name || 'Edit Template' : 'Create Template'}</h1>
          <span className={`email-editor__status email-editor__status--${status.toLowerCase()}`}>
            {status === 'ACTIVE' ? 'Active' : 'Draft'}
          </span>
        </div>
        <div className="email-editor__header-actions">
          <Button variant="secondary" size="sm" onClick={() => navigate('/admin/communications')}>Cancel</Button>
          <Button variant="primary" size="sm" onClick={handleSave} loading={saving}>
            {isEditMode ? 'Save' : 'Create'}
          </Button>
        </div>
      </div>

      {saveMessage && (
        <div className={`email-editor__message${saveMessage.includes('Failed') ? ' email-editor__message--error' : ''}`}>
          {saveMessage}
        </div>
      )}

      <div className="email-editor__card">
        <TabBar tabs={TABS} activeTab={activeTab} onChange={(tab) => setActiveTab(tab as EditorTab)} />

        {/* ===== DETAILS TAB ===== */}
        {activeTab === 'details' && (
          <div className="email-editor__form">
            <div className="email-editor__form-row">
              <FormField label="Template Name" required>
                <input className="email-editor__input" placeholder="e.g., Survey Invitation" value={name} onChange={e => setName(e.target.value)} />
              </FormField>
              <FormField label="Category" required>
                <select className="email-editor__select" value={category} onChange={e => setCategory(e.target.value as EmailCategory)}>
                  {EMAIL_CATEGORIES.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}
                </select>
              </FormField>
            </div>
            <FormField label="Description">
              <input className="email-editor__input" placeholder="Brief description of this template" value={description} onChange={e => setDescription(e.target.value)} />
            </FormField>
            <div className="email-editor__form-row">
              <FormField label="Subject Line" required>
                <input className="email-editor__input" placeholder="e.g., You are invited to complete: {{survey_title}}" value={subject} onChange={e => setSubject(e.target.value)} />
              </FormField>
              <FormField label="From Name">
                <input className="email-editor__input" placeholder="Teammate Voices" value={fromName} onChange={e => setFromName(e.target.value)} />
              </FormField>
            </div>
            <div className="email-editor__form-row">
              <div>
                <span className="email-editor__field-label">Status</span>
                <ToggleSwitch label={status === 'ACTIVE' ? 'Active' : 'Draft'} checked={status === 'ACTIVE'} onChange={v => setStatus(v ? 'ACTIVE' : 'DRAFT')} />
              </div>
            </div>
          </div>
        )}

        {/* ===== TEMPLATE BUILDER TAB ===== */}
        {activeTab === 'builder' && (
          <div className="email-editor__builder">

            {/* Toolbar */}
            <div className="wysiwyg-toolbar">

              {/* Text formatting */}
              <div className="wysiwyg-toolbar__group">
                <button className={`wysiwyg-toolbar__btn${editor?.isActive('bold') ? ' wysiwyg-toolbar__btn--active' : ''}`} onMouseDown={e => { e.preventDefault(); editor?.chain().focus().toggleBold().run() }} title="Bold"><b>B</b></button>
                <button className={`wysiwyg-toolbar__btn${editor?.isActive('italic') ? ' wysiwyg-toolbar__btn--active' : ''}`} onMouseDown={e => { e.preventDefault(); editor?.chain().focus().toggleItalic().run() }} title="Italic"><i>I</i></button>
                <button className={`wysiwyg-toolbar__btn${editor?.isActive('underline') ? ' wysiwyg-toolbar__btn--active' : ''}`} onMouseDown={e => { e.preventDefault(); editor?.chain().focus().toggleUnderline().run() }} title="Underline"><u>U</u></button>
                <button className={`wysiwyg-toolbar__btn${editor?.isActive('strike') ? ' wysiwyg-toolbar__btn--active' : ''}`} onMouseDown={e => { e.preventDefault(); editor?.chain().focus().toggleStrike().run() }} title="Strikethrough"><s>S</s></button>
              </div>

              <div className="wysiwyg-toolbar__separator" />

              {/* Block format */}
              <div className="wysiwyg-toolbar__group">
                <button className={`wysiwyg-toolbar__btn${editor?.isActive('heading', { level: 1 }) ? ' wysiwyg-toolbar__btn--active' : ''}`} onMouseDown={e => { e.preventDefault(); editor?.chain().focus().toggleHeading({ level: 1 }).run() }} title="Heading 1">H1</button>
                <button className={`wysiwyg-toolbar__btn${editor?.isActive('heading', { level: 2 }) ? ' wysiwyg-toolbar__btn--active' : ''}`} onMouseDown={e => { e.preventDefault(); editor?.chain().focus().toggleHeading({ level: 2 }).run() }} title="Heading 2">H2</button>
                <button className={`wysiwyg-toolbar__btn${editor?.isActive('paragraph') ? ' wysiwyg-toolbar__btn--active' : ''}`} onMouseDown={e => { e.preventDefault(); editor?.chain().focus().setParagraph().run() }} title="Paragraph">P</button>
              </div>

              <div className="wysiwyg-toolbar__separator" />

              {/* Lists */}
              <div className="wysiwyg-toolbar__group">
                <button className={`wysiwyg-toolbar__btn${editor?.isActive('bulletList') ? ' wysiwyg-toolbar__btn--active' : ''}`} onMouseDown={e => { e.preventDefault(); editor?.chain().focus().toggleBulletList().run() }} title="Bullet List">• List</button>
                <button className={`wysiwyg-toolbar__btn${editor?.isActive('orderedList') ? ' wysiwyg-toolbar__btn--active' : ''}`} onMouseDown={e => { e.preventDefault(); editor?.chain().focus().toggleOrderedList().run() }} title="Numbered List">1. List</button>
              </div>

              <div className="wysiwyg-toolbar__separator" />

              {/* Alignment */}
              <div className="wysiwyg-toolbar__group">
                <button className={`wysiwyg-toolbar__btn${editor?.isActive({ textAlign: 'left' }) ? ' wysiwyg-toolbar__btn--active' : ''}`} onMouseDown={e => { e.preventDefault(); editor?.chain().focus().setTextAlign('left').run() }} title="Align Left">⫷</button>
                <button className={`wysiwyg-toolbar__btn${editor?.isActive({ textAlign: 'center' }) ? ' wysiwyg-toolbar__btn--active' : ''}`} onMouseDown={e => { e.preventDefault(); editor?.chain().focus().setTextAlign('center').run() }} title="Align Center">≡</button>
                <button className={`wysiwyg-toolbar__btn${editor?.isActive({ textAlign: 'right' }) ? ' wysiwyg-toolbar__btn--active' : ''}`} onMouseDown={e => { e.preventDefault(); editor?.chain().focus().setTextAlign('right').run() }} title="Align Right">⫸</button>
              </div>

              <div className="wysiwyg-toolbar__separator" />

              {/* Link */}
              <div className="wysiwyg-toolbar__group" style={{ position: 'relative' }}>
                <button className={`wysiwyg-toolbar__btn${editor?.isActive('link') ? ' wysiwyg-toolbar__btn--active' : ''}`} onMouseDown={e => { e.preventDefault(); setLinkInputOpen(v => !v); setCtaFormOpen(false); setMergeDropdownOpen(false) }} title="Insert Link">🔗</button>
                {linkInputOpen && (
                  <div className="wysiwyg-inline-input">
                    <input
                      className="wysiwyg-inline-input__field"
                      placeholder="https://"
                      value={linkUrl}
                      onChange={e => setLinkUrl(e.target.value)}
                      onKeyDown={e => e.key === 'Enter' && insertLink()}
                      autoFocus
                    />
                    <button className="wysiwyg-inline-input__btn wysiwyg-inline-input__btn--primary" onClick={insertLink}>Insert</button>
                    <button className="wysiwyg-inline-input__btn" onClick={() => setLinkInputOpen(false)}>✕</button>
                  </div>
                )}
              </div>

              <div className="wysiwyg-toolbar__separator" />

              {/* Custom blocks */}
              <div className="wysiwyg-toolbar__group" style={{ position: 'relative' }}>
                <button className="wysiwyg-toolbar__btn" onMouseDown={e => { e.preventDefault(); editor?.chain().focus().insertContent({ type: 'cardBlock', content: [{ type: 'paragraph' }] }).run() }} title="Insert Card Block">▢ Card</button>

                <button className="wysiwyg-toolbar__btn" onMouseDown={e => { e.preventDefault(); setCtaFormOpen(v => !v); setLinkInputOpen(false); setMergeDropdownOpen(false) }} title="Insert CTA Button">▣ Button</button>
                {ctaFormOpen && (
                  <div className="wysiwyg-inline-input wysiwyg-inline-input--wide">
                    <input className="wysiwyg-inline-input__field" placeholder="Button text" value={ctaText} onChange={e => setCtaText(e.target.value)} autoFocus />
                    <input className="wysiwyg-inline-input__field" placeholder="https://" value={ctaUrl} onChange={e => setCtaUrl(e.target.value)} onKeyDown={e => e.key === 'Enter' && insertCtaButton()} />
                    <button className="wysiwyg-inline-input__btn wysiwyg-inline-input__btn--primary" onClick={insertCtaButton}>Insert</button>
                    <button className="wysiwyg-inline-input__btn" onClick={() => setCtaFormOpen(false)}>✕</button>
                  </div>
                )}

                <button className="wysiwyg-toolbar__btn" onMouseDown={e => { e.preventDefault(); editor?.chain().focus().setHorizontalRule().run() }} title="Insert Divider">― Line</button>
              </div>

              <div className="wysiwyg-toolbar__separator" />

              {/* Merge fields */}
              <div className="wysiwyg-toolbar__group" style={{ position: 'relative' }}>
                <button
                  className="wysiwyg-toolbar__btn wysiwyg-toolbar__btn--merge"
                  onMouseDown={e => { e.preventDefault(); setMergeDropdownOpen(v => !v); setLinkInputOpen(false); setCtaFormOpen(false) }}
                >
                  + Merge Field ▾
                </button>
                {mergeDropdownOpen && (
                  <div className="merge-dropdown">
                    {Object.entries(MERGE_FIELDS).map(([group, fields]) => (
                      <div key={group} className="merge-dropdown__group">
                        <div className="merge-dropdown__group-label">{group}</div>
                        {fields.map(f => (
                          <button key={f.field} className="merge-dropdown__item" onMouseDown={e => { e.preventDefault(); insertMergeField(f.field) }}>
                            <span className="merge-dropdown__item-label">{f.label}</span>
                            <code className="merge-dropdown__item-field">{f.field}</code>
                          </button>
                        ))}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>

            {/* TipTap editor area */}
            <div className="wysiwyg-editor__wrapper">
              <EditorContent editor={editor} />
            </div>
          </div>
        )}

        {/* ===== PREVIEW TAB ===== */}
        {activeTab === 'preview' && (
          <div className="email-editor__preview">
            <div className="email-editor__preview-toolbar">
              <button className={`email-editor__preview-toggle${previewMode === 'desktop' ? ' email-editor__preview-toggle--active' : ''}`} onClick={() => setPreviewMode('desktop')}>Desktop</button>
              <button className={`email-editor__preview-toggle${previewMode === 'mobile' ? ' email-editor__preview-toggle--active' : ''}`} onClick={() => setPreviewMode('mobile')}>Mobile</button>
            </div>
            <div className="email-editor__preview-subject">
              <strong>Subject:</strong> {replaceMergeFields(subject)}
            </div>
            <div className="email-editor__preview-from">
              <strong>From:</strong> {fromName} &lt;noreply@bofa.com&gt;
            </div>
            <div className={`email-editor__preview-frame email-editor__preview-frame--${previewMode}`}>
              <div dangerouslySetInnerHTML={{ __html: replaceMergeFields(bodyHtml) }} />
            </div>
          </div>
        )}

        {/* ===== SETTINGS TAB ===== */}
        {activeTab === 'settings' && (
          <div className="email-editor__form">
            <p style={{ color: '#86868b', fontSize: 14, marginBottom: 24 }}>
              Assign this template to surveys. Configure the trigger event and send delay.
            </p>

            {assignments.length > 0 && (
              <div style={{ marginBottom: 32 }}>
                <h3 style={{ fontSize: 15, fontWeight: 600, color: '#1d1d1f', marginBottom: 12 }}>Current Assignments</h3>
                <table className="assignment-table">
                  <thead>
                    <tr>
                      <th>Survey</th><th>Trigger</th><th>Delay (days)</th><th>Status</th><th style={{ width: 80 }}></th>
                    </tr>
                  </thead>
                  <tbody>
                    {assignments.map(a => {
                      const survey = surveys.find(s => s.surveyId === a.surveyId)
                      const trigger = TRIGGER_TYPES.find(t => t.value === a.triggerType)
                      return (
                        <tr key={a.assignmentId}>
                          <td>{survey?.title || `Survey #${a.surveyId}`}</td>
                          <td><span className="assignment-trigger-pill">{trigger?.label || a.triggerType}</span></td>
                          <td>{a.sendDelayDays || 0}</td>
                          <td>
                            <span className={`assignment-status ${a.isActive ? 'assignment-status--active' : 'assignment-status--inactive'}`}>
                              {a.isActive ? 'Active' : 'Inactive'}
                            </span>
                          </td>
                          <td>
                            <button className="assignment-delete-btn" onClick={() => handleDeleteAssignment(a.assignmentId)}>Remove</button>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}

            {isEditMode && (
              <div style={{ border: '1px solid #d2d2d7', borderRadius: 12, padding: 24 }}>
                <h3 style={{ fontSize: 15, fontWeight: 600, color: '#1d1d1f', marginBottom: 16 }}>Add Assignment</h3>
                <div className="email-editor__form-row" style={{ gap: 16 }}>
                  <FormField label="Survey">
                    <select className="email-editor__select" value={newSurveyId} onChange={e => setNewSurveyId(e.target.value)}>
                      <option value="">Select survey...</option>
                      {surveys.map(s => <option key={s.surveyId} value={s.surveyId}>{s.title}</option>)}
                    </select>
                  </FormField>
                  <FormField label="Trigger Event">
                    <select className="email-editor__select" value={newTrigger} onChange={e => setNewTrigger(e.target.value)}>
                      <option value="">Select trigger...</option>
                      {TRIGGER_TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
                    </select>
                  </FormField>
                  <FormField label="Delay (days)">
                    <input className="email-editor__input" type="number" min={0} max={365} value={newDelayDays} onChange={e => setNewDelayDays(Number(e.target.value))} style={{ width: 100 }} />
                  </FormField>
                </div>
                <div style={{ marginTop: 16 }}>
                  <Button variant="primary" size="sm" onClick={handleAddAssignment}>+ Add Assignment</Button>
                </div>
              </div>
            )}

            {!isEditMode && (
              <div style={{ padding: 40, textAlign: 'center', color: '#86868b', border: '1px dashed #d2d2d7', borderRadius: 12 }}>
                <p style={{ fontSize: 14 }}>Save the template first, then you can add survey assignments.</p>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
