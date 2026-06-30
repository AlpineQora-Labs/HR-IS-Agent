import { useMemo } from 'react'
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import type {
  AnalyticsSummary,
  ApplicationRow,
  Assessment,
  AuditRow,
  BiasRow,
  Campaign,
  CandidateDetail,
  CandidateSummary,
  ChatReplyInput,
  ChatState,
  CopilotArtifact,
  EventCreate,
  EventRow,
  GenerateCopilotInput,
  Integration,
  Interview,
  JobDetail,
  JobSummary,
  MatchRow,
  MobilityRow,
  Offer,
  OnboardingTask,
  PipelineColumn,
  Pool,
  Referral,
  Slot,
  SourcingRow,
  StartChatInput,
  SurveyRow,
  UpdateApplicationStageInput,
} from './types'

// Centralised query keys so mutations can invalidate precisely.
export const qk = {
  jobs: ['jobs'] as const,
  job: (id: string | undefined) => ['job', id] as const,
  candidates: ['candidates'] as const,
  candidate: (id: string | undefined) => ['candidate', id] as const,
  applications: (params?: ApplicationsParams) => ['applications', params ?? {}] as const,
  pipeline: (jobId: string | undefined) => ['pipeline', jobId] as const,
  interviews: (applicationId: string | undefined) => ['interviews', applicationId] as const,
  slots: (jobId: string | undefined) => ['slots', jobId] as const,
  assessments: (applicationId: string | undefined) => ['assessments', applicationId] as const,
  offers: ['offers'] as const,
  onboarding: (applicationId: string | undefined) => ['onboarding', applicationId] as const,
  match: (jobId: string | undefined) => ['match', jobId] as const,
  sourcing: (jobId: string | undefined) => ['sourcing', jobId] as const,
  reactivation: ['reactivation'] as const,
  mobility: ['mobility'] as const,
  pools: ['pools'] as const,
  campaigns: ['campaigns'] as const,
  referrals: ['referrals'] as const,
  events: ['events'] as const,
  surveys: ['surveys'] as const,
  copilot: ['copilot'] as const,
  integrations: ['integrations'] as const,
  bias: ['bias'] as const,
  audit: ['audit'] as const,
  analyticsSummary: ['analytics', 'summary'] as const,
  careerJobs: ['careers', 'jobs'] as const,
  careerJob: (id: string | undefined) => ['careers', 'job', id] as const,
  chat: (conversationId: string | undefined) => ['chat', conversationId] as const,
}

export interface ApplicationsParams {
  jobId?: string
  stage?: string
}

// ---- Jobs / requisitions ----

export function useJobs() {
  return useQuery({ queryKey: qk.jobs, queryFn: () => api.get<JobSummary[]>('/jobs').then((r) => r.data) })
}

export function useJob(id: string | undefined) {
  return useQuery({
    enabled: !!id,
    queryKey: qk.job(id),
    queryFn: () => api.get<JobDetail>(`/jobs/${id}`).then((r) => r.data),
  })
}

// ---- Candidates ----

export function useCandidates() {
  return useQuery({
    queryKey: qk.candidates,
    queryFn: () => api.get<CandidateSummary[]>('/candidates').then((r) => r.data),
  })
}

export function useCandidate(id: string | undefined) {
  return useQuery({
    enabled: !!id,
    queryKey: qk.candidate(id),
    queryFn: () => api.get<CandidateDetail>(`/candidates/${id}`).then((r) => r.data),
  })
}

// ---- Applications / pipeline ----

export function useApplications(params?: ApplicationsParams) {
  return useQuery({
    queryKey: qk.applications(params),
    queryFn: () => api.get<ApplicationRow[]>('/applications', { params }).then((r) => r.data),
  })
}

export function usePipeline(jobId: string | undefined) {
  return useQuery({
    enabled: !!jobId,
    queryKey: qk.pipeline(jobId),
    queryFn: () => api.get<PipelineColumn[]>('/pipeline', { params: { jobId } }).then((r) => r.data),
  })
}

// ---- Interviews / scheduling ----

export function useInterviews(applicationId: string | undefined) {
  return useQuery({
    enabled: !!applicationId,
    queryKey: qk.interviews(applicationId),
    queryFn: () => api.get<Interview[]>('/interviews', { params: { applicationId } }).then((r) => r.data),
  })
}

