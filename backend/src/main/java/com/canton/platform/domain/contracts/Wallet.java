package com.canton.platform.domain.contracts;

import java.util.List;

import com.canton.platform.domain.Money;

import lombok.Builder;
import lombok.With;

/**
 * Mirrors DAML template {@code Wallet.Wallet.Wallet}: a KYC-gated custodial
 * wallet. {@code owner} and {@code custodian} are joint signatories;
 * {@code regulator} is an observer for supervisory visibility.
 */
@With
@Builder
public record Wallet(
        String owner,
        String custodian,
        String regulator,
        String kycProvider,
        String walletId,
        List<WalletHolding> holdings,
        List<Money> cashBalances
) {}
