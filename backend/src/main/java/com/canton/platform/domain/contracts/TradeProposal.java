package com.canton.platform.domain.contracts;

import java.math.BigDecimal;

import com.canton.platform.domain.Money;
import com.canton.platform.domain.enums.AssetClass;
import com.canton.platform.domain.enums.TradeStatus;

import lombok.Builder;
import lombok.With;

/**
 * Mirrors DAML templates {@code Trading.Trade.BondTradeProposal} /
 * {@code BillTradeProposal}, unified via {@code assetClass}.
 */
@With
@Builder
public record TradeProposal(
        String seller,
        String buyer,
        String regulator,
        String tradeId,
        AssetClass assetClass,
        String instrumentId,
        BigDecimal quantity,
        Money price,
        TradeStatus status
) {}
