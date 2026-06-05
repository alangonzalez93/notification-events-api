# Notification Events API

Event notification service built with Java 21 + Spring Boot 3, hexagonal architecture, transactional outbox pattern, and async webhook delivery via a dedicated thread pool.

---

## How this was built — Spec Driven Development

This project follows **Spec Driven Development (SDD)**: no code is written until the spec for that feature is complete and reviewed.

Each feature lives in `specs/<number>-<feature>/` with three files:

| File | Purpose |
|---|---|
| `requirements.md` | User stories with acceptance criteria in WHEN/THEN format |
| `design.md` | Stack decisions, architecture, domain models, API contracts |
| `tasks.md` | Implementation phases with checkboxes, checked as completed |

### Why SDD?

Writing the spec first forces architectural decisions to be made explicitly, with justification, before any code is written. It also creates a natural review checkpoint — the spec is reviewed and approved before implementation begins, which prevents building the wrong thing.

The discipline of committing one phase at a time produces a git history that reads like a changelog of intentional decisions rather than a pile of "fix stuff" commits.

### Specs in this project

```
specs/
  01-setup/                   ← Project scaffold, Client entity, Docker, Flyway, base tests
  02-notification-delivery/   ← Webhook delivery, retry, scheduler, self-service API, security
```

---

## Architecture

Hexagonal (Ports & Adapters). The domain has zero imports from Spring or any infrastructure library.

```
domain/
  model/      ← pure Java records and enums
  port/in/    ← use case interfaces (driving ports)
  port/out/   ← repository + webhook interfaces (driven ports)
  usecase/    ← business logic implementations

infrastructure/
  web/        ← REST controllers (adapt HTTP → domain)
  persistence/← JPA entities + mappers (adapt DB → domain)
  webhook/    ← RestClient adapter (adapt HTTP out → domain)
  scheduler/  ← @Scheduled job + ShedLock
```

See [doc/system-design.md](doc/system-design.md) for the full design including C4 diagrams, sequence diagrams, and scalability/resilience analysis.

---

## Security

Three OWASP API Top 10 vulnerabilities identified and mitigated:

1. **API1 — Broken Object Level Authorization**: authorization check in every use case using the security context, never trusting user-supplied identifiers
2. **API2 — Broken Authentication**: Spring Security with role-based zones (PLATFORM, CLIENT, OPS)
3. **API8 — Security Misconfiguration**: Actuator whitelist-only exposure, bound to internal interface, OPS-role required

Full analysis in [doc/security.md](doc/security.md).

---

## Running the project

### 1. Prerequisites

- Docker Desktop running

> Java 21 is only needed to run the test suite locally. The app itself builds and runs entirely inside Docker — no local Java installation required to start the API.

### 2. Start

```bash
# Copy env file (only needed once)
cp .env.example .env

# Build image and start MySQL + API
docker compose up --build
```

Flyway runs V1 + V2 migrations automatically on startup. The API is ready when you see:

```
Started NotificationEventsApiApplication
```

### 3. Load seed data

In a separate terminal, once the API is up:

```bash
docker compose exec -T mysql mysql -u cobre -pcobre_pass notification_events_api < scripts/seed_data.sql
```

This creates 3 clients (`CLIENT001`, `CLIENT002`, `CLIENT003`), one subscription per client covering all event types, and 40 pre-loaded notification events in varied statuses (DELIVERED, FAILED, PENDING).

### 4. Stop

```bash
docker compose down          # keep DB volume
docker compose down -v       # also delete DB volume (clean reset)
```

---

## API reference

All endpoints are under `http://localhost:8080/api`.

### Subscriptions

```bash
# Create a subscription for CLIENT001
curl -s -X POST http://localhost:8080/api/clients/CLIENT001/subscriptions \
  -H "Content-Type: application/json" \
  -d '{
    "webhookUrl":      "https://webhook.site/your-unique-url",
    "authHeaderName":  "X-Webhook-Secret",
    "authHeaderValue": "my-secret",
    "eventTypes":      ["credit_transfer", "debit_purchase", "credit_deposit"]
  }' | jq

# Get CLIENT001's active subscription
curl -s http://localhost:8080/api/clients/CLIENT001/subscriptions | jq

# Update subscription (change webhook URL or event types)
# Replace {subscriptionUniqueCode} with the value returned above
curl -s -X PUT http://localhost:8080/api/clients/CLIENT001/subscriptions/{subscriptionUniqueCode} \
  -H "Content-Type: application/json" \
  -d '{
    "webhookUrl": "https://webhook.site/new-url",
    "active": true
  }' | jq

# Delete subscription
curl -s -X DELETE http://localhost:8080/api/clients/CLIENT001/subscriptions/{subscriptionUniqueCode} \
  -o /dev/null -w "%{http_code}"
```

### Platform event ingestion

> **Production note:** in a real deployment this entry point would not be an HTTP endpoint.
> It would be a message-driven adapter — an SQS listener (AWS), a Pub/Sub subscriber (GCP),
> or a Kafka consumer — that reacts to events published by the payments platform.
> The HTTP endpoint exists here only to make the flow testable during challenge evaluation.
> Because the use case (`IngestPlatformEventUseCase`) is fully decoupled from the transport layer,
> swapping this controller for a queue listener requires **zero changes** to application or domain code.

```bash
# Ingest a platform event — creates a PENDING notification if a matching subscription exists
curl -s -X POST http://localhost:8080/api/platform-events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId":          "EVT-LIVE-001",
    "eventType":        "credit_transfer",
    "payload":          "Bank transfer received for $500.00",
    "clientUniqueCode": "CLIENT001"
  }' | jq

# Response 201 → notification created and queued for delivery
# Response 202 → no matching subscription, event silently discarded
# Response 409 → duplicate eventId, already processed
```

