package com.canton.integration.domain

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MoneySpec extends AnyWordSpec with Matchers {

  "Money" should {
    "add and subtract amounts in the same currency" in {
      val a = Money("USD", BigDecimal("100.00"))
      val b = Money("USD", BigDecimal("25.50"))

      (a + b).amount shouldBe BigDecimal("125.50")
      (a - b).amount shouldBe BigDecimal("74.50")
    }

    "reject operations across currencies" in {
      val usd = Money("USD", BigDecimal("100"))
      val eur = Money("EUR", BigDecimal("100"))

      an[IllegalArgumentException] should be thrownBy (usd + eur)
    }
  }

  "Money.accrueInterest" should {
    "match the Java/DAML day-count convention: principal * rate% * days/360" in {
      val principal = BigDecimal("1000000")
      val ratePct = BigDecimal("5.00") // 5% annual
      val days = 90L

      // 1,000,000 * 0.05 * 90/360 = 12,500
      Money.accrueInterest(principal, ratePct, days) shouldBe BigDecimal("12500.00")
    }

    "return zero for zero days" in {
      Money.accrueInterest(BigDecimal("1000000"), BigDecimal("5.00"), 0L) shouldBe BigDecimal("0.00")
    }
  }
}
