export interface EmailTemplate {
  templateId: string
  name: string
  description: string
  category: EmailCategory
  subject: string
  fromName: string
  bodyHtml: string
  bodyJson?: string
  mergeFields?: string
  status: 'DRAFT' | 'ACTIVE' | 'ARCHIVED'
  isDefault: boolean
  createdBy?: number
  createdAt: string
  updatedAt: string
}

export type EmailCategory =
  | 'INVITATION'
  | 'REMINDER'
  | 'THANK_YOU'
  | 'WELCOME'
  | 'COMPLETION'
  | 'ANNOUNCEMENT'
  | 'CUSTOM'

export const EMAIL_CATEGORIES: { value: EmailCategory; label: string; color: string }[] = [
  { value: 'INVITATION', label: 'Invitation', color: '#007aff' },
  { value: 'REMINDER', label: 'Reminder', color: '#ff9500' },
  { value: 'THANK_YOU', label: 'Thank You', color: '#34c759' },
  { value: 'WELCOME', label: 'Welcome', color: '#5856d6' },
  { value: 'COMPLETION', label: 'Completion', color: '#30b0c7' },
  { value: 'ANNOUNCEMENT', label: 'Announcement', color: '#ff2d55' },
  { value: 'CUSTOM', label: 'Custom', color: '#8e8e93' },
]

export interface MergeField {
  field: string
  label: string
}

export interface MergeFieldGroup {
  [category: string]: MergeField[]
}

export interface EmailTemplateAssignment {
  assignmentId: number
  templateId: string
  templateName?: string
  programId?: string
  surveyId?: string
  triggerType: string
  sendDelayDays: number
  isActive: boolean
  createdAt: string
}

export const TRIGGER_TYPES: { value: string; label: string; description: string }[] = [
  { value: 'INITIAL_INVITE', label: 'Initial Invitation', description: 'Sent when the survey is first dispatched' },
  { value: 'REMINDER_1', label: 'Reminder 1', description: 'First reminder to non-respondents' },
  { value: 'REMINDER_2', label: 'Reminder 2', description: 'Second reminder to non-respondents' },
  { value: 'REMINDER_3', label: 'Reminder 3', description: 'Final reminder before close' },
  { value: 'THANK_YOU', label: 'Thank You', description: 'Sent after survey completion' },
  { value: 'SURVEY_CLOSED', label: 'Survey Closed', description: 'Sent when survey is closed' },
  { value: 'PROGRAM_WELCOME', label: 'Program Welcome', description: 'Sent when participant joins program' },
  { value: 'PROGRAM_COMPLETE', label: 'Program Complete', description: 'Sent when program ends' },
  { value: 'AD_HOC', label: 'Ad Hoc', description: 'Manually triggered' },
]
