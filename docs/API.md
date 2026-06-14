# REST API Reference

Base URL: `http://localhost:8080` (configurable via `VITE_API_BASE_URL` for
the frontend). Interactive docs: `GET /swagger-ui.html` (springdoc-openapi).

## Conventions

- **Idempotency**: every mutating (`POST`) endpoint accepts an optional
  `Idempotency-Key` header. If supplied and a prior request with the same key
  already succeeded, the cached result is returned without re-executing the
  ledger command. The frontend client (`frontend/src/api/client.js`) always
  generates one via `crypto.randomUUID()`.
- **Correlation ids**: every response includes an `X-Correlation-Id` header.
  Some request bodies also accept an optional `correlationId` field to
  propagate a caller-supplied id through to `LedgerEvent`/`DomainEvent`
  records.
- **Errors**: non-2xx responses return an `ErrorResponse` JSON body
  (`{ "message": "...", ... }`) handled by `GlobalExceptionHandler`. Typical
  status codes: `400` (validation / `InvalidStateTransitionException`), `403`
  (`UnauthorizedActionException` / `KycNotApprovedException`), `404`
  (`ResourceNotFoundException`), `409` (`IdempotencyConflictException` /
  `InsufficientBalanceException`).
- **Money**: represented as `{ "currency": "USD", "amount": 1000.00 }`.
- **Dates**: ISO-8601 (`YYYY-MM-DD` for `LocalDate`, full timestamps for
  `Instant`).

---

## KYC — `/api/kyc` (mirrors `KYC.KycService`)

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/approve` | `KycApproveRequest { applicant, kycProvider, regulator, legalName, jurisdiction, riskRating: LOW\|MEDIUM\|HIGH, correlationId? }` | `KycApproval` |
| `POST` | `/reject` | `KycRejectRequest { applicant, kycProvider, regulator, legalName, jurisdiction, reason, correlationId? }` | `KycApproval` (status `REJECTED`) |
| `POST` | `/revoke` | `KycRevokeRequest { applicant, reason, correlationId? }` | `KycApproval` (status `REVOKED`) |
| `GET` | `/{applicant}` | – | `KycApproval` (404 if none) |

`KycApproval` fields: `applicant, kycProvider, regulator, legalName,
jurisdiction, riskRating, status: PENDING\|APPROVED\|REJECTED\|REVOKED,
approvedAt`.

---

## Wallets — `/api/wallets` (mirrors `Wallet.Wallet`)

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `` | `WalletOpenRequest { owner, custodian, regulator, kycProvider, walletId }` | `Wallet` (201). Requires `owner` to hold an `APPROVED` `KycApproval`. |
| `GET` | `/{walletId}` | – | `Wallet` |
| `GET` | `/party/{party}` | – | `Wallet[]` — wallets where `party` is owner, custodian, or regulator |

`Wallet` fields: `walletId, owner, custodian, regulator,
holdings: WalletHolding[], cashBalances: Money[]`. `WalletHolding` fields:
`instrumentId, assetClass, quantity, locked`.

---

## Assets & Deposits — `/api/assets` (mirrors `Assets.Bond`, `Assets.Deposit`)

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/issue` | `AssetIssueRequest { issuer, owner, custodian, regulator, walletId, assetClass: GOVERNMENT_BOND\|TREASURY_BILL, instrumentId, isin, currency, faceValuePerUnit, quantity, couponRatePct?, purchasePricePerUnit?, issueDate, maturityDate }` | `TokenizedAsset` (201) |
| `GET` | `/{instrumentId}` | – | `TokenizedAsset` |
| `GET` | `/owner/{owner}` | – | `TokenizedAsset[]` |
| `POST` | `/deposits/issue` | `DepositIssueRequest { bank, owner, regulator, walletId, depositId, currency, amount }` | `TokenizedDeposit` (201) |
| `GET` | `/deposits/{depositId}` | – | `TokenizedDeposit` |
| `GET` | `/deposits/owner/{owner}` | – | `TokenizedDeposit[]` |

`TokenizedAsset` fields: `instrumentId, isin, assetClass, issuer, owner,
custodian, regulator, currency, faceValuePerUnit, quantity, couponRatePct,
purchasePricePerUnit, issueDate, maturityDate, status:
ACTIVE\|LOCKED\|MATURED\|REDEEMED`.

`TokenizedDeposit` fields: `depositId, bank, owner, regulator, currency,
amount, frozen, status`.

For `GOVERNMENT_BOND`, `couponRatePct` is required (coupon-bearing).
For `TREASURY_BILL`, `purchasePricePerUnit` is required (discount instrument,
redeemed at `faceValuePerUnit` on maturity); if omitted it defaults to
`faceValuePerUnit`.

---

