package com.canton.platform.events;

import java.time.Instant;

/**
 * Emitted at each repo lifecycle transition: opened (collateral locked /
 * cash disbursed), matured, and closed (repurchased or defaulted).
 */
public record RepoLifecycleEvent(
        String repoId,
        String borrower,
        String lender,
        String topic,
        String detail,
        String correlationId,
        Instant occurredAt
) implements DomainEvent {
    @Override public String topic() { return topic; }
}
