# CLAUDE.md

## Project Context

This repository contains the Marcura Full Stack Developer Technical Assessment: an Exchange Rate Management System.

The system must:
- fetch exchange rates from Fixer.io once per day;
- persist rates locally;
- calculate spread-adjusted exchange rates;
- expose REST APIs;
- track currency usage safely under concurrent requests;
- expose analytics;
- expose historical rate data;
- generate a short AI trend insight through Spring AI;
- later provide an Angular frontend.

For the current implementation phase, focus on the **backend only**.

## Primary Goal

Build a clean, production-minded Spring Boot backend that is easy to run locally and easy to explain during the assessment review.

Prefer simple, explicit engineering decisions over unnecessary abstractions.

## Required Technology

- Java 17+
- Spring Boot
- Maven
- Spring Web / Spring MVC
- Spring Data JPA / Hibernate
- PostgreSQL
- Bean Validation
- Swagger / OpenAPI
- Spring Scheduler
- Spring AI
- JUnit 5
- Mockito
- Testcontainers for at least one integration test
- Fixer.io as the external rate provider

Use only libraries that have a clear purpose.

## Backend Architecture

Use package-by-feature or a similarly cohesive structure. A suggested layout is:

```text
src/main/java/.../
  exchange/
    api/
    application/
    domain/
    persistence/
  rate/
    application/
    domain/
    persistence/
    provider/
    scheduler/
  analytics/
    api/
    application/
    persistence/
  insight/
    api/
    application/
  common/
    error/
    config/
```

Avoid a single large global `controller/service/repository` package structure if it makes features harder to understand.

## Domain and Persistence Guidelines

### Exchange Rate Snapshot

Persist the rate date reported by Fixer.io. Do not substitute the system fetch date.

Recommended uniqueness:
- `(rate_date, currency_code, base_currency)`

Use database constraints to prevent duplicates.

Store numeric rates with `BigDecimal`, never `double` or `float`.

Use a scale and precision suitable for exchange rates and avoid premature rounding.

### Base Currency

Fixer.io may return a base currency determined by the subscription.

Do not hard-code USD as the storage base.

For cross-rate calculation, both currencies must come from the same rate date and the same base snapshot.

The ratio between rates from the same base is valid even when the common base is not USD.

The base currency returned by Fixer.io has a spread of `0.00%`.

### Usage Analytics

Every successful exchange query must increment usage for both currencies.

Use a database-backed, concurrency-safe design.

Preferred design:
- table `currency_usage_daily`
- unique key `(currency_code, query_date)`
- atomic PostgreSQL upsert:
  `INSERT ... ON CONFLICT ... DO UPDATE SET query_count = currency_usage_daily.query_count + EXCLUDED.query_count`

Increment both currencies in one transaction.

This design supports:
- total query count per currency;
- dates on which queries were made;
- daily usage patterns;
- concurrent requests.

Do not implement counters using read-modify-write logic in Java.

## Spread Rules

Use the higher spread of the two currencies.

Spread reference:

- Base currency returned by Fixer.io: `0.00%`
- JPY, HKD, KRW: `3.25%`
- MYR, INR, MXN: `4.50%`
- RUB, CNY, ZAR: `6.00%`
- All other currencies: `2.75%`

Formula:

```text
adjustedRate =
    (toRate / fromRate)
    * ((100 - max(toSpread, fromSpread)) / 100)
```

Implement spread resolution as a dedicated domain component or enum-backed policy.

The calculation must be unit-tested thoroughly.

## REST API

Required or strongly implied backend endpoints:

### Exchange

```http
GET /api/v1/exchange?from=EUR&to=PLN&date=2024-03-15
```

- `date` is optional.
- If absent, use the most recent available rate date.
- If the requested date has no rates, return `404`.
- A successful call increments usage for both currencies.
- Validate currency codes.
- Return appropriate HTTP status codes.

Suggested response:

```json
{
  "from": "EUR",
  "to": "PLN",
  "exchange": 4.4405487565413254,
  "date": "2024-03-15",
  "fromQueryCount": 142,
  "toQueryCount": 37
}
```

