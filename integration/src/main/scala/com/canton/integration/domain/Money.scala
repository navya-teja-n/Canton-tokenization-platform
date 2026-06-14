package com.canton.integration.domain

/**
 * Mirrors DAML `Roles.Types.Money` and Java `com.canton.platform.domain.Money`:
 * an immutable amount tagged with an ISO currency code, plus the shared
 * day-count interest accrual used by repo pricing.
 */
final case class Money(currency: String, amount: BigDecimal) {
  def +(other: Money): Money = {
    requireSameCurrency(other)
    Money(currency, amount + other.amount)
  }

  def -(other: Money): Money = {
    requireSameCurrency(other)
    Money(currency, amount - other.amount)
  }

  private def requireSameCurrency(other: Money): Unit =
    require(currency == other.currency, s"Currency mismatch: $currency vs ${other.currency}")
}

object Money {

  /**
   * Mirrors DAML `accrueInterest`: simple day-count interest accrual,
   * `principal * rate% * (days / 360)`. Used identically by the Java
   * `RepoService` and this Scala client so repo quotes agree regardless of
   * which JVM service computes them.
   */
  def accrueInterest(principal: BigDecimal, annualRatePct: BigDecimal, days: Long): BigDecimal =
    principal * (annualRatePct / BigDecimal(100)) * (BigDecimal(days) / BigDecimal(360))
}
