# Requirements — Notification Delivery

## Context

This spec covers the full scope of Task 2 from the challenge. Two distinct entities exist:

- **`PlatformEvent`** — a raw event emitted by the platform (balance update, payment, etc.).
  In production this would arrive via a message queue (SQS, Kafka). For this challenge it arrives
  via a REST ingest endpoint. The system evaluates it against active subscriptions and, if a match
  exists, creates a `NotificationEvent`. Otherwise it is discarded.

- **`NotificationEvent`** — the entity that represents the intent to notify a client. It is created
  only when a matching subscription exists. It owns its delivery lifecycle (status, retries,
  audit trail) and is the subject of the client-facing self-service API.

Authentication on inbound endpoints is intentionally deferred and addressed in the security spec.

---

## User Stories

### 1. Domain Enumerations

**US-1.1** — As a developer, I want a strongly-typed `EventType` enum so that event types are
validated at compile time and never represented as arbitrary strings.

**Acceptance Criteria:**
- `EventType` shall be defined in `domain.model` with values:
  `CREDIT_CARD_PAYMENT`, `DEBIT_CARD_WITHDRAWAL`, `CREDIT_TRANSFER`, `DEBIT_AUTOMATIC_PAYMENT`,
  `CREDIT_REFUND`, `DEBIT_TRANSFER`, `CREDIT_DEPOSIT`, `DEBIT_PURCHASE`,
  `CREDIT_CASHBACK`, `DEBIT_SUBSCRIPTION`.
- The enum shall serialize/deserialize to/from snake_case (`CREDIT_TRANSFER` ↔ `"credit_transfer"`)
  in JSON payloads and database columns.

**US-1.2** — As a developer, I want a `DeliveryStatus` enum to represent the lifecycle of a
`NotificationEvent`.

**Acceptance Criteria:**
- `DeliveryStatus` shall be defined in `domain.model` with values:
  - `PENDING` — created and waiting to be dispatched (also reset state after a retryable failure).
  - `PROCESSING` — claimed by the dispatcher; delivery in progress.
  - `DELIVERED` — webhook returned 2xx; terminal success state.
  - `FAILED` — all retry attempts exhausted; terminal failure state.

---

### 2. Subscription Management

**US-2.1** — As a client, I want to register a webhook subscription so the platform knows where
to deliver notifications and which event types I want to receive.

**Acceptance Criteria:**
- Each client SHALL have at most one active (non-deleted) subscription at a time.
- A subscription SHALL store:
  - `webhookUrl` — HTTPS endpoint to POST notifications to (max 500 chars).
  - `authHeaderName` — name of the auth header included in every webhook call.
  - `authHeaderValue` — value of that header; treated as a secret, never logged.
  - `eventTypes` — non-empty set of `EventType` values the client opts into.
  - `active` — flag to pause delivery without deleting the subscription.
- `WHEN POST /clients/{clientUniqueCode}/subscriptions` is called with a valid body,
  THEN the system shall return 201 with the created subscription.
- `WHEN the client already has an active subscription`, THEN return 409 Conflict.
- `WHEN eventTypes is empty`, THEN return 400 Bad Request.
- `WHEN webhookUrl does not start with https://`, THEN return 400 Bad Request.
- `WHEN clientUniqueCode does not exist`, THEN return 404 Not Found.

**US-2.2** — As a client, I want to retrieve, update, and remove my subscription.

**Acceptance Criteria:**
- `GET /clients/{clientUniqueCode}/subscriptions` — returns the active subscription or 404.
- `PUT /clients/{clientUniqueCode}/subscriptions/{subscriptionUniqueCode}` — updates `webhookUrl`,
  `authHeaderName`, `authHeaderValue`, `eventTypes`, and/or `active`. Returns 200.
- `DELETE /clients/{clientUniqueCode}/subscriptions/{subscriptionUniqueCode}` — soft-deletes the
  subscription. Returns 204.
- `WHEN subscriptionUniqueCode does not belong to the given clientUniqueCode`,
  THEN return 403 Forbidden.

---

### 3. Platform Event Ingestion

**US-3.1** — As the platform, I want to push individual events via API so the system can evaluate
them against subscriptions and create notifications accordingly.

