# Backend (Spring Boot)

Java 17 / Spring Boot 3 REST API, in-memory Canton Ledger API simulator, and
event bus. See `../docs/ARCHITECTURE.md` and `../docs/API.md` for details on
the request lifecycle and full endpoint reference.

## Run locally

```bash
mvn spring-boot:run
```

The API listens on `http://localhost:8080` (`server.port`, overridable via
the `PORT` env var). Swagger UI: `http://localhost:8080/swagger-ui.html`.
Health check: `http://localhost:8080/actuator/health`.

## Run with Docker

```bash
docker build -t canton-backend .
docker run -p 8080:8080 canton-backend
```

## Deploying (e.g. Render)

This module includes a `Dockerfile` and binds to `${PORT:8080}`, so it can be
deployed to any container-based PaaS (Render, Railway, Fly.io, etc.).

### Render (free tier)

1. Push this repo to GitHub (already done).
2. In the Render dashboard: **New -> Web Service**, connect this repo.
3. Set **Root Directory** to `backend`.
4. Render should auto-detect the `Dockerfile`; if asked, choose **Docker**
   as the environment/runtime.
5. Leave **Health Check Path** as `/actuator/health` (optional but
   recommended).
6. Create the service. The free tier may take ~30-60s to "wake up" after
   idling (cold start) on the first request.
7. Once deployed, copy the public URL Render gives you (e.g.
   `https://canton-tokenization-backend.onrender.com`).

A `render.yaml` blueprint is also provided at the repo root if you prefer
**New -> Blueprint** instead of a manual web service.

### Wiring up the frontend

After the backend is deployed, set the `VITE_API_BASE_URL` repository
variable (Settings -> Secrets and variables -> Actions -> Variables) to the
backend's public URL, then re-run (or push to trigger) the
`.github/workflows/deploy-frontend.yml` workflow so the hosted frontend
points at the live backend. CORS is already configured (`WebConfig`) to
allow any origin, so the GitHub Pages frontend can call it directly.

## Production caveats

This backend uses an **in-memory** `CantonLedgerSimulator` and in-memory
event bus / idempotency cache -- all state is lost on restart, and there is
no authentication/authorization layer. See `../docs/ARCHITECTURE.md` for what
a real deployment would replace these with (a real Canton participant node,
a database, Kafka, and an auth layer).
