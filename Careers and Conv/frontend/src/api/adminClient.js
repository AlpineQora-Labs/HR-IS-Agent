// Token-gated admin/recruiter API client. The recruiter token is sent as X-Admin-Token (stub auth
// for the POC; real SSO is out of scope). Kept separate from the public runtime client.

async function adminRequest(path, token, options = {}) {
  const res = await fetch(path, {
    ...options,
    headers: { 'Content-Type': 'application/json', 'X-Admin-Token': token, ...(options.headers || {}) },
  });
  if (res.status === 401) throw new Error('Unauthorized — check the recruiter token.');
  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try {
      const body = await res.json();
      if (body && body.message) message = body.message;
    } catch {
      /* keep generic */
    }
    throw new Error(message);
  }
  return res.status === 204 ? null : res.json();
}

export function listApplications(token) {
  return adminRequest('/api/admin/applications', token);
}

export function getApplication(id, token) {
  return adminRequest(`/api/admin/applications/${id}`, token);
}

export function advanceApplication(id, decision, note, token, recruiterName) {
  return adminRequest(`/api/admin/applications/${id}/advance`, token, {
    method: 'POST',
    headers: { 'X-Recruiter-Name': recruiterName || '' },
    body: JSON.stringify({ decision, note }),
  });
}

// ----- flow + rule authoring (admin console) -------------------------------

export const listFlows = (token) => adminRequest('/api/admin/flows', token);

export const getFlow = (id, token) => adminRequest(`/api/admin/flows/${id}`, token);

export const createFlow = (body, token) =>
  adminRequest('/api/admin/flows', token, { method: 'POST', body: JSON.stringify(body) });

export const publishFlow = (id, token) =>
  adminRequest(`/api/admin/flows/${id}/publish`, token, { method: 'POST' });

export const exportFlow = (id, token) => adminRequest(`/api/admin/flows/${id}/export`, token);

export const importFlow = (payload, token) =>
  adminRequest('/api/admin/flows/import', token, { method: 'POST', body: JSON.stringify(payload) });

export const addNode = (flowId, node, token) =>
  adminRequest(`/api/admin/flows/${flowId}/nodes`, token, { method: 'POST', body: JSON.stringify(node) });

export const updateNode = (flowId, nodeKey, node, token) =>
  adminRequest(`/api/admin/flows/${flowId}/nodes/${nodeKey}`, token, { method: 'PUT', body: JSON.stringify(node) });

export const deleteNode = (flowId, nodeKey, token) =>
  adminRequest(`/api/admin/flows/${flowId}/nodes/${nodeKey}`, token, { method: 'DELETE' });

export const ruleClasses = (token) => adminRequest('/api/admin/rule-classes', token);

export const createRule = (rule, token) =>
  adminRequest('/api/admin/rules', token, { method: 'POST', body: JSON.stringify(rule) });

export const updateRule = (id, rule, token) =>
  adminRequest(`/api/admin/rules/${id}`, token, { method: 'PUT', body: JSON.stringify(rule) });

export const deleteRule = (id, token) =>
  adminRequest(`/api/admin/rules/${id}`, token, { method: 'DELETE' });

/** Non-destructive guardrail check — returns the list of violations (empty = would save). */
export const lintRule = (rule, token) =>
  adminRequest('/api/admin/rules/lint', token, { method: 'POST', body: JSON.stringify(rule) });

export const simulateFlow = (id, answers, token) =>
  adminRequest(`/api/admin/flows/${id}/simulate`, token, { method: 'POST', body: JSON.stringify({ answers }) });

// ----- scheduling ----------------------------------------------------------

export const proposeSlots = (id, token) => adminRequest(`/api/admin/applications/${id}/slots`, token);

export const getBooking = (id, token) => adminRequest(`/api/admin/applications/${id}/booking`, token);

export const bookSlot = (id, slotId, token, recruiterName) =>
  adminRequest(`/api/admin/applications/${id}/book`, token, {
    method: 'POST', headers: { 'X-Recruiter-Name': recruiterName || '' }, body: JSON.stringify({ slotId }),
  });

export const cancelBooking = (bookingId, token, recruiterName) =>
  adminRequest(`/api/admin/bookings/${bookingId}/cancel`, token, {
    method: 'POST', headers: { 'X-Recruiter-Name': recruiterName || '' },
  });

export const getMessages = (id, token) => adminRequest(`/api/admin/applications/${id}/messages`, token);

export const getIdentityRisk = (id, token) => adminRequest(`/api/admin/applications/${id}/identity`, token);

export const getInterviewKit = (id, token) => adminRequest(`/api/admin/applications/${id}/interview-kit`, token);

export const getScorecards = (id, token) => adminRequest(`/api/admin/applications/${id}/scorecards`, token);

export const submitScorecard = (id, body, token) =>
  adminRequest(`/api/admin/applications/${id}/scorecards`, token, { method: 'POST', body: JSON.stringify(body) });

export const getAnalytics = (token) => adminRequest('/api/admin/analytics', token);

export const getBiasAudit = (token) => adminRequest('/api/admin/bias-audit', token);

export const getRediscovery = (flowKey, token) =>
  adminRequest(`/api/admin/rediscovery${flowKey ? `?flowKey=${encodeURIComponent(flowKey)}` : ''}`, token);

export const reEngageCandidate = (sessionId, flowKey, token) =>
  adminRequest('/api/admin/rediscovery/reengage', token, {
    method: 'POST', body: JSON.stringify({ sessionId, flowKey }),
  });
