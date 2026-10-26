// Mirrors the backend DTOs (com.taportal.api.*). See docs/CONTRACT.md.
// IDs serialize as strings, money as number, dates as ISO strings.

// ---- Jobs / requisitions ----

export interface JobSummary {
  id: string
  title: string
  /** The job ID on the careers site, when it has one. */
  jobNo?: string | null
  department: string
  location: string
  workMode: string
  employmentType: string
  family: string
  status: string
  openings: number
  payMin: number
  payMax: number
  payPeriod: string
  applicants: number
  postedAt: string
  summary: string
}

export interface SkillRef {
  id: string
  name: string
  category: string
  weight: number
  required: boolean
}

export interface Knockout {
  id: string
  ordinal: number
  prompt: string
  answerType: string
  choices: string[]
  required: boolean
}

export interface JobPreview {
  headline: string
  mediaUrl: string
  dayInLife: string
}

export interface JobDetail extends JobSummary {
  description: string
  hiringManager: string
  recruiter: string
  skills: SkillRef[]
  knockoutQuestions: Knockout[]
  preview: JobPreview
  languages: string[]
  stageCounts: Record<string, number>
}

// ---- Candidates ----

export interface CandidateSummary {
  id: string
  name: string
  email: string
  location: string
  headline: string
  yearsExperience: number
  source: string
  lifecycle: string
  preferredLanguage: string
  applicationCount: number
  topSkills: string[]
}

export interface CandidateSkill {
  name: string
  category: string
  proficiency: string
  years: number
  inferred: boolean
}

export interface CandidateDetail extends CandidateSummary {
  phone: string
  skills: CandidateSkill[]
  applications: ApplicationRow[]
}

// ---- Applications / pipeline ----

export interface ApplicationRow {
  id: string
  candidateId: string
  candidateName: string
  jobId: string
  jobTitle: string
  stage: string
  source: string
  /** Empty until the matching engine has scored the application. */
  fitScore: number | null
  /** Empty until the screening questions have been answered. */
  knockoutPassed: boolean | null
  appliedAt: string
  updatedAt: string
}

export interface PipelineColumn {
  stage: string
  count: number
  cards: ApplicationRow[]
}

// ---- Interviews / scheduling ----

export interface Interview {
  id: string
  applicationId: string
  type: string
  scheduledAt: string
  durationMin: number
  status: string
  interviewers: string[]
  score: number
  recommendation: string
  summary: string
  meetingLink: string | null
  /** How the time was set: TEXT, WEB_PAGE or CHAT (the candidate picked, from times worked out automatically), or TEAM. Null when not recorded. */
  bookedVia?: 'TEXT' | 'WEB_PAGE' | 'CHAT' | 'TEAM' | null
}

export interface Slot {
  id: string
  jobId: string
  interviewerId: string
  interviewerName: string
  startsAt: string
  endsAt: string
  booked: boolean
  interviewId: string | null
  status: string
}

// ---- Scheduling radar ----

export interface AttentionItem {
  interviewId: string
  applicationId: string
  candidateName: string
  jobTitle: string
  status: string
  scheduledAt: string | null
  waitingHours: number
  meetingLink: string | null
  interviewers: string[]
}

export interface InterviewerLoad {
  userId: string
  name: string
  initials: string
  role: string
  next7Days: number
}

export interface SchedulingOverview {
  awaitingCandidate: AttentionItem[]
  needsOutcome: AttentionItem[]
  noShows: AttentionItem[]
  today: AttentionItem[]
  load: InterviewerLoad[]
}

// ---- Forms ----

export interface FormDefinitionDto {
  id: string
  purpose: string
  name: string
  schema: string
  template: boolean
  defaultForKind: boolean
  updatedAt: string
}

