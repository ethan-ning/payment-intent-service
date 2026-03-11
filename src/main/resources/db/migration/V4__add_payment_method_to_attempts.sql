-- Add payment method (scheme/type) fields to payment_attempts
-- This is a VALUE OBJECT — the method the shopper used for this attempt.
-- Stored as JSONB for flexible sub-type schema (card, wechat_pay, etc.)
ALTER TABLE payment_attempts
    ADD COLUMN payment_method      JSONB,
    ADD COLUMN payment_method_type VARCHAR(50);   -- denormalized for indexed queries

-- Index for analytics: "how many WECHAT_PAY attempts this week?"
CREATE INDEX idx_pa_payment_method_type ON payment_attempts(payment_method_type);

-- Add available_payment_methods to payment_intents
-- Comma-separated list of PaymentMethodType enum values
-- Empty string = all methods available (no restriction)
ALTER TABLE payment_intents
    ADD COLUMN available_payment_methods VARCHAR(500) NOT NULL DEFAULT '';

-- Drop old payment_method_id column (replaced by payment_method value object)
ALTER TABLE payment_attempts DROP COLUMN IF EXISTS payment_method_id;
