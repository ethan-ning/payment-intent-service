-- Payment Intents table
CREATE TABLE payment_intents (
    id                  VARCHAR(255)    PRIMARY KEY,
    amount              BIGINT          NOT NULL CHECK (amount > 0),
    currency            VARCHAR(3)      NOT NULL,
    status              VARCHAR(50)     NOT NULL,
    capture_method      VARCHAR(20)     NOT NULL DEFAULT 'automatic',
    confirmation_method VARCHAR(20)     NOT NULL DEFAULT 'automatic',
    customer_id         VARCHAR(255),
    payment_method_id   VARCHAR(255),
    description         TEXT,
    metadata            JSONB           NOT NULL DEFAULT '{}',
    idempotency_key     VARCHAR(255)    UNIQUE,
    client_secret       VARCHAR(500)    NOT NULL,
    canceled_at         TIMESTAMPTZ,
    cancellation_reason VARCHAR(50),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Indexes for common query patterns
CREATE INDEX idx_pi_customer_id   ON payment_intents(customer_id);
CREATE INDEX idx_pi_status        ON payment_intents(status);
CREATE INDEX idx_pi_created_at    ON payment_intents(created_at DESC);
CREATE INDEX idx_pi_idempotency   ON payment_intents(idempotency_key) WHERE idempotency_key IS NOT NULL;

-- Auto-update updated_at trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_payment_intents_updated_at
    BEFORE UPDATE ON payment_intents
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
