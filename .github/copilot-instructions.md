# GitHub Copilot Instructions

## Project

Marcura Exchange Rate Management System technical assessment.

Current priority: backend implementation only.

Read and follow:
- `/CLAUDE.md`
- `/PLAN.md`

Treat those files as project-level implementation constraints and workflow guidance.

## Backend Rules

- Java 17+
- Spring Boot
- Maven
- PostgreSQL
- Liquibase
- Spring Data JPA
- Springdoc OpenAPI
- Spring Scheduler
- ShedLock JDBC
- Spring AI
- JUnit 5 / Mockito / Testcontainers
- constructor injection
- `BigDecimal` for rates and spreads
- centralized exception handling
- thin controllers
- no secrets in source control

## Key Domain Rules

- persist the date returned by Fixer.io;
- do not replace it with system date;
- do not hard-code USD as Fixer base;
- both rates in one calculation must use the same date and common base;
- base currency returned by Fixer has 0% spread;
- use the higher currency spread;
- usage increments must be database-atomic under concurrency;
- history/analytics/insight reads do not increment calculator usage;
- scheduler must be safe across multiple service instances.

## AI Workflow

Prefer coherent multi-file implementation phases.

Before large changes:
- inspect existing code;
- explain the intended change briefly;
- preserve requirements;
- identify ambiguity rather than guessing.

After changes:
- run tests;
- run Maven build;
- diagnose failures;
- summarize trade-offs.

Do not commit or push automatically.

Suggested commit prefix for significant AI-assisted work:

```text
[AI]
```
