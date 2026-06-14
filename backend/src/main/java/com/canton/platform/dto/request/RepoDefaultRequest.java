package com.canton.platform.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request for the lender to declare default after maturity: the lender
 * retains the collateral permanently. Mirrors {@code DeclareDefault}.
 */
public record RepoDefaultRequest(
        @NotBlank String lender,
        @NotNull LocalDate asOfDate,
        @NotBlank String releasedInstrumentId,
        @NotBlank String lenderWalletId,
        String correlationId
) {}
