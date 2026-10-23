# Trade Settlement & Invoice Reconciliation System

A production-style **Spring Boot 3 / Java 21** backend for commodity trading back
offices: trade capture, invoice generation, payment tracking, settlement
reconciliation, overdue monitoring and an append-only audit trail.

Every monetary value is computed with `BigDecimal` (never `float`/`double`), every
business rule is enforced in a service layer, and every financially significant
operation is written to a tamper-evident audit log.

---

## 1. Feature overview

| Module | What it does |
| --- | --- |
| **Trade management** | Register, amend, search and life-cycle commodity trades (DRAFT → CONFIRMED → COMPLETED / CANCELLED). Only `COMPLETED` trades can be invoiced. |
| **Invoice generation** | Generate invoices from completed trades with a full snapshot of the trade, configurable tax rate, additional charges and discounts. Issue, cancel, print and inspect outstanding balances. |
| **Payment tracking & settlement** | Record incoming/outgoing payments, complete, fail and reverse them. The invoice balance is always recomputed from net **completed** payments. |
| **Invoice reconciliation** | Compare expected vs actual settled amounts, classify the variance (`MATCHED`, `UNDERPAID`, `OVERPAID`, `UNMATCHED`, `REVIEW_REQUIRED`) against a configurable tolerance and resolve discrepancies. |
| **Overdue alerts** | A scheduled monitor raises `OVERDUE_INVOICE` and `UPCOMING_DUE` alerts with escalating severity; alerts can be acknowledged. Duplicate alerts are prevented by a database unique constraint. |
| **Audit history** | Append-only `audit_logs` for every create/update/status change/payment/reconciliation, with secrets redacted before persistence. |
| **Dashboard** | Aggregated counters and currency-aware totals for finance and audit users. |
| **Security** | JWT (HS256) authentication with role-based authorization: `ADMIN`, `FINANCE`, `TRADER`, `AUDITOR`. |

### Money formulas

```
Subtotal   = Quantity × UnitPrice
Tax        = Subtotal × TaxPercentage / 100
Total      = Subtotal + Tax + AdditionalCharges − Discount
Outstanding = Total − Net Completed Payments
Variance   = Actual Settled − Expected (invoice total)
```

Rounding is `HALF_UP` at scale 2 by default, and both are configurable
(`settlement.invoice.rounding-mode`, `settlement.invoice.amount-scale`).

---

## 2. Technology stack

| Concern | Choice |
| --- | --- |
| Language / runtime | Java 21 |
| Framework | Spring Boot 3.3.5 (Web, Data JPA/Hibernate, Validation, Security, Scheduling) |
| Build | Maven |
| Database | MySQL 8 (primary) / H2 in MySQL mode (local `dev` profile) |
| Migrations | Flyway (`V1__init_schema.sql`) |
| API docs | springdoc-openapi / Swagger UI |
| Auth | JJWT 0.12.6 (HS256), BCrypt password hashing |
| Tests | JUnit 5, Mockito, AssertJ, Spring Boot Test, MockMvc |
| Utilities | Lombok |

---

## 3. Prerequisites

* **JDK 21 or newer** — `java -version`
* **Maven 3.9+** — `mvn -version`
* **MySQL 8+** (only for the `mysql` profile; the `dev` profile needs nothing)

---

## 4. Project layout

```
src/main/java/com/example/settlement
├── audit/          append-only audit trail (service + action constants)
├── config/         SettlementProperties, OpenAPI, sample-data seeder
├── controller/     REST controllers (one per module)
├── dto/            request/response records + ApiResponse/PageResponse/ErrorResponse envelopes
├── entity/         Trade, Invoice, Payment, Reconciliation, OverdueAlert, AuditLog, User
├── enums/          status and type enums with embedded state-machine guards
├── exception/      domain exceptions + @RestControllerAdvice global handler
├── mapper/         entity → DTO mappers
├── repository/     Spring Data JPA repositories (incl. Criteria specifications)
├── scheduler/      OverdueAlertScheduler
├── security/       JWT service, filter, SecurityConfig, CurrentUserProvider
├── service/        interfaces, service/impl/ implementations
└── util/           InvoiceCalculator, ReferenceGenerator
```

---

## 5. Setup — step by step

### Step 1 — Install the prerequisites

Install JDK 21 and Maven 3.9+, then verify:

```bash
java -version
mvn -version
```

### Step 2 — Install and start MySQL 8

```bash
# Docker (fastest)
docker run --name settlement-mysql -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=trade_settlement -p 3306:3306 -d mysql:8
```

Or start a locally installed MySQL service.

### Step 3 — Create the database and a user

```sql
CREATE DATABASE trade_settlement CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'settlement'@'%' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON trade_settlement.* TO 'settlement'@'%';
FLUSH PRIVILEGES;
```

> The JDBC URL also sets `createDatabaseIfNotExist=true`, so the database is
> created automatically if the user has permission.

### Step 4 — Configure environment variables

Copy the template and fill in real values:

```bash
cp .env.example .env
```

