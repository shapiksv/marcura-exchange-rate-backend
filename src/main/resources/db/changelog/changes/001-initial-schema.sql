-- liquibase formatted sql

-- changeset ai-agent:001-create-exchange-rate-table
-- comment: Create exchange_rate table to store currency exchange rates from Fixer.io
CREATE TABLE exchange_rate (
    id                BIGSERIAL PRIMARY KEY,
    rate_date         DATE                        NOT NULL,
    base_currency     VARCHAR(3)                  NOT NULL,
    currency_code     VARCHAR(3)                  NOT NULL,
    rate_value        NUMERIC(19, 10)             NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_exchange_rate_date_base_currency UNIQUE (rate_date, base_currency, currency_code)
);

CREATE INDEX idx_exchange_rate_date ON exchange_rate (rate_date);
CREATE INDEX idx_exchange_rate_currency_date ON exchange_rate (currency_code, rate_date);
CREATE INDEX idx_exchange_rate_base_currency_date ON exchange_rate (base_currency, currency_code, rate_date);
-- rollback DROP TABLE exchange_rate CASCADE;

-- changeset ai-agent:002-create-currency-usage-daily-table
-- comment: Create currency_usage_daily table for atomic usage tracking
CREATE TABLE currency_usage_daily (
    id                BIGSERIAL PRIMARY KEY,
    currency_code     VARCHAR(3)                  NOT NULL,
    query_date        DATE                        NOT NULL,
    query_count       BIGINT                      NOT NULL DEFAULT 0,
    last_queried_at   TIMESTAMP WITH TIME ZONE    NOT NULL,
    CONSTRAINT uk_currency_usage_daily_currency_date UNIQUE (currency_code, query_date)
);

CREATE INDEX idx_currency_usage_daily_date ON currency_usage_daily (query_date);
-- rollback DROP TABLE currency_usage_daily CASCADE;

-- changeset ai-agent:003-create-shedlock-table
-- comment: Create shedlock table for distributed scheduler lock
CREATE TABLE shedlock (
    name        VARCHAR(64) PRIMARY KEY,
    lock_until  TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_by   VARCHAR(255)             NOT NULL
);
-- rollback DROP TABLE shedlock CASCADE;
