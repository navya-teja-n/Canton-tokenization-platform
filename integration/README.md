# Canton Integration Layer (Scala)

This module is the Scala "Module 4" of the Canton Tokenization & Deposit
Network platform. It exists because **Canton itself, and Daml's official
Ledger API client bindings, are written in Scala** -- so an institution that
runs Canton in production typically has at least one JVM service that talks
to the participant node's Ledger API in Scala, even if the rest of its
estate (line-of-business apps, REST APIs) is Java/Spring, as in
`backend/`. This module demonstrates that split.

## What's here

```
integration/
  build.sbt                                  sbt project (Scala 2.13, Pekko, Circe)
  project/build.properties
  src/main/scala/com/canton/integration/
    domain/
      Enums.scala            AssetClass, InstrumentStatus, TradeStatus,
                              RepoStatus, KycStatus, RepoDirection
                              -- mirrors com.canton.platform.domain.enums
                              and daml/daml/Roles/Types.daml
      Money.scala             Money(currency, amount) + accrueInterest,
                               same day-count convention as Money.java
      Contracts.scala          Scala case classes mirroring the DAML
                               templates / Java domain.contracts records
                               (KycApproval, Wallet, TokenizedAsset,
                               TokenizedDeposit, TradeProposal,
                               ExecutedTrade, RepoProposal, RepoAgreement,
                               RepoClosedRecord)
      LedgerCommands.scala     Minimal stand-ins for the Daml Ledger API v1
                               command/event/transaction Protobuf messages
                               (CreateCommand, ExerciseCommand, Completion,
                               CreatedEvent, ArchivedEvent, LedgerTransaction)
    CantonLedgerApiClient.scala        trait mirroring
                                        CommandSubmissionService +
                                        TransactionService
    SimulatedCantonLedgerApiClient.scala  in-memory ACS + transaction log +
                                        Pekko Streams broadcast, implementing
                                        the trait above
    JsonCodecs.scala            circe Encoders/Decoders for the whole model
    gateway/IntegrationGateway.scala   Pekko HTTP routes exposing the client
                                        to the Java backend
    Main.scala                  boots the Pekko HTTP server (default :8090)
  src/main/resources/
    application.conf, logback.xml
  src/test/scala/com/canton/integration/
    domain/MoneySpec.scala
    SimulatedCantonLedgerApiClientSpec.scala
```

## Why Scala here, and Java in `backend/`

| Concern | Real-world precedent | This repo |
|---|---|---|
| Talking to a Canton participant's Ledger API (gRPC) | Canton + the official Daml Ledger API bindings are Scala (`com.daml.ledger.api.v1.*`, built with scalapb) | `CantonLedgerApiClient` trait + `SimulatedCantonLedgerApiClient`, using Pekko Streams for the transaction-subscription side of the API |
| Internal service-to-service contract | Banks commonly front a Scala ledger-integration service with a small internal HTTP/JSON (or gRPC) API consumed by Java/Spring line-of-business services | `gateway.IntegrationGateway` (Pekko HTTP + circe) |
| Customer-facing REST APIs, business workflows, validation, idempotency/retry | Java/Spring Boot is the dominant stack for enterprise REST services | `backend/` (`com.canton.platform.*`) |
| Domain model | Kept structurally identical across languages so payloads round-trip cleanly | `domain/Enums.scala`, `domain/Money.scala`, `domain/Contracts.scala` mirror `com.canton.platform.domain.enums` / `domain.contracts` / DAML templates field-for-field |

## Two integration modes

1. **HTTP gateway (default, used by this repo's `backend/`)** -- run this
   module as its own JVM process (`sbt run`), and have the Java backend call
   `http://localhost:8090/v1/...` for command submission, ACS queries, and
   transaction streaming (via Server-Sent Events). This is the
   loosely-coupled, independently-deployable option, and the one documented
   in `docs/LEDGER_INTEGRATION.md`.

2. **Library mode** -- a JVM service (Java or Scala) can instead add this
   module as a dependency and call `CantonLedgerApiClient` / `JsonCodecs`
   directly in-process, avoiding the HTTP hop. Useful for latency-sensitive
   settlement components.

In both modes, swapping `SimulatedCantonLedgerApiClient` for a real Pekko/Akka
gRPC client against a Canton participant (port `6865` by default) requires
no changes to `IntegrationGateway` or to callers coded against the
`CantonLedgerApiClient` trait.

## Running

```bash
cd integration
sbt run        # starts the gateway on :8090 (override with INTEGRATION_HTTP_PORT)
sbt test       # ScalaTest specs for Money and the simulated client
```

## Endpoints

| Method | Path | Description |
|---|---|---|
| GET  | `/v1/health` | liveness probe |
| POST | `/v1/commands/submit` | submit a `CommandSubmission` (create/exercise/archive), returns a `Completion` |
| GET  | `/v1/contracts/active?party=...&templateId=...` | active contracts of a template visible to `party` |
| GET  | `/v1/contracts/lookup?party=...&contractId=...` | a single active contract, if visible |
| GET  | `/v1/transactions/stream?party=...&afterTransactionId=...` | Server-Sent-Events stream of committed transactions visible to `party` |