/** Library listing row (schema omitted). */
export interface FormMeta {
  id: string
  purpose: string
  name: string
  template: boolean
  defaultForKind: boolean
  updatedAt: string
  /** Events that use it: to register students, or as their intake form. */
  events: number
  /** Event templates that use it: new events made from them will use it too. */
  eventTemplates: number
}

export interface FormResponseRow {
  id: string
  formPurpose: string
  answers: string
  createdAt: string
}

// ---- Assessments ----

export interface Assessment {
  id: string
  applicationId: string
  type: string
  name: string
  status: string
  score: number
  percentile: number
  completedAt: string
}

// ---- Offers ----

export interface Offer {
  id: string
  applicationId: string
  candidateName: string
  jobTitle: string
  title: string
  compBase: number
  compPeriod: string
  compBonus: number
  equity: string
  startDate: string
  status: string
  letterBody: string
  sentAt: string
}

// ---- Onboarding ----

export interface OnboardingTask {
  id: string
  applicationId: string
  name: string
  category: string
  status: string
  dueDate: string
}

// ---- Talent intelligence ----

export interface MatchRow {
  candidateId: string
  candidateName: string
  headline: string
  fitScore: number
  explanation: string
  matchedSkills: string[]
  gapSkills: string[]
}

export interface SourcingRow {
  candidateId: string
  candidateName: string
  headline: string
  location: string
  fitScore: number
  lifecycle: string
  source: string
}

export interface MobilityRow {
  employeeId: string
  employeeName: string
  currentRole: string
  jobId: string
  jobTitle: string
  fitScore: number
  rationale: string
}

// ---- Engagement ----

export interface Pool {
  id: string
  name: string
  description: string
  memberCount: number
}

export interface Campaign {
  id: string
  name: string
  audience: string
  channel: string
  status: string
  sent: number
  opened: number
  replied: number
  openRate: number
  replyRate: number
}

export interface Referral {
  id: string
  referrerName: string
  candidateName: string
  jobTitle: string
  status: string
  bonusAmount: number
  createdAt: string
}

export interface EventRow {
  id: string
  name: string
  type: string
  location: string
  startsAt: string
  endsAt: string | null
  timezone: string
  /** Latest approval status (PENDING/APPROVED/AUTO_APPROVED/REJECTED), null if never routed. */
  approvalStatus: string | null
  registrations: number
  attended: number
  hires: number
  /** The most it seats; null for no limit. */
  capacity: number | null
  /** When registration ends; null keeps it open until the event starts. */
  registrationClosesAt: string | null
  waitlistEnabled: boolean
  waitlisted: number
  shortlisted: number
  /** The form students register with; null for the default Student registration form. */
  registrationFormId: string | null
  /** The template it was created from; null for an event started blank. */
  templateId: string | null
  country: string | null
  /** IN_PERSON | VIRTUAL | HYBRID; null on older events. */
  format: string | null
  /** When it goes on the careers site, once approved; null for as soon as approved. */
  publishAt: string | null
  /** Whether students can see it now: approved, and its publishing moment has come. */
  published: boolean
}

/** Everything that is the same for a kind of event; a new event starts from one. */
export interface EventTemplate {
  id: string
  name: string
  eventType: string
  format: string
  country: string | null
  timezone: string
  intakeFormId: string | null
  registrationFormId: string | null
  capacity: number | null
  waitlistEnabled: boolean
  /** Registration closes this many days before the event; null for when it starts. */
  closeDaysBefore: number | null
  /** An approved event goes on the careers site this many days before it starts; null for as soon as approved. */
  publishDaysBefore: number | null
  consentText: string | null
  active: boolean
  eventsMade: number
}

/** How many an event seats, when registration ends, and whether it keeps a waitlist. */
export interface EventSeatSettings {
  capacity: number | null
  registrationClosesAt: string | null
  waitlistEnabled: boolean
  /** The form students register with; null for the default. */
  registrationFormId: string | null
  /** When the event goes on the careers site, once approved; null for as soon as approved. */
  publishAt: string | null
}

