# Institutional Tokenization & Deposit Network (Canton-Inspired)

A demonstrable, production-style reference implementation of an institutional
**Tokenization Engine** + **Blockchain-Based Deposit Network**, modeled on
Canton Network / DAML concepts. It simulates capital-markets infrastructure
comparable in spirit to DTCC's tokenized securities pilots, Goldman Sachs'
Digital Asset Platform (GS DAP), and Broadridge's Distributed Ledger Repo
(DLR) platform — scaled down to a runnable, end-to-end demo.

> **Scope note:** This sandbox does not have the DAML SDK / Canton runtime
> installed. The `daml/` module contains real, syntactically-correct DAML
> templates and workflows as the canonical business-logic model. The
> `backend/` module implements a **Canton Ledger API simulator** — an
> in-memory ledger that mirrors DAML contract semantics (active contract set,
> command submission, transaction trees, event streaming) so the whole
> system runs end-to-end without Canton installed. See
> `docs/LEDGER_INTEGRATION.md` for how this maps to a real DAML/Canton
> deployment.

---

## High-Level Architecture

```mermaid
flowchart TB
    subgraph Frontend["Frontend (React + Vite)"]
        UI_Wallet[Wallet Dashboard]
        UI_Trade[Trading Desk]
        UI_Repo[Repo Desk]
        UI_Settle[Settlement Monitor]
    end

    subgraph Backend["Backend (Spring Boot)"]
        REST[REST API Layer]
        ORCH[Orchestration / Saga Services]
        EVT[Event Bus<br/>(Kafka-style, in-memory)]
        LEDGER[Ledger Gateway<br/>(Ledger API client)]
    end

    subgraph Integration["Integration Layer (Scala / Pekko)"]
        GW[Pekko HTTP Gateway<br/>IntegrationGateway]
        SCLIENT[CantonLedgerApiClient<br/>Scala Ledger API bindings style]
        SIM[Simulated Canton Ledger<br/>Active Contract Set + Tx Stream]
    end

    subgraph DAML["DAML Smart Contracts (Canton)"]
        KYC[KYC Service]
        WALLET[Custodial Wallet]
        ASSETS[Bond / Treasury / Deposit Tokens]
        TRADE[Trade Workflow]
        REPO[Repo / Reverse Repo]
    end

    UI_Wallet --> REST
    UI_Trade --> REST
    UI_Repo --> REST
    UI_Settle --> REST

    REST --> ORCH
    ORCH --> LEDGER
    ORCH <--> EVT
    LEDGER -.HTTP/JSON.-> GW
    GW --> SCLIENT
    SCLIENT --> SIM
    SIM -.maps to.-> KYC & WALLET & ASSETS & TRADE & REPO
```

> The Java backend's `ledger/CantonLedgerSimulator` is a self-contained,
> in-process simulator so the Spring Boot app runs standalone. The Scala
> `integration/` module is a **second, independent** simulator exposed over
> Pekko HTTP, demonstrating the pattern banks use when their Canton/Daml
> Ledger API integration is a separate Scala service (see
> `integration/README.md` and `docs/LEDGER_INTEGRATION.md`).

---

## Repository Structure

```
canton-tokenization-platform/
├── daml/                       # DAML smart contracts (source of truth for business logic)
│   ├── daml.yaml
│   └── daml/
│       ├── Roles/Types.daml          # Shared types, parties, enums
│       ├── KYC/KycService.daml       # KYC verification workflow
│       ├── Wallet/Wallet.daml        # KYC-gated custodial wallet
│       ├── Assets/Bond.daml          # GovernmentBond / TreasuryInstrument templates
│       ├── Assets/Deposit.daml       # TokenizedDeposit (bank liability) template
│       ├── Trading/Trade.daml        # Trade proposal / acceptance / DvP settlement
│       └── Repo/Repo.daml            # Repo & reverse repo lifecycle
│
├── backend/                    # Spring Boot backend (Ledger API integration + orchestration)
│   ├── pom.xml
│   └── src/main/java/com/canton/platform/
│       ├── PlatformApplication.java
│       ├── config/              # Beans, CORS, OpenAPI, async config
│       ├── domain/               # Java mirrors of DAML templates (contracts, choices)
│       ├── ledger/                # Canton Ledger API simulator (ACS, command processor)
│       ├── events/                # In-memory event bus (Kafka-style topics)
│       ├── service/               # Orchestration services (issuance, trade, repo, settlement)
│       ├── controller/            # REST controllers
│       ├── dto/                   # Request/response DTOs
│       └── exception/             # Error handling, idempotency, retry
│   └── src/main/resources/application.yml
│
├── frontend/                   # React + Vite UI
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── api/                  # REST client
│       ├── pages/                 # Wallet, Trading, Repo, Settlement
│       └── components/
│
├── integration/                 # Scala/Pekko Canton Ledger API integration layer
│   ├── build.sbt
│   ├── project/build.properties
│   └── src/
│       ├── main/scala/com/canton/integration/
│       │   ├── domain/           # Enums, Money, Contracts, LedgerCommands (mirrors DAML + Java domain)
│       │   ├── CantonLedgerApiClient.scala          # trait mirroring Daml Ledger API v1 (Scala bindings style)
│       │   ├── SimulatedCantonLedgerApiClient.scala # in-memory ACS + Pekko Streams tx feed
│       │   ├── JsonCodecs.scala
│       │   ├── gateway/IntegrationGateway.scala     # Pekko HTTP routes for the Java backend
│       │   └── Main.scala
│       ├── main/resources/       # application.conf, logback.xml
│       └── test/scala/...        # ScalaTest specs
│
├── docs/
│   ├── ARCHITECTURE.md
│   ├── LEDGER_INTEGRATION.md
│   └── API.md
│
└── .github/workflows/deploy-frontend.yml   # GitHub Pages deployment
```

