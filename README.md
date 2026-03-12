# payment-intent-service

Production-grade Payment Intent microservice — Kotlin + Spring Boot + Clean Architecture.

Implements a [Stripe-compatible PaymentIntent API](https://docs.stripe.com/api/payment_intents) with full state machine, PaymentAttempt tracking, CIT/MIT support, and transactional event publishing via the Outbox pattern.

---

## What This Service Does

A **PaymentIntent** represents a merchant's intent to collect payment from a customer. It orchestrates the full checkout flow: from payment method selection, through processing (including 3DS), to capture.

### Relationship to `payment-instrument-service`

When a customer checks out and opts to save their payment method (`setup_future_usage`), this service calls `payment-instrument-service` after a successful CIT to:
1. Create a `PaymentInstrument` (pm_xxx) in the instrument service
2. Record the `networkTransactionId` from the acquiring network — establishing the stored credential for future MIT charges

```
payment-intent-service                       payment-instrument-service
──────────────────────                       ──────────────────────────
POST /v1/payment_intents/:id/confirm
  { payment_method: {...},
    setup_future_usage: "off_session" }
        │
        │  on success
        ▼
POST /v1/payment_methods                 ─── create instrument (pm_xxx)
POST /v1/payment_methods/pm_xxx/stored-credential ─── record networkTransactionId

        │  result: payment_instrument_id: "pm_xxx"
        ▼
PaymentIntent.paymentInstrumentId = "pm_xxx"
```

---

## CIT / MIT (Customer vs Merchant Initiated Transactions)

| | CIT | MIT |
|---|---|---|
| **Who initiates** | Customer present at checkout | Merchant, without customer |
| **Examples** | First checkout, save card | Subscription renewal, installment |
| **SCA (EU)** | Full 3DS required | Exempt if stored credential established |
| **`setup_future_usage`** | `on_session` or `off_session` set here | Uses saved pm_xxx from instrument service |
| **`networkTransactionId`** | Returned by acquirer, stored on instrument | Referenced for MIT exemption |

### setup_future_usage values

| Value | Meaning |
|---|---|
| `on_session` | Save for convenience — customer will be present for future payments |
| `off_session` | Save for recurring/MIT — merchant will charge without customer |

### Flow: CIT → instrument creation → MIT

```
1. Merchant creates intent
   POST /v1/payment_intents
     { amount: 2000, currency: "sgd", customer: "cus_001",
       setup_future_usage: "off_session" }

2. Customer confirms at checkout
   POST /v1/payment_intents/pi_xxx/confirm
     { payment_method: { type: "CARD", scheme: "VISA", last4: "4242", ... } }

3. payment-intent-service processes CIT:
   - Sends to payment processor → acquirer returns networkTransactionId
   - Creates PaymentInstrument in instrument service: pm_abc
   - Records networkTransactionId on pm_abc (MIT consent established)
   - Response: { status: "succeeded", payment_instrument_id: "pm_abc" }

4. Future MIT (subscription renewal):
   POST /v1/payment_intents
     { amount: 2000, currency: "sgd", customer: "cus_001" }
   POST /v1/payment_intents/pi_yyy/confirm
     { payment_instrument_id: "pm_abc" }
   → Uses stored credential framework, SCA exemption applies
```

---

## API Reference

All endpoints are Stripe-compatible. Service runs on port **8080**.

### Create Payment Intent

```http
POST /v1/payment_intents
Idempotency-Key: <uuid>
Content-Type: application/json

{
  "amount": 2000,
  "currency": "sgd",
  "customer_id": "cus_001",
  "description": "Order #1234",
  "capture_method": "AUTOMATIC",
  "available_payment_methods": ["CARD", "PAYNOW", "GRABPAY"],
  "setup_future_usage": "off_session",
  "metadata": { "order_id": "ord_456" }
}
```

### Confirm (with transient payment method)

```http
POST /v1/payment_intents/{id}/confirm

{
  "payment_method": {
    "type": "CARD",
    "scheme": "VISA",
    "last4": "4242",
    "expiryMonth": 12,
    "expiryYear": 2027
  },
  "setup_future_usage": "off_session"
}
```

Response includes `payment_instrument_id: "pm_xxx"` after success with `setup_future_usage`.

### Confirm (with saved instrument — MIT)

```http
POST /v1/payment_intents/{id}/confirm

{
  "payment_instrument_id": "pm_abc123"
}
```

### Capture (manual capture only)

```http
POST /v1/payment_intents/{id}/capture
{ "amount_to_capture": 1500 }
```

### Cancel

```http
POST /v1/payment_intents/{id}/cancel
{ "cancellation_reason": "requested_by_customer" }
```

### Get / List

```http
GET /v1/payment_intents/{id}
GET /v1/payment_intents?customer_id=cus_001&limit=10
```

### Error Response (Stripe-compatible)

```json
{
  "error": {
    "type": "invalid_request_error",
    "code": "payment_intent_unexpected_state",
    "message": "PaymentIntent[pi_xxx]: invalid transition from succeeded to canceled"
  }
}
```

---

## State Machine

```
REQUIRES_PAYMENT_METHOD
        │
        │ attachPaymentMethod() / confirm()
        ▼
REQUIRES_CONFIRMATION
        │
        │ confirm()
        ├──────────────────────► REQUIRES_ACTION (3DS challenge)
        │                              │
        │                              │ completeAction()
        │                              ▼
        ├──────────────────────► PROCESSING
        │                              │ succeed()
        │                              ▼
        └──────────────────────► REQUIRES_CAPTURE ──► SUCCEEDED
                                                capture()

Any cancellable state ──► CANCELED
```

### PaymentAttempt state machine

Each `confirm()` creates a new `PaymentAttempt`. One intent can have multiple attempts (retries after failure).

```
PENDING → PROCESSING → SUCCEEDED
                    └─► REQUIRES_ACTION → SUCCEEDED | FAILED
                    └─► FAILED
        └─► CANCELLED
```

Invariants enforced in the domain:
- No new attempt after one has `SUCCEEDED`
- The `SUCCEEDED` attempt must be the most recent attempt

---

## Payment Method Types

This service accepts transient payment methods (value objects) at confirm time. For saved instruments, reference a `pm_xxx` ID from `payment-instrument-service`.

| Category | Types |
|---|---|
| **Card** | `CARD` (Visa, Mastercard, AMEX, UnionPay) |
| **Device Wallet** | `APPLE_PAY`, `GOOGLE_PAY` (card passthrough) |
| **Digital Wallet** | `PAYPAL`, `ALIPAY`, `WECHAT_PAY`, `GRABPAY` |
| **Real-time Bank** | `PAYNOW` (SG), `PROMPTPAY` (TH), `FPS` (HK), `UPI` (IN), `SEPA_INSTANT`, `FASTER_PAYMENTS` |
| **BNPL** | `KLARNA`, `AFTERPAY`, `ATOME` |
| **Bank Transfer** | `BANK_TRANSFER` (ACH, SEPA, BACS) |

---

## Architecture

```
adapter/rest/v1/          ← Controllers, Request/Response DTOs, Exception handler
application/
  port/inbound/           ← Use case interfaces + command objects
  port/outbound/          ← Repository, processor, instrument service, outbox interfaces
  usecase/                ← Business logic (no Spring, no framework)
domain/
  model/                  ← PaymentIntent, PaymentAttempt, PaymentMethod (sealed), Money
  event/                  ← PaymentIntentEvent, PaymentAttemptEvent (sealed)
  exception/              ← Domain exceptions
infrastructure/
  persistence/            ← JPA entities, Spring Data repos, domain↔entity mappers
    outbox/               ← Transactional Outbox + Poller (SELECT FOR UPDATE SKIP LOCKED)
  client/                 ← InstrumentServiceHttpClient (calls payment-instrument-service)
  messaging/              ← Kafka publisher
  cache/                  ← Redis idempotency store
  config/                 ← Spring bean wiring
```

### Key design decisions

| Decision | Rationale |
|---|---|
| Spring Data JPA over jOOQ | Simpler for domain-entity mapping; Flyway owns schema |
| Outbox over direct Kafka | Guaranteed at-least-once delivery without 2PC |
| `SELECT FOR UPDATE SKIP LOCKED` | Safe concurrent outbox polling across replicas |
| `Money` as `Long` minor units | Avoids floating-point precision issues |
| State machine in domain layer | Business rules enforced at the aggregate, not scattered in services |
| `PaymentMethod` as value object | Transient — not persisted as its own entity; use instrument service for saved methods |
| Instrument service call is best-effort | A post-auth failure must not roll back the payment |
| `InstrumentServiceClient` optional | `@ConditionalOnProperty` — safe to run standalone without instrument service |

---

## Running Locally

```bash
# 1. Start infrastructure
docker-compose up -d

# 2. Start service (Flyway migrations run automatically on startup)
./gradlew bootRun

# 3. Test
curl -X POST http://localhost:8080/v1/payment_intents \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "amount": 2000,
    "currency": "sgd",
    "setup_future_usage": "off_session"
  }'
```

### Run with instrument service integration

```bash
INSTRUMENT_SERVICE_URL=http://localhost:8082 ./gradlew bootRun
```

### Infrastructure ports

| Service | Port |
|---|---|
| payment-intent-service | 8080 |
| PostgreSQL | 5432 |
| Redis | 6379 |
| Kafka | 9092 |
| Kafka UI | 8090 |

---

## Running Tests

```bash
# Domain unit tests only (no Spring, fast)
./gradlew test --tests "*.domain.*"

# All tests
./gradlew test
```

---

## Events Published (Kafka: `payment-intents.events`)

| Event | When |
|---|---|
| `PaymentIntentCreated` | New intent created |
| `PaymentMethodAttached` | Shopper picked a payment method |
| `PaymentIntentConfirmed` | Confirm called, processing started |
| `ActionRequired` | 3DS or redirect challenge triggered |
| `PaymentIntentSucceeded` | Payment captured |
| `PaymentIntentCaptured` | Manual capture completed |
| `PaymentIntentCanceled` | Intent canceled |
| `AttemptFailed` | Individual attempt failed (intent allows retry) |

---

## DB Migrations

| Version | Description |
|---|---|
| V1 | `payment_intents` table |
| V2 | `outbox_events` table |
| V3 | `payment_attempts` table |
| V4 | `payment_method` JSONB + `available_payment_methods` on attempts |
| V5 | `setup_future_usage` + `payment_instrument_id` on payment_intents |
