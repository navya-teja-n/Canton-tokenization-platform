package com.canton.platform.dto.request;

import java.math.BigDecimal;

import com.canton.platform.domain.Money;
import com.canton.platform.domain.enums.AssetClass;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Request for a seller to propose a DvP trade. Mirrors creating a {@code BondTradeProposal}. */
public record TradeProposeRequest(
        @NotBlank String seller,
        @NotBlank String buyer,
        @NotBlank String regulator,
        @NotBlank String tradeId,
        @NotNull AssetClass assetClass,
        @NotBlank String instrumentId,
        @NotNull @Positive BigDecimal quantity,
        @NotNull @Valid Money price
) {}
