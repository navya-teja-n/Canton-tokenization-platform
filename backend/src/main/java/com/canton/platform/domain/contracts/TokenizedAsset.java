package com.canton.platform.domain.contracts;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.canton.platform.domain.enums.AssetClass;
import com.canton.platform.domain.enums.InstrumentStatus;

import lombok.Builder;
import lombok.With;

/**
 * Mirrors DAML templates {@code Assets.Bond.GovernmentBond} and
 * {@code Assets.Bond.TreasuryBill}. The two DAML templates are unified here
 * via the {@code assetClass} discriminator for backend simplicity; see
 * {@code docs/LEDGER_INTEGRATION.md} for the mapping. {@code couponRatePct}
 * applies to GOVERNMENT_BOND only; {@code purchasePricePerUnit} applies to
 * TREASURY_BILL only (discount instrument).
 */
@With
@Builder
public record TokenizedAsset(
        String issuer,
        String owner,
        String custodian,
        String regulator,
        String instrumentId,
        String isin,
        AssetClass assetClass,
        String currency,
        BigDecimal faceValuePerUnit,
        BigDecimal quantity,
        BigDecimal couponRatePct,          // GOVERNMENT_BOND only
        BigDecimal purchasePricePerUnit,   // TREASURY_BILL only
        LocalDate issueDate,
        LocalDate maturityDate,
        InstrumentStatus status
) {}