/** How one school has done across every event. */
export interface SchoolReportRow {
  schoolId: string
  name: string
  location: string | null
  tier: string
  country: string | null
  events: number
  registered: number
  attended: number
  shortlisted: number
  putForward: number
  hired: number
}

// ---- Campus: schools + event rosters ----

export interface School {
  id: string
  name: string
  location: string | null
  tier: string
  /** ISO country code; null when not said. */
  country: string | null
}

export interface EventRegistration {
  id: string
  eventId: string
  candidateId: string | null
  name: string
  email: string
  schoolId: string | null
  schoolName: string | null
  major: string | null
  gradYear: number | null
  source: string
  /** REGISTERED | CHECKED_IN | NO_SHOW | WAITLISTED | CANCELLED */
  status: string
  checkedInAt: string | null
  /** Their place on the waitlist, from 1; null when not waitlisted. */
  waitlistPosition: number | null
  /** A second address the student gave: their own, beside the school one. */
  personalEmail: string | null
  consentAt: string | null
  /** The recruiter's rating at the booth, 1 to 5. */
  rating: number | null
  notes: string | null
  shortlisted: boolean
  /** The application made when they were put forward for a job. */
  applicationId: string | null
  jobTitle: string | null
  applicationStage: string | null
}

export interface RegisterAttendeeInput {
  name: string
  email: string
  schoolId?: string
  major?: string
  gradYear?: number
  walkIn?: boolean
  /** The student agreed to their details being kept. Required. */
  consent: boolean
}

/** What can be changed about an event after it is created. */
export interface EventUpdate {
  name: string
  type: string
  location: string
  startsAt: string
  endsAt?: string
  timezone: string
  format: string
}

export interface EventCreate {
  name: string
  type: string
  location: string
  startsAt: string
  endsAt?: string
  timezone?: string
  intakeFormId?: string
  /** The form students register with; left out for the default. */
  registrationFormId?: string
  /** The template to start from. */
  templateId?: string
  /** IN_PERSON | VIRTUAL | HYBRID */
  format?: string
  /** When it goes on the careers site, once approved; left out for the template's default (or as soon as approved). */
  publishAt?: string
  /** Route this event down the workflow's flagged/exception path. */
  flaggedCritical?: boolean
}

// ---- Write payloads (recruiter actions) ----

export interface JobCreate {
  title: string
  department: string
  location: string
  workMode: string
  employmentType: string
  family: string
  status: string
  openings: number
  summary?: string
  description?: string
  payMin?: number
  payMax?: number
  payPeriod?: string
}

export interface OfferCreate {
  applicationId: string
  title: string
  compBase?: number
  compPeriod?: string
  compBonus?: number
  equity?: string
  startDate?: string
  letterBody?: string
}

export interface InterviewCreate {
  applicationId: string
  type: string
  durationMin?: number
  interviewers?: string
}

export interface ScheduleInterviewInput {
  id: string
  slotId: string
  scheduledAt?: string
}

export interface PoolCreate {
  name: string
  description?: string
}

export interface SurveyRow {
  id: string
  name: string
  stage: string
  sent: number
  responses: number
  avgSentiment: number
  nps: number
  responseRate: number
}

export interface CopilotArtifact {
  id: string
  kind: string
  jobTitle: string
  content: string
  createdAt: string
}

// ---- Platform ----

export interface Integration {
  id: string
  name: string
  category: string
  status: string
  lastSyncAt: string
}

export interface BiasRow {
  id: string
  jobTitle: string
  stage: string
  dimension: string
  groupLabel: string
  passRate: number
  impactRatio: number
  flagged: boolean
}

export interface AuditRow {
  id: string
  actor: string
  action: string
  entityType: string
  detail: string
  createdAt: string
}

export interface FunnelStage {
  stage: string
  count: number
}

