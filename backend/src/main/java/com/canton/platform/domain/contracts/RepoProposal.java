package com.canton.platform.domain.contracts;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.canton.platform.domain.Money;
import com.canton.platform.domain.enums.TradeStatus;

import lombok.Builder;
import lombok.With;

/**
 * Mirrors DAML templates {@code Repo.Repo.RepoProposal} (borrower-initiated)
 * and {@code Repo.Repo.ReverseRepoProposal} (lender-initiated), unified via
 * the {@code direction} field.
 */
@With
@Builder
public record RepoProposal(
        String borrower,
        String lender,
        String regulator,
        String repoId,
        RepoDirection direction,
        String collateralInstrumentId,
        BigDecimal collateralQty,
        Money principal,
        BigDecimal repoRatePct,
        LocalDate startDate,
        LocalDate maturityDate,
        TradeStatus status
) {
    public enum RepoDirection { REPO, REVERSE_REPO }
}
