package com.canton.platform.domain;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mirrors DAML {@code Roles.Types.Money}: an immutable amount tagged with an
 * ISO currency code.
 */
public record Money(String currency, BigDecimal amount) {

    @JsonCreator
    public Money(@JsonProperty("currency") String currency, @JsonProperty("amount") BigDecimal amount) {
        this.currency = currency;
        this.amount = amount;
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(currency, amount.add(other.amount));
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(currency, amount.subtract(other.amount));
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: " + currency + " vs " + other.currency);
        }
    }

    /**
     * Mirrors DAML {@code accrueInterest}: simple day-count interest accrual,
     * principal * rate% * (days / 360).
     */
    public static BigDecimal accrueInterest(BigDecimal principal, BigDecimal annualRatePct, long days) {
        return principal
                .multiply(annualRatePct)
                .divide(BigDecimal.valueOf(100), 10, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(360), 10, java.math.RoundingMode.HALF_UP);
    }
}
