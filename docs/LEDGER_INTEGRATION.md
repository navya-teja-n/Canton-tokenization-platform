# Ledger Integration

This document maps the DAML templates in `daml/` to their realizations in the
Java backend and the Scala integration module, and explains how each piece
would be swapped for a real Canton/Daml deployment.

## Template -> domain object -> service mapping

| DAML template (`daml/daml/...`) | Java domain record (`backend/.../domain/contracts`) | Scala mirror (`integration/.../domain/Contracts.scala`) | Backend service / controller |
|---|---|---|---|
| `KYC.KycService.KycRequest`, `KycApproval` | `KycApproval` | `KycApproval` | `KycService`, `KycController` (`/api/kyc/*`) |
| `Wallet.Wallet.WalletApplication`, `Wallet` | `Wallet`, `WalletHolding` | `Wallet`, `WalletHolding` | `WalletService`, `WalletController` (`/api/wallets/*`) |
| `Assets.Bond.GovernmentBondIssuance`, `GovernmentBond` | `TokenizedAsset` (assetClass = `GOVERNMENT_BOND`) | `TokenizedAsset` | `AssetIssuanceService`, `AssetController` (`/api/assets/*`) |
| `Assets.Bond.TreasuryBillIssuance`, `TreasuryBill` | `TokenizedAsset` (assetClass = `TREASURY_BILL`) | `TokenizedAsset` | `AssetIssuanceService`, `AssetController` |
| `Assets.Deposit.DepositIssuanceRequest`, `TokenizedDeposit` | `TokenizedDeposit` | `TokenizedDeposit` | `AssetIssuanceService`, `AssetController` (`/api/assets/deposits/*`) |
| `Trading.Trade.BondTradeProposal`, `BillTradeProposal` | `TradeProposal` | `TradeProposal` | `TradeService`, `TradeController` (`/api/trades/*`) |
| `Trading.Trade.ExecutedTrade`, `ExecutedBillTrade` | `ExecutedTrade` | `ExecutedTrade` | `TradeService`, `TradeController` |
| `Repo.Repo.RepoProposal`, `ReverseRepoProposal` | `RepoProposal` | `RepoProposal` | `RepoService`, `RepoController` (`/api/repos/*`) |
| `Repo.Repo.RepoAgreement` | `RepoAgreement` | `RepoAgreement` | `RepoService`, `RepoController` |
| `Repo.Repo.RepoClosedRecord` | `RepoClosedRecord` | `RepoClosedRecord` | `RepoService`, `RepoController` |

Each Java record in `domain/contracts/` carries the same fields as its DAML
template's parameters (signatories/observers become plain `String` party
fields; DAML `Decimal`/`Date`/`Time` become `BigDecimal`/`LocalDate`/
`Instant`). The Scala case classes in `integration/.../domain/Contracts.scala`
are intentionally kept structurally identical so payloads can cross the
JVM-language boundary as plain data (via the circe codecs in
`JsonCodecs.scala`) without an adapter layer.

## The two ledger simulators

This sandbox has no DAML SDK / Canton runtime, so **two independent in-memory
simulators** stand in for a real participant node:

1. **`backend/.../ledger/CantonLedgerSimulator`** (Java) — used directly by
   the Spring Boot services. Maintains an Active Contract Set (`ConcurrentHashMap<ContractId, LedgerContract>`)
   and an append-only transaction log of `LedgerEvent`s (`CREATED`/`ARCHIVED`),
   each tagged with `witnessParties` for privacy filtering.
2. **`integration/.../SimulatedCantonLedgerApiClient`** (Scala/Pekko) — a
   separate ACS (`ConcurrentHashMap[ContractId, CreatedEvent]`) + transaction
   log, exposed over Pekko HTTP (`IntegrationGateway`) and a Pekko Streams
   `BroadcastHub` for live transaction subscriptions
   (`GET /v1/transactions/stream` as Server-Sent Events).

They are **not wired together** in this demo — the Java backend talks to its
own simulator, and the Scala gateway is a standalone service on port 8090
that a Java backend *could* call instead, exactly as described below.

## Mapping to a real Canton deployment

In a production deployment, both simulators are replaced by calls to a real
Canton participant node's **Ledger API** (gRPC, defined in
`com.daml.ledger.api.v1.*`):

| Simulator concept | Real Ledger API equivalent |
|---|---|
| `CantonLedgerSimulator.submit(...)` / `SimulatedCantonLedgerApiClient.submit(...)` | `CommandSubmissionService.SubmitAndWait` (or `CommandService.SubmitAndWaitForTransaction`) |
| Active Contract Set map | `ActiveContractsService.GetActiveContracts` |
| `getTransactionStream(party)` / `transactions(party, ...)` | `TransactionService.GetTransactions` (streaming, filtered by `TransactionFilter` per party) |
| `LedgerEvent` (`CREATED`/`ARCHIVED`) | `CreatedEvent` / `ArchivedEvent` within a `Transaction` |
| `witnessParties` filtering | enforced server-side by the participant based on `signatory`/`observer`/`TransactionFilter` |
| `ContractId`, `TemplateId` | `value.ContractId`, `value.Identifier` |
| in-memory idempotency cache (`IdempotencyService`) | Ledger API's own `commandId` + `applicationId` + `actAs` deduplication window |

### Two supported integration modes (`integration/` module)

The Scala module models both ways institutions commonly wire a Canton
integration tier to their Java services:

1. **HTTP gateway mode** (`IntegrationGateway` + `Main`): the Scala service
   runs standalone (Pekko HTTP on `:8090`) and exposes a small JSON/REST
   facade over the Ledger API (`/v1/commands/submit`, `/v1/contracts/active`,
   `/v1/contracts/lookup`, `/v1/transactions/stream`). The Java backend would
   call this facade with a normal `RestTemplate`/`WebClient`, instead of (or
   in addition to) its own `CantonLedgerSimulator`. This mirrors the common
   "ledger integration microservice" pattern.
2. **In-process library mode**: `CantonLedgerApiClient` (the trait) and
   `SimulatedCantonLedgerApiClient` (the implementation) can be used directly
   from other Scala/JVM code — e.g. a Scala batch job or stream processor —
   without going through HTTP, the same way a real deployment might depend on
   the official `com.daml:bindings-akka`/Pekko Ledger API client library
   in-process.

To point either mode at a **real** Canton participant, only
`SimulatedCantonLedgerApiClient` (Scala) and `CantonLedgerSimulator` (Java)
need to be replaced with implementations backed by the official Daml Ledger
API gRPC bindings (`com.daml.ledger.api.v1.CommandSubmissionServiceGrpc`,
`TransactionServiceGrpc`, `ActiveContractsServiceGrpc`); the `CantonLedgerApiClient`
trait and the Java service interfaces are designed so no caller-side code
needs to change.

## Why Scala for the integration tier

Daml's official Ledger API client bindings are published for Scala (built on
Akka/Pekko gRPC and streams), which is why many banks' Canton integration
services are written in Scala even when the surrounding platform is Java.
`integration/` follows that convention: `CantonLedgerApiClient` mirrors the
shape of `CommandSubmissionServiceGrpc`/`TransactionServiceGrpc`, and
`SimulatedCantonLedgerApiClient` uses Pekko Streams (`Source`, `BroadcastHub`)
the way a real client wraps the Ledger API's server-streaming
`GetTransactions` RPC. See `integration/README.md` for build/run instructions
and the full file listing.
