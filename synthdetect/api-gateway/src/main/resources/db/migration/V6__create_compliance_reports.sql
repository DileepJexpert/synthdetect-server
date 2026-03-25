CREATE TYPE compliance_action AS ENUM ('FLAGGED', 'TAKEDOWN_REQUESTED', 'TAKEDOWN_COMPLETED', 'CLEARED', 'ESCALATED');

CREATE TABLE compliance_reports (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id          UUID NOT NULL REFERENCES detection_requests(id),
    user_id             UUID NOT NULL REFERENCES users(id),

    jurisdiction        VARCHAR(100) NOT NULL DEFAULT 'india_it_rules_2026',
    action              compliance_action NOT NULL DEFAULT 'FLAGGED',

    -- Reporter info (for takedown requests)
    reporter_name       VARCHAR(255),
    reporter_email      VARCHAR(255),
    reporter_org        VARCHAR(255),
    description         TEXT,

    -- Deadlines
    reported_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deadline_at         TIMESTAMPTZ,           -- 3 hours for India IT Rules 2026
    resolved_at         TIMESTAMPTZ,
    resolution_notes    TEXT,

    -- Audit
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_compliance_user ON compliance_reports(user_id, created_at DESC);
CREATE INDEX idx_compliance_pending ON compliance_reports(action, deadline_at)
    WHERE action IN ('FLAGGED', 'TAKEDOWN_REQUESTED');
