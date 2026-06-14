package com.canton.platform.domain.enums;

/** Mirrors DAML {@code Roles.Types.InstrumentStatus}. */
public enum InstrumentStatus {
    ACTIVE,
    COLLATERALIZED,
    MATURED,
    REDEEMED,
    DEFAULTED
}
