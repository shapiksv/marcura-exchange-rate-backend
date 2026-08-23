# PLAN.md

## Marcura Exchange Rate Management System

## 1. Purpose

This plan was created before implementation and is intended to guide AI-assisted development of the Marcura technical assessment.

The first delivery phase is **backend only**.

The plan prioritizes:
1. backend correctness;
2. concurrency and multi-instance behavior;
3. testability;
4. clear AI workflow evidence;
5. simple local setup;
6. a clean contract for the future Angular frontend.

## 2. Assessment Requirements Mapped to Backend Work

The backend must provide:

- daily Fixer.io data collection at 00:05 GMT/UTC;
- persistence of currency, rate value, and API-reported rate date;
- graceful duplicate handling;
- correct behavior when multiple service instances run;
- exchange calculation from locally stored rates;
- optional historical date support;
- 404 when requested rates do not exist;
- concurrency-safe usage increments for both currencies;
- analytics showing counts and query dates;
- historical rate data for the future frontend chart;
- an AI trend-insight endpoint through Spring AI;
- Swagger/OpenAPI;
- tests, including spread calculation and at least one integration test.

## 3. Proposed Backend Stack

- Java 17+
- Spring Boot
- Maven
- Spring Web
- Spring Data JPA
- PostgreSQL
- Liquibase
- Bean Validation
- Springdoc OpenAPI
- Spring Scheduler
- ShedLock JDBC
- Spring AI
- Ollama for local LLM execution
- JUnit 5
- Mockito
- Testcontainers PostgreSQL

## 4. Architecture

Use a modular monolith with feature-oriented packages.

High-level flow:

```text
Controller
   |
Application Service
   |
Domain logic
   |
Repository
   |
PostgreSQL
```

External integrations:

```text
Scheduler -> Rate Collection Service -> Fixer Client -> Fixer.io

Insight Controller
   -> Insight Service
   -> Historical Rate Service
   -> Spring AI ChatClient
   -> Ollama/OpenAI-compatible endpoint
```

## 5. Data Model

### 5.1 exchange_rate

Suggested columns:

```text
id
rate_date
base_currency
currency_code
rate_value
created_at
updated_at
```

Constraints:

```text
UNIQUE(rate_date, base_currency, currency_code)
```

Indexes:
- `(rate_date)`
- `(currency_code, rate_date)`
- `(base_currency, currency_code, rate_date)`

Use `BigDecimal`/NUMERIC for `rate_value`.

### 5.2 currency_usage_daily

Suggested columns:

```text
id
currency_code
query_date
query_count
last_queried_at
```

Constraint:

```text
UNIQUE(currency_code, query_date)
```

Concurrency strategy:

```sql
INSERT INTO currency_usage_daily(currency_code, query_date, query_count, last_queried_at)
VALUES (?, ?, 1, ?)
ON CONFLICT (currency_code, query_date)
DO UPDATE
SET query_count = currency_usage_daily.query_count + 1,
    last_queried_at = EXCLUDED.last_queried_at;
```

This avoids unsafe Java-side read-modify-write logic.

### 5.3 shedlock

Create the standard database table required by ShedLock.

This is used to make the scheduled Fixer fetch safe when multiple application instances are running.

## 6. Exchange Calculation

Spread policy:

```text
Base currency returned by Fixer: 0.00%
JPY, HKD, KRW:               3.25%
MYR, INR, MXN:               4.50%
RUB, CNY, ZAR:               6.00%
All others:                   2.75%
```

Formula:

```text
adjustedRate =
    (toRate / fromRate)
    * ((100 - max(toSpread, fromSpread)) / 100)
```

Implementation notes:
- use `BigDecimal`;
- define an explicit `MathContext` or division scale/rounding policy;
- do not round early;
- both rates must be from the same date and base snapshot.

## 7. API Design

### 7.1 Exchange Calculator

```http
GET /api/v1/exchange
```

Parameters:

```text
from     required, ISO 4217-like currency code
to       required
date     optional, yyyy-MM-dd
```

Behavior:
- if `date` is omitted, resolve latest available date;
- load both currencies from the same rate snapshot;
- 404 if required rate data is unavailable;
- calculate adjusted rate;
- atomically increment usage for both currencies;
- return current total counts.

### 7.2 Historical Rates

```http
GET /api/v1/exchange/history
```

Parameters:

```text
from
to
fromDate
toDate
```

Behavior:
- validate `fromDate <= toDate`;
- return one rate point per available date;
- do not increment usage counters;
- provide data suitable for an Angular table and line chart.

### 7.3 Analytics

```http
GET /api/v1/analytics
```

Suggested response:

