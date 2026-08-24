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
- Docker and Docker Compose (recommended for local development)
- (Alternative) PostgreSQL 13+ and Ollama installed locally

### Option 1: Local Infrastructure with Docker Compose (Recommended)

The easiest way to run the backend locally is using Docker Compose for infrastructure:

1. **Start PostgreSQL and Ollama:**
```bash
docker compose up -d
```

This starts:
- PostgreSQL on `localhost:5432`
- Ollama on `localhost:11434`

2. **Verify services are running:**
```bash
docker compose ps
```

Expected output:
```
NAME                IMAGE                  STATUS
marcura-postgres    postgres:16-alpine     Up (healthy)
marcura-ollama      ollama/ollama:latest   Up
```

3. **Pull the Ollama language model:**
```bash
docker compose exec ollama ollama pull llama3.2
```

This downloads the `llama3.2` model (~2GB). This step is required for AI trend insight features.

4. **Run the Spring Boot application:**
```bash
mvn spring-boot:run
```

The application will connect to the Docker Compose infrastructure automatically using default configuration.

5. **Verify the application:**
- Health check: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui.html

**Shutdown:**

Stop services (keeps data):
```bash
docker compose down
```

Stop services and remove all data:
```bash
docker compose down -v
```

⚠️ **Warning:** The `-v` flag removes PostgreSQL database and Ollama model data. Use only for full cleanup.

**Troubleshooting:**

View logs:
```bash
docker compose logs postgres
docker compose logs ollama
```

Restart a service:
```bash
docker compose restart postgres
```

### Option 2: Manual Local Setup

If you prefer not to use Docker Compose:

#### PostgreSQL Setup

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

#### Ollama Setup

Follow the Ollama setup instructions in the **AI Trend Insight** section below.

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

## API Endpoints

### Exchange Rate Calculator

```http
GET /api/v1/exchange?from=EUR&to=PLN&date=2024-03-15
```

Calculates spread-adjusted exchange rate between two currencies.

**Parameters:**
- `from` (required): Source currency code (e.g., EUR)
- `to` (required): Target currency code (e.g., PLN)
- `date` (optional): Rate date in ISO format (YYYY-MM-DD). If omitted, uses most recent available rate.

**Response:**
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

```http
GET /api/v1/exchange/history?from=EUR&to=GBP&fromDate=2024-02-01&toDate=2024-03-01
```

Retrieves historical exchange rates for a currency pair.

**Parameters:**
- `from` (required): Source currency code
- `to` (required): Target currency code
- `fromDate` (required): Start date (ISO format)
- `toDate` (required): End date (ISO format)

**Response:**
```json
{
  "from": "EUR",
  "to": "GBP",
  "fromDate": "2024-02-01",
  "toDate": "2024-03-01",
  "rates": [
    {
      "date": "2024-02-01",
      "rawRate": 0.85421,
      "adjustedRate": 0.83284
    }
  ]
}
```

### Analytics

```http
GET /api/v1/analytics
```

Retrieves currency usage analytics.

**Response:**
```json
{
  "topCurrencies": [
    {
      "currencyCode": "EUR",
      "totalQueries": 1523,
      "lastQueriedDate": "2024-03-15"
    }
  ],
  "dailyUsage": [
    {
      "date": "2024-03-15",
      "totalQueries": 342
    }
  ]
}
```

### AI Trend Insight

```http
GET /api/v1/exchange/insight?from=EUR&to=GBP&fromDate=2024-02-01&toDate=2024-03-01
```

Generates AI-powered natural-language trend analysis for historical exchange rates.

**Parameters:**
- `from` (required): Source currency code
- `to` (required): Target currency code
- `fromDate` (required): Start date (ISO format)
- `toDate` (required): End date (ISO format)

**Response:**
```json
{
  "from": "EUR",
  "to": "GBP",
  "fromDate": "2024-02-01",
  "toDate": "2024-03-01",
  "insight": "EUR/GBP declined by approximately 1.8% over the selected period, with the strongest downward movement occurring near the end of February."
}
```

**Status Codes:**
- `200 OK` — Insight generated successfully
- `400 Bad Request` — Invalid parameters (invalid currency, invalid date range)
- `404 Not Found` — No historical data available for the selected period
- `503 Service Unavailable` — AI provider (Ollama) is unavailable

#### Ollama Setup

The AI trend insight feature requires a running Ollama instance with a configured language model.

**Install Ollama:**

```bash
# macOS
brew install ollama

# Linux
curl -fsSL https://ollama.com/install.sh | sh

# Windows: Download from https://ollama.com/download
```

**Pull a language model:**

```bash
ollama pull llama3.2
```

**Start Ollama server:**

```bash
ollama serve
```

**Configuration:**

The application uses the following environment variables (or defaults):

```bash
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_MODEL=llama3.2
```

Configuration properties in `application.yml`:
```yaml
spring:
  ai:
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      chat:
        model: ${OLLAMA_MODEL:llama3.2}
```

#### AI Behavior

The AI trend insight feature uses a **deliberate system prompt** that constrains the language model to:

- **Use only supplied historical data**: Actual raw exchange rates from PostgreSQL are injected into the prompt. The model does not have access to external market data.
- **No fabricated dates**: Missing dates in the historical record remain missing (no interpolation).
- **No invented causes**: The model is explicitly instructed not to invent news, market events, or geopolitical explanations.
- **No financial advice**: The model is prohibited from providing investment recommendations or predictions.
- **No unsupported predictions**: The model describes only observable movement in the supplied period.
- **Concise output**: Maximum two short sentences.

**Example prompt structure:**
```
System: You are a concise exchange-rate trend analyst.
Analyze only the historical exchange-rate data supplied by the application.
Rules: Use only the provided numbers. Do not invent news, causes...

User: Currency pair: EUR/GBP
Period: 2024-02-01 to 2024-03-01

Historical raw cross-rates:
2024-02-01: 0.85421
2024-02-02: 0.85610
2024-02-05: 0.85143
...
```

#### Failure Behavior

| Scenario | HTTP Status | Error Code | Response |
|----------|-------------|------------|----------|
| No historical data in selected period | 404 | `RATE_NOT_FOUND` | Standard error response |
| Only one historical data point | 200 | — | Deterministic message: "Insufficient historical data to determine a trend for the selected period." |
| AI provider (Ollama) unavailable | 503 | `AI_UNAVAILABLE` | "AI provider is currently unavailable" |

**Important:**
- The AI insight endpoint is **read-only** and does **NOT** increment currency usage counters.
- Only the `/api/v1/exchange` calculator endpoint increments usage.

## AI Workflow

This project is being implemented with AI assistance following a structured, phase-by-phase approach. Each phase is reviewed before proceeding to ensure correctness and alignment with requirements.

---

*Documentation will be expanded as implementation progresses.*
