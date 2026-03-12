-- V5: add setup_future_usage and payment_instrument_id to payment_intents
--
-- setup_future_usage: whether to save the payment method after a successful CIT.
--   ON_SESSION  — save for convenience, customer present for future payments.
--   OFF_SESSION — save for recurring/MIT, merchant charges without customer present.
--
-- payment_instrument_id: the pm_xxx ID in payment-instrument-service created after
--   a successful CIT with setup_future_usage set. Populated post-success.

ALTER TABLE payment_intents
    ADD COLUMN IF NOT EXISTS setup_future_usage   VARCHAR(20),
    ADD COLUMN IF NOT EXISTS payment_instrument_id VARCHAR(60);

COMMENT ON COLUMN payment_intents.setup_future_usage IS
    'ON_SESSION or OFF_SESSION. Set at intent create or confirm time. '
    'Triggers PaymentInstrument creation in payment-instrument-service on CIT success.';

COMMENT ON COLUMN payment_intents.payment_instrument_id IS
    'ID of the PaymentInstrument (pm_xxx) created in payment-instrument-service '
    'after a successful CIT with setup_future_usage set. Null until success.';

CREATE INDEX IF NOT EXISTS idx_pi_payment_instrument_id
    ON payment_intents (payment_instrument_id)
    WHERE payment_instrument_id IS NOT NULL;
