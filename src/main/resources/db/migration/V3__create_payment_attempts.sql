-- Payment Attempts table
-- Models each shopper payment try for a given PaymentIntent.
-- Invariants (enforced in domain layer):
--   - At most one SUCCEEDED attempt per payment_intent_id
--   - The SUCCEEDED attempt must be the latest one
CREATE TABLE payment_attempts (
    id                   VARCHAR(255)    PRIMARY KEY,
    payment_intent_id    VARCHAR(255)    NOT NULL REFERENCES payment_intents(id),
    amount               BIGINT          NOT NULL,
    currency             VARCHAR(3)      NOT NULL,
    status               VARCHAR(30)     NOT NULL,    -- PENDING | PROCESSING | REQUIRES_ACTION | SUCCEEDED | FAILED | CANCELLED
    payment_method_id    VARCHAR(255),
    captured_amount      BIGINT,
    processor_reference  VARCHAR(255),
    failure_code         VARCHAR(100),
    failure_message      TEXT,
    next_action          JSONB,                       -- { type, redirectUrl }
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Enforce: only one SUCCEEDED attempt per intent at the DB level
CREATE UNIQUE INDEX idx_pa_one_succeeded_per_intent
    ON payment_attempts(payment_intent_id)
    WHERE status = 'SUCCEEDED';

-- Common query indexes
CREATE INDEX idx_pa_payment_intent_id ON payment_attempts(payment_intent_id);
CREATE INDEX idx_pa_status             ON payment_attempts(status);
CREATE INDEX idx_pa_created_at         ON payment_attempts(created_at DESC);

-- Add latest_payment_attempt_id to payment_intents (denormalized for fast lookup)
ALTER TABLE payment_intents
    ADD COLUMN latest_payment_attempt_id VARCHAR(255) REFERENCES payment_attempts(id);

-- Auto-update trigger
CREATE TRIGGER update_payment_attempts_updated_at
    BEFORE UPDATE ON payment_attempts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
