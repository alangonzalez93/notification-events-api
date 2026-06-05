# Tasks — Notification Delivery

Each task references the requirement it satisfies. Tasks marked with `[P]` within a phase can be executed in parallel.

---

## Phase 1 — Dependencies & Global Config

> Prerequisite: Phase 1 of spec 01-setup complete.

- [x] **1.1** Add to `pom.xml`: `spring-boot-starter-validation`, `shedlock-spring`, `shedlock-provider-jdbc-template`, `resilience4j-spring-boot3`, `springdoc-openapi-starter-webmvc-ui`, `logstash-logback-encoder` → satisfies US-4.1, US-4.2
  (`spring-boot-starter-webflux` was removed — RestClient is included in `spring-boot-starter-web`)
- [x] **1.2** Add `server.servlet.context-path=/api` to `application.yml` → all REST endpoints served under `/api`
- [x] **1.3** Add `notifications.*` config block to `application.yml` (dispatcher with `processing-timeout-minutes`, retry, webhook timeouts, async thread pool) → satisfies US-4.1, US-5.1
- [x] **1.4** Add `resilience4j.circuitbreaker` config block to `application.yml` with `register-health-indicator: true` → satisfies US-4.2

---

## Phase 2 — Domain Models `[P]`

> Prerequisite: Phase 1 complete.

- [x] **2.1** `[P]` Implement `domain.model.EventType` enum (`@JsonValue`/`@JsonCreator` for snake_case) → satisfies US-1.1
- [x] **2.2** `[P]` Implement `domain.model.DeliveryStatus` enum (same serialization pattern) → satisfies US-1.2
- [x] **2.3** `[P]` Implement `domain.model.Subscription` record → satisfies US-2.1
- [x] **2.4** `[P]` Implement `domain.model.NotificationEvent` record (includes `version` field for optimistic lock) → satisfies US-4.2
- [x] **2.5** `[P]` Implement `domain.model.PlatformEvent` record (transient input for ingest) → satisfies US-3.1
- ~~**2.5b**~~ ~~Implement `domain.model.SeedEvent`~~ — eliminado junto con el bulk seed endpoint (ver US-3.2)
- [x] **2.6** `[P]` Implement `domain.model.PageResult<T>` record (no Spring imports) → satisfies US-6.1
- [x] **2.7** `[P]` Add `domain.exception.ConflictException` → HTTP 409
- [x] **2.8** `[P]` Add `domain.exception.ForbiddenException` → HTTP 403

---

## Phase 3 — Domain Ports `[P]`

> Prerequisite: Phase 2 complete.

- [x] **3.1** `[P]` Implement `domain.port.out.SubscriptionRepository` interface → satisfies US-2.1, US-3.1
- [x] **3.2** `[P]` Implement `domain.port.out.NotificationEventRepository` interface → satisfies US-3.1, US-4.1, US-6.1
- [x] **3.3** `[P]` Implement `domain.port.out.WebhookPort` interface + `WebhookResult` record → satisfies US-4.2
- [x] **3.4** `[P]` Implement all `domain.port.in` use case interfaces:
  `CreateSubscriptionUseCase`, `GetSubscriptionUseCase`, `UpdateSubscriptionUseCase`,
  `DeleteSubscriptionUseCase`, `IngestPlatformEventUseCase`,
  `GetNotificationEventsUseCase`, `GetNotificationEventUseCase`,
  `ReplayNotificationEventUseCase`, `ProcessPendingNotificationsUseCase`
  (`SeedPlatformEventsUseCase` eliminado — ver US-3.2)

---

## Phase 4 — DB Migration `[P]`

> Can run in parallel with Phase 3.

- [x] **4.1** `[P]` Create `V2__create_notification_schema.sql` with tables: `subscriptions`,
  `subscription_event_types`, `notification_events`, `shedlock` + all indexes → satisfies US-2.1, US-3.1, US-4.1

---

## Phase 5 — Infrastructure Config `[P]`

> Prerequisite: Phase 1 complete.

