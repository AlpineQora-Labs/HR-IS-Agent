import { create } from 'zustand';
import * as api from '../api/adminClient.js';

// Admin-console state: token, flow list, the open flow's detail, and the safe rule-class library.
export const useAdmin = create((set, get) => ({
  token: localStorage.getItem('tb_admin_token') || '',
  authed: false,
  error: null,
  busy: false,
  flows: [],
  ruleClasses: [],
  detail: null, // { flow, nodes, rules }

  setToken: (token) => set({ token }),

  signIn: async (tok) => {
    const token = tok ?? get().token;
    set({ error: null });
    try {
      const [flows, ruleClasses] = await Promise.all([api.listFlows(token), api.ruleClasses(token)]);
      localStorage.setItem('tb_admin_token', token);
      set({ token, authed: true, flows, ruleClasses });
    } catch (e) {
      set({ authed: false, error: e.message });
    }
  },

  refreshFlows: async () => {
    try {
      set({ flows: await api.listFlows(get().token) });
    } catch (e) {
      set({ error: e.message });
    }
  },

  openFlow: async (id) => {
    set({ error: null });
    try {
      set({ detail: await api.getFlow(id, get().token) });
    } catch (e) {
      set({ error: e.message });
    }
  },

  closeFlow: () => set({ detail: null }),

  createDraft: async (body) => {
    set({ busy: true, error: null });
    try {
      const flow = await api.createFlow(body, get().token);
      await get().refreshFlows();
      await get().openFlow(flow.id);
    } catch (e) {
      set({ error: e.message });
    } finally {
      set({ busy: false });
    }
  },

  publish: async (id) => {
    set({ busy: true, error: null });
    try {
      await api.publishFlow(id, get().token);
      await get().refreshFlows();
    } catch (e) {
      set({ error: e.message });
    } finally {
      set({ busy: false });
    }
  },

  saveNode: async (flowId, node, isNew) => {
    set({ error: null });
    try {
      if (isNew) await api.addNode(flowId, node, get().token);
      else await api.updateNode(flowId, node.nodeKey, node, get().token);
      await get().openFlow(flowId);
    } catch (e) {
      set({ error: e.message });
      throw e;
    }
  },

  removeNode: async (flowId, nodeKey) => {
    try {
      await api.deleteNode(flowId, nodeKey, get().token);
      await get().openFlow(flowId);
    } catch (e) {
      set({ error: e.message });
    }
  },

  saveRule: async (rule, isNew) => {
    set({ error: null });
    const flowId = get().detail.flow.id;
    if (isNew) await api.createRule(rule, get().token);
    else await api.updateRule(rule.id, rule, get().token);
    await get().openFlow(flowId);
  },

  removeRule: async (id) => {
    const flowId = get().detail.flow.id;
    try {
      await api.deleteRule(id, get().token);
      await get().openFlow(flowId);
    } catch (e) {
      set({ error: e.message });
    }
  },

  lint: (rule) => api.lintRule(rule, get().token),
  simulate: (id, answers) => api.simulateFlow(id, answers, get().token),
  exportFlow: (id) => api.exportFlow(id, get().token),
}));
