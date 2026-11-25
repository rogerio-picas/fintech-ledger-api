# Banking Ledger & Transfer API (`ledger-api`)

A high-concurrency, ACID-compliant REST API for core banking ledger operations, built with **Java 21**, **Spring Boot 4.2.0**, **Flyway**, and **PostgreSQL 16**.

Designed around production fintech invariants, this service provides exact `BigDecimal` arithmetic, deadlock-free pessimistic row locking, atomic request idempotency, an append-only audit trail enforced at both the application and database layers, and safe compensating reversals.

---

## Key Engineering & Fintech Features

* **Exact Monetary Precision:** All balances and transaction amounts use `java.math.BigDecimal` mapped to PostgreSQL `NUMERIC(19, 4)`. `Money.normalize()` enforces scale `4` with `RoundingMode.UNNECESSARY` (rejecting inputs with more than 4 fractional digits rather than silently rounding). Comparisons strictly use `BigDecimal.compareTo()`, and a database `CHECK (balance >= 0)` constraint guards against overdrafts.
* **Concurrency & Deadlock Prevention:** All balance mutations run inside `@Transactional` boundaries using PostgreSQL row-level locks (`SELECT ... FOR UPDATE` via `@Lock(LockModeType.PESSIMISTIC_WRITE)` in `AccountRepository.findByIdForUpdate` and `TransactionRepository.findByIdForUpdate`). Accounts are always locked in deterministic ascending `UUID` order (`a.compareTo(b) <= 0`) to eliminate deadlocks under simultaneous opposing transfers.
* **Atomic Idempotent Transfers:** `POST /api/transfers` requires a non-blank `Idempotency-Key` header (max 255 characters). `IdempotencyRecordRepository.tryClaim()` executes `INSERT ... ON CONFLICT (idempotency_key) DO NOTHING` so concurrent duplicate requests serialize cleanly at the database level. Payloads are fingerprinted via canonical SHA-256 (`sourceAccountId|targetAccountId|normalizedAmount`, so `100` and `100.00` hash identically). Replays return the original status code and JSON payload with `Idempotent-Replayed: true` (initial executions return `Idempotent-Replayed: false`). Insufficient-funds failures (`422`) commit the `FAILED` transaction, `TRANSFER_FAILED` audit row, and cached `422` response via `@Transactional(noRollbackFor = InsufficientFundsException.class)`. Reusing an `Idempotency-Key` with a different payload returns `422`.
* **Four-Layer Append-Only Audit Trail:** Immutability of `audit_logs` is enforced at every layer:
  1. `AuditLog` exposes no setters, marks all columns `updatable = false`, and is annotated with Hibernate `@Immutable`.
  2. `AuditLogRepository` extends Spring Data's bare `Repository` interface without `delete` or `update` methods.
  3. Every movement records `previous_balance`, `new_balance`, `event_type` (`DEBIT`, `CREDIT`, `TRANSFER_FAILED`, `REVERSAL_DEBIT`, `REVERSAL_CREDIT`), and `transaction_id`.
  4. PostgreSQL trigger `trg_audit_logs_append_only` rejects any `UPDATE` or `DELETE` on `audit_logs`.
* **Safe Compensating Reversals:** `POST /api/transfers/{id}/reverse` locks the original transaction row to serialize concurrent reversal attempts, verifies it is `COMPLETED` and not itself a reversal, locks both accounts in `UUID` order, checks that the original target has sufficient funds, marks the original transaction `REVERSED`, persists a new compensating `COMPLETED` transaction with `reversal_of_id` (protected by unique partial index `uk_transactions_reversal_of`), and appends `REVERSAL_DEBIT` and `REVERSAL_CREDIT` audit rows without mutating historical records.

---

## Tech Stack

