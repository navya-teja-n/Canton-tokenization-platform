package com.canton.platform.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Request to reject a KYC applicant. Mirrors {@code KycRequest.Reject}. */
public record KycRejectRequest(
        @NotBlank String applicant,
        @NotBlank String kycProvider,
        @NotBlank String regulator,
        @NotBlank String legalName,
        @NotBlank String jurisdiction,
        @NotBlank String reason,
        String correlationId
) {}