```json
{
  "topCurrencies": [
    {
      "currency": "EUR",
      "totalCount": 142,
      "lastQueried": "2024-03-15"
    }
  ],
  "dailyUsage": [
    {
      "date": "2024-03-15",
      "currency": "EUR",
      "count": 12
    }
  ]
}
```

### 7.4 AI Trend Insight

```http
GET /api/v1/exchange/insight
```

Parameters:

```text
from
to
fromDate
toDate
```

Flow:
1. load actual historical rate points;
2. serialize a compact context;
3. send context through Spring AI ChatClient;
4. return concise insight.

### 7.5 Manual Refresh — Optional

```http
POST /api/v1/rates/refresh
```

Must not modify usage counters.

## 8. Error Model

Use centralized exception handling.

Suggested error codes:

```text
VALIDATION_ERROR
INVALID_CURRENCY
INVALID_DATE_RANGE
RATE_NOT_FOUND
FIXER_UNAVAILABLE
FIXER_RESPONSE_INVALID
AI_UNAVAILABLE
INTERNAL_ERROR
```

Use correct HTTP semantics:
- 400 for invalid input;
- 404 for unavailable requested rate/date;
- 502/503 for external integration failure where appropriate;
- 500 only for unexpected server errors.

## 9. Implementation Phases

### Phase 0 — Repository Bootstrap

Tasks:
- create Spring Boot Maven project;
- add dependencies;
- create package structure;
- add configuration files;
- add PostgreSQL local setup;
- add Liquibase;
- add OpenAPI;
- verify empty application starts.

Exit criteria:
- `mvn test` succeeds;
- application starts against local PostgreSQL;
- Swagger UI loads.

Recommended commit:

```text
[AI] bootstrap backend project and infrastructure
```

### Phase 1 — Database Schema and Core Domain

Tasks:
- add `exchange_rate`;
- add `currency_usage_daily`;
- add ShedLock table;
- add entities/repositories;
- implement spread policy;
- implement exchange calculation domain service;
- add calculation unit tests.

Exit criteria:
- migrations run from a clean DB;
- spread and calculation tests pass.

Recommended commit:

```text
[AI] implement exchange rate domain and persistence
```

### Phase 2 — Fixer.io Client and Rate Upsert

Tasks:
- create Fixer client abstraction;
- map Fixer response;
- validate `success`, `base`, `date`, and `rates`;
- persist API-reported date;
- implement idempotent upsert;
- add integration/service tests.

Exit criteria:
- repeated ingestion of same snapshot does not create duplicates;
- malformed/external errors are handled.

Recommended commit:

```text
[AI] add Fixer integration and idempotent rate ingestion
```

### Phase 3 — Scheduled Collection and Multi-Instance Safety

Tasks:
- schedule for 00:05 UTC;
- configure ShedLock;
- ensure only one instance performs the fetch;
- document the choice.

Exit criteria:
- schedule timezone is explicit;
- DB-backed lock is configured;
- manual service-level invocation is testable.

Recommended commit:

```text
[AI] add distributed-safe daily rate scheduler
```

### Phase 4 — Exchange API and Concurrent Usage Tracking

Tasks:
- implement request validation;
- resolve explicit/latest rate date;
- calculate exchange;
- atomic usage increments;
- return usage counts;
- add controller/service tests;
- add concurrency-focused integration test if practical.

Exit criteria:
- endpoint returns expected formula output;
- missing date returns 404;
- concurrent requests do not lose counter increments.

Recommended commit:

```text
[AI] implement exchange API and atomic usage tracking
```

### Phase 5 — Historical Rates and Analytics

Tasks:
- implement history endpoint;
- implement analytics aggregation;
- ensure responses support Angular views;
- add tests.

Exit criteria:
- history range works;
- analytics returns totals and dates.

Recommended commit:

```text
[AI] add historical rates and usage analytics APIs
```

### Phase 6 — Spring AI Trend Insight

Tasks:
- add Spring AI/Ollama configuration;
- implement `ChatClient`;
- build a constrained system prompt;
- pass actual historical points as context;
- implement endpoint;
- handle unavailable model;
- test prompt/context builder separately from the live model.

Exit criteria:
- endpoint uses real historical numbers;
- response is concise;
- configuration is documented;
- application can start with documented local model setup.

Recommended commit:

```text
[AI] integrate Spring AI trend insight endpoint
```

### Phase 7 — OpenAPI, Tests, Hardening

Tasks:
- document all endpoints;
- verify HTTP status codes;
- add/complete unit tests;
- add at least one PostgreSQL Testcontainers integration test;
- check logs for secrets;
- run full build;
- review code smells and dead code.

Exit criteria:

```bash
mvn clean verify
```

passes.

Recommended commit:

```text
[AI] harden backend API tests and documentation
```

### Phase 8 — README and AI Workflow Evidence

Tasks:
- add local run instructions;
- add PostgreSQL instructions;
- add Fixer setup;
- add Ollama setup;
- add architecture overview;
- add assumptions and trade-offs;
- add `AI Workflow` section;
- document at least one AI recommendation that was rejected/corrected.

Recommended commit:

```text
[AI] document backend architecture and AI workflow
```

## 10. Test Plan

### Unit

- spread for base currency;
- spread for JPY/HKD/KRW;
- spread for MYR/INR/MXN;
- spread for RUB/CNY/ZAR;
- spread for default currency;
- higher spread selected from pair;
- worked EUR/PLN example;
- same-currency behavior;
- missing rate behavior;
- latest-date resolution;
- insight context includes exact rate values.

### Integration

At least one Testcontainers test is mandatory.

Preferred integration scenarios:

1. repeated rate snapshot import is idempotent;
2. `/exchange` persists two usage increments;
3. concurrent usage updates produce the exact expected total;
4. requested missing date returns 404.

## 11. AI Prompt Design for Trend Insight

System prompt intent:

```text
You are a concise exchange-rate trend summarizer.

Use only the historical rate data supplied by the application.
Do not invent causes, news, market events, or missing values.
Do not give financial advice or predictions.
Describe the observable movement in no more than 2 short sentences.
Mention approximate percentage change when it can be calculated from the supplied data.
If the data is insufficient, say that clearly.
```

User/context message should include:

```text
Pair: EUR/GBP
Period: 2024-02-01 to 2024-03-01

Historical adjusted/raw cross rates:
2024-02-01: ...
2024-02-02: ...
...
```

## 12. Assumptions to Document

### Fixer base currency

The brief uses “rate to USD” wording, but Fixer may return a subscription-dependent base currency.

Decision:
- persist the actual base returned by Fixer;
- calculate cross rates from a common-base snapshot;
- treat the returned base currency spread as 0%.

### Usage counter meaning

Only successful calculator `/exchange` calls increment usage.

History, analytics, AI insight, and manual rate refresh do not increment usage.

### Historical gaps

History results return available stored dates within the requested interval.

Do not fabricate missing dates.

### Same-currency exchange

Recommended behavior:
- allow `from == to`;
- base cross-rate is 1;
- apply the pair spread according to the assessment formula unless a deliberate alternative is documented.

This point should be reviewed before final implementation because the brief does not explicitly define same-currency behavior.

## 13. Known Risks

### Fixer free plan restrictions
The available base currency and endpoint capabilities may depend on the Fixer subscription.

Mitigation:
- isolate provider integration;
- store returned base;
- avoid assuming USD.

### Scheduler duplication
Multiple instances can trigger the same scheduled job.

Mitigation:
- DB-backed ShedLock.

### Lost usage increments
Naive entity read/increment/save can lose updates.

Mitigation:
- PostgreSQL atomic upsert.

### LLM unavailable on reviewer machine
The AI endpoint may fail if Ollama/model is not running.

Mitigation:
- clear setup instructions;
- explicit error response;
- no fake fallback insight presented as LLM output.

## 14. Review Checkpoints

After each phase:
1. run tests;
2. run formatting/static checks if configured;
3. review generated code;
4. remove unnecessary abstractions;
5. inspect SQL/migrations;
6. verify no secrets are committed;
7. update plan status if implementation differs;
8. record important AI corrections for the README.

## 15. Backend Definition of Done

- [ ] Spring Boot application starts locally
- [ ] PostgreSQL schema is migration-managed
- [ ] Fixer integration is configurable
- [ ] API-reported rate date is persisted
- [ ] duplicate snapshots are handled safely
- [ ] scheduler runs at 00:05 UTC
- [ ] scheduler is multi-instance safe
- [ ] exchange endpoint supports optional date
- [ ] missing requested date returns 404
- [ ] formula is implemented with `BigDecimal`
- [ ] currency spreads match the assessment
- [ ] usage increments are atomic under concurrency
- [ ] analytics returns counts and dates
- [ ] history endpoint supports a date range
- [ ] Spring AI insight uses actual rate data
- [ ] OpenAPI/Swagger documents all endpoints
- [ ] spread calculation tests pass
- [ ] at least one PostgreSQL integration test passes
- [ ] `mvn clean verify` passes
- [ ] README contains backend setup
- [ ] README contains `AI Workflow`
- [ ] at least one AI disagreement/correction is documented
- [ ] AI-assisted commits use a consistent `[AI]` prefix