export interface AnalyticsSummary {
  openJobs: number
  activeCandidates: number
  applications30d: number
  interviews30d: number
  offers30d: number
  hires30d: number
  avgTimeToHireDays: number
  avgFitScore: number
  offerAcceptRate: number
  byStage: Record<string, number>
  funnel: FunnelStage[]
}

// ---- Aria conversation engine ----

export interface ChatMessage {
  id: string
  sender: string
  body: string
  intent: string
  createdAt: string
}

export interface ChatState {
  conversationId: string
  status: string
  step: string
  messages: ChatMessage[]
  options?: string[]
  applicationId?: string
}

// ---- Request payloads ----

export interface StartChatInput {
  jobId: string
  channel: string
  language: string
}

export interface ChatReplyInput {
  conversationId: string
  text: string
}

export interface GenerateCopilotInput {
  kind: string
  jobId?: string
  prompt?: string
}

export interface UpdateApplicationStageInput {
  id: string
  stage: string
}

// ---- Candidate messages: texts and emails, to and from a candidate ----
// Wording, status and reasons come from the server as written. Screens lay them out.

export interface MessageRow {
  id: string
  channel: 'SMS' | 'EMAIL'
  direction: 'OUTBOUND' | 'INBOUND'
  /** QUEUED | SENDING | SENT | FAILED | SUPPRESSED | RECEIVED */
  status: string
  statusText: string
  reason: string | null
  point: string | null
  sentWhen: string | null
  templateName: string | null
  address: string | null
  subject: string | null
  text: string
  parts: number | null
  offersTimes: boolean
  applicationId: string | null
  interviewId: string | null
  createdAt: string
  sentAt: string | null
}

export interface MessageTimeline {
  candidateId: string
  candidateName: string
  phone: string | null
  email: string | null
  canBeTexted: boolean
  whyNot: string | null
  journeyOn: boolean
  /** Something about this candidate's messages a recruiter should know. */
  note: string | null
  messages: MessageRow[]
}

export interface InboundResult {
  inboundId: string
  understood: string
  /** What the text was taken to mean, in words. */
  understoodAs: string
  duplicate: boolean
  replies: MessageRow[]
}

export interface JourneyPointInfo {
  key: string
  sentWhen: string
  kind: 'UNPROMPTED' | 'REPLY' | 'NOTICE' | 'SERVICE'
  byText: boolean
  byEmail: boolean
  drawn: boolean
  needs: string[]
  templateKey: string
  /** The fields a message at this point has values for. */
  has: string[]
  /** The templates that can word this point, as the server judges it. */
  textTemplates: string[]
  emailTemplates: string[]
}

export interface JourneyField {
  name: string
  holds: string
}

export interface JourneyRule {
  value: string
  sentence: string
}

export interface JourneyInfo {
  on: boolean
  points: JourneyPointInfo[]
  fields: JourneyField[]
  rules: JourneyRule[]
}

export interface ReminderResult {
  sent: MessageRow[]
  note: string | null
}

/* ---------------- The portal assistant (read-only) ---------------- */

/** "Brief me", "Explain why", or a question in the recruiter's own words. */
export type AssistantIntent = 'BRIEF' | 'WHY' | 'ASK'

export interface AssistantTurn {
  from: 'RECRUITER' | 'ASSISTANT'
  text: string
}

export interface AssistantAsk {
  intent: AssistantIntent
  question?: string
  /** What is open on screen; empty asks about the day's interviews. */
  context: { candidateId?: string; applicationId?: string; jobId?: string }
  /** The chat so far about this same thing, oldest first. */
  history: AssistantTurn[]
}

export interface AssistantReply {
  /** DAY, CANDIDATE or JOB. */
  scope: string
  /** What the answer is about, in words. */
  about: string
  /** Plain text; lines starting "- " are list items. */
  answer: string
  /** Screens of the portal the answer refers to. */
  links: { label: string; path: string }[]
  /** True when AI worded the answer from the records; false when the portal's rules did. */
  byModel: boolean
}
