package com.canton.platform.events;

import java.time.Instant;

import com.canton.platform.domain.enums.KycStatus;

public record KycStatusChangedEvent(
        String applicant,
        KycStatus status,
        String correlationId,
        Instant occurredAt
) implements DomainEvent {
    @Override public String topic() { return Topics.KYC_STATUS_CHANGED; }
}
