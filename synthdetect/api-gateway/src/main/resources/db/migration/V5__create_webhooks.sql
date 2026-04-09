CREATE TYPE webhook_status AS ENUM ('ACTIVE', 'INACTIVE', 'FAILED');
CREATE TYPE webhook_event AS ENUM ('detection.completed', 'detection.failed', 'quota.warning', 'quota.exceeded');

CREATE TABLE webhooks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    url             TEXT NOT NULL,
    secret          VARCHAR(64) NOT NULL,
    events          VARCHAR(500) NOT NULL DEFAULT 'detection.completed',
    status          webhook_status NOT NULL DEFAULT 'ACTIVE',
    description     VARCHAR(255),

    -- Health tracking
    last_success_at TIMESTAMPTZ,
    last_failure_at TIMESTAMPTZ,
    consecutive_failures INTEGER NOT NULL DEFAULT 0,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhooks_user ON webhooks(user_id) WHERE status = 'ACTIVE';

-- Delivery log
CREATE TABLE webhook_deliveries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    webhook_id      UUID NOT NULL REFERENCES webhooks(id) ON DELETE CASCADE,
    request_id      UUID REFERENCES detection_requests(id),
    event_type      webhook_event NOT NULL,
    payload         JSONB NOT NULL,
    response_status INTEGER,
    response_body   TEXT,
    duration_ms     INTEGER,
    delivered_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    success         BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_deliveries_webhook ON webhook_deliveries(webhook_id, delivered_at DESC);