- [x] **5.1** `[P]` Implement `AsyncProperties` (`@ConfigurationProperties("notifications.async")`) → corePoolSize, maxPoolSize, queueCapacity
- [x] **5.2** `[P]` Implement `AsyncConfig` (`@EnableAsync` + `webhookDeliveryExecutor` reads from `AsyncProperties`, discard+log handler when saturated — avoids blocking scheduler thread and risking ShedLock expiry) → satisfies US-4.2
- [x] **5.3** `[P]` Implement `SchedulerConfig` (`@EnableScheduling` + `@EnableSchedulerLock` + `LockProvider` with `usingDbTime()`) → satisfies US-4.1
- [x] **5.4** `[P]` Implement `RestClientConfig` (RestClient bean with `SimpleClientHttpRequestFactory` connect/read timeouts via `@Value`) → satisfies US-4.2
- [x] **5.5** `[P]` Implement `CircuitBreakerProperties` (`@ConfigurationProperties("notifications.circuitbreaker")`) + `CircuitBreakerConfig` (builds `CircuitBreakerRegistry` programmatically from `CircuitBreakerProperties` so per-client dynamic breakers inherit the config — do NOT use `CircuitBreakerRegistry.ofDefaults()`) → satisfies US-4.2
- [x] **5.6** `[P]` Implement `RetryProperties` + `DispatcherProperties` (`@ConfigurationProperties`) → satisfies US-5.1

---

## Phase 6 — Persistence Layer

> Prerequisite: Phases 3 and 4 complete.

- [x] **6.1** `[P]` Implement `SubscriptionJpaEntity` (extends `BaseEntity`, `@SQLRestriction`, `@ElementCollection` for eventTypes) → satisfies US-2.1
- [x] **6.2** `[P]` Implement `NotificationEventJpaEntity` (extends `BaseEntity`, `@SQLRestriction`, `@Version Long version` for optimistic locking) → satisfies US-3.1
- [x] **6.3** `[P]` Implement `SubscriptionMapper` (MapStruct; `@Mapping(target="clientUniqueCode", source="client.uniqueCode")` — resolved via `@ManyToOne`, no extra query needed) → satisfies US-2.1
- [x] **6.4** `[P]` Implement `NotificationEventMapper` (MapStruct; maps `client.uniqueCode → clientUniqueCode` and `subscription.uniqueCode → subscriptionUniqueCode` via `@ManyToOne` relationships; repository sets the entity references before calling `toEntity()`) → satisfies US-3.1
- [x] **6.5** Implement `SubscriptionSpringDataRepository` + `SubscriptionJpaRepository` (implements `SubscriptionRepository` port; custom JPQL for subscription + event type join query) → satisfies US-2.1, US-3.1
- [x] **6.6** Implement `NotificationEventSpringDataRepository` + `NotificationEventJpaRepository` (implements `NotificationEventRepository` port; `findPendingBatch` with `@Lock` + `@Modifying` for atomic claim) → satisfies US-4.1, US-6.1

---

## Phase 7 — Application Use Cases

> Prerequisite: Phases 3 and 6 complete.

- [x] **7.1** `[P]` Implement subscription use cases: `CreateSubscriptionUseCaseImpl`, `GetSubscriptionUseCaseImpl`, `UpdateSubscriptionUseCaseImpl`, `DeleteSubscriptionUseCaseImpl` → satisfies US-2.1, US-2.2
- [x] **7.2** `[P]` Implement `IngestPlatformEventUseCaseImpl` (subscription check → create PENDING or discard) → satisfies US-3.1
- ~~**7.3**~~ ~~Implement `SeedPlatformEventsUseCaseImpl`~~ — eliminado junto con el bulk seed endpoint (ver US-3.2)
- [x] **7.4** `[P]` Implement notification query use cases: `GetNotificationEventsUseCaseImpl` (paginated + filters), `GetNotificationEventUseCaseImpl` → satisfies US-6.1, US-6.2
- [x] **7.5** `[P]` Implement `ReplayNotificationEventUseCaseImpl` (`@Transactional`; optimistic lock via `@Version` — catch `ObjectOptimisticLockingFailureException` → 409; only allows replay when `status=FAILED`) → satisfies US-6.3
- [x] **7.6** Implement `ProcessPendingNotificationsUseCaseImpl` (`@Transactional`: reset stuck PROCESSING → PENDING, claim batch, return list — method commits before returning; `@Async` dispatch happens in `NotificationDispatcherJob` AFTER the TX commits, never inside this method) → satisfies US-4.1
- [x] **7.7** Implement `DeliverNotificationService` (`@Async`, no outer `@Transactional`; three-phase: [Tx readOnly] fetch subscription → [no Tx] HTTP call via `WebhookPort` → [Tx] update status via `updateStatus()`; Phase 3 has **two distinct catch paths**: `CircuitOpenException` (thrown by adapter when circuit OPEN) → `retryCount` unchanged, `nextRetryAt=NOW()+ex.waitDurationSeconds()`, status=PENDING; `WebhookCallException` → `retryCount++`, backoff or FAILED; each TX phase in separate `@Service` bean; MDC + metrics tagged `event_type` + `client_unique_code`) → satisfies US-4.2, US-5.1, US-7.1, US-7.2

