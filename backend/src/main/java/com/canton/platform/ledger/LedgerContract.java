package com.canton.platform.ledger;

import java.time.Instant;
import java.util.Set;

/**
 * An entry in the simulated Active Contract Set (ACS). Mirrors the
 * signatory/observer model of a DAML contract: {@code signatories} must
 * jointly authorize any consuming choice, {@code observers} may see the
 * contract but not act on it (enforced by service-layer authorization
 * checks, mirroring DAML's {@code controller} keyword).
 */
public record LedgerContract<T>(
        String contractId,
        String templateId,
        T payload,
        Set<String> signatories,
        Set<String> observers,
        Instant createdAt
) {
    /** Union of signatories and observers: parties who can "see" this contract. */
    public Set<String> witnesses() {
        java.util.Set<String> all = new java.util.HashSet<>(signatories);
        all.addAll(observers);
        return all;
    }
}
