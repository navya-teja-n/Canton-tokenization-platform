package com.canton.platform.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Request to revoke a previously approved KYC record. Mirrors {@code KycApproval.Revoke}. */
public record KycRevokeRequest(
        @NotBlank String applicant,
        @NotBlank String reason,
        String correlationId
) {}
