-- ─────────────────────────────────────────────────────────────────
-- V1__init_schema.sql
-- TravelAPI initial database schema (PostgreSQL / GCP Cloud SQL)
-- ─────────────────────────────────────────────────────────────────

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    full_name   VARCHAR(255) NOT NULL,
    role        VARCHAR(50)  NOT NULL DEFAULT 'USER',
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Trips table
CREATE TABLE IF NOT EXISTS trips (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title        VARCHAR(255) NOT NULL,
    destination  VARCHAR(255) NOT NULL,
    start_date   DATE         NOT NULL,
    end_date     DATE         NOT NULL,
    notes        TEXT,
    status       VARCHAR(50)  NOT NULL DEFAULT 'PLANNED',
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Reminders table
CREATE TABLE IF NOT EXISTS reminders (
    id           BIGSERIAL PRIMARY KEY,
    trip_id      BIGINT       NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title        VARCHAR(255) NOT NULL,
    remind_at    TIMESTAMP    NOT NULL,
    sent         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Trip attachments (metadata; file stored in GCS)
CREATE TABLE IF NOT EXISTS attachments (
    id           BIGSERIAL PRIMARY KEY,
    trip_id      BIGINT       NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    file_name    VARCHAR(255) NOT NULL,
    gcs_path     VARCHAR(512) NOT NULL,
    content_type VARCHAR(100),
    size_bytes   BIGINT,
    uploaded_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_trips_user_id      ON trips(user_id);
CREATE INDEX IF NOT EXISTS idx_trips_status        ON trips(status);
CREATE INDEX IF NOT EXISTS idx_reminders_trip_id   ON reminders(trip_id);
CREATE INDEX IF NOT EXISTS idx_reminders_remind_at ON reminders(remind_at) WHERE sent = FALSE;
CREATE INDEX IF NOT EXISTS idx_attachments_trip_id ON attachments(trip_id);
