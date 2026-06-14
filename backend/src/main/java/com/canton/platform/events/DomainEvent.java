package com.canton.platform.events;

import java.time.Instant;

/**
 * Base interface for all domain events published on the in-memory event
 * bus. Each event corresponds to a ledger transaction (contract
 * creation/archival) that downstream processes react to -- e.g. trade
 * execution triggers settlement confirmation, repo maturity triggers a
 * repurchase/collateral-release workflow.
 */
public interface DomainEvent {
    String topic();
    Instant occurredAt();
    /** Correlation id propagated from the originating REST request. */
    String correlationId();
}
