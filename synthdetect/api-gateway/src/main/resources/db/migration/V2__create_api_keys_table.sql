CREATE TYPE api_key_status AS ENUM ('ACTIVE', 'REVOKED', 'EXPIRED');

CREATE TABLE api_keys (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),

    -- Key storage: store SHA-256 hash, never plaintext
    key_hash        VARCHAR(64) NOT NULL UNIQUE,
    key_prefix      VARCHAR(12) NOT NULL,

    name            VARCHAR(100) NOT NULL DEFAULT 'Default',
    status          api_key_status NOT NULL DEFAULT 'ACTIVE',

    -- Permissions (comma-separated: detect,batch,compliance,admin)
    scopes          VARCHAR(500) NOT NULL DEFAULT 'detect',

    -- Usage tracking
    last_used_at    TIMESTAMPTZ,
    total_calls     BIGINT NOT NULL DEFAULT 0,

    -- Expiry
    expires_at      TIMESTAMPTZ,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at      TIMESTAMPTZ
);

CREATE INDEX idx_api_keys_hash ON api_keys(key_hash) WHERE status = 'ACTIVE';
CREATE INDEX idx_api_keys_user ON api_keys(user_id);
