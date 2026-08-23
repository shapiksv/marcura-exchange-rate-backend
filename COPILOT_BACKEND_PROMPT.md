# Copilot Backend Kickoff Prompt

You are acting as the implementation agent for the backend of the Marcura Exchange Rate Management System technical assessment.

Before changing code:

1. Read `CLAUDE.md`.
2. Read `PLAN.md`.
3. Read the original assessment PDF if it is present in the repository.
4. Inspect the existing repository structure and current code.
5. Do not start the Angular frontend.

Your task is to implement the backend in coherent, reviewable phases using the plan as the source of execution structure.

## Working mode

Use agentic, multi-file changes rather than giving me isolated code snippets.

For every phase:

1. State the phase you are starting.
2. Briefly list the files/components you expect to create or modify.
3. Implement the phase.
4. Run the relevant tests/build.
5. Diagnose and fix failures.
6. Summarize what was completed.
7. Point out any requirement ambiguity, assumption, or trade-off.
8. Suggest a commit message using the `[AI]` prefix.
9. Stop and wait for my review before moving to the next major phase.

Do not automatically commit or push anything.

## Engineering constraints

Follow these rules strictly:

- Java 17+.
- Spring Boot + Maven.
- PostgreSQL.
- Spring Data JPA/Hibernate.
- Liquibase for database migrations.
- Swagger/OpenAPI.
- Spring Scheduler.
- DB-backed ShedLock for multi-instance scheduler safety.
- Spring AI with configurable Ollama/OpenAI-compatible endpoint.
- JUnit 5, Mockito, and Testcontainers.
- Constructor injection.
- `BigDecimal` for all exchange-rate/spread calculations.
- No secrets committed to the repository.
- No Java read-modify-write counters for usage tracking.
- Use atomic PostgreSQL upsert/increment for usage counters.
- External Fixer and LLM calls must not be performed inside long-running DB transactions.
- Use centralized API exception handling.
- Keep controllers thin.
- Keep provider HTTP details outside domain logic.
- Use the rate date returned by Fixer, not the system fetch date.
- Do not hard-code USD as the Fixer base currency.
- Both currencies used in a calculation must come from the same date/common-base snapshot.

## Functional requirements

Implement:

### 1. Rate collection
- fetch latest rates from Fixer.io;
- scheduled once daily at 00:05 UTC;
- persist the API-reported rate date;
- safely handle duplicates;
- behave correctly with multiple app instances.

### 2. Exchange API

```http
GET /api/v1/exchange?from=EUR&to=PLN&date=2024-03-15
```

- date optional;
- latest available date when omitted;
- 404 when requested rate data is missing;
- apply assessment spread formula;
- every successful query increments usage for both currencies;
- return the updated total usage counts.

### 3. Historical API

```http
GET /api/v1/exchange/history?from=EUR&to=GBP&fromDate=2024-02-01&toDate=2024-03-01
```

Return historical points needed by the future Angular table and chart.

Do not increment usage counters for history reads.

### 4. Analytics API

```http
GET /api/v1/analytics
```

Return at minimum:
- total count per currency;
- last queried date;
- daily/date-based usage data.

### 5. AI trend insight

```http
GET /api/v1/exchange/insight?from=EUR&to=GBP&fromDate=2024-02-01&toDate=2024-03-01
```

- load real historical rates;
- pass the actual values to Spring AI;
- constrain the model to a concise factual trend summary;
- no financial advice;
- no invented causes/news;
- fail gracefully if the model is unavailable.

### 6. Optional manual refresh

```http
POST /api/v1/rates/refresh
```

This must not modify usage counters.

## Spread rules

Use the higher spread in the pair:

```text
Fixer base currency: 0.00%
JPY, HKD, KRW:       3.25%
MYR, INR, MXN:       4.50%
RUB, CNY, ZAR:       6.00%
All other currencies: 2.75%
```

Formula:

```text
adjustedRate =
    (toRate / fromRate)
    * ((100 - max(toSpread, fromSpread)) / 100)
```

Reproduce the assessment's EUR/PLN worked example in a unit test.

## Persistence design

Prefer:

### exchange_rate
Unique key:

```text
(rate_date, base_currency, currency_code)
```

### currency_usage_daily
Unique key:

```text
(currency_code, query_date)
```

Use PostgreSQL:

```sql
INSERT ... ON CONFLICT ...
DO UPDATE SET query_count = currency_usage_daily.query_count + 1
```

for concurrency-safe increments.

### shedlock
Create the required lock table through Liquibase.

## Testing expectations

Create tests as part of each phase, not at the very end.

At minimum:

- spread lookup tests;
- exchange formula tests;
- higher-spread tests;
- latest-date behavior;
- missing-date behavior;
- usage increment behavior;
- rate upsert/idempotency test;
- AI prompt/context construction test;
- at least one Spring Boot + PostgreSQL Testcontainers integration test.

Prefer an integration test that proves atomic usage increments or the full successful `/exchange` flow.

Do not make a failing test pass by weakening the assertion unless the original expectation was wrong and you explain why.

## Documentation

As you implement, keep the repository ready for the final README.

The README will need:
- local run instructions;
- PostgreSQL setup;
- Fixer API key setup;
- Ollama/model setup;
- Swagger URL;
- architecture overview;
- assumptions;
- trade-offs;
- an `AI Workflow` section;
- at least one example where AI output was rejected or corrected.

When you make a recommendation that I should challenge or review, explicitly flag it so it can later be used as evidence of critical AI use.

## Important ambiguity to preserve and document

The assessment formula describes rates as “rate to USD”, but Fixer may return a subscription-dependent base currency.

Implementation decision:
- store the actual Fixer `base`;
- use both rates from the same common-base snapshot;
- calculate the pair through their ratio;
- apply 0% spread to the Fixer base currency.

Do not silently replace this with a hard-coded USD assumption.

## First action

Do not implement the whole backend in one uncontrolled pass.

Start with **Phase 0 — repository bootstrap and infrastructure** from `PLAN.md`.

First inspect the repository and tell me:
- what already exists;
- what is missing;
- the exact files/dependencies/configuration you plan to add in Phase 0;
- any decisions you need me to approve.

Then execute Phase 0 only.
