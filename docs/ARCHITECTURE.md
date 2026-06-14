# Architecture

This document describes how the four modules fit together, the data flow for
a representative end-to-end transaction, the privacy model, and the
production-grade practices applied throughout the codebase.

## Modules at a glance

| Module | Language / stack | Role |
|---|---|---|
| `daml/` | DAML (Canton) | Canonical business logic: contract templates, choices, signatories/observers/controllers. Source of truth for every workflow. |
| `backend/` | Java 17, Spring Boot 3 | Customer-facing REST API, orchestration services, in-memory Canton Ledger API simulator, event bus. |
| `integration/` | Scala 2.13, Pekko | Standalone Ledger API client + HTTP gateway, mirroring how banks run their Canton/Daml integration tier in Scala, independent of the Java backend. |
| `frontend/` | React 18 + Vite | Operator UI: KYC, wallets, asset/deposit issuance, DvP trading desk, repo desk, settlement monitor. Deployed to GitHub Pages. |

## Request lifecycle (example: DvP trade settlement)

1. **Frontend** (`TradingPage.jsx`) submits `POST /api/trades/{tradeId}/settle`
   with an `Idempotency-Key` header and `X-Correlation-Id` propagated back in
   the response.
2. **`TradeController`** delegates to **`CommandExecutor`**, which:
   - Checks `IdempotencyService` for a previously-recorded result for this
     key; if found, replays it without re-executing side effects.
   - Wraps the call in `RetryService` (exponential backoff) to tolerate
     transient ledger-submission failures.
   - Sets the correlation id in the SLF4J MDC (`CorrelationIdFilter`) so every
     log line for this request can be traced.
3. **`TradeService.acceptAndSettle`** performs the DvP settlement as a single
   synchronized operation against **`CantonLedgerSimulator`**:
   - Archives the `TradeProposal` contract.
   - Archives the seller's instrument holding and the buyer's cash deposit.
   - Creates new `TokenizedAsset`/`TokenizedDeposit` contracts for the new
     owners (the "settled" instrument/deposit ids supplied by the caller),
     and creates the `ExecutedTrade` record.
   - Each create/archive is appended to the ledger's transaction stream as a
     `LedgerEvent`, scoped to the contract's `witnessParties`.
4. **`EventBus`** publishes a `TradeExecutedEvent` (topic `trade.executed`).
   `SettlementEventHandler` (an async `@EventListener`) reacts to it — in a
   full deployment this is where confirmation messages, regulatory reporting,
   or downstream repo/collateral workflows would be triggered.
5. **Frontend** settlement monitor (`SettlementMonitorPage.jsx`) polls
   `/api/transactions/party/{party}` and `/api/transactions/events/recent` to
   show the resulting `CREATED`/`ARCHIVED` ledger events and the
   `trade.executed` domain event side-by-side.

The repo lifecycle (`propose -> accept -> [mature] -> repurchase | default`)
and KYC/wallet flows follow the same pattern: `Controller -> CommandExecutor
-> Service -> CantonLedgerSimulator (+ EventBus)`.

## Privacy / visibility model

DAML enforces visibility via `signatory` and `observer` annotations on each
template — a party can only see a contract if it appears in one of those
sets. This is mirrored end-to-end:

- **`CantonLedgerSimulator`** stores, alongside each contract, the set of
  `witnessParties` (signatories ∪ observers). `getTransactionStream(party)`
  and the `*ForParty` service methods filter the active contract set and
  transaction log to only what that party is a witness to.
- **Backend services** (`WalletService.getWalletsForParty`,
  `TradeService.getProposalsForParty`, `RepoService.*ForParty`, etc.) apply
  the same filter before returning DTOs.
- **Frontend** has no privileged access of its own: it always queries
  `/api/.../party/{party}` for the currently "Acting as" party (see
  `PartyContext.jsx`), so the UI only ever shows what that party is entitled
  to see — a lightweight simulation of per-participant Ledger API views.
- The **Scala integration module** (`integration/`) implements the identical
  visibility rule in `SimulatedCantonLedgerApiClient.isWitness` /
  `visibleTo`, independently of the Java implementation, since it runs its
  own Active Contract Set.

## Event-driven architecture

`backend/.../events/` implements a small in-memory event bus
(`EventBus`) that stands in for a Kafka topic:

- **Topics** (`Topics.java`): `kyc.status-changed`, `trade.executed`,
  `repo.opened` / `repo.matured` / `repo.closed`,
  `collateral.locked` / `collateral.released`.
- **Producers**: services publish a `DomainEvent` record (e.g.
  `TradeExecutedEvent`, `RepoLifecycleEvent`, `CollateralEvent`,
  `KycStatusChangedEvent`) after a successful ledger command.
- **Consumers**: `SettlementEventHandler` and `RepoMaturityEventHandler`
  subscribe via `@EventListener` + `@Async` (configured in
  `AsyncConfig`/`ThreadPoolTaskExecutor`), decoupling settlement/maturity
  side effects from the originating HTTP request.
- **History / replay**: `EventBus.recentHistory(limit)` retains a bounded
  in-memory history, exposed via `GET /api/transactions/events/recent` for
  the settlement monitor UI — analogous to a consumer re-reading recent
  offsets from a Kafka topic.

In a production deployment, `EventBus` would be replaced by a real Kafka (or
Pulsar) producer/consumer pair with the same `DomainEvent` payloads, and
`CantonLedgerSimulator` would be replaced by a real Ledger API client (see
`docs/LEDGER_INTEGRATION.md`) whose transaction stream is itself often
bridged onto Kafka for downstream consumers.

## Idempotency & retry

- Every mutating endpoint accepts an optional `Idempotency-Key` header.
  `CommandExecutor` + `IdempotencyService` record `(key -> result)` so a
  retried request (e.g. a network timeout followed by a client retry) is
  answered from the cache rather than re-executing the ledger command —
  mirroring the Ledger API's own command-deduplication guarantee
  (`commandId` + `applicationId` + `actAs`).
- `RetryService` wraps ledger command execution with exponential backoff for
  transient failures (`LedgerException`), so a momentary contention error on
  the in-memory ACS does not surface as a hard failure to the caller.
- `Correlation.idOrNew` ensures every request has a correlation id — either
  client-supplied (`X-Correlation-Id` / request body field) or freshly
  generated — propagated through the MDC, the `LedgerEvent`/`DomainEvent`
  records, and back to the client via the `X-Correlation-Id` response header
  (see `CorrelationIdFilter`).

## Production-grade practices

- **Modular separation** of business logic (DAML), orchestration/API (Java),
  ledger-integration tier (Scala), and UI (React).
- **Immutability**: every DAML workflow step archives the prior contract and
  creates a new one; the Java domain mirrors this with immutable records and
  `@With`-style copy helpers rather than mutation.
- **Bean Validation** (`jakarta.validation`) on every request DTO.
- **Structured logging** via SLF4J with correlation-id MDC.
- **OpenAPI/Swagger** (`springdoc-openapi`) documents the full REST surface
  at `/swagger-ui.html`.
- **Idempotency + retry** as described above.
- **Role/privacy model** mirrored from DAML signatories/observers through to
  party-scoped REST responses and a frontend "Acting as" party switcher.
