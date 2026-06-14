package com.canton.platform.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.canton.platform.domain.enums.AssetClass;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request to issue a government bond or treasury bill into a KYC-verified
 * owner's wallet. Mirrors {@code GovernmentBondIssuance.IssueBond} /
 * {@code TreasuryBillIssuance.IssueTreasuryBill}.
 * {@code couponRatePct} applies to GOVERNMENT_BOND, {@code purchasePricePerUnit}
 * to TREASURY_BILL (may be left null/zero for the other asset class).
 */
public record AssetIssueRequest(
        @NotBlank String issuer,
        @NotBlank String owner,
        @NotBlank String custodian,
        @NotBlank String regulator,
        @NotBlank String walletId,
        @NotNull AssetClass assetClass,
        @NotBlank String instrumentId,
        @NotBlank String isin,
        @NotBlank String currency,
        @NotNull @Positive BigDecimal faceValuePerUnit,
        @NotNull @Positive BigDecimal quantity,
        BigDecimal couponRatePct,
        BigDecimal purchasePricePerUnit,
        @NotNull LocalDate issueDate,
        @NotNull LocalDate maturityDate
) {}
