package com.canton.platform.domain.contracts;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Builder;
import lombok.With;

/**
 * Mirrors DAML template {@code Assets.Deposit.TokenizedDeposit}: an
 * on-ledger representation of a bank deposit liability. {@code bank} is the
 * sole signatory (issuing institution); {@code owner} is the current
 * beneficial holder.
 */
@With
@Builder
public record TokenizedDeposit(
        String bank,
        String owner,
        String regulator,
        String depositId,
        String currency,
        BigDecimal amount,
        Instant issuedAt,
        boolean frozen
) {}
