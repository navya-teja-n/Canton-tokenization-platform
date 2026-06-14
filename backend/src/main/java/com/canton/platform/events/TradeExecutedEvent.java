package com.canton.platform.events;

import java.time.Instant;

public record TradeExecutedEvent(
        String tradeId,
        String seller,
        String buyer,
        String instrumentId,
        String correlationId,
        Instant occurredAt
) implements DomainEvent {
    @Override public String topic() { return Topics.TRADE_EXECUTED; }
}
