package com.canton.platform.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Request to approve a KYC applicant. Mirrors {@code KycRequest.Approve}. */
public record KycApproveRequest(
        @NotBlank String applicant,
        @NotBlank String kycProvider,
        @NotBlank String regulator,
        @NotBlank String legalName,
        @NotBlank String jurisdiction,
        @NotBlank String riskRating,
        String correlationId
) {}
