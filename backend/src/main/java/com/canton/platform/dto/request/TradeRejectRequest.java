package com.canton.platform.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TradeRejectRequest(
        @NotBlank String buyer,
        String reason
) {}
