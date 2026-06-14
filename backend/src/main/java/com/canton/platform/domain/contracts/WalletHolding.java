package com.canton.platform.domain.contracts;

import java.math.BigDecimal;

import com.canton.platform.domain.enums.AssetClass;

import lombok.Builder;
import lombok.With;

/** Mirrors DAML {@code Wallet.Wallet.WalletHolding}. */
@With
@Builder
public record WalletHolding(
        AssetClass assetClass,
        String instrumentId,
        BigDecimal quantity,
        boolean locked
) {}
