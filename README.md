# Marcura Exchange Rate Management System - Backend

Backend implementation for the Marcura Full Stack Developer Technical Assessment.

## Phase 0, 1, 2, 3 Status: ✅ Complete

### Completed Features

**Phase 0 — Infrastructure:**

- ✅ Spring Boot Maven project structure
- ✅ All required dependencies added (Web, JPA, PostgreSQL, Liquibase, OpenAPI, Scheduler, ShedLock, Spring AI, Testcontainers)
- ✅ Feature-oriented package structure created
- ✅ Configuration files (application.yml)
- ✅ Liquibase master changelog
- ✅ OpenAPI/Swagger configuration
- ✅ Scheduler configuration with ShedLock
- ✅ Global exception handler
- ✅ Health check endpoint
- ✅ Environment variables template (.env.example)

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.6+
- PostgreSQL 13+
- (Optional) Ollama for AI features

### PostgreSQL Setup

1. Install PostgreSQL (if not already installed):
```bash
# macOS
brew install postgresql@15
brew services start postgresql@15

# Ubuntu/Debian
sudo apt-get install postgresql-15
sudo systemctl start postgresql
```

2. Create the database:
```bash
psql -U postgres
CREATE DATABASE marcura_exchange_rate;
\q
```

### Application Setup

1. Copy environment variables:
```bash
cp .env.example .env
```

2. Edit `.env` with your actual values:
- Set your Fixer API key
- Adjust PostgreSQL credentials if needed
- Configure Ollama if using AI features

3. Build the project:
```bash
mvn clean install
```

4. Run the application:
```bash
mvn spring-boot:run
```

5. Verify the application is running:
- Health check: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui.html
- API docs: http://localhost:8080/api-docs

## Technology Stack

- **Java**: 17+
- **Framework**: Spring Boot 4.1.1
- **Spring Framework**: 7.0.9
- **Build Tool**: Maven
- **Database**: PostgreSQL
- **Migration**: Liquibase
- **API Documentation**: Springdoc OpenAPI 2.8.3
- **Scheduler**: Spring Scheduler + ShedLock 5.16.0 (JDBC)
- **AI Integration**: Spring AI 2.0.1 with Ollama
- **Health & Monitoring**: Spring Boot Actuator
- **Testing**: JUnit 5, Mockito, Testcontainers 1.20.4

## Project Structure

```
src/main/java/com/example/marcuraexchangeratebackend/
├── exchange/           # Exchange rate calculation feature
│   ├── api/           # REST controllers
│   ├── application/   # Application services
│   ├── domain/        # Domain logic
│   └── persistence/   # Repositories
├── rate/              # Rate collection feature
│   ├── application/   # Rate collection service
│   ├── domain/        # Rate snapshot model
│   ├── persistence/   # Repositories
│   ├── provider/      # External API clients (Fixer)
│   └── scheduler/     # Scheduled tasks (daily collection)
├── analytics/         # Usage analytics feature
│   ├── api/
│   ├── application/
│   └── persistence/
├── insight/           # AI-powered insights feature
│   ├── api/
│   └── application/
└── common/            # Shared components
    ├── config/        # Configuration classes
    └── error/         # Exception handling
```

## Scheduled Rate Collection

The system automatically collects exchange rates from Fixer.io once per day.

**Schedule:**
- Time: **00:05 UTC** daily
- Configurable via `scheduler.rate-collection.cron` and `scheduler.rate-collection.zone`
- Timezone is explicit (UTC) to avoid server timezone dependency

**Multi-Instance Safety:**

When multiple application instances are running (e.g., in Kubernetes or behind a load balancer):

1. **ShedLock** (database-backed distributed lock)
   - Prevents duplicate scheduler execution across instances
   - Uses `shedlock` PostgreSQL table
   - Uses database time (not application server time) to avoid clock skew issues
   - Lock duration: `lockAtMostFor=10m` (protects against crashed instances)
   - Lock minimum: `lockAtLeastFor=30s` (prevents rapid re-execution)

2. **PostgreSQL ON CONFLICT** (database-level protection)
   - Atomic upsert ensures no duplicate rates if lock fails
   - Unique constraint: `(rate_date, base_currency, currency_code)`
   - Provides data integrity even under concurrent writes

**Why Both Layers?**
- ShedLock reduces unnecessary load (only one instance fetches from Fixer)
- PostgreSQL constraint ensures database correctness regardless of lock behavior
- Defense in depth: if ShedLock fails, data remains consistent

**Transaction Boundary:**
```
Scheduler (no @Transactional)
  → RateCollectionService
     → HTTP fetch from Fixer.io (outside transaction)
     → Validation/normalization
     → RatePersistenceService (@Transactional)
        → PostgreSQL atomic upsert
```

HTTP requests execute **before** the database transaction to avoid holding connections during external calls.

**Error Handling:**
- Failures are logged with full context
- Exceptions propagate (ShedLock automatically releases lock)
- Next scheduled execution runs normally
- No automatic retry (waits for next daily schedule)

## Next Steps

Phase 4 will implement:
- Exchange calculator REST API (`/api/v1/exchange`)
- Request validation and rate date resolution
- Atomic concurrent usage tracking
- Controller tests

## AI Workflow

This project is being implemented with AI assistance following a structured, phase-by-phase approach. Each phase is reviewed before proceeding to ensure correctness and alignment with requirements.

---

*Documentation will be expanded as implementation progresses.*
