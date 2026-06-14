package com.canton.platform.domain.enums;

/** Mirrors DAML {@code Roles.Types.TradeStatus}. */
public enum TradeStatus {
    PROPOSED,
    ACCEPTED,
    SETTLED,
    REJECTED,
    CANCELLED
}
