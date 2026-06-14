package com.canton.platform.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.canton.platform.domain.Money;
import com.canton.platform.domain.contracts.RepoProposal.RepoDirection;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request to create a repo proposal ({@code direction = REPO}, borrower-initiated,
 * mirrors {@code RepoProposal}) or a reverse-repo proposal
 * ({@code direction = REVERSE_REPO}, lender-initiated, mirrors {@code ReverseRepoProposal}).
 */
public record RepoProposeRequest(
        @NotBlank String borrower,
        @NotBlank String lender,
        @NotBlank String regulator,
        @NotBlank String repoId,
        @NotNull RepoDirection direction,
        @NotBlank String collateralInstrumentId,
        @NotNull @Positive BigDecimal collateralQty,
        @NotNull @Valid Money principal,
        @NotNull @Positive BigDecimal repoRatePct,
        @NotNull LocalDate startDate,
        @NotNull LocalDate maturityDate
) {}
