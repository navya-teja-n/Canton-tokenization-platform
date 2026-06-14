package com.canton.platform.ledger;

import java.time.Instant;
import java.util.Set;

/**
 * Represents one entry in the simulated Ledger API transaction stream
 * (analogous to a Canton/DAML transaction event: a contract creation or
 * archival, visible only to its witness parties).
 */
public record LedgerEvent(
        String eventId,
        EventType type,
        String templateId,
        String contractId,
        Object payload,
        Set<String> witnessParties,
        Instant effectiveAt
) {
    public enum EventType { CREATED, ARCHIVED }
}
