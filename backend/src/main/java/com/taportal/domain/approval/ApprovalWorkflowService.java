package com.taportal.domain.approval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taportal.api.ApprovalDtos.RequiredApproval;
import com.taportal.api.ApprovalDtos.SimulateRequest;
import com.taportal.api.ApprovalDtos.SimulateResponse;
import com.taportal.api.ApprovalDtos.WorkflowDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ApprovalWorkflowService {

    private static final Pattern NUMBER = Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]+)?)");

    private final ApprovalWorkflowRepository repository;
    private final ObjectMapper mapper;

    public ApprovalWorkflowService(ApprovalWorkflowRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public List<WorkflowDto> list() {
        return repository.findByOrderByWfKey().stream().map(this::toDto).toList();
    }

    /** Upsert the full set (frontend saves its working copy wholesale). */
    @Transactional
    public List<WorkflowDto> saveAll(List<WorkflowDto> in) {
        for (WorkflowDto dto : in) {
            ApprovalWorkflowRecord rec = repository.findByWfKey(dto.key()).orElse(null);
            if (rec == null) {
                rec = new ApprovalWorkflowRecord(
                        null, dto.key(), dto.name(), dto.trigger(), dto.enabled(), dto.threshold(),
                        dto.autoApprove(), json(dto.levels(), "[]"), jsonOrNull(dto.graph()), null, null);
            } else {
                rec.setName(dto.name());
                rec.setTriggerType(dto.trigger());
                rec.setEnabled(dto.enabled());
                rec.setThreshold(dto.threshold());
                rec.setAutoApprove(dto.autoApprove());
                rec.setLevelsJson(json(dto.levels(), "[]"));
                rec.setGraphJson(jsonOrNull(dto.graph()));
            }
            repository.save(rec);
        }
        return list();
    }

    /* ---------- the engine: walk the graph and evaluate a sample request ---------- */

    public SimulateResponse simulate(String key, SimulateRequest req) {
        ApprovalWorkflowRecord wf = repository.findByWfKey(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found"));
        double threshold = parseNumber(wf.getThreshold());
        List<String> notes = new ArrayList<>();

        boolean flagged = Boolean.TRUE.equals(req.flaggedCritical());
        boolean withinPolicy = !flagged
                && under(req.billRate(), threshold)
                && under(req.amount(), threshold)
                && under(req.durationMonths(), threshold);
        boolean autoApproved = wf.isAutoApprove() && withinPolicy;

        JsonNode graph = readGraph(wf);
        if (graph == null) {
            return simulateFromLevels(wf, req, threshold, autoApproved, notes);
        }

        Map<String, JsonNode> nodes = new HashMap<>();
        graph.path("nodes").forEach(n -> nodes.put(n.path("id").asText(), n));
        Map<String, List<JsonNode>> out = new HashMap<>();
        graph.path("edges").forEach(e -> out.computeIfAbsent(e.path("source").asText(), k -> new ArrayList<>()).add(e));

        List<String> path = new ArrayList<>(List.of("trigger"));
        List<RequiredApproval> approvals = new ArrayList<>();
        if (autoApproved) {
            notes.add("Request falls within the auto-approve policy (" + wf.getThreshold() + ") — chain skipped.");
            // Prefer the explicit policy branch when it exists on the canvas.
            if (nodes.containsKey("policy")) {
                path.add("policy");
            }
            path.add("end");
            return new SimulateResponse(true, path, approvals, notes);
        }

        String cur = "trigger";
        Set<String> visited = new HashSet<>(List.of("trigger"));
        while (cur != null) {
            List<JsonNode> outgoing = out.getOrDefault(cur, List.of());
            JsonNode curNode = nodes.get(cur);
            String type = curNode == null ? "" : curNode.path("type").asText();

            JsonNode next = null;
            if ("condition".equals(type)) {
                boolean yes = evalCondition(curNode.path("data").path("condition").asText("Always"), req, threshold, notes);
                String want = yes ? "yes" : "no";
                next = outgoing.stream().filter(e -> want.equals(e.path("sourceHandle").asText(null))).findFirst()
                        .orElse(outgoing.isEmpty() ? null : outgoing.get(0));
                if (!yes && next != null && want.equals(next.path("sourceHandle").asText(null))) {
                    notes.add("Condition not met — took the NO branch.");
                }
            } else {
                // Skip the policy branch when the request isn't auto-approved.
                next = outgoing.stream().filter(e -> !"policy".equals(e.path("target").asText())).findFirst().orElse(null);
            }
            if (next == null) {
                break;
            }
            String target = next.path("target").asText();
            if (!visited.add(target)) {
                break; // cycle guard
            }
            path.add(target);
            JsonNode tn = nodes.get(target);
            if (tn != null && "approval".equals(tn.path("type").asText())) {
                approvals.add(new RequiredApproval(
                        target,
                        tn.path("data").path("label").asText("Approval"),
                        tn.path("data").path("approverRole").asText("")));
            }
            cur = target;
        }
        return new SimulateResponse(false, path, approvals, notes);
    }

    /** Linear fallback when the canvas has never been saved: evaluate the level chain. */
    private SimulateResponse simulateFromLevels(
            ApprovalWorkflowRecord wf, SimulateRequest req, double threshold, boolean autoApproved, List<String> notes) {
        List<String> path = new ArrayList<>(List.of("trigger"));
        List<RequiredApproval> approvals = new ArrayList<>();
        if (autoApproved) {
            notes.add("Request falls within the auto-approve policy (" + wf.getThreshold() + ") — chain skipped.");
            path.add("end");
            return new SimulateResponse(true, path, approvals, notes);
        }
        JsonNode levels = readJson(wf.getLevelsJson());
        int i = 0;
        if (levels != null) {
            for (JsonNode lv : levels) {
                i++;
                String cond = lv.path("condition").asText("Always");
                if (evalCondition(cond, req, threshold, notes)) {
                    String id = "lvl-" + lv.path("id").asText(String.valueOf(i));
                    path.add(id);
                    approvals.add(new RequiredApproval(id, "Level " + i, lv.path("approverRole").asText("")));
                } else {
                    notes.add("Level " + i + " skipped — condition \"" + cond + "\" not met.");
                }
            }
        }
        path.add("end");
        return new SimulateResponse(false, path, approvals, notes);
    }

    private boolean evalCondition(String condition, SimulateRequest req, double threshold, List<String> notes) {
        return switch (condition) {
            case "Bill rate over threshold" -> req.billRate() != null && req.billRate() > threshold;
            case "Amount over threshold" -> req.amount() != null && req.amount() > threshold;
            case "Duration over threshold" -> req.durationMonths() != null && req.durationMonths() > threshold;
            case "Flagged critical" -> Boolean.TRUE.equals(req.flaggedCritical());
            default -> true; // "Always"
        };
    }

    private static boolean under(Double v, double threshold) {
        return v == null || v <= threshold;
    }

    private static double parseNumber(String s) {
        if (s == null) {
            return Double.MAX_VALUE;
        }
        Matcher m = NUMBER.matcher(s);
        return m.find() ? Double.parseDouble(m.group(1).replace(",", "")) : Double.MAX_VALUE;
    }

    /* ---------- json helpers ---------- */

    private WorkflowDto toDto(ApprovalWorkflowRecord r) {
        return new WorkflowDto(
                r.getWfKey(), r.getName(), r.getTriggerType(), r.isEnabled(), r.getThreshold(), r.isAutoApprove(),
                readJson(r.getLevelsJson()), readJson(r.getGraphJson()), r.getUpdatedAt());
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
