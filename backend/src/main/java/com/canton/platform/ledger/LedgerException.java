package com.canton.platform.ledger;

/**
 * Thrown by {@link CantonLedgerSimulator} when a command cannot be
 * processed -- analogous to a {@code GrpcStatus} error returned by a real
 * Canton participant node (e.g. CONTRACT_NOT_FOUND, NOT_AUTHORIZED).
 */
public class LedgerException extends RuntimeException {

    private final String errorCode;

    public LedgerException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public static LedgerException contractNotFound(String contractId) {
        return new LedgerException("CONTRACT_NOT_FOUND", "No active contract with id " + contractId);
    }

    public static LedgerException notAuthorized(String party, String action) {
        return new LedgerException("NOT_AUTHORIZED",
                "Party " + party + " is not authorized to perform: " + action);
    }

    public static LedgerException templateMismatch(String contractId, String expected) {
        return new LedgerException("TEMPLATE_MISMATCH",
                "Contract " + contractId + " is not of expected template " + expected);
    }
}
