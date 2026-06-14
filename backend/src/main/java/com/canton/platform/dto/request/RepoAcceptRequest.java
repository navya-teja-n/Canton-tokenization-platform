package com.canton.platform.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request for the counterparty to accept a repo proposal: atomic DvP --
 * collateral locked with the lender, principal cash delivered to the
 * borrower. Mirrors {@code AcceptRepo} / {@code AcceptReverseRepo}.
 */
public record RepoAcceptRequest(
        @NotBlank String accepter,
        @NotBlank String borrowerWalletId,
        @NotBlank String lenderWalletId,
        @NotBlank String settledCollateralInstrumentId,
        @NotBlank String settledCashDepositId,
        String correlationId
) {}