| Variable | Purpose |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `mysql` (default) or `dev` |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | MySQL connection |
| `SERVER_PORT` | HTTP port (default `8080`) |
| `JWT_SECRET` | HS256 signing secret, **minimum 32 characters**. Generate with `openssl rand -base64 48` |
| `JWT_EXPIRATION_MINUTES` | Token lifetime (default `120`) |
| `SETTLEMENT_DEFAULT_TAX_PERCENTAGE` | Default tax rate for generated invoices |
| `SETTLEMENT_PAYMENT_TERM_DAYS` | Invoice due date offset |
| `SETTLEMENT_ALLOW_OVERPAYMENT` | Set `true` to permit payments above the balance |
| `SETTLEMENT_RECONCILIATION_TOLERANCE` | Absolute matching tolerance |
| `SETTLEMENT_OVERDUE_CRON` | Overdue monitor schedule (default hourly) |
| `SETTLEMENT_SEED_SAMPLE_DATA` | Set `false` in production |

### Step 5 — Run the schema migrations

Flyway runs automatically the first time the application starts against the
`mysql` profile, so no separate command is required. The migration lives in
`src/main/resources/db/migration/V1__init_schema.sql` and creates `users`,
`trades`, `invoices`, `payments`, `reconciliations`, `overdue_alerts` and
`audit_logs` (money columns are `DECIMAL(19,4)`). Hibernate then runs with
`ddl-auto: validate`, so any drift between the entities and the migration fails
fast at start-up.

### Step 6 — Start the application

```bash
# MySQL
mvn -B spring-boot:run -Dspring-boot.run.profiles=mysql

# or, with no database installed at all, the in-memory H2 profile:
mvn -B spring-boot:run -Dspring-boot.run.profiles=dev
```

The API is served on `http://localhost:8080`. When `SETTLEMENT_SEED_SAMPLE_DATA`
is `true` and the database is empty, realistic sample data is inserted
(crude oil, natural gas, milling wheat, copper cathode, aluminium ingots).

### Step 7 — Open the Swagger UI

* Swagger UI — <http://localhost:8080/swagger-ui.html>
* OpenAPI JSON — <http://localhost:8080/v3/api-docs>
* H2 console (dev profile only) — <http://localhost:8080/h2-console>

Authenticate with `POST /api/v1/auth/login` and paste the returned token into
the **Authorize** dialog.

### Step 8 — Exercise the API with Postman

Import `docs/Trade-Settlement-System.postman_collection.json`, set the
`baseUrl` collection variable and run the `Auth → Login` request first; the
token is stored automatically for the remaining requests.

### Step 9 — Run the tests

```bash
mvn -B test
```

The suite covers the calculation engine, both state machines, trade creation and
validation, invoice generation and duplicate prevention, currency mismatches,
partial/full payments, failed and reversed payments, overpayment guards,
reconciliation variance classification with configurable tolerance, and
end-to-end REST behaviour (authentication, authorization, validation and the
consistent error envelope).

### Step 10 — Push to a Git repository

```bash
git init
git add .
git commit -m "Initial commit: trade settlement & invoice reconciliation system"
git remote add origin <your-repository-url>
git push -u origin main
```

`.env` is git-ignored; commit `.env.example` only.

---

## 6. Default credentials (sample data)

| Username | Password | Role |
| --- | --- | --- |
| `admin` | `Admin@123` | ADMIN |
| `finance` | `Finance@123` | FINANCE |
| `trader` | `Trader@123` | TRADER |
| `auditor` | `Auditor@123` | AUDITOR |

> Change or disable these before any real deployment
> (`SETTLEMENT_SEED_SAMPLE_DATA=false`).

---

## 7. API reference

All endpoints are versioned under `/api/v1` and return a consistent envelope:

```json
{ "success": true, "message": "OK", "data": { }, "timestamp": "2026-01-15T10:15:30" }
```

Errors use the same shape with `success: false` and a stable `errorCode`:

```json
{
  "success": false,
  "errorCode": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "details": [{ "field": "quantity", "message": "quantity must be greater than zero" }],
  "path": "/api/v1/trades",
  "timestamp": "2026-01-15T10:15:30"
}
```

### Authentication

| Method | Path | Roles |
| --- | --- | --- |
| POST | `/api/v1/auth/login` | public |
| POST | `/api/v1/auth/register` | ADMIN |

### Trades

| Method | Path | Roles |
| --- | --- | --- |
| POST | `/api/v1/trades` | TRADER, ADMIN, FINANCE |
| GET | `/api/v1/trades` | any authenticated |
| GET | `/api/v1/trades/{id}` | any authenticated |
| PUT | `/api/v1/trades/{id}` | TRADER, ADMIN |
| GET | `/api/v1/trades/reference/{reference}` | any authenticated |
| GET | `/api/v1/trades/{id}/invoices` | any authenticated |

### Invoices