**Acceptance Criteria:**
- `POST /platform-events/ingest` shall accept: `eventId`, `eventType`, `payload`, `clientUniqueCode`.
- `WHEN the client has an active subscription that includes the event's eventType`,
  THEN the system shall create a `NotificationEvent` with `deliveryStatus = PENDING`
  linked to that subscription, and return **201 Created**.
- `WHEN no matching active subscription exists`, THEN return **202 Accepted**
  with no record created (event discarded).
- `WHEN clientUniqueCode does not exist`, THEN return 404 Not Found.
- `WHEN eventType is not a valid EventType value`, THEN return 400 Bad Request.
- `WHEN the same eventId + clientUniqueCode is received again`, THEN return 409 Conflict
  (idempotency guard at the platform event level).

**US-3.2** — As a developer, I want to bulk-ingest events from a JSON payload so I can seed
realistic data for demos without calling the ingest endpoint individually.

**Acceptance Criteria:**
- `POST /platform-events` shall accept a JSON body with an `events` array in the exact
  format of `notification_events.json`: each item containing `event_id`, `event_type`, `content`,
  `delivery_date`, `delivery_status` (`"completed"` or `"failed"`), and `client_id`.
- Each event SHALL be created with the delivery status as provided in the payload —
  `"completed"` maps to `DELIVERED` (with `deliveredAt = delivery_date`),
  `"failed"` maps to `FAILED`. This bypasses the delivery pipeline intentionally.
- The system SHALL resolve `subscription_id` by looking up any active subscription for
  the given `client_id`. If no active subscription exists for that client, the event is discarded.
- Duplicate `event_id` + `client_id` combinations SHALL be skipped silently.
- `WHEN clientId does not exist`, the event SHALL be discarded (not an error).
- The endpoint shall return 200: `{ "created": N, "discarded": N }`.

---

### 4. Notification Delivery Pipeline

**US-4.1** — As the platform, I want a scheduled dispatcher that continuously picks up pending
notifications and sends them to the corresponding webhooks.

**Acceptance Criteria:**
- The system shall run a dispatcher job on a configurable fixed delay (default: 5 seconds).
- `WHEN the dispatcher runs`, it SHALL first reset any `PROCESSING` notifications whose
  `lastModifiedDate < NOW() - processingTimeout` back to `PENDING` (recovery for crash scenarios).
- After recovery, it SHALL atomically claim a batch of `PENDING` notifications
  (configurable, default 100) where `nextRetryAt IS NULL OR nextRetryAt <= NOW()`,
  marking them `PROCESSING`.
- The dispatcher SHALL use **ShedLock** to guarantee only one instance runs at a time across
  all deployed nodes — the recovery and claim steps are safe because no parallel execution occurs.
- `WHEN no eligible notifications exist`, the dispatcher SHALL exit silently.

**US-4.2** — As the platform, I want each notification delivered asynchronously over HTTPS with
the client's configured authentication.

**Acceptance Criteria:**
- Each `PROCESSING` notification SHALL be dispatched to an async worker using non-blocking
  HTTP via `WebClient`.
- The system SHALL fetch the subscription at delivery time to get the current `webhookUrl`,
  `authHeaderName`, and `authHeaderValue`.
  - `WHEN the linked subscription is inactive or deleted at delivery time`,
    THEN mark the notification `FAILED` with `lastError = "Subscription no longer active"`.
- The HTTP POST SHALL include:
  - `{authHeaderName}: {authHeaderValue}` — client-configured auth.
  - `X-Idempotency-Key: {notificationUniqueCode}` — receiver-side deduplication.
- `WHEN the webhook returns 2xx`, THEN `deliveryStatus = DELIVERED` and `deliveredAt = NOW()`.
- `WHEN the webhook returns non-2xx or a connection error`, the retry strategy (US-5.1) applies.
- `WHEN the circuit breaker for that client is OPEN`, the HTTP call SHALL NOT be attempted.
  THEN `deliveryStatus = PENDING`, `nextRetryAt = NOW() + circuitBreakerWaitDuration`,
  and `retryCount` SHALL remain unchanged — circuit breaker rejections do not consume the retry budget.
  `lastError` SHALL record `"Circuit open for client {clientId}"`.
  This guarantees that a client with a broken webhook cannot exhaust its notification retry budget
  while the circuit is protecting it.

