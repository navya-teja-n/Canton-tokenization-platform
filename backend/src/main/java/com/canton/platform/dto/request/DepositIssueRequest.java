package com.canton.platform.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Request to issue a tokenized bank deposit. Mirrors {@code DepositIssuanceRequest.IssueDeposit}. */
public record DepositIssueRequest(
        @NotBlank String bank,
        @NotBlank String owner,
        @NotBlank String regulator,
        @NotBlank String walletId,
        @NotBlank String depositId,
        @NotBlank String currency,
        @NotNull @Positive BigDecimal amount
) {}
