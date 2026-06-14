package com.canton.platform.events;

import java.math.BigDecimal;
import java.time.Instant;

public record CollateralEvent(
        String walletId,
        String instrumentId,
        BigDecimal quantity,
        String topic,
        String correlationId,
        Instant occurredAt
) implements DomainEvent {
    @Override public String topic() { return topic; }
}