### Notification events (self-service API)

```bash
# List all notifications for CLIENT001 (paginated)
curl -s "http://localhost:8080/api/notification_events?clientUniqueCode=CLIENT001" | jq

# Filter by delivery status
curl -s "http://localhost:8080/api/notification_events?clientUniqueCode=CLIENT001&deliveryStatus=pending" | jq
curl -s "http://localhost:8080/api/notification_events?clientUniqueCode=CLIENT001&deliveryStatus=failed" | jq
curl -s "http://localhost:8080/api/notification_events?clientUniqueCode=CLIENT001&deliveryStatus=delivered" | jq

# Filter by date range
curl -s "http://localhost:8080/api/notification_events?clientUniqueCode=CLIENT001&from=2024-01-01T00:00:00Z&to=2024-12-31T23:59:59Z" | jq

# Pagination (default size=20, max=100)
curl -s "http://localhost:8080/api/notification_events?clientUniqueCode=CLIENT001&page=0&size=5" | jq

# Get detail of a single notification
# Replace {uniqueCode} with a value from the list above
curl -s "http://localhost:8080/api/notification_events/{uniqueCode}?clientUniqueCode=CLIENT001" | jq

# Replay a failed notification (resets to PENDING and re-queues for delivery)
curl -s -X POST "http://localhost:8080/api/notification_events/{uniqueCode}/replay?clientUniqueCode=CLIENT001" \
  -o /dev/null -w "%{http_code}"
```

### Observability

```bash
# Health check — includes DB connectivity
curl -s http://localhost:8081/actuator/health | jq

# List all registered metrics
curl -s http://localhost:8081/actuator/metrics | jq '.names[]'

# Delivery counters — only appear after at least one event of that type has occurred
curl -s "http://localhost:8081/actuator/metrics/notifications.delivered" | jq
curl -s "http://localhost:8081/actuator/metrics/notifications.failed" | jq
curl -s "http://localhost:8081/actuator/metrics/notifications.retried" | jq
curl -s "http://localhost:8081/actuator/metrics/notifications.webhook.duration" | jq
```

> Micrometer registers a counter the first time it is incremented. If no notifications have been
> delivered/failed/retried yet, the endpoint returns 404 — that is expected behavior, not an error.
> Run the end-to-end demo flow below to generate real events and see the counters populate.

> Actuator runs on port **8081** (separate management port). In production, this port would be
> restricted to internal network access only — see [doc/security.md](doc/security.md).

### Swagger UI

```
http://localhost:8080/api/swagger-ui.html
```

Interactive docs with all endpoints, request/response schemas, and try-it-out.

### End-to-end demo flow

```bash
# 1. Create a subscription pointing to a public webhook inspector
#    Go to https://webhook.site and copy your unique URL first

WEBHOOK_URL="https://webhook.site/your-unique-url"
CLIENT="CLIENT001"

# 2. Update CLIENT001's subscription to use your webhook URL
SUB_CODE=$(curl -s http://localhost:8080/api/clients/$CLIENT/subscriptions | jq -r '.uniqueCode')

curl -s -X PUT http://localhost:8080/api/clients/$CLIENT/subscriptions/$SUB_CODE \
  -H "Content-Type: application/json" \
  -d "{\"webhookUrl\": \"$WEBHOOK_URL\", \"active\": true}" | jq

# 3. Ingest a new event
curl -s -X POST http://localhost:8080/api/platform-events \
  -H "Content-Type: application/json" \
  -d "{
    \"eventId\": \"EVT-DEMO-$(date +%s)\",
    \"eventType\": \"credit_transfer\",
    \"payload\": \"Transfer of \$1,000 received\",
    \"clientUniqueCode\": \"$CLIENT\"
  }" | jq

# 4. Watch the notification appear as PENDING, then DELIVERED
#    (dispatcher runs every 5 seconds)
curl -s "http://localhost:8080/api/notification_events?clientUniqueCode=$CLIENT&deliveryStatus=pending" | jq
# Wait ~5 seconds, then:
curl -s "http://localhost:8080/api/notification_events?clientUniqueCode=$CLIENT&deliveryStatus=delivered" | jq

# 5. Check webhook.site to see the received payload with X-Idempotency-Key header
```

---

## Testing

```bash
# Run the full test suite (requires Java 21 + Docker running for Testcontainers)
./mvnw test
```

The test suite spins up a real MySQL 8 container via Testcontainers and a WireMock server for webhook simulation. No mocked repositories — all tests run against real infrastructure.

**What's covered (26 tests):**

| Test class | What it covers |
|---|---|
| `RetryBackoffTest` | Exponential backoff formula — delay values and jitter range per attempt |
| `ClientJpaRepositoryTest` | Client persistence, soft delete, duplicate email constraint |
| `SubscriptionControllerTest` | Subscription CRUD, 409 on duplicate, 403 on wrong owner |
| `PlatformEventControllerTest` | Ingest 201/202, duplicate eventId → 409, unknown client → 404 |
| `NotificationEventControllerTest` | List with filters, pagination max=100 validation, detail, 403 on wrong client |
| `ReplayControllerTest` | Reset to PENDING, 409 if already PENDING |
| `WebhookDeliveryTest` | Full async delivery via WireMock: success → DELIVERED, 503 → retry+backoff, exhausted retries → FAILED |

---

