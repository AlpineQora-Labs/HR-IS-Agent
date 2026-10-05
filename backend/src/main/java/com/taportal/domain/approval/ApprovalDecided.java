package com.taportal.domain.approval;

/**
 * An approval request reached its end: approved (its last step was approved) or rejected.
 * Published in the transaction that decides it, so whatever owns the item can act on the
 * outcome in step with the decision.
 *
 * @param itemType the kind of item, e.g. "EVENT"
 * @param itemRef  the item's reference; for an event, its id
 * @param approved true when approved, false when rejected
 */
public record ApprovalDecided(String itemType, String itemRef, boolean approved) {}
