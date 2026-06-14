package com.canton.platform.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request for the borrower to repurchase: pays principal + accrued interest
 * to the lender and receives the collateral back. Mirrors {@code Repurchase}.
 */
public record RepoRepurchaseRequest(
        @NotBlank String borrower,
        @NotNull LocalDate asOfDate,
        @NotBlank String repaymentDepositId,
        @NotBlank String returnedInstrumentId,
        @NotBlank String borrowerWalletId,
        @NotBlank String lenderWalletId,
        String correlationId
) {}