* **Language:** Java 21 (`record` DTOs, text blocks, `java.net.http.HttpClient`)
* **Framework:** Spring Boot 4.2.0 (Web MVC, Spring Data JPA, Jakarta Validation, Jackson 3 `JsonMapper`)
* **API Docs / Explorer:** `springdoc-openapi` 3.2.1 (OpenAPI 3 & Swagger UI at `/swagger-ui.html`)
* **Database:** PostgreSQL 16 (`postgres:16-alpine` via Docker Compose)
* **Migrations:** Flyway (`spring-boot-starter-flyway`, `flyway-database-postgresql`) with `spring.jpa.hibernate.ddl-auto: validate`
* **Testing:** JUnit 5, AssertJ, Spring Boot Testcontainers (`testcontainers-postgresql`)
* **Build Tool:** Maven Wrapper (`./mvnw` / `mvnw.cmd`)

---

## Project Structure & Architecture

```text
src/
├── main/
│   ├── java/com/rpicas/ledgerapi/
│   │   ├── LedgerApiApplication.java
│   │   ├── config/
│   │   │   ├── DemoDataSeeder.java
│   │   │   └── OpenApiConfig.java
│   │   ├── domain/
│   │   │   ├── Account.java
│   │   │   ├── AuditEventType.java
│   │   │   ├── AuditLog.java
│   │   │   ├── IdempotencyRecord.java
│   │   │   ├── Money.java
│   │   │   ├── Transaction.java
│   │   │   └── TransactionStatus.java
│   │   ├── repository/
│   │   │   ├── AccountRepository.java
│   │   │   ├── AuditLogRepository.java
│   │   │   ├── IdempotencyRecordRepository.java
│   │   │   └── TransactionRepository.java
│   │   ├── service/
│   │   │   ├── AccountService.java
│   │   │   ├── IdempotentResponse.java
│   │   │   ├── RequestHasher.java
│   │   │   └── TransferService.java
│   │   ├── web/
│   │   │   ├── AccountController.java
│   │   │   ├── TransferController.java
│   │   │   └── dto/
│   │   │       ├── AccountResponse.java
│   │   │       ├── ApiError.java
│   │   │       ├── AuditLogResponse.java
│   │   │       ├── CreateAccountRequest.java
│   │   │       ├── TransferRequest.java
│   │   │       └── TransferResponse.java
│   │   └── exception/
│   │       ├── AccountNotFoundException.java
│   │       ├── BadRequestException.java
│   │       ├── BusinessRuleException.java
│   │       ├── ConflictException.java
│   │       ├── GlobalExceptionHandler.java
│   │       ├── InsufficientFundsException.java
│   │       ├── ResourceNotFoundException.java
│   │       └── TransactionNotFoundException.java
│   └── resources/
│       ├── application.yml
│       └── db/migration/
│           └── V1__init_ledger_schema.sql
└── test/
    └── java/com/rpicas/ledgerapi/
        ├── AbstractIntegrationTest.java
        ├── LedgerApiApplicationTests.java
        ├── TestcontainersConfiguration.java
        ├── TransferApiIntegrationTest.java
        └── TransferConcurrencyIntegrationTest.java
```

---

## Database Schema (Flyway `V1__init_ledger_schema.sql`)

Schema ownership belongs to Flyway (`ddl-auto: validate` in `application.yml`). All monetary columns are `NUMERIC(19, 4)` and timestamps are `TIMESTAMPTZ` in UTC:

1. **`accounts`**: Stores `id` (`UUID` PK), `owner_name`, `iban` (`uk_accounts_iban`), `balance` (`ck_accounts_balance_non_negative`: `balance >= 0`), and `created_at`.
2. **`transactions`**: Stores `id` (`UUID` PK), `source_account_id`, `target_account_id`, `amount` (`ck_transactions_amount_positive`: `amount > 0`), `status` (`ck_transactions_status`: `COMPLETED`, `FAILED`, `REVERSED`), `reversal_of_id`, and `created_at`. Enforces `ck_transactions_distinct_accounts` (`source_account_id <> target_account_id`) and unique partial index `uk_transactions_reversal_of` (`WHERE reversal_of_id IS NOT NULL`) so a transaction can be reversed at most once.
3. **`idempotency_records`**: Stores `idempotency_key` (`VARCHAR(255)` PK), `request_hash` (`VARCHAR(64)` SHA-256), `response_payload` (`TEXT`), `status_code` (`INTEGER`), and `created_at`.
4. **`audit_logs`**: Append-only table storing `id` (`BIGSERIAL` PK), `transaction_id`, `account_id`, `event_type`, `previous_balance`, `new_balance`, and `created_at`. Protected by `trg_audit_logs_append_only` (`BEFORE UPDATE OR DELETE`).

