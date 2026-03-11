# payment-intent-service

A production-grade Payment Intent microservice built with **Kotlin + Spring Boot**, following **Clean Architecture** (Uncle Bob).

Stripe-compatible API surface.

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Adapter Layer                        │
│   REST Controllers (PaymentIntentController)            │
│   Request/Response DTOs                                 │
│   Global Exception Handler                             │
└──────────────────────┬──────────────────────────────────┘
                       │  depends on (inbound ports)
┌──────────────────────▼──────────────────────────────────┐
│                  Application Layer                      │
│   Use Case Interfaces (input ports)                     │
│   Use Case Implementations                             │
│   Repository / External Service Interfaces (out ports)  │
└──────────────────────┬──────────────────────────────────┘
                       │  depends on
┌──────────────────────▼──────────────────────────────────┐
│                   Domain Layer                          │
│   PaymentIntent (Aggregate)                            │
│   Value Objects: Money, Currency                       │
│   Domain Events: PaymentIntentEvent                    │
│   State Machine (enforced in domain methods)           │
│   Domain Exceptions                                    │
│   ← NO framework dependencies                         │
└─────────────────────────────────────────────────────────┘
                       ↑
┌──────────────────────┴──────────────────────────────────┐
│               Infrastructure Layer                      │
│   JooqPaymentIntentRepository (implements out port)     │
│   JooqOutboxRepository                                 │
│   OutboxPoller (SELECT FOR UPDATE SKIP LOCKED)         │
│   KafkaEventPublisher                                  │
│   RedisIdempotencyStore                                │
│   NoOpPaymentProcessor (swap with real processor)      │
│   Spring @Configuration wiring                         │
└─────────────────────────────────────────────────────────┘
```

### Dependency Rule
> Source code dependencies must point inward. Nothing in an inner circle can know anything about something in an outer circle.

- `domain` → knows nothing about anyone
- `application` → knows `domain` only
- `adapter` → knows `application` (via ports), never touches `domain` directly for business logic
- `infrastructure` → implements `application` ports, wires with Spring

---

## API Reference

All endpoints are Stripe-compatible.

### Create Payment Intent
```http
POST /v1/payment_intents
Idempotency-Key: <uuid>
Content-Type: application/json

{
  "amount": 2000,
  "currency": "usd",
  "customer_id": "cus_123",
  "description": "Coffee × 2",
  "capture_method": "automatic",
  "metadata": { "order_id": "ord_456" }
}
```

### Confirm
```http
POST /v1/payment_intents/{id}/confirm

{
  "payment_method_id": "pm_card_visa"
}
```

### Capture (manual capture only)
```http
POST /v1/payment_intents/{id}/capture

{
  "amount_to_capture": 1500
}
```

### Cancel
```http
POST /v1/payment_intents/{id}/cancel

{
  "cancellation_reason": "requested_by_customer"
}
```

### Get
```http
GET /v1/payment_intents/{id}
```

### List
```http
GET /v1/payment_intents?customer_id=cus_123&limit=10
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
        │ attachPaymentMethod()
        ▼
REQUIRES_CONFIRMATION
        │
        │ confirm()
        ├──────────────────────► REQUIRES_ACTION ──► REQUIRES_CAPTURE
        │                                        └──► PROCESSING
        ├──────────────────────► PROCESSING
        └──────────────────────► REQUIRES_CAPTURE
                │                       │
                │ succeed()             │ capture()
                ▼                       ▼
           SUCCEEDED               SUCCEEDED

Any cancellable state ──► CANCELED
```

---

## Outbox Pattern

```
┌─────────────────────────────────────────────┐
│ DB Transaction                              │
│  1. UPDATE payment_intents SET status = ... │
│  2. INSERT INTO outbox_events (payload)     │
└──────────────────┬──────────────────────────┘
                   │ (atomic)
    OutboxPoller (every 1s)
    SELECT ... FOR UPDATE SKIP LOCKED
                   │
                   ▼
            Kafka: payment-intents.events
```

---

## Running Locally

```bash
# 1. Start infrastructure
docker-compose up -d

# 2. Run migrations (auto via Flyway on startup)

# 3. Start service
./gradlew bootRun

# 4. Test
curl -X POST http://localhost:8080/v1/payment_intents \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"amount": 2000, "currency": "usd"}'
```

Kafka UI: http://localhost:8090

---

## Running Tests

```bash
# Domain unit tests (no Spring, fast)
./gradlew test --tests "*.domain.*"

# All tests
./gradlew test
```

---

## Key Design Decisions

| Decision | Rationale |
|---|---|
| jOOQ over Hibernate | Fine-grained SQL control, explicit queries, no N+1 surprises |
| Outbox over direct Kafka publish | Guaranteed at-least-once delivery without 2PC |
| `SELECT FOR UPDATE SKIP LOCKED` | Safe concurrent outbox polling across multiple instances |
| Money as `Long` minor units | Avoids floating-point precision issues entirely |
| State machine in domain | Business rules enforced at the aggregate, not scattered in services |
| No `@Transactional` in use cases | Transaction boundaries belong in infrastructure, not application logic |
| Idempotency keys in Redis | Fast lookup, TTL-based expiry, decoupled from DB |
