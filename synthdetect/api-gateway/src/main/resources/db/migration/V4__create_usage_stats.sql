-- Monthly usage rollup per user
CREATE TABLE usage_stats (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    year_month      CHAR(7) NOT NULL,          -- e.g. '2026-03'

    image_calls     INTEGER NOT NULL DEFAULT 0,
    text_calls      INTEGER NOT NULL DEFAULT 0,
    batch_calls     INTEGER NOT NULL DEFAULT 0,
    total_calls     INTEGER NOT NULL DEFAULT 0,

    quota_limit     INTEGER NOT NULL DEFAULT 500,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    UNIQUE (user_id, year_month)
);

CREATE INDEX idx_usage_user_month ON usage_stats(user_id, year_month);