---

## Phase 8 — Webhook Adapter & Scheduler `[P]`

> Prerequisite: Phases 5 and 7 complete.

- [x] **8.1** `[P]` Implement `WebhookAdapter` (implements `WebhookPort` via `RestClient`; wraps HTTP call with Resilience4j circuit breaker per `clientUniqueCode`; catches `CallNotPermittedException` and rethrows `CircuitOpenException` (domain exception) so the application layer has no dependency on Resilience4j; `WebhookResult` carries `success`, `httpStatus`, `errorMessage` — no `duration`, latency is measured in the service layer) → satisfies US-4.2
- [x] **8.2** `[P]` Implement `NotificationDispatcherJob` (`@Scheduled(fixedDelayString)` + `@SchedulerLock`) → satisfies US-4.1

---

## Phase 9 — REST Layer

> Prerequisite: Phase 7 complete.

- [x] **9.1** `[P]` Implement request/response DTOs (all `@Valid` annotations on request fields)
  (`SeedPlatformEventsRequest` y `SeedResultResponse` eliminados — ver US-3.2)
- [x] **9.2** `[P]` Implement `GlobalExceptionHandler` (`@RestControllerAdvice`; maps all domain exceptions to consistent error envelope)
- [x] **9.3** `[P]` Implement `SubscriptionController` (`POST`, `GET`, `PUT`, `DELETE /api/clients/{code}/subscriptions`) → satisfies US-2.1, US-2.2
- [x] **9.4** `[P]` Implement `PlatformEventController` (`POST /api/platform-events`) → satisfies US-3.1
  (endpoint renombrado de `/ingest` a raíz por convención REST; bulk seed eliminado — ver US-3.2)
- [x] **9.5** `[P]` Implement `NotificationEventController` (`GET /api/notification_events`, `GET /api/notification_events/{code}`, `POST /api/notification_events/{code}/replay`) → satisfies US-6.1, US-6.2, US-6.3

---

## Phase 10 — Tests

> Prerequisite: Phase 9 complete.

- [x] **10.1** `[P]` Integration tests for subscription CRUD (create, get, update, delete, duplicate → 409, wrong owner → 403)
- [x] **10.2** `[P]` Integration tests for platform event ingestion (201 with subscription, 202 without, 409 duplicate eventId, 404 unknown client, 400 invalid eventType)
- [x] **10.3** `[P]` Integration tests for notification query API (list with filters, pagination, get by code, 404)
- [x] **10.4** `[P]` Integration tests for replay (reset to PENDING, 409 if already PENDING, 404 if not found)
- [x] **10.5** `[P]` Unit test for retry backoff formula (verify delay values and jitter range for each retry attempt)
- [x] **10.6** Integration test for the full async delivery flow using **WireMock**: seed a PENDING notification → trigger dispatcher → assert webhook called with correct headers (`X-Idempotency-Key`, auth header) → assert `deliveryStatus = DELIVERED`
- [x] **10.7** Integration test for retry flow using WireMock: mock webhook returns 500 → assert status=PENDING with `nextRetryAt` set → after N failures assert status=FAILED

---

## Phase 11 — Demo Seed Script

> Can be written in parallel with any other phase.

- [x] **11.1** Create `scripts/seed_data.sql` with: 3 clients (`CLIENT001`, `CLIENT002`, `CLIENT003`), one subscription per client covering all event types, and 10 pre-loaded notification events matching `notification_events.json` in varied statuses (PENDING, DELIVERED, FAILED) — ready to run after `docker compose up`

---

## Phase 12 — Validation

> Prerequisite: Phases 8 and 10 complete.

- [x] **12.1** `mvn test` — all tests pass including WireMock delivery tests
- [x] **12.2** `docker compose up` — API and MySQL start clean, Flyway runs V1 + V2
- [x] **12.3** Run `scripts/seed_data.sql` against the running MySQL; verify data via `SHOW TABLES` + `SELECT COUNT(*) FROM notification_events`
- [x] **12.4** Smoke test via curl: create subscription → `POST /api/platform-events` → `GET /api/notification_events` → confirm PENDING notification exists
- [x] **12.5** Verify dispatcher picks up PENDING notifications within 5 seconds; check logs for `INFO` delivery attempt
- [x] **12.6** Verify `GET localhost:8081/actuator/metrics` exposes `notifications.delivered`, `notifications.failed`, `notifications.retried` (Actuator runs on management port 8081, not the API port 8080)
- [x] **12.7** Verify `GET /swagger-ui.html` renders all endpoints with correct request/response schemas
