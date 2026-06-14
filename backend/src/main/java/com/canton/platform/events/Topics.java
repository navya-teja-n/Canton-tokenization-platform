package com.canton.platform.events;

/**
 * Topic names for the in-memory event bus, modeled after Kafka topics that
 * a production deployment would use to decouple ledger-event consumption
 * from downstream orchestration (settlement, collateral release,
 * notifications).
 */
public final class Topics {
    private Topics() {}

    public static final String TRADE_EXECUTED = "trade.executed";
    public static final String REPO_OPENED = "repo.opened";
    public static final String REPO_MATURED = "repo.matured";
    public static final String REPO_CLOSED = "repo.closed";
    public static final String COLLATERAL_LOCKED = "collateral.locked";
    public static final String COLLATERAL_RELEASED = "collateral.released";
    public static final String KYC_STATUS_CHANGED = "kyc.status_changed";
    public static final String DEPOSIT_FROZEN = "deposit.frozen";
}
