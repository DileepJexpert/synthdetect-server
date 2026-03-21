CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TYPE user_plan AS ENUM ('FREE', 'STARTER', 'BUSINESS', 'ENTERPRISE');
CREATE TYPE user_status AS ENUM ('ACTIVE', 'SUSPENDED', 'DELETED');

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    company_name    VARCHAR(255),
    plan            user_plan NOT NULL DEFAULT 'FREE',
    status          user_status NOT NULL DEFAULT 'ACTIVE',

    -- Plan limits (overridable per user for enterprise)
    monthly_quota   INTEGER NOT NULL DEFAULT 500,
    rate_limit_rpm  INTEGER NOT NULL DEFAULT 60,

    -- Metadata
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login_at   TIMESTAMPTZ,

    -- Soft delete
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_users_email ON users(email) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_status ON users(status);
