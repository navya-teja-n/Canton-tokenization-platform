package com.canton.platform.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Request for the buyer to accept and atomically settle (DvP) a trade proposal. */
public record TradeSettleRequest(
        @NotBlank String buyer,
        @NotBlank String sellerWalletId,
        @NotBlank String buyerWalletId,
        @NotBlank String settledInstrumentId,
        @NotBlank String settledDepositId,
        String correlationId
) {}
