# Marcura Exchange Rate Management System - Backend

Backend implementation for the Marcura Full Stack Developer Technical Assessment.

## Phase 0 Status: ✅ Complete

### What's Been Completed

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
- Health check: http://localhost:8080/api/v1/health
- Swagger UI: http://localhost:8080/swagger-ui.html
- API docs: http://localhost:8080/api-docs

## Technology Stack

- **Java**: 17+
- **Framework**: Spring Boot 4.1.1
- **Build Tool**: Maven
- **Database**: PostgreSQL
- **Migration**: Liquibase
- **API Documentation**: Springdoc OpenAPI
- **Scheduler**: Spring Scheduler + ShedLock (JDBC)
- **AI Integration**: Spring AI with Ollama
- **Testing**: JUnit 5, Mockito, Testcontainers

## Project Structure

```
src/main/java/com/example/marcuraexchangeratebackend/
├── exchange/           # Exchange rate calculation feature
│   ├── api/           # REST controllers
│   ├── application/   # Application services
│   ├── domain/        # Domain logic
│   └── persistence/   # Repositories
├── rate/              # Rate collection feature
│   ├── application/
│   ├── domain/
│   ├── persistence/
│   ├── provider/      # External API clients (Fixer)
│   └── scheduler/     # Scheduled tasks
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

## Next Steps

Phase 1 will implement:
- Database schema (exchange_rate, currency_usage_daily, shedlock tables)
- JPA entities and repositories
- Spread policy and exchange calculation domain logic
- Unit tests for calculation formula

## AI Workflow

This project is being implemented with AI assistance following a structured, phase-by-phase approach. Each phase is reviewed before proceeding to ensure correctness and alignment with requirements.

---

*Documentation will be expanded as implementation progresses.*
