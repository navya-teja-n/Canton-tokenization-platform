package com.canton.platform.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Quoted repurchase amount (principal + accrued repo interest) as of a date. */
public record RepoQuoteResponse(
        String repoId,
        LocalDate asOfDate,
        BigDecimal repurchaseAmount
) {}
