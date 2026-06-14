package com.canton.platform.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TradeCancelRequest(
        @NotBlank String seller
) {}