## Trades — `/api/trades` (mirrors `Trading.Trade`, DvP settlement)

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/propose` | `TradeProposeRequest { seller, buyer, regulator, tradeId, assetClass, instrumentId, quantity, price: Money }` | `TradeProposal` (201, status `PROPOSED`) |
| `POST` | `/{tradeId}/settle` | `TradeSettleRequest { buyer, sellerWalletId, buyerWalletId, settledInstrumentId, settledDepositId, correlationId? }` | `ExecutedTrade` — atomic DvP: instrument moves seller→buyer, cash moves buyer→seller |
| `POST` | `/{tradeId}/reject` | `TradeRejectRequest { buyer, reason }` | `TradeProposal` (status `REJECTED`) |
| `POST` | `/{tradeId}/cancel` | `TradeCancelRequest { seller }` | `TradeProposal` (status `CANCELLED`) |
| `GET` | `/{tradeId}` | – | `TradeProposal` |
| `GET` | `/{tradeId}/executed` | – | `ExecutedTrade` |
| `GET` | `/party/{party}/proposals` | – | `TradeProposal[]` (status `PROPOSED`, visible to `party`) |
| `GET` | `/party/{party}/executed` | – | `ExecutedTrade[]` visible to `party` |

`TradeProposal` fields: `tradeId, seller, buyer, regulator, assetClass,
instrumentId, quantity, price: Money, status: PROPOSED\|REJECTED\|CANCELLED\|SETTLED`.
`ExecutedTrade` fields: `tradeId, seller, buyer, instrumentId, quantity,
price: Money, settledAt`.

Settlement publishes a `trade.executed` domain event (`TradeExecutedEvent`).

---

## Repos — `/api/repos` (mirrors `Repo.Repo`)

`propose -> accept (atomic DvP) -> [mature] -> repurchase | default`

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/propose` | `RepoProposeRequest { borrower, lender, regulator, repoId, direction: REPO\|REVERSE_REPO, collateralInstrumentId, collateralQty, principal: Money, repoRatePct, startDate, maturityDate }` | `RepoProposal` (201) |
| `POST` | `/{repoId}/accept` | `RepoAcceptRequest { accepter, borrowerWalletId, lenderWalletId, settledCollateralInstrumentId, settledCashDepositId, correlationId? }` | `RepoAgreement` — collateral locked with lender, principal disbursed to borrower |
| `POST` | `/{repoId}/mature` | `RepoMatureRequest { asOfDate, correlationId? }` | `RepoAgreement` (status `MATURED`) |
| `POST` | `/{repoId}/repurchase` | `RepoRepurchaseRequest { borrower, asOfDate, repaymentDepositId, returnedInstrumentId, borrowerWalletId, lenderWalletId, correlationId? }` | `RepoClosedRecord` (outcome `REPURCHASED`) |
| `POST` | `/{repoId}/default` | `RepoDefaultRequest { lender, asOfDate, releasedInstrumentId, lenderWalletId, correlationId? }` | `RepoClosedRecord` (outcome `DEFAULTED`) |
| `GET` | `/{repoId}/quote?asOfDate=YYYY-MM-DD` | – | `RepoQuoteResponse { repoId, asOfDate, repurchaseAmount }` |
| `GET` | `/{repoId}/proposal` | – | `RepoProposal` |
| `GET` | `/{repoId}/agreement` | – | `RepoAgreement` |
| `GET` | `/{repoId}/closed` | – | `RepoClosedRecord` |
| `GET` | `/party/{party}/proposals` | – | `RepoProposal[]` |
| `GET` | `/party/{party}/agreements` | – | `RepoAgreement[]` |
| `GET` | `/party/{party}/closed` | – | `RepoClosedRecord[]` |

`RepoProposal`/`RepoAgreement` fields: `repoId, direction, borrower, lender,
regulator, collateralInstrumentId, collateralQty, principal: Money,
repoRatePct, startDate, maturityDate, status:
PROPOSED\|OPEN\|MATURED\|REPURCHASED\|DEFAULTED`.
`RepoClosedRecord` fields: `repoId, borrower, lender, principal: Money,
interestPaid, closedAt, outcome: REPURCHASED\|DEFAULTED`.

Acceptance, maturity and closure publish `repo.opened` / `repo.matured` /
`repo.closed` and `collateral.locked` / `collateral.released` domain events
(`RepoLifecycleEvent`, `CollateralEvent`).

Interest accrual uses an actual/360 day-count: `interest = principal *
(repoRatePct / 100) * (days / 360)`; `repurchaseAmount = principal +
interest`.

---

## Transactions / Settlement Monitor — `/api/transactions`

| Method | Path | Returns |
|---|---|---|
| `GET` | `/party/{party}` | `LedgerEvent[]` — `CREATED`/`ARCHIVED` ledger transaction stream visible to `party`, newest first |
| `GET` | `/audit` | `LedgerEvent[]` — full transaction stream (regulator/audit view, all parties) |
| `GET` | `/events/recent?limit=50` | `DomainEvent[]` — recent events from the in-memory event bus |

`LedgerEvent` fields: `eventId, type: CREATED\|ARCHIVED, templateId,
contractId, payload, witnessParties: string[], effectiveAt`.

`DomainEvent` is a sealed family; every variant has `topic, occurredAt,
correlationId` plus variant-specific fields:

| Topic | Record | Extra fields |
|---|---|---|
| `kyc.status-changed` | `KycStatusChangedEvent` | `applicant, status` |
| `trade.executed` | `TradeExecutedEvent` | `tradeId, seller, buyer, instrumentId` |
| `repo.opened` / `repo.matured` / `repo.closed` | `RepoLifecycleEvent` | `repoId, borrower, lender, detail` |
| `collateral.locked` / `collateral.released` | `CollateralEvent` | `walletId, instrumentId, quantity` |

---

## Integration gateway — `/v1/*` (Scala/Pekko, port 8090, independent service)

See `integration/README.md` for the full endpoint list
(`/v1/health`, `/v1/commands/submit`, `/v1/contracts/active`,
`/v1/contracts/lookup`, `/v1/transactions/stream`). This is a separate
service from the Java backend's `/api/*` surface above — see
`docs/LEDGER_INTEGRATION.md` for how the two relate.