---

## Core Concepts Modeled

| Concept | DAML Template | Notes |
|---|---|---|
| KYC verification | `KycService`, `KycApproval` | Regulator/KYC-provider approves a party before it can hold assets |
| Custodial wallet | `Wallet` | Signatory = owner + custodian; observers = regulator; holds asset/deposit/collateral references |
| Government bond | `GovernmentBond` | Issuance, transfer, collateralization, maturity/redemption |
| Treasury instrument | `TreasuryBill` | Discount instrument with maturity-based redemption value |
| Tokenized deposit | `TokenizedDeposit` | Bank liability token; transferable, used as DvP settlement leg |
| Trade | `TradeProposal` / `ExecutedTrade` | Buyer/seller propose-accept-settle (DvP) |
| Repo | `RepoAgreement` | Collateral lock + cash leg, interest accrual, repurchase, default handling |
| Reverse repo | modeled via `RepoAgreement` with swapped lender/borrower roles | Symmetric workflow |

Roles: **Buyer, Seller, Lender, Borrower, Custodian, Regulator, KYC Provider, Issuer**.
Privacy is enforced via DAML `signatory`/`observer`/`controller` annotations so
only relevant parties can see/act on a contract — mirrored in the backend by
party-scoped ledger views.

---

## Running the Demo

### Backend
```bash
cd backend
mvn spring-boot:run
# REST API on http://localhost:8080, Swagger UI on /swagger-ui.html
```

### Frontend
```bash
cd frontend
npm install
npm run dev
# UI on http://localhost:5173, points at backend on :8080
```

### DAML (requires DAML SDK locally)
```bash
cd daml
daml build
daml start   # launches Sandbox ledger + Navigator
```

### Integration layer (Scala/Pekko, requires sbt)
```bash
cd integration
sbt run    # Pekko HTTP gateway on http://localhost:8090
sbt test   # ScalaTest specs
```

---

## Why Scala *and* Java

Canton and Daml's official Ledger API client bindings are themselves written
in Scala, which is why many institutions run their core ledger-integration
service in Scala (`integration/`, using Pekko — an open-source Akka fork —
for gRPC-style streaming and HTTP) while building customer-facing REST APIs
and business orchestration in Java/Spring Boot (`backend/`). Both modules
share a structurally identical domain model (enums, `Money`, contract
payload shapes) so data crosses the language boundary cleanly. See
`integration/README.md` and `docs/LEDGER_INTEGRATION.md` for the full
rationale and the two supported integration modes (HTTP gateway vs.
in-process library).

---

## Production-Grade Practices Applied

- **Modular separation**: DAML (business logic) / backend (Java orchestration & API) / integration (Scala Ledger API client + Pekko HTTP gateway) / frontend (UX)
- **Immutability & contract-based state transitions** in DAML — every state change archives the old contract and creates a new one
- **Idempotency keys** on all command-submission endpoints
- **Retry with exponential backoff** for ledger command submission
- **Event-driven settlement**: trade execution / repo maturity emit domain events consumed by settlement & collateral-release services
- **Structured logging** (SLF4J + correlation IDs) and **Bean Validation** on all DTOs
- **OpenAPI/Swagger** documentation of REST surface
- **Role/privacy model** mirrored end-to-end: DAML signatories/observers → backend party-scoped views → frontend role-based UI

See `docs/ARCHITECTURE.md` and `docs/LEDGER_INTEGRATION.md` for details.