---

## Getting Started & Configuration

### Prerequisites

* **Java 21+**
* **Docker & Docker Compose**

### Default Local Configuration (`application.yml` & `docker-compose.yml`)

| Setting | Value |
| :--- | :--- |
| Database | `ledger_db` (`postgres:16-alpine`) |
| JDBC URL | `jdbc:postgresql://localhost:5432/ledger_db` |
| Credentials | `ledger_user` / `ledger_pass` |
| HikariCP Pool Size | `20` (`ledger-hikari`) |
| JPA `open-in-view` | `false` |
| Hibernate Time Zone | `UTC` |
| Hibernate DDL Auto | `validate` (managed by Flyway) |

### 1. Start PostgreSQL

```bash
docker compose up -d
```

### 2. Run the Application

**Standard (empty database):**
```bash
./mvnw spring-boot:run
```

**With Sample Demo Data (`demo` profile — recommended for interactive exploration):**

Unix / macOS:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Windows (PowerShell):
```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
```

When the `demo` profile is active on an empty database, `DemoDataSeeder` creates three sample accounts (`Alice Silva`, `Bob Santos`, `Charlie Mendes`) and executes an initial `150.0000` transfer so accounts, transactions, audit logs, and idempotency records are immediately populated.

### 3. Explore Interactively in Swagger UI

Open `http://localhost:8080` (automatically redirects to `http://localhost:8080/swagger-ui.html`). OpenAPI JSON is also available at `http://localhost:8080/v3/api-docs`.

Suggested walkthrough inside Swagger UI:
1. **`GET /api/accounts`** — copy two account `id`s (e.g., Alice `850.0000` and Bob `650.0000`).
2. **`POST /api/transfers`** — transfer `100.0000` with an `Idempotency-Key` (e.g., `demo-key-1`). Observe `201 Created` and response header `Idempotent-Replayed: false`.
3. **Replay the exact `POST /api/transfers`** — click *Execute* again with the same `Idempotency-Key` and body. Observe the cached `201` payload and `Idempotent-Replayed: true` (balances do not move twice).
4. **`POST /api/transfers/{id}/reverse`** — paste the transaction `id` to execute a compensating reversal (`201 Created`).
5. **`GET /api/accounts/{id}/audit`** — inspect the chronological append-only ledger (`DEBIT` -> `REVERSAL_CREDIT`) verifying historical rows were never mutated.

### 4. Run Integration & Concurrency Tests

Integration tests use Spring Boot Testcontainers to launch an ephemeral `postgres:16-alpine` container automatically—Docker must be running, and no manual `docker compose up` is required for the test suite.

**Unix / macOS:**
```bash
./mvnw verify
```

**Windows (PowerShell):**
```powershell
.\mvnw.cmd verify
```

*(You can also run `./mvnw test` / `.\mvnw.cmd test`.)*

---

## API Reference & Examples

### Endpoints Summary

| Method | Path | Description | Success Status |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/accounts` | Create an account (`iban` must match `^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$`, `initialBalance >= 0`) | `201 Created` |
| `GET` | `/api/accounts` | List all accounts ordered by creation time | `200 OK` |
| `GET` | `/api/accounts/{id}` | Fetch account details and current balance | `200 OK` |
| `GET` | `/api/accounts/{id}/audit` | Inspect the immutable append-only audit trail for an account | `200 OK` |
| `POST` | `/api/transfers` | Execute an idempotent transfer (requires `Idempotency-Key` header; returns `Idempotent-Replayed` header) | `201 Created` |
| `GET` | `/api/transfers` | List all transfers and reversals ordered newest first | `200 OK` |
| `GET` | `/api/transfers/{id}` | Fetch a transaction by ID | `200 OK` |
| `POST` | `/api/transfers/{id}/reverse` | Reverse a `COMPLETED` transfer via compensating transaction | `201 Created` |

### `curl` Examples

#### Create an Account
```bash
curl -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" \
  -d '{
    "ownerName": "Rogério Picas",
    "iban": "PT50000201231234567890154",
    "initialBalance": 1000.0000
  }'
