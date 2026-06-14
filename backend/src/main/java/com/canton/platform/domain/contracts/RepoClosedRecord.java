package com.canton.platform.domain.contracts;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.canton.platform.domain.Money;
import com.canton.platform.domain.enums.RepoStatus;

import lombok.Builder;
import lombok.With;

/** Mirrors DAML template {@code Repo.Repo.RepoClosedRecord}: immutable audit record. */
@With
@Builder
public record RepoClosedRecord(
        String borrower,
        String lender,
        String regulator,
        String repoId,
        String collateralInstrumentId,
        BigDecimal collateralQty,
        Money principal,
        BigDecimal interestPaid,
        LocalDate startDate,
        LocalDate maturityDate,
        Instant closedAt,
        RepoStatus outcome
) {}