export function useSlots(jobId: string | undefined) {
  return useQuery({
    enabled: !!jobId,
    queryKey: qk.slots(jobId),
    queryFn: () => api.get<Slot[]>('/slots', { params: { jobId } }).then((r) => r.data),
  })
}

// Pipelines for many jobs at once (jobs×stages matrix). /pipeline is per-job,
// so fan out one query per job and key the result by jobId.
export function useAllPipelines(jobIds: string[]) {
  const results = useQueries({
    queries: jobIds.map((id) => ({
      enabled: !!id,
      queryKey: qk.pipeline(id),
      queryFn: () => api.get<PipelineColumn[]>('/pipeline', { params: { jobId: id } }).then((r) => r.data),
    })),
  })
  const signature = results.map((r) => r.dataUpdatedAt).join(',')
  const byJob = useMemo(() => {
    const map: Record<string, PipelineColumn[]> = {}
    jobIds.forEach((id, i) => {
      map[id] = results[i]?.data ?? []
    })
    return map
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [signature, jobIds.join(',')])
  const isLoading = results.length > 0 && results.some((r) => r.isLoading)
  return { byJob, isLoading }
}

// Open/booked slots across many jobs (for the Overview schedule timeline).
export function useAllSlots(jobIds: string[]) {
  const results = useQueries({
    queries: jobIds.map((id) => ({
      enabled: !!id,
      queryKey: qk.slots(id),
      queryFn: () => api.get<Slot[]>('/slots', { params: { jobId: id } }).then((r) => r.data),
    })),
  })
  const signature = results.map((r) => r.dataUpdatedAt).join(',')
  const slots = useMemo(
    () => results.flatMap((r) => r.data ?? []),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [signature],
  )
  const isLoading = results.length > 0 && results.some((r) => r.isLoading)
  return { slots, isLoading }
}

// All interviews for a job, aggregated across its applications.
// (/interviews is application-scoped, so we fan out one query per application.)
export function useJobInterviews(applicationIds: string[]) {
  const results = useQueries({
    queries: applicationIds.map((id) => ({
      enabled: !!id,
      queryKey: qk.interviews(id),
      queryFn: () => api.get<Interview[]>('/interviews', { params: { applicationId: id } }).then((r) => r.data),
    })),
  })
  const signature = results.map((r) => r.dataUpdatedAt).join(',')
  const interviews = useMemo(
    () => results.flatMap((r) => r.data ?? []),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [signature],
  )
  const isLoading = results.length > 0 && results.some((r) => r.isLoading)
  return { interviews, isLoading }
}

// ---- Assessments ----

export function useAssessments(applicationId?: string) {
  return useQuery({
    queryKey: qk.assessments(applicationId),
    queryFn: () => api.get<Assessment[]>('/assessments', { params: { applicationId } }).then((r) => r.data),
  })
}

// ---- Offers ----

export function useOffers() {
  return useQuery({ queryKey: qk.offers, queryFn: () => api.get<Offer[]>('/offers').then((r) => r.data) })
}

// ---- Onboarding ----

export function useOnboarding(applicationId?: string) {
  return useQuery({
    queryKey: qk.onboarding(applicationId),
    queryFn: () => api.get<OnboardingTask[]>('/onboarding', { params: { applicationId } }).then((r) => r.data),
  })
}

// ---- Talent intelligence ----

export function useMatch(jobId: string | undefined) {
  return useQuery({
    enabled: !!jobId,
    queryKey: qk.match(jobId),
    queryFn: () => api.get<MatchRow[]>('/match', { params: { jobId } }).then((r) => r.data),
  })
}

export function useSourcing(jobId: string | undefined) {
  return useQuery({
    enabled: !!jobId,
    queryKey: qk.sourcing(jobId),
    queryFn: () => api.get<SourcingRow[]>('/sourcing', { params: { jobId } }).then((r) => r.data),
  })
}

export function useReactivation() {
  return useQuery({
    queryKey: qk.reactivation,
    queryFn: () => api.get<MatchRow[]>('/reactivation').then((r) => r.data),
  })
}

export function useMobility() {
  return useQuery({ queryKey: qk.mobility, queryFn: () => api.get<MobilityRow[]>('/mobility').then((r) => r.data) })
}

// ---- Engagement ----

export function usePools() {
  return useQuery({ queryKey: qk.pools, queryFn: () => api.get<Pool[]>('/pools').then((r) => r.data) })
}

export function useCampaigns() {
  return useQuery({ queryKey: qk.campaigns, queryFn: () => api.get<Campaign[]>('/campaigns').then((r) => r.data) })
}

export function useReferrals() {
  return useQuery({ queryKey: qk.referrals, queryFn: () => api.get<Referral[]>('/referrals').then((r) => r.data) })
}

export function useEvents() {
  return useQuery({ queryKey: qk.events, queryFn: () => api.get<EventRow[]>('/events').then((r) => r.data) })
}

export function useSurveys() {
  return useQuery({ queryKey: qk.surveys, queryFn: () => api.get<SurveyRow[]>('/surveys').then((r) => r.data) })
}

export function useCopilot() {
  return useQuery({ queryKey: qk.copilot, queryFn: () => api.get<CopilotArtifact[]>('/copilot').then((r) => r.data) })
}

// ---- Platform ----

export function useIntegrations() {
  return useQuery({
    queryKey: qk.integrations,
    queryFn: () => api.get<Integration[]>('/integrations').then((r) => r.data),
  })
}

export function useBias() {
  return useQuery({ queryKey: qk.bias, queryFn: () => api.get<BiasRow[]>('/compliance/bias').then((r) => r.data) })
}

export function useAudit() {
  return useQuery({ queryKey: qk.audit, queryFn: () => api.get<AuditRow[]>('/audit').then((r) => r.data) })
}

export function useAnalyticsSummary() {
  return useQuery({
    queryKey: qk.analyticsSummary,
    queryFn: () => api.get<AnalyticsSummary>('/analytics/summary').then((r) => r.data),
  })
}

// ---- Candidate-facing (career site) ----

export function useCareerJobs() {
  return useQuery({
    queryKey: qk.careerJobs,
    queryFn: () => api.get<JobSummary[]>('/careers/jobs').then((r) => r.data),
  })
}

export function useCareerJob(id: string | undefined) {
  return useQuery({
    enabled: !!id,
    queryKey: qk.careerJob(id),
    queryFn: () => api.get<JobDetail>(`/careers/jobs/${id}`).then((r) => r.data),
  })
}

// Recruiter-facing: the Aria transcript tied to an application (null if none).
export function useApplicationConversation(applicationId: string | undefined, enabled: boolean) {
  return useQuery({
    enabled: enabled && !!applicationId,
    queryKey: ['conversation', 'by-application', applicationId],
    queryFn: () =>
      api.get<ChatState | null>(`/chat/by-application/${applicationId}`).then((r) => r.data),
  })
}

export function useChat(conversationId: string | undefined) {
  return useQuery({
    enabled: !!conversationId,
    queryKey: qk.chat(conversationId),
    queryFn: () => api.get<ChatState>(`/chat/${conversationId}`).then((r) => r.data),
  })
}

// ---- Mutations ----

export function useStartChat() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: StartChatInput) => api.post<ChatState>('/chat/start', input).then((r) => r.data),
    onSuccess: (data) => qc.setQueryData(qk.chat(data.conversationId), data),
  })
}

export function useChatReply() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ conversationId, text }: ChatReplyInput) =>
      api.post<ChatState>(`/chat/${conversationId}/reply`, { text }).then((r) => r.data),
    onSuccess: (data) => qc.setQueryData(qk.chat(data.conversationId), data),
  })
}

export function useGenerateCopilot() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: GenerateCopilotInput) =>
      api.post<CopilotArtifact>('/copilot/generate', input).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.copilot }),
  })
}

export function useUpdateApplicationStage() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, stage }: UpdateApplicationStageInput) =>
      api.patch<ApplicationRow>(`/applications/${id}`, { stage }).then((r) => r.data),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: ['applications'] })
      qc.invalidateQueries({ queryKey: qk.pipeline(data.jobId) })
    },
  })
}

export function useCreateEvent() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: EventCreate) => api.post<EventRow>('/events', input).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.events }),
  })
}
