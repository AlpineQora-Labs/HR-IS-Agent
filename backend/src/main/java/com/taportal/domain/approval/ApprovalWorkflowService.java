package com.taportal.domain.approval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taportal.api.ApprovalDtos.CheckIssue;
import com.taportal.api.ApprovalDtos.SimulateRequest;
import com.taportal.api.ApprovalDtos.SimulateResponse;
import com.taportal.api.ApprovalDtos.WorkflowDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ApprovalWorkflowService {

    private final ApprovalWorkflowRepository repository;
    private final ApprovalRequestRepository requestRepository;
    private final ObjectMapper mapper;

    public ApprovalWorkflowService(
            ApprovalWorkflowRepository repository,
            ApprovalRequestRepository requestRepository,
            ObjectMapper mapper) {
        this.repository = repository;
        this.requestRepository = requestRepository;
        this.mapper = mapper;
    }

    public List<WorkflowDto> list() {
        return repository.findByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    /**
     * Delete a workflow. Blocked while PENDING requests still route through it —
     * decide those first. Historical (decided) requests keep their record; they
     * fall back to the raw key where the name used to resolve.
     */
    @Transactional
    public void delete(String key) {
        ApprovalWorkflowRecord rec = repository.findByWfKey(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found"));
        long pending = requestRepository.countByWfKeyAndStatus(key, ApprovalRequest.Status.PENDING);
        if (pending > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    pending + " pending request" + (pending == 1 ? "" : "s")
                            + " still route through this workflow — decide them first.");
        }
        repository.delete(rec);
    }

    /** Upsert the full set (frontend saves its working copy wholesale). */
    @Transactional
    public List<WorkflowDto> saveAll(List<WorkflowDto> in) {
        List<ApprovalWorkflowRecord> toCheck = new ArrayList<>();
        for (WorkflowDto dto : in) {
            ApprovalWorkflowRecord rec = repository.findByWfKey(dto.key()).orElse(null);
            String before = rec == null ? null : liveSignature(rec);
            if (rec == null) {
                rec = new ApprovalWorkflowRecord(
                        null, dto.key(), dto.name(), dto.trigger(), dto.enabled(),
                        dto.autoApprove(), json(dto.levels(), "[]"), jsonOrNull(dto.graph()), null, null);
            } else {
                rec.setName(dto.name());
                rec.setTriggerType(dto.trigger());
                rec.setEnabled(dto.enabled());
                rec.setAutoApprove(dto.autoApprove());
                rec.setLevelsJson(json(dto.levels(), "[]"));
                // Only the canvas sends graphs. A payload without one (the list
                // page's autosave) must never erase a saved canvas graph.
                if (dto.graph() != null && !dto.graph().isNull()) {
                    rec.setGraphJson(jsonOrNull(dto.graph()));
                }
            }
            if (!java.util.Objects.equals(before, liveSignature(rec))) {
                toCheck.add(rec);
            }
            repository.save(rec);
        }
        // Business rule: the trigger is the router, so at most ONE enabled
        // workflow may handle a given trigger type. 422 rolls the save back.
        Map<String, List<String>> enabledByTrigger = new HashMap<>();
        for (ApprovalWorkflowRecord r : repository.findByOrderByWfKey()) {
            if (r.isEnabled()) {
                enabledByTrigger.computeIfAbsent(r.getTriggerType().toLowerCase(), k -> new ArrayList<>()).add(r.getName());
            }
        }
        for (var entry : enabledByTrigger.entrySet()) {
            if (entry.getValue().size() > 1) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Only one enabled workflow per trigger — " + String.join(" and ", entry.getValue())
                                + " both handle it. Disable one first.");
            }
        }
        // Only what THIS save switches on, or redefines while live, is held to
        // the switch-on checks. A workflow that was already live and is merely
        // re-sent unchanged (the list page saves every workflow wholesale) must
        // not block unrelated saves; its live requests are covered by the
        // needs-attention backstop in ApprovalRequestService.
        for (ApprovalWorkflowRecord r : toCheck) {
            requireSound(r);
        }
        return list();
    }

    /** "off" unless the workflow routes live requests; otherwise its normalised definition. */
    private String liveSignature(ApprovalWorkflowRecord r) {
        boolean live = r.isEnabled() && routed(r.getTriggerType());
        return live ? readJson(r.getGraphJson()) + "|" + readJson(r.getLevelsJson()) : "off";
    }

    /* ---------- the engine: route a request through a workflow ---------- */

    /**
     * Evaluate a request against a workflow. Test and live routing both come
     * through here, so what Test shows is what a real request does.
     *
     * <p>When the request carries a draft graph (the canvas's unsaved state),
     * that is evaluated instead of the stored one — Test never has to save.
     */
    public SimulateResponse simulate(String key, SimulateRequest req) {
        JsonNode draft = req.graph() != null && !req.graph().isNull() ? req.graph() : null;
        ApprovalWorkflowRecord wf = repository.findByWfKey(key).orElse(null);
        // A draft carries everything needed to test it; only a request to
        // evaluate the STORED workflow needs that workflow to exist.
        if (wf == null && draft == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found");
        }
        boolean autoApprove = req.autoApprove() != null ? req.autoApprove() : wf != null && wf.isAutoApprove();
        ApprovalRouteEngine.Route route = ApprovalRouteEngine.evaluate(
                draft != null ? draft : readGraph(wf),
                wf == null ? null : readJson(wf.getLevelsJson()),
                autoApprove,
                new ApprovalRouteEngine.Facts(req.eventFormat(), req.daysNotice(), req.flaggedCritical()));
        return new SimulateResponse(
                route.autoApproved(), route.path(), route.approvals(), route.notes(), route.problems(), route.lines());
    }

    /**
     * What is wrong or unfinished about a drawing, for the canvas to show as it
     * is edited. Nothing is stored. The canvas never decides this itself.
     *
     * <p>An issue is only reported as holding the workflow back when a save
     * would really be refused for it — that is, when the workflow's trigger is
     * one that routes live requests (see {@link #requireSound}). Likewise a
     * line that leads back only matters where a request is routed: in a
     * design, going back to an earlier step is simply part of the journey.
     */
    public List<CheckIssue> check(JsonNode graph, JsonNode levels, String trigger) {
        boolean held = routed(trigger);
        return ApprovalRouteEngine.validate(graph, levels).stream()
                .filter(i -> held || !ApprovalRouteEngine.LOOP_BACK.equals(i.code()))
                .map(i -> new CheckIssue(i.code(), held && i.blocks(), i.nodeId(), i.message()))
                .toList();
    }

    /** Matched the way live routing finds a workflow: by name, whatever the case. */
    private static boolean routed(String trigger) {
        return trigger != null && ROUTED_TRIGGERS.contains(trigger.toLowerCase());
    }

    /**
     * Trigger types that real requests are actually routed through today. Only
     * these are held to the switch-on checks: other triggers are designs the
     * engine never runs, and blocking their saves would protect nothing.
     */
    private static final Set<String> ROUTED_TRIGGERS = Set.of("event");

    /**
     * Business rule: a workflow that routes live requests cannot be switched on
     * while a rule in it is unanswerable. Drafts (disabled) always save.
     */
    private void requireSound(ApprovalWorkflowRecord rec) {
        if (!rec.isEnabled() || !routed(rec.getTriggerType())) {
            return;
        }
        List<ApprovalRouteEngine.Issue> issues =
                ApprovalRouteEngine.validate(readGraph(rec), readJson(rec.getLevelsJson())).stream()
                        .filter(ApprovalRouteEngine.Issue::blocks)
                        .toList();
        if (issues.isEmpty()) {
            return;
        }
        String more = issues.size() > 1 ? " (and " + (issues.size() - 1) + " more to fix)" : "";
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "\u201c" + rec.getName() + "\u201d can't be switched on yet \u2014 "
                        + issues.get(0).message() + more);
    }

    /* ---------- json helpers ---------- */

    private WorkflowDto toDto(ApprovalWorkflowRecord r) {
        return new WorkflowDto(
                r.getWfKey(), r.getName(), r.getTriggerType(), r.isEnabled(), r.isAutoApprove(),
                readJson(r.getLevelsJson()), readGraph(r), r.getUpdatedAt());
    }

    private JsonNode readGraph(ApprovalWorkflowRecord wf) {
        JsonNode g = readJson(wf.getGraphJson());
        return g != null && g.path("nodes").size() > 0 ? g : null;
    }

    private JsonNode readJson(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(s);
        } catch (Exception e) {
            return null;
        }
    }

    private String json(JsonNode n, String fallback) {
        return n == null || n.isNull() ? fallback : n.toString();
    }

    private String jsonOrNull(JsonNode n) {
        return n == null || n.isNull() ? null : n.toString();
    }
}
