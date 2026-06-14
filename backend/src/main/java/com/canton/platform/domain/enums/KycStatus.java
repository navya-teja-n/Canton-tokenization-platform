package com.canton.platform.domain.enums;

/** Mirrors DAML {@code Roles.Types.KycStatus}. */
public enum KycStatus {
    KYC_PENDING,
    KYC_APPROVED,
    KYC_REJECTED,
    KYC_REVOKED
}