| Method | Path | Roles |
| --- | --- | --- |
| POST | `/api/v1/invoices/from-trade/{tradeId}` | FINANCE, ADMIN |
| GET | `/api/v1/invoices` | any authenticated |
| GET | `/api/v1/invoices/{id}` | any authenticated |
| GET | `/api/v1/invoices/number/{invoiceNumber}` | any authenticated |
| POST | `/api/v1/invoices/{id}/issue` | FINANCE, ADMIN |
| POST | `/api/v1/invoices/{id}/cancel` | FINANCE, ADMIN |
| GET | `/api/v1/invoices/{id}/outstanding` | any authenticated |
| GET | `/api/v1/invoices/overdue` | FINANCE, AUDITOR, ADMIN |
| GET | `/api/v1/invoices/{id}/printable` | any authenticated |
| GET | `/api/v1/invoices/{invoiceId}/payments` | any authenticated |

### Payments

| Method | Path | Roles |
| --- | --- | --- |
| POST | `/api/v1/payments` | FINANCE, ADMIN |
| GET | `/api/v1/payments` | any authenticated |
| GET | `/api/v1/payments/{id}` | any authenticated |
| POST | `/api/v1/payments/{id}/complete` | FINANCE, ADMIN |
| POST | `/api/v1/payments/{id}/fail` | FINANCE, ADMIN |
| POST | `/api/v1/payments/{id}/reverse` | FINANCE, ADMIN |

### Reconciliations

| Method | Path | Roles |
| --- | --- | --- |
| POST | `/api/v1/reconciliations/invoice/{invoiceId}/run` | FINANCE, ADMIN |
| GET | `/api/v1/reconciliations` | any authenticated |
| GET | `/api/v1/reconciliations/unresolved` | FINANCE, AUDITOR, ADMIN |
| GET | `/api/v1/reconciliations/{id}` | any authenticated |
| POST | `/api/v1/reconciliations/{id}/resolve` | FINANCE, ADMIN |

### Alerts

| Method | Path | Roles |
| --- | --- | --- |
| GET | `/api/v1/alerts` | FINANCE, AUDITOR, ADMIN |
| GET | `/api/v1/alerts/overdue` | FINANCE, AUDITOR, ADMIN |
| POST | `/api/v1/alerts/{id}/acknowledge` | FINANCE, ADMIN |

### Dashboard

| Method | Path | Roles |
| --- | --- | --- |
| GET | `/api/v1/dashboard/summary` | any authenticated |
| GET | `/api/v1/dashboard/invoice-status` | any authenticated |
| GET | `/api/v1/dashboard/payment-summary` | any authenticated |
| GET | `/api/v1/dashboard/overdue-summary` | any authenticated |
| GET | `/api/v1/dashboard/reconciliation-summary` | any authenticated |

### Audit

| Method | Path | Roles |
| --- | --- | --- |
| GET | `/api/v1/audit-logs` | AUDITOR, ADMIN, FINANCE |
| GET | `/api/v1/audit-logs/{entityType}/{entityId}` | AUDITOR, ADMIN, FINANCE |

---

## 8. Business rules enforced

* Only `COMPLETED` trades can be invoiced; a second invoice for the same trade is
  rejected unless `settlement.invoice.allow-multiple-invoices-per-trade=true`.
* An invoice currency must equal the trade currency; a payment currency must equal
  the invoice currency (`CURRENCY_MISMATCH`). Foreign-currency payments are never
  summed into a settled amount — reconciliation forces `REVIEW_REQUIRED` instead.
* A payment direction must match the invoice type (`RECEIVABLE` → `INCOMING`,
  `PAYABLE` → `OUTGOING`).
* Overpayment is rejected unless `settlement.payment.allow-overpayment=true`.
* Only `COMPLETED` payments reduce the outstanding balance; `PENDING`, `FAILED`
  and `REVERSED` payments never do. Reversal keeps the original row for audit.
* An invoice with recorded payments cannot be cancelled.
* Duplicate `OVERDUE_INVOICE` / `UPCOMING_DUE` alerts are blocked by the unique
  constraint `uk_alerts_invoice_type (invoice_id, alert_type)`.
* `audit_logs` is append-only: JPA `@PreUpdate`/`@PreRemove` reject any attempt to
  modify or delete an audit row.

---

## 9. Profiles

| Profile | Database | Schema | Notes |
| --- | --- | --- | --- |
| `mysql` (default) | MySQL 8 | Flyway migrations, `ddl-auto: validate` | Production-style |
| `dev` | H2 in MySQL mode | Hibernate `create-drop`, Flyway disabled | Zero-setup local run |
| `test` | H2 in MySQL mode | `create-drop`, seeder and scheduler off | Used by the test suite |

---

## 10. Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| `JWT secret must be configured` at start-up | `JWT_SECRET` is missing or shorter than 32 characters. Set it (or use the `dev` profile). |
| `Schema-validation: missing table` | Flyway did not run or the schema is stale. Check `spring.flyway.enabled` and run `mvn flyway:migrate`. |
| `401` on every call | Missing/expired bearer token. Call `/api/v1/auth/login` again. |
| `403` on audit endpoints | The token's role is not `AUDITOR`, `ADMIN` or `FINANCE`. |
| `409 DATA_INTEGRITY_VIOLATION` | A unique constraint was hit (duplicate trade/invoice/payment reference). |

---

## 11. License

MIT.
