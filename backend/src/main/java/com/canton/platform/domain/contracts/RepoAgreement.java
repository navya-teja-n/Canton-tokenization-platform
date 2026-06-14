package com.canton.platform.domain.contracts;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.canton.platform.domain.Money;
import com.canton.platform.domain.enums.RepoStatus;

import lombok.Builder;
import lombok.With;

/** Mirrors DAML template {@code Repo.Repo.RepoAgreement}. */
@With
@Builder
public record RepoAgreement(
        String borrower,
        String lender,
        String regulator,
        String repoId,
        String collateralInstrumentId,
        BigDecimal collateralQty,
        Money principal,
        BigDecimal repoRatePct,
        LocalDate startDate,
        LocalDate maturityDate,
        String collateralContractId,
        RepoStatus status
) {}
