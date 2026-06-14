package com.canton.platform.domain.contracts;

import java.time.Instant;

import com.canton.platform.domain.enums.KycStatus;

import lombok.Builder;
import lombok.With;

/** Mirrors DAML template {@code KYC.KycService.KycApproval}. */
@With
@Builder
public record KycApproval(
        String applicant,
        String kycProvider,
        String regulator,
        String legalName,
        String jurisdiction,
        KycStatus status,
        String riskRating,
        Instant approvedAt
) {}
