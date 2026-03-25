CREATE TYPE detection_type AS ENUM ('IMAGE', 'TEXT', 'VIDEO', 'AUDIO');
CREATE TYPE detection_status AS ENUM ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED');

CREATE TABLE detection_requests (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id),
    api_key_id          UUID REFERENCES api_keys(id),

    type                detection_type NOT NULL,
    status              detection_status NOT NULL DEFAULT 'PENDING',

    -- Input metadata
    content_url         TEXT,
    content_text        TEXT,
    content_hash        VARCHAR(64),
    file_size_bytes     BIGINT,
    mime_type           VARCHAR(100),
    language            VARCHAR(10),

    -- Result (denormalized for fast reads)
    is_synthetic        BOOLEAN,
    confidence_score    DECIMAL(5,4),
    model_version       VARCHAR(50),
    processing_ms       INTEGER,

    -- Compliance
    jurisdiction        VARCHAR(100) DEFAULT 'india_it_rules_2026',
    flagged_for_review  BOOLEAN NOT NULL DEFAULT FALSE,

    -- Webhook
    webhook_url         TEXT,
    webhook_delivered   BOOLEAN NOT NULL DEFAULT FALSE,

    error_message       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ
);

CREATE INDEX idx_detection_user ON detection_requests(user_id, created_at DESC);
CREATE INDEX idx_detection_status ON detection_requests(status) WHERE status IN ('PENDING', 'PROCESSING');
CREATE INDEX idx_detection_flagged ON detection_requests(flagged_for_review) WHERE flagged_for_review = TRUE;

-- Detailed signal breakdown per request
CREATE TABLE detection_signals (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id      UUID NOT NULL REFERENCES detection_requests(id) ON DELETE CASCADE,
    signal_name     VARCHAR(100) NOT NULL,
    signal_value    DECIMAL(5,4),
    signal_weight   DECIMAL(5,4),
    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_signals_request ON detection_signals(request_id);
