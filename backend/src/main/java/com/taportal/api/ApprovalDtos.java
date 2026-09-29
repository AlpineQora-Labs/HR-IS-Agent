package com.taportal.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;

/** Approval-workflow persistence + simulation DTOs. */
public final class ApprovalDtos {

    private ApprovalDtos() {}

    /** One workflow; {@code levels}/{@code graph} are passed through as JSON (canvas owns the shape). */
    public record WorkflowDto(
            String key,
            String name,
            String trigger,
            boolean enabled,
            boolean autoApprove,
            JsonNode levels,
            JsonNode graph,
            OffsetDateTime updatedAt) {
    }

    /**
     * Sample request the engine evaluates a workflow against — real event context.
     *
     * @param graph       optional unsaved canvas to evaluate INSTEAD of the stored
     *                    one, so Test never has to save first; null = stored
     * @param autoApprove optional unsaved auto-approve setting; null = stored
     */
    public record SimulateRequest(
            String eventFormat, Double daysNotice, Boolean flaggedCritical, JsonNode graph, Boolean autoApprove) {
    }

    /**
     * A drawing to be checked without saving it.
     *
     * @param trigger what the workflow handles (Event, Offer, …): decides
     *                whether anything is held back before it can be switched on
     */
    public record CheckRequest(JsonNode graph, JsonNode levels, String trigger) {
    }

    /**
     * One thing wrong or unfinished about a drawing.
     *
     * @param blocking true when it stops the workflow being switched on
     * @param nodeId   the step it concerns, when there is one
     */
    public record CheckIssue(String code, boolean blocking, String nodeId, String message) {
    }

    /** One approval the request would require. */
    public record RequiredApproval(String nodeId, String label, String role) {
    }

    /**
     * Evaluation result: the node path walked plus what approvals fire along it.
     *
     * @param problems plain-English reasons the route could not be completed
     *                 (a rule the engine can't check, an answer with no path);
     *                 empty when the request reached the end of a path
     * @param lines    the line followed into each step of {@code path} after
     *                 the first; "" where no line is drawn for it
     */
    public record SimulateResponse(
            boolean autoApproved,
            List<String> path,
            List<RequiredApproval> requiredApprovals,
            List<String> notes,
            List<String> problems,
            List<String> lines) {
    }

    /** Submit a real item through a workflow; the engine routes it. */
    public record SubmitApprovalRequest(
            /** Trigger type (Event, Offer, …) — the engine resolves WHICH workflow handles it. */
            String trigger,
            String itemType,
            String itemRef,
            String title,
            String sub,
            /** IN_PERSON | VIRTUAL — how the event runs. */
            String eventFormat,
            /** Days between submission and the event start. */
            Double daysNotice,
            Boolean flaggedCritical) {
    }

    /** One live approval request in the queue. */
    public record ApprovalRequestResponse(
            java.util.UUID id,
            String wfKey,
            String wfName,
            String itemType,
            String itemRef,
            String title,
            String sub,
            String status,
            int currentStep,
            List<RequiredApproval> required,
            String decidedBy,
            OffsetDateTime decidedAt,
            OffsetDateTime createdAt) {
    }
}