```

#### List Accounts
```bash
curl http://localhost:8080/api/accounts
```

#### Get an Account
```bash
curl http://localhost:8080/api/accounts/<ACCOUNT_UUID>
```

#### Get Account Audit Trail
```bash
curl http://localhost:8080/api/accounts/<ACCOUNT_UUID>/audit
```

#### Execute an Idempotent Transfer
```bash
curl -i -X POST http://localhost:8080/api/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{
    "sourceAccountId": "<SOURCE_UUID>",
    "targetAccountId": "<TARGET_UUID>",
    "amount": 150.5000
  }'
```

#### List Transfers
```bash
curl http://localhost:8080/api/transfers
```

#### Get a Transfer
```bash
curl http://localhost:8080/api/transfers/<TRANSACTION_UUID>
```

#### Reverse a Transfer
```bash
curl -X POST http://localhost:8080/api/transfers/<TRANSACTION_UUID>/reverse
```

---

## Error Handling

All errors are handled centrally by `GlobalExceptionHandler` and return a uniform `ApiError` JSON body without exposing internal stack traces or SQL messages:

```json
{
  "timestamp": "2026-11-25T23:00:00Z",
  "status": 422,
  "error": "Unprocessable Content",
  "message": "Insufficient funds in account ... for amount 100.0000",
  "path": "/api/transfers"
}
```

*(Bean validation failures also include a `fieldErrors` map.)*

| HTTP Status | Condition |
| :--- | :--- |
| `400 Bad Request` | Validation failure (invalid IBAN, non-positive amount, >4 decimal places), missing/blank or oversized `Idempotency-Key`, malformed JSON, or invalid path parameter type |
| `404 Not Found` | Account ID, transaction ID, or endpoint does not exist |
| `409 Conflict` | Transaction already reversed, duplicate IBAN, concurrent in-flight request with the same `Idempotency-Key`, data integrity violation, or pessimistic lock contention |
| `422 Unprocessable Content` | Insufficient funds (on transfer or reversal), same source and target account, `Idempotency-Key` reused with a different payload, or attempting to reverse a `FAILED` transaction or an existing reversal |
| `405` / `415` / `500` | Unsupported HTTP method/media type or unexpected server error (safe generic message) |

---

## Test Suite Overview

* **`TransferConcurrencyIntegrationTest`**:
  * *20 parallel transfers against balance for 5*: Fires 20 simultaneous `100.00` transfers against an account holding `500.0000`; verifies exactly `5` return `201` and `15` return `422`, final balances are `0.0000` and `500.0000`, and `transactions` / `audit_logs` counts match (`5 COMPLETED`, `15 FAILED`, `5 DEBIT`, `5 CREDIT`, `15 TRANSFER_FAILED`).
  * *Concurrent identical `Idempotency-Key`*: Fires 10 parallel requests with the same key; verifies all 10 receive `201` with identical response bodies, exactly one has `Idempotent-Replayed: false`, and funds move only once.
  * *Opposing bidirectional transfers*: Fires 20 simultaneous `A -> B` and `B -> A` transfers; verifies deterministic `UUID` lock ordering prevents deadlocks and conserves total balances.
* **`TransferApiIntegrationTest`**:
  * Verifies idempotent response replay (including equivalent numeric scales like `100` vs `100.00`), caching of `422` insufficient-funds responses, `422` rejection when reusing a key with a different payload, transfer reversals (balance restoration, append-only `REVERSAL_DEBIT`/`REVERSAL_CREDIT` entries without modifying historical audit rows, `409` on double reversal, `422` on reversing a reversal), and `400`/`404`/`422` error status mappings.
* **`LedgerApiApplicationTests`**:
  * Verifies full Spring application context startup and Flyway migration against PostgreSQL.
