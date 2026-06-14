package com.canton.platform.domain.contracts;

import java.math.BigDecimal;
import java.time.Instant;

import com.canton.platform.domain.Money;
import com.canton.platform.domain.enums.AssetClass;

import lombok.Builder;
import lombok.With;

/**
 * Mirrors DAML templates {@code Trading.Trade.ExecutedTrade} /
 * {@code ExecutedBillTrade}: an immutable, terminal record of a settled
 * DvP trade.
 */
@With
@Builder
public record ExecutedTrade(
        String seller,
        String buyer,
        String regulator,
        String tradeId,
        AssetClass assetClass,
        String instrumentId,
        BigDecimal quantity,
        Money price,
        String settledAssetContractId,
        String settledCashContractId,
        Instant settledAt
) {}
