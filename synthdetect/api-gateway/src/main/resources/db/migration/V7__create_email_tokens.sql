CREATE TYPE email_token_type AS ENUM ('EMAIL_VERIFICATION', 'PASSWORD_RESET');

CREATE TABLE email_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    type        email_token_type NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_tokens_user ON email_tokens(user_id);
CREATE INDEX idx_email_tokens_hash ON email_tokens(token_hash) WHERE used_at IS NULL;
