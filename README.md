# Event Ledger

A two-service system for ingesting financial transaction events that may arrive **out of order** or **more than once**, applying them to account balances correctly and idempotently, with distributed tracing, observability, and resiliency between services.

## Architecture

```
                          ┌──────────────────────┐
Browser / Client ──────→  │  Event Gateway API    │
                          │  (public-facing)      │
                          └──────┬───────────────┘
                                 │ REST (sync)
                                 ▼
                          ┌──────────────────────┐
                          │  Account Service      │
                          │  (internal)           │
                          └──────────────────────┘
```

- **Event Gateway** (`gateway-service`, port `8080`) — public entry point. Validates incoming events, enforces idempotency, stores event records in its own H2 database, and calls the Account Service to apply transactions. Also exposes chronological event listings and a balance passthrough, both of which keep working even if the Account Service is unreachable.
- **Account Service** (`account-service`, port `8081`) — internal only, never called directly by clients. Owns account balances and transaction history in its own H2 database, independent of the Gateway's storage.

The two services are independently runnable processes with no shared database or in-process state; they communicate only over synchronous REST.

### Why the Gateway also exposes a balance endpoint

The spec's literal endpoint table doesn't list one, but requirement #6 (Graceful Degradation) expects "Balance queries" to return a clear error when the Account Service is down — and the Account Service is explicitly internal-only. So the Gateway adds a thin passthrough, `GET /accounts/{accountId}/balance`, protected by the same resiliency policy as event submission.

## Design Decisions

- **Idempotency (defense in depth)**: `eventId` is the dedup key on *both* services. The Gateway dedupes for the client-facing contract; the Account Service also dedupes so that a Gateway-side retry or replay can never double-apply a transaction. The Gateway persists an event as `PENDING`, calls the Account Service, and only marks it `APPLIED` on success — on failure the `PENDING` row is removed so the same `eventId` can be legitimately retried later instead of being stuck.
- **Out-of-order tolerance**: balance = sum(CREDIT) − sum(DEBIT), which is commutative — arrival order never affects the final balance, so no reordering/replay buffer is needed. The only ordering concern is presentation: `GET /events?account=` sorts by `eventTimestamp`, not insertion order.
- **Resiliency pattern — Circuit Breaker + Timeout (Resilience4j)**: chosen over blind retries because a financial ledger should fail fast and predictably rather than risk retry-induced duplicate side effects. Combined with server-side idempotency on the Account Service, this is safe, and fail-fast protects the Gateway's threads from a hung downstream dependency.
- **`/health` is a literal plain path** on each service (not `/actuator/health`), implemented as a small controller that pings its own database and reports status directly, matching the spec's endpoint table.

## API Reference

**Event Gateway** (`localhost:8080`)

| Method | Endpoint | Notes |
|---|---|---|
| `POST` | `/events` | 201 on first submission, 200 on a duplicate `eventId` (idempotent) |
| `GET` | `/events/{id}` | 404 if unknown |
| `GET` | `/events?account={accountId}` | Chronological by `eventTimestamp` |
| `GET` | `/accounts/{accountId}/balance` | Passthrough to Account Service |
| `GET` | `/health` | |

**Account Service** (`localhost:8081`, internal only)

| Method | Endpoint | Notes |
|---|---|---|
| `POST` | `/accounts/{accountId}/transactions` | 201 on first submission, 200 on a duplicate `eventId` |
| `GET` | `/accounts/{accountId}/balance` | Sum of CREDITs minus DEBITs; 0 for an account with no transactions |
| `GET` | `/accounts/{accountId}` | Balance + up to 20 most recent transactions |
| `GET` | `/health` | |

## Tech Stack

- Java 17, Spring Boot 3.3.4 (Web, Data JPA, Validation, Actuator)
- H2 (in-memory, one instance per service)
- Resilience4j (circuit breaker + timeout)
- Micrometer Tracing + OpenTelemetry bridge (trace propagation, log-only exporter)
- Logback + `logstash-logback-encoder` (structured JSON logs)
- JUnit 5, Spring Boot Test, WireMock

## Project Structure

```
event-ledger/
├── pom.xml                 # parent/reactor POM
├── gateway-service/        # public-facing Event Gateway API
├── account-service/        # internal Account Service
├── docker-compose.yml      # (added once both services are runnable)
└── README.md
```

## Setup

**Prerequisites**: JDK 17+, Maven 3.9+ (or use the Maven wrapper once added), Docker + Docker Compose (optional, for containerized run).

```bash
mvn -f pom.xml install
```

## Running the services

Manual (each in its own terminal, from the repo root):

```bash
mvn -f account-service/pom.xml spring-boot:run   # http://localhost:8081
mvn -f gateway-service/pom.xml spring-boot:run   # http://localhost:8080
```

Check both are up:

```bash
curl http://localhost:8081/health
curl http://localhost:8080/health
```

Docker Compose instructions will be added once `docker-compose.yml` lands (see Status below).

## Running tests

```bash
mvn -f pom.xml test
```

## Status

Build is in progress; this checklist tracks what's landed so far.

- [x] Maven multi-module scaffold (parent POM + `gateway-service` + `account-service`)
- [x] Walking skeleton: both services boot with `/health` + structured JSON logging
- [x] Account Service core (transactions, balance, idempotency)
- [x] Gateway happy path (events, idempotency, out-of-order listing) + integration test
- [x] Graceful degradation baseline
- [ ] Resiliency: circuit breaker + timeout + metrics
- [ ] Distributed tracing (trace ID propagation, structured logs)
- [ ] Docker Compose
- [ ] Final pass