### Historical Rates

The frontend requirement needs a historical table and chart, so expose a backend endpoint even though the brief does not prescribe the exact path.

Suggested endpoint:

```http
GET /api/v1/exchange/history?from=EUR&to=GBP&fromDate=2024-02-01&toDate=2024-03-01
```

Return one calculated/raw cross-rate item per available date.

Do not increment usage counters for history reads unless explicitly documented as an assumption. Default assumption: only the calculator exchange query increments usage.

### Analytics

```http
GET /api/v1/analytics
```

At minimum return:
- total query count per currency;
- last queried date;
- per-date usage information sufficient for the Angular analytics dashboard.

### AI Insight

```http
GET /api/v1/exchange/insight?from=EUR&to=GBP&fromDate=2024-02-01&toDate=2024-03-01
```

Use actual historical rate data as context for the LLM.

The LLM must not invent rates.

The system prompt must request a short, readable trend commentary and explicitly forbid financial advice.

### Manual Refresh

Optional:

```http
POST /api/v1/rates/refresh
```

This must fetch/upsert rates without changing usage counters.

## Fixer.io Integration

Create a dedicated client abstraction, for example:

```java
public interface ExchangeRateProvider {
    RateSnapshot fetchLatestRates();
}
```

Keep HTTP transport details outside domain/application services.

Configuration must come from environment/application properties:
- Fixer base URL
- Fixer API key
- timeouts

Never commit secrets.

Handle:
- transport errors;
- non-2xx responses;
- invalid/malformed payloads;
- Fixer response with `success=false`;
- missing required fields.

## Scheduler

Fetch rates once per day at **00:05 GMT/UTC**.

Make the timezone explicit in the schedule.

The assessment assumes multiple application instances may be running.

Use a distributed lock backed by the database. A simple acceptable option is ShedLock with JDBC.

The lock must prevent multiple instances from performing the same daily fetch simultaneously.

Document why the chosen lock works in a multi-instance deployment.

Do not rely on `synchronized`, JVM-local locks, or an in-memory flag.

## Transactions and Concurrency

Use `@Transactional` at application-service boundaries where multiple DB changes must be atomic.

For the calculator:
1. resolve the rate date;
2. load both rates from the same snapshot;
3. calculate the adjusted rate;
4. atomically increment both currency usage counters;
5. return the updated counts.

Avoid holding a transaction open while performing external HTTP or LLM calls.

## Error Handling

Use a centralized `@RestControllerAdvice`.

Prefer a stable error response such as:

```json
{
  "code": "RATE_NOT_FOUND",
  "message": "Rates are not available for 2024-03-15",
  "timestamp": "2026-01-01T10:00:00Z"
}
```

Handle at least:
- invalid currency;
- invalid date range;
- missing rate data;
- external provider failure;
- AI provider unavailable;
- validation errors.

Do not expose stack traces to API clients.

## OpenAPI

All public endpoints must appear in Swagger/OpenAPI.

Document:
- parameters;
- response models;
- important status codes;
- endpoint purpose.

Swagger UI must work locally.

## AI Trend Insight

Use Spring AI chat client abstraction.

Keep model details configurable.

Preferred local setup:
- Ollama
- model name supplied through configuration

The prompt must include the actual historical numbers.

The system prompt should constrain the response:
- concise;
- factual based only on supplied values;
- no invented events or causes;
- no financial advice;
- no unsupported predictions.

The AI integration should fail gracefully when the model is unavailable.

## Testing Requirements

Use AI to generate an initial test suite, then review and correct it.

At minimum create:

### Unit tests
- spread lookup rules;
- exchange calculation;
- higher-spread selection;
- base-currency spread;
- service behavior when date is missing;
- service behavior for latest available date;
- invalid/missing data;
- AI prompt/context construction.

### Integration tests
At least one Spring Boot integration test with a real PostgreSQL container.

