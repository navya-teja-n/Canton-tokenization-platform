package com.canton.integration.domain

/**
 * Mirrors the DAML enums in `daml/daml/Roles/Types.daml` and their Java
 * counterparts in `com.canton.platform.domain.enums`. Kept as Scala `enum`
 * (Scala 2.13 sealed-trait encoding) so the integration layer's wire format
 * stays structurally identical to both the DAML ledger and the Java backend,
 * which is essential when the same JSON payloads cross the Scala <-> Java
 * boundary at a real institution running Canton (written in Scala) alongside
 * JVM line-of-business services (often Java/Spring).
 */
sealed trait AssetClass
object AssetClass {
  case object GovernmentBond  extends AssetClass
  case object TreasuryBill    extends AssetClass
  case object TokenizedDeposit extends AssetClass

  def values: Seq[AssetClass] = Seq(GovernmentBond, TreasuryBill, TokenizedDeposit)
}

sealed trait InstrumentStatus
object InstrumentStatus {
  case object Active         extends InstrumentStatus
  case object Collateralized extends InstrumentStatus
  case object Matured        extends InstrumentStatus
  case object Redeemed       extends InstrumentStatus
  case object Defaulted      extends InstrumentStatus
}

sealed trait TradeStatus
object TradeStatus {
  case object Proposed  extends TradeStatus
  case object Accepted  extends TradeStatus
  case object Settled   extends TradeStatus
  case object Rejected  extends TradeStatus
  case object Cancelled extends TradeStatus
}

sealed trait RepoStatus
object RepoStatus {
  case object RepoOpen      extends RepoStatus
  case object RepoMatured   extends RepoStatus
  case object RepoClosed    extends RepoStatus
  case object RepoDefaulted extends RepoStatus
}

sealed trait KycStatus
object KycStatus {
  case object KycPending  extends KycStatus
  case object KycApproved extends KycStatus
  case object KycRejected extends KycStatus
  case object KycRevoked  extends KycStatus
}

sealed trait RepoDirection
object RepoDirection {
  case object Repo        extends RepoDirection
  case object ReverseRepo extends RepoDirection
}
