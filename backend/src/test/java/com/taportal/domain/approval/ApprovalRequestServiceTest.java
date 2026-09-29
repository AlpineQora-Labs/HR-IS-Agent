package com.taportal.domain.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taportal.api.ApprovalDtos.ApprovalRequestResponse;
import com.taportal.api.ApprovalDtos.RequiredApproval;
import com.taportal.api.ApprovalDtos.SimulateResponse;
import com.taportal.api.ApprovalDtos.SubmitApprovalRequest;
import com.taportal.domain.notification.NotificationService;
import java.util.List;
import java.util.Optional;
import com.taportal.config.CurrentUser;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Live routing: what becomes of a real request once the engine has spoken. */
class ApprovalRequestServiceTest {

    private static final SubmitApprovalRequest EVENT = new SubmitApprovalRequest(
            "Event", "EVENT", "evt-1", "Fall Careers Night", "Charlotte, NC", "IN_PERSON", 30.0, false);

    private final ApprovalRequestRepository requests = mock(ApprovalRequestRepository.class);
    private final ApprovalWorkflowRepository workflows = mock(ApprovalWorkflowRepository.class);
    private final ApprovalWorkflowService engine = mock(ApprovalWorkflowService.class);
    private final NotificationService notifications = mock(NotificationService.class);

    private final ApprovalRequestService service =
            new ApprovalRequestService(requests, workflows, engine, new ObjectMapper(), notifications);

    @BeforeEach
    void anEnabledEventWorkflowExists() {
        ApprovalWorkflowRecord wf = new ApprovalWorkflowRecord(
                null, "e1", "Event approval", "Event", true, false, "[]", null, null, null);
        when(workflows.findFirstByTriggerTypeIgnoreCaseAndEnabledTrue("Event")).thenReturn(Optional.of(wf));
        when(workflows.findByWfKey("e1")).thenReturn(Optional.of(wf));
        when(requests.save(any(ApprovalRequest.class))).thenAnswer(call -> call.getArgument(0));
    }

    private void engineAnswers(boolean autoApproved, List<RequiredApproval> approvals, List<String> problems) {
        when(engine.simulate(eq("e1"), any()))
                .thenReturn(new SimulateResponse(autoApproved, List.of("trigger"), approvals, List.of(), problems));
    }

    private static final RequiredApproval LEVEL_1 = new RequiredApproval("lvl", "Level 1", "Hiring Manager");

    @Test
    void aCompleteRouteIsStoredAsTheEngineDecided() {
        engineAnswers(false, List.of(LEVEL_1), List.of());

        ApprovalRequestResponse saved = service.submit(EVENT);

        assertThat(saved.status()).isEqualTo("PENDING");
        assertThat(saved.required()).containsExactly(LEVEL_1);
        verify(notifications, never()).notifyRole(anyString(), anyString(), startsWith("Workflow needs attention"), any(), any());
    }

    @Test
    @DisplayName("a route that could not be completed waits for an admin — it is never guessed")
    void anIncompleteRouteAddsAnAdminSignOff() {
        engineAnswers(false, List.of(LEVEL_1), List.of("The rule “Flagged critical” has no path for NO (otherwise)."));

        ApprovalRequestResponse saved = service.submit(EVENT);

        assertThat(saved.status()).isEqualTo("PENDING");
        assertThat(saved.required()).extracting(RequiredApproval::nodeId)
                .containsExactly("lvl", ApprovalRequestService.NEEDS_ATTENTION);
        assertThat(saved.required().get(1).role()).isEqualTo("Admin");
        verify(notifications).notifyRole(
                eq("ADMIN"), eq("APPROVAL_NEEDED"), eq("Workflow needs attention: Event approval"),
                eq("The rule “Flagged critical” has no path for NO (otherwise)."), eq("/admin?tab=workflow"));
    }

    @Test
    @DisplayName("an incomplete route with no approvals is not left ownerless")
    void anIncompleteRouteWithNoApprovalsStillHasAnOwner() {
        engineAnswers(false, List.of(), List.of("This workflow has no starting point."));

        ApprovalRequestResponse saved = service.submit(EVENT);

        assertThat(saved.required()).extracting(RequiredApproval::nodeId)
                .containsExactly(ApprovalRequestService.NEEDS_ATTENTION);
        verify(notifications).notifyRole(eq("ADMIN"), eq("APPROVAL_NEEDED"), eq("Approval needed: Fall Careers Night"), any(), eq("/approvals"));
    }

    @Test
    void anAutoApprovedRequestNeedsNobody() {
        engineAnswers(true, List.of(), List.of());

        ApprovalRequestResponse saved = service.submit(EVENT);

        assertThat(saved.status()).isEqualTo("AUTO_APPROVED");
        assertThat(saved.required()).isEmpty();
        assertThat(saved.decidedBy()).isEqualTo("Policy engine");
    }

    @AfterEach
    void nobodyIsActing() {
        CurrentUser.clear();
    }

    /** A pending request whose only remaining step is the backstop. */
    private ApprovalRequest awaitingTheBackstop() {
        ApprovalRequest req = new ApprovalRequest(
                UUID.randomUUID(), "e1", "EVENT", "evt-1", "Fall Careers Night", "Charlotte, NC",
                ApprovalRequest.Status.PENDING,
                "[{\"nodeId\":\"lvl\",\"label\":\"Level 1\",\"role\":\"Hiring Manager\"},"
                        + "{\"nodeId\":\"needs-attention\",\"label\":\"Needs attention\",\"role\":\"Admin\"}]",
                1, null, null, null, null);
        when(requests.findById(req.getId())).thenReturn(Optional.of(req));
        return req;
    }

    @Test
    @DisplayName("only an admin can clear the backstop step")
    void aHiringManagerCannotClearTheBackstop() {
        ApprovalRequest req = awaitingTheBackstop();
        CurrentUser.set(UUID.randomUUID(), "David Okafor", "HIRING_MANAGER");

        assertThatThrownBy(() -> service.approve(req.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        assertThat(req.getStatus()).isEqualTo(ApprovalRequest.Status.PENDING);
        assertThat(req.getCurrentStep()).isEqualTo(1);
        verify(requests, never()).save(any());
    }

    @Test
    void anAdminClearsTheBackstop() {
        ApprovalRequest req = awaitingTheBackstop();
        CurrentUser.set(UUID.randomUUID(), "Priya Raman", "ADMIN");

        ApprovalRequestResponse decided = service.approve(req.getId());

        assertThat(decided.status()).isEqualTo("APPROVED");
    }

    @Test
    void ordinaryStepsAreDecidedAsBefore() {
        ApprovalRequest req = awaitingTheBackstop();
        req.setCurrentStep(0);
        CurrentUser.set(UUID.randomUUID(), "David Okafor", "HIRING_MANAGER");

        ApprovalRequestResponse decided = service.approve(req.getId());

        assertThat(decided.status()).isEqualTo("PENDING");
        assertThat(decided.currentStep()).isEqualTo(1);
    }

    @Test
    void withNoEnabledWorkflowNothingIsCreated() {
        when(workflows.findFirstByTriggerTypeIgnoreCaseAndEnabledTrue("Offer")).thenReturn(Optional.empty());

        ApprovalRequestResponse saved = service.submit(new SubmitApprovalRequest(
                "Offer", "OFFER", "off-1", "Offer", null, null, null, null));

        assertThat(saved).isNull();
        verify(requests, never()).save(any());
    }
}
