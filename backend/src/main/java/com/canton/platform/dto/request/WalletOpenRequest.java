package com.canton.platform.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Request to open a KYC-gated custodial wallet. Mirrors {@code WalletApplication.OpenWallet}. */
public record WalletOpenRequest(
        @NotBlank String owner,
        @NotBlank String custodian,
        @NotBlank String regulator,
        @NotBlank String kycProvider,
        @NotBlank String walletId
) {}