---

### 5. Retry Strategy

**US-5.1** — As the platform, I want failed deliveries retried with exponential backoff and jitter
so transient failures recover automatically without thundering-herd effects.

**Acceptance Criteria:**
- `WHEN delivery fails AND retryCount < maxRetries`, THEN:
  - Increment `retryCount`.
  - Reset `deliveryStatus = PENDING`.
  - Set `nextRetryAt = NOW() + delay` where:
    `delay = min(baseDelay × 2^retryCount, maxDelay) + random(0, delay × jitterFactor)`.
  - Store failure reason in `lastError`.
- `WHEN retryCount >= maxRetries AND delivery fails`, THEN `deliveryStatus = FAILED` (terminal).
- Default parameters (all configurable via `application.yml`):
  - `maxRetries`: 3 — effective delays ~5 s, ~10 s, ~20 s before terminal failure.
  - `baseDelay`: 5 s
  - `maxDelay`: 60 s
  - `jitterFactor`: 0.2

---

### 6. Notification Self-Service API

**US-6.1** — As a client, I want to query all my notification events with optional filters so I
can monitor the delivery status of my events.

**Acceptance Criteria:**
- `GET /notification_events` shall return a paginated list of `NotificationEvent` records
  belonging to a given client (identified by `clientUniqueCode` query param).
- The endpoint SHALL support the following optional filters:
  - `deliveryStatus` — filter by `DeliveryStatus` value.
  - `from` / `to` — filter by `createdDate` range (ISO 8601).
- Pagination params: `page` (default 0), `size` (default 20, max 100). Requests with `size > 100`
  SHALL return 400 Bad Request.
- The response SHALL include pagination metadata: `page`, `size`, `totalElements`, `totalPages`.
- `WHEN clientUniqueCode does not exist`, THEN return 404.

**US-6.2** — As a client, I want to retrieve the full detail of a single notification event.

**Acceptance Criteria:**
- `GET /notification_events/{notificationEventUniqueCode}` shall return the full
  `NotificationEvent` including `deliveryStatus`, `retryCount`, `lastError`, `deliveredAt`.
- `WHEN the notification does not exist`, THEN return 404.
- `WHEN the notification belongs to a different client than the one in the request`,
  THEN return 403 Forbidden.

**US-6.3** — As a client, I want to replay a failed notification so I can trigger a new delivery
attempt after the underlying issue has been resolved.

**Acceptance Criteria:**
- `POST /notification_events/{notificationEventUniqueCode}/replay` shall be available only for
  notifications with `deliveryStatus = FAILED`.
- `WHEN replayed`, the system SHALL reset `deliveryStatus = PENDING`, `retryCount = 0`,
  `nextRetryAt = null`, and return 202 Accepted.
- The operation SHALL be idempotent — calling replay on an already-PENDING notification
  SHALL return 409 Conflict (already queued).
- `WHEN the notification does not exist`, THEN return 404.

---

### 7. Observability

**US-7.1** — As an operator, I want structured logs with contextual identifiers on every step
of the delivery pipeline for near-realtime tracing.

**Acceptance Criteria:**
- Every log line during ingestion and delivery SHALL include MDC fields:
  `notificationId`, `clientId`, `eventType`.
- Dispatcher batch claims SHALL be logged at INFO.
- Successful deliveries at INFO, retried failures at WARN, terminal failures at ERROR.
- `authHeaderValue` SHALL never appear in any log output.

**US-7.2** — As an operator, I want Micrometer metrics exposed via Actuator so delivery health
can be monitored in real-time.

**Acceptance Criteria:**
- The system SHALL expose:
  - `notifications.delivered` (counter) tagged with `event_type` and `client_unique_code`.
  - `notifications.failed` (counter) tagged with `event_type` and `client_unique_code`.
  - `notifications.retried` (counter) tagged with `event_type` and `client_unique_code`.
  - `notifications.webhook.duration` (timer) tagged with `event_type` and `client_unique_code`.
- Note: tagging with `client_unique_code` introduces high-cardinality label values. See
  `doc/system-design.md §10` for the production mitigation strategy.
