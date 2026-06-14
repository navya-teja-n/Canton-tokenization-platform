package com.canton.integration.domain

import java.time.{Instant, LocalDate}

/**
 * Scala mirrors of the active-contract payloads produced by the simulated
 * Canton ledger (`com.canton.platform.ledger.CantonLedgerSimulator` on the
 * Java side, and `SimulatedCantonLedgerApiClient` here). These case classes
 * correspond 1:1 with the DAML templates under `daml/daml/**` and with the
 * Java records in `com.canton.platform.domain.contracts` -- the same
 * contract, viewed from three different ledger-API client stacks (DAML/
 * Canton itself, a Java Spring backend, and this Scala integration layer),
 * which is the realistic shape of a multi-language institutional deployment.
 */

/** Mirrors `KYC.KycService.KycApproval`. */
final case class KycApproval(
    applicant: String,
    kycProvider: String,
    regulator: String,
    legalName: String,
    jurisdiction: String,
    status: KycStatus,
    riskRating: String,
    approvedAt: Instant
)

/** Mirrors `Wallet.Wallet.WalletHolding`. */
final case class WalletHolding(
    assetClass: AssetClass,
    instrumentId: String,
    quantity: BigDecimal,
    locked: Boolean
)

/** Mirrors `Wallet.Wallet.Wallet`. */
final case class Wallet(
    owner: String,
    custodian: String,
    regulator: String,
    kycProvider: String,
    walletId: String,
    holdings: List[WalletHolding],
    cashBalances: List[Money]
)

/** Mirrors `Assets.Bond.GovernmentBond` / `Assets.Bond.TreasuryBill` (unified). */
final case class TokenizedAsset(
    issuer: String,
    owner: String,
    custodian: String,
    regulator: String,
    instrumentId: String,
    isin: String,
    assetClass: AssetClass,
    currency: String,
    faceValuePerUnit: BigDecimal,
    quantity: BigDecimal,
    couponRatePct: BigDecimal,
    purchasePricePerUnit: BigDecimal,
    issueDate: LocalDate,
    maturityDate: LocalDate,
    status: InstrumentStatus
)

/** Mirrors `Assets.Deposit.TokenizedDeposit`. */
final case class TokenizedDeposit(
    bank: String,
    owner: String,
    regulator: String,
    depositId: String,
    currency: String,
    amount: BigDecimal,
    issuedAt: Instant,
    frozen: Boolean
)

/** Mirrors `Trading.Trade.BondTradeProposal` / `BillTradeProposal` (unified). */
final case class TradeProposal(
    seller: String,
    buyer: String,
    regulator: String,
    tradeId: String,
    assetClass: AssetClass,
    instrumentId: String,
    quantity: BigDecimal,
    price: Money,
    status: TradeStatus
)

/** Mirrors `Trading.Trade.ExecutedTrade` / `ExecutedBillTrade` (unified). */
final case class ExecutedTrade(
    seller: String,
    buyer: String,
    regulator: String,
    tradeId: String,
    assetClass: AssetClass,
    instrumentId: String,
    quantity: BigDecimal,
    price: Money,
    settledAssetContractId: String,
    settledCashContractId: String,
    settledAt: Instant
)

/** Mirrors `Repo.Repo.RepoProposal` / `ReverseRepoProposal` (unified via `direction`). */
final case class RepoProposal(
    borrower: String,
    lender: String,
    regulator: String,
    repoId: String,
    direction: RepoDirection,
    collateralInstrumentId: String,
    collateralQty: BigDecimal,
    principal: Money,
    repoRatePct: BigDecimal,
    startDate: LocalDate,
    maturityDate: LocalDate,
    status: TradeStatus
)

/** Mirrors `Repo.Repo.RepoAgreement`. */
final case class RepoAgreement(
    borrower: String,
    lender: String,
    regulator: String,
    repoId: String,
    collateralInstrumentId: String,
    collateralQty: BigDecimal,
    principal: Money,
    repoRatePct: BigDecimal,
    startDate: LocalDate,
    maturityDate: LocalDate,
    collateralContractId: String,
    status: RepoStatus
)

/** Mirrors `Repo.Repo.RepoClosedRecord`. */
final case class RepoClosedRecord(
    borrower: String,
    lender: String,
    regulator: String,
    repoId: String,
    collateralInstrumentId: String,
    collateralQty: BigDecimal,
    principal: Money,
    interestPaid: BigDecimal,
    startDate: LocalDate,
    maturityDate: LocalDate,
    closedAt: Instant,
    outcome: RepoStatus
)
