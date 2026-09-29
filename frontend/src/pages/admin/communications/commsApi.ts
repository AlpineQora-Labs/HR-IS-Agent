import { api } from '@/api/client'
import type { EmailTemplate } from './emailTemplate'

export interface SmsTemplate {
  id: string
  name: string
  category: string
  body: string
  status: 'DRAFT' | 'ACTIVE' | 'ARCHIVED'
  createdAt?: string | null
  updatedAt?: string | null
}

/* Thin client mirroring the teammate-voices api surface, pointed at the
   TA Portal backend (lift-and-shift adapter — the components stay 1:1). */
export const commsApi = {
  getEmailTemplates: () => api.get<EmailTemplate[]>('/email-templates').then((r) => r.data),
  getEmailTemplate: (id: string) => api.get<EmailTemplate>(`/email-templates/${id}`).then((r) => r.data),
  createEmailTemplate: (payload: Partial<EmailTemplate>) => api.post<EmailTemplate>('/email-templates', payload).then((r) => r.data),
  updateEmailTemplate: (id: string, payload: Partial<EmailTemplate>) => api.put<EmailTemplate>(`/email-templates/${id}`, payload).then((r) => r.data),
  deleteEmailTemplate: (id: string) => api.delete(`/email-templates/${id}`).then(() => undefined),
  duplicateEmailTemplate: (id: string) => api.post<EmailTemplate>(`/email-templates/${id}/duplicate`).then((r) => r.data),
  getSmsTemplates: () => api.get<SmsTemplate[]>('/sms-templates').then((r) => r.data),
  getSmsTemplate: (id: string) => api.get<SmsTemplate>(`/sms-templates/${id}`).then((r) => r.data),
  createSmsTemplate: (payload: Partial<SmsTemplate>) => api.post<SmsTemplate>('/sms-templates', payload).then((r) => r.data),
  updateSmsTemplate: (id: string, payload: Partial<SmsTemplate>) => api.put<SmsTemplate>(`/sms-templates/${id}`, payload).then((r) => r.data),
  deleteSmsTemplate: (id: string) => api.delete(`/sms-templates/${id}`).then(() => undefined),
}