Strong candidates:
- concurrent usage increment correctness;
- unique rate upsert;
- `/exchange` success + persistence of usage;
- scheduler/upsert persistence path.

Do not write tests that merely mirror implementation details.

## Coding Standards

- Prefer constructor injection.
- Use records for immutable API DTOs when appropriate.
- Use `BigDecimal` for monetary/rate calculations.
- Avoid `Optional` in entity fields and DTO fields.
- Avoid business logic in controllers.
- Avoid static utility classes for core domain behavior.
- Avoid overusing Lombok; explicit code is acceptable.
- Keep methods small and intention-revealing.
- Prefer clear names over comments explaining unclear code.
- Do not catch `Exception` unless rethrowing through a deliberate boundary.
- Do not log secrets.
- Do not log full LLM or Fixer credentials.
- Use UTC for application-level timestamps unless a domain date has no time component.

## Database Migrations

Use a migration tool.

Preferred: Liquibase or Flyway.

Do not use Hibernate schema auto-update as the primary schema-management mechanism.

Create explicit migrations for:
- exchange rate table;
- currency usage daily table;
- ShedLock table if ShedLock is used;
- indexes and uniqueness constraints.

## Configuration

Provide safe defaults where possible.

Expected environment variables may include:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
FIXER_API_KEY
FIXER_BASE_URL
OLLAMA_BASE_URL
OLLAMA_MODEL
```

No secrets in Git.

Provide a `.env.example` or equivalent sample configuration if useful.

## Documentation Expectations

README must eventually contain:
- local setup;
- PostgreSQL setup;
- Fixer API key setup;
- Ollama/model setup;
- run commands;
- Swagger location;
- architecture overview;
- assumptions;
- trade-offs;
- AI Workflow section;
- an example of AI output that was rejected or corrected and why.

## AI-Assisted Workflow Rules

This assessment explicitly evaluates how AI is used.

When acting as an agent:

1. Read `PLAN.md` before implementation.
2. Work in coherent phases, not isolated snippets.
3. Before modifying multiple files, summarize the intended changes.
4. After implementation, run tests and build.
5. If a test fails, diagnose the cause instead of weakening the test.
6. Do not silently change requirements.
7. Record assumptions explicitly.
8. Flag ambiguity instead of inventing requirements.
9. If you propose a shortcut, explain the trade-off.
10. Do not automatically commit unless explicitly asked.

Use `[AI]` as the recommended prefix for commits containing substantial AI-assisted work.

## Important Assessment Ambiguities / Decisions

### 1. “Rate to USD” wording vs Fixer base currency

The formula labels stored values as rates “to USD”, while Fixer may return another base currency depending on the subscription.

Implementation decision:
- store the actual `base` returned by Fixer;
- use two rates from the same snapshot/common base;
- calculate cross rates using the ratio;
- treat the returned base currency as 0% spread.

Document this in README under assumptions.

### 2. Historical endpoint

The frontend requires a historical table/chart, but the backend section does not define a specific historical API.

Implementation decision:
- provide `/api/v1/exchange/history`.

### 3. Usage definition

The brief says every successful exchange query increments usage for both currencies.

Implementation decision:
- calculator `/exchange` increments usage;
- history, analytics, and insight reads do not increment usage;
- manual refresh does not increment usage.

Document this assumption.

## Definition of Done for Backend Phase

Backend phase is complete when:

- project builds successfully;
- migrations run on clean PostgreSQL;
- Fixer client is configurable;
- daily scheduler is configured for 00:05 UTC;
- scheduler is protected by a DB-backed distributed lock;
- rates are upserted without duplicates;
- `/exchange` works with optional date;
- formula is correct and uses `BigDecimal`;
- usage increments are concurrency-safe;
- `/analytics` works;
- `/exchange/history` works;
- `/exchange/insight` works through Spring AI;
- Swagger UI works;
- unit tests pass;
- at least one PostgreSQL integration test passes;
- README backend setup is sufficient to run locally;
- AI-assisted workflow evidence is visible in repository history and documentation.

Do not start the Angular frontend until the backend phase has been reviewed.
