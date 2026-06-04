# Design — Notification Delivery

## Stack Additions

| Component | Technology | Rationale |
|---|---|---|
| Async HTTP client | WebClient (spring-boot-starter-webflux) | Non-blocking I/O for webhook calls; handles thousands of concurrent requests with a small thread pool |
| Distributed scheduler lock | ShedLock + JDBC provider | Guarantees the dispatcher job runs on exactly one node at a time; uses the existing MySQL DB as lock store |
| Bean validation | spring-boot-starter-validation | `@Valid` on controller DTOs; declarative constraint checking without boilerplate |
| Circuit breaker | Resilience4j (resilience4j-spring-boot3) | Opens circuit per client after N consecutive failures; prevents hammering a broken webhook and degrading delivery for other clients |
| API docs | springdoc-openapi-starter-webmvc-ui | Auto-generates OpenAPI 3.0 spec + Swagger UI at `/swagger-ui.html`; essential for panel demo |

---

## Package Structure — Additions

```
domain/
  model/
    EventType.java                     ← enum, @JsonValue snake_case
    DeliveryStatus.java                ← enum
    Subscription.java                  ← record
    NotificationEvent.java             ← record
    PlatformEvent.java                 ← record (transient input, not persisted)
    PageResult.java                    ← generic pagination wrapper (no Spring imports)
  port/
    in/
      CreateSubscriptionUseCase.java
      GetSubscriptionUseCase.java
      UpdateSubscriptionUseCase.java
      DeleteSubscriptionUseCase.java
      IngestPlatformEventUseCase.java
      SeedPlatformEventsUseCase.java
      GetNotificationEventsUseCase.java
      GetNotificationEventUseCase.java
      ReplayNotificationEventUseCase.java
      ProcessPendingNotificationsUseCase.java
    out/
      SubscriptionRepository.java      ← new
      NotificationEventRepository.java ← new
      WebhookPort.java                 ← new
  exception/
    ConflictException.java             ← new → HTTP 409
    ForbiddenException.java            ← new → HTTP 403

application/
  usecase/
    CreateSubscriptionUseCaseImpl.java
    GetSubscriptionUseCaseImpl.java
    UpdateSubscriptionUseCaseImpl.java
    DeleteSubscriptionUseCaseImpl.java
    IngestPlatformEventUseCaseImpl.java
    SeedPlatformEventsUseCaseImpl.java
    GetNotificationEventsUseCaseImpl.java
    GetNotificationEventUseCaseImpl.java
    ReplayNotificationEventUseCaseImpl.java
    ProcessPendingNotificationsUseCaseImpl.java
  service/
    DeliverNotificationService.java    ← @Async worker; called by ProcessPendingNotificationsUseCaseImpl

infrastructure/
  config/
    AsyncConfig.java                   ← @EnableAsync + ThreadPoolTaskExecutor bean (reads AsyncProperties)
    SchedulerConfig.java               ← @EnableScheduling + @EnableSchedulerLock + LockProvider bean
    WebClientConfig.java               ← WebClient bean with timeouts
    CircuitBreakerConfig.java          ← Resilience4j CircuitBreakerRegistry bean
    AsyncProperties.java               ← @ConfigurationProperties("notifications.async")
    RetryProperties.java               ← @ConfigurationProperties("notifications.retry")
    DispatcherProperties.java          ← @ConfigurationProperties("notifications.dispatcher")
  persistence/
    entity/
      SubscriptionJpaEntity.java
      NotificationEventJpaEntity.java
    repository/
      SubscriptionJpaRepository.java
      SubscriptionSpringDataRepository.java
      NotificationEventJpaRepository.java
      NotificationEventSpringDataRepository.java
    mapper/
      SubscriptionMapper.java          ← MapStruct; maps client.uniqueCode → clientUniqueCode
      NotificationEventMapper.java     ← MapStruct; maps client.uniqueCode + subscription.uniqueCode
  web/
    controller/
      SubscriptionController.java
      PlatformEventController.java
      NotificationEventController.java
    dto/
      request/
        CreateSubscriptionRequest.java
        UpdateSubscriptionRequest.java
        IngestPlatformEventRequest.java
        SeedPlatformEventsRequest.java
      response/
        SubscriptionResponse.java
        NotificationEventSummaryResponse.java
        NotificationEventDetailResponse.java
        SeedResultResponse.java
    advice/
      GlobalExceptionHandler.java      ← @RestControllerAdvice
  scheduler/
    NotificationDispatcherJob.java     ← @Scheduled + @SchedulerLock
  webhook/
    WebhookAdapter.java                ← implements WebhookPort via WebClient
```

---

## Domain Models

### `EventType` enum
```java
public enum EventType {
    CREDIT_CARD_PAYMENT, DEBIT_CARD_WITHDRAWAL, CREDIT_TRANSFER,
    DEBIT_AUTOMATIC_PAYMENT, CREDIT_REFUND, DEBIT_TRANSFER,
    CREDIT_DEPOSIT, DEBIT_PURCHASE, CREDIT_CASHBACK, DEBIT_SUBSCRIPTION;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static EventType fromJson(String value) {
        return valueOf(value.toUpperCase());
    }
}
```
Stored in the DB as VARCHAR using the Java enum name (e.g., `"CREDIT_TRANSFER"`).
JSON API uses snake_case (e.g., `"credit_transfer"`).

### `DeliveryStatus` enum — same serialization pattern as `EventType`.

### `Subscription` record
```java
public record Subscription(
    Long id, String uniqueCode,
    String clientUniqueCode,
    String webhookUrl,
    String authHeaderName,
    String authHeaderValue,   // never returned in API responses
    Set<EventType> eventTypes,
    Boolean active,
    Boolean deleted,
    OffsetDateTime createdDate,
    OffsetDateTime lastModifiedDate
) {}
```

### `NotificationEvent` record
```java
public record NotificationEvent(
    Long id, String uniqueCode,
    String eventId,           // external platform event ID
    EventType eventType,
    String payload,
    String clientUniqueCode,
    String subscriptionUniqueCode,
    DeliveryStatus deliveryStatus,
    OffsetDateTime deliveredAt,
    Integer retryCount,
    OffsetDateTime nextRetryAt,
    String lastError,
    Long version,             // optimistic lock — managed by Hibernate @Version
    Boolean deleted,
    OffsetDateTime createdDate,
    OffsetDateTime lastModifiedDate
) {}
```
`OffsetDateTime` is used for all temporal fields across the entire system. Hibernate stores them as
`DATETIME` in UTC via `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`. This guarantees
consistent timezone semantics whether reading from DB, serializing to JSON, or filtering via query
parameters — no ambiguity around "which timezone is this LocalDateTime?"

### `PlatformEvent` record — transient input, never persisted (used by ingest)
```java
public record PlatformEvent(
    String eventId,
    EventType eventType,
    String payload,
    String clientUniqueCode
) {}
```

### `SeedEvent` record — transient input for bulk seed, never persisted
Pure domain record — no Jackson annotations. The `"completed"`/`"failed"` → `DeliveryStatus`
conversion happens in the DTO layer (infrastructure), not in the domain.
```java
public record SeedEvent(
    String eventId,
    EventType eventType,
    String payload,             // mapped from DTO's "content" field
    OffsetDateTime deliveryDate,
    DeliveryStatus deliveryStatus,  // already converted from "completed"/"failed" by DTO mapper
    String clientId             // "CLIENT001" — clientUniqueCode in the system
) {}
```

The `SeedPlatformEventsRequest` DTO (infrastructure/web layer) owns the JSON-specific details:
```java
// infrastructure/web/dto/request/SeedPlatformEventsRequest.java
public record SeedPlatformEventsRequest(@Valid @NotEmpty List<SeedEventRequest> events) {
    public record SeedEventRequest(
        @NotBlank @JsonProperty("event_id")       String eventId,
        @NotNull  @JsonProperty("event_type")      EventType eventType,
        @NotBlank                                  String content,
        @NotNull  @JsonProperty("delivery_date")   OffsetDateTime deliveryDate,
        @NotNull  @JsonProperty("delivery_status") SeedStatus deliveryStatus,
        @NotBlank @JsonProperty("client_id")       String clientId
    ) {
        // Inner enum lives in the DTO — Jackson annotations belong in infrastructure
        public enum SeedStatus {
            @JsonProperty("completed") COMPLETED,
            @JsonProperty("failed")    FAILED;

            public DeliveryStatus toDeliveryStatus() {
                return this == COMPLETED ? DeliveryStatus.DELIVERED : DeliveryStatus.FAILED;
            }
        }
    }
}
```

### `PageResult<T>` — pure domain, no Spring imports
```java
public record PageResult<T>(
    List<T> content, int page, int size, long totalElements, int totalPages
) {}
```

---

## Domain Ports

### `SubscriptionRepository` (port.out)
```java
public interface SubscriptionRepository {
    Subscription save(Subscription subscription);
    Optional<Subscription> findByUniqueCode(String uniqueCode);
    Optional<Subscription> findActiveByClientUniqueCode(String clientUniqueCode);
    // Used at ingestion time to check if event type is subscribed
    Optional<Subscription> findActiveByClientUniqueCodeAndEventType(String clientUniqueCode, EventType eventType);
    boolean existsActiveByClientUniqueCode(String clientUniqueCode);
    void softDelete(String uniqueCode);
}
```

### `NotificationEventRepository` (port.out)
```java
public interface NotificationEventRepository {
    NotificationEvent save(NotificationEvent event);
    Optional<NotificationEvent> findByUniqueCode(String uniqueCode);
    boolean existsByEventIdAndClientUniqueCode(String eventId, String clientUniqueCode);
    // Dispatcher: claims next batch ready for processing
    List<NotificationEvent> findPendingBatch(int limit);
    void markAllAsProcessing(List<Long> ids);
    PageResult<NotificationEvent> findByClientUniqueCode(
        String clientUniqueCode, DeliveryStatus status,
        OffsetDateTime from, OffsetDateTime to, int page, int size);
}
```

### `WebhookPort` (port.out)
```java
public interface WebhookPort {
    WebhookResult post(String url, String authHeaderName, String authHeaderValue,
                       String idempotencyKey, String payload);
}

public record WebhookResult(boolean success, int httpStatus, String errorMessage, Duration duration) {}
```

---

## DB Migrations

### `V2__create_notification_schema.sql`
```sql
CREATE TABLE subscriptions (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    unique_code         VARCHAR(36)  NOT NULL,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    created_date        DATETIME     NOT NULL,
    last_modified_date  DATETIME     NOT NULL,
    client_id           BIGINT       NOT NULL,
    webhook_url         VARCHAR(500) NOT NULL,
    auth_header_name    VARCHAR(100) NOT NULL,
    auth_header_value   VARCHAR(500) NOT NULL,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,

    PRIMARY KEY (id),
    CONSTRAINT uq_subscriptions_unique_code UNIQUE (unique_code),
    CONSTRAINT fk_subscriptions_client FOREIGN KEY (client_id) REFERENCES clients(id)
);

CREATE INDEX idx_subscriptions_client_id ON subscriptions (client_id);
CREATE INDEX idx_subscriptions_deleted   ON subscriptions (deleted);
CREATE INDEX idx_subscriptions_active    ON subscriptions (active);

CREATE TABLE subscription_event_types (
    subscription_id BIGINT      NOT NULL,
    event_type      VARCHAR(50) NOT NULL,
    PRIMARY KEY (subscription_id, event_type),
    CONSTRAINT fk_set_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions(id)
);

CREATE TABLE notification_events (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    unique_code         VARCHAR(36)  NOT NULL,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    created_date        DATETIME     NOT NULL,
    last_modified_date  DATETIME     NOT NULL,
    event_id            VARCHAR(100) NOT NULL,
    event_type          VARCHAR(50)  NOT NULL,
    payload             TEXT         NOT NULL,
    client_id           BIGINT       NOT NULL,
    subscription_id     BIGINT       NOT NULL,
    delivery_status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    delivered_at        DATETIME,
    retry_count         INT          NOT NULL DEFAULT 0,
    next_retry_at       DATETIME,
    last_error          TEXT,
    version             BIGINT       NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT uq_notification_events_unique_code  UNIQUE (unique_code),
    CONSTRAINT uq_notification_events_event_client UNIQUE (event_id, client_id),
    CONSTRAINT fk_ne_client       FOREIGN KEY (client_id)       REFERENCES clients(id),
    CONSTRAINT fk_ne_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions(id)
);

CREATE INDEX idx_ne_deleted  ON notification_events (deleted);
-- Composite: dispatcher query — status + retry window
CREATE INDEX idx_ne_dispatch ON notification_events (delivery_status, next_retry_at);
-- Composite: self-service query — client + status + date
CREATE INDEX idx_ne_query    ON notification_events (client_id, delivery_status, created_date);

-- ShedLock uses DB clock as single source of truth for lock expiry across nodes
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
```

---

## JPA Entities

### `SubscriptionJpaEntity`
```java
@Entity
@SQLRestriction("deleted = false")
@Table(name = "subscriptions", indexes = {
    @Index(name = "idx_subscriptions_client_id", columnList = "client_id"),
    @Index(name = "idx_subscriptions_active",    columnList = "active"),
    @Index(name = "idx_subscriptions_deleted",   columnList = "deleted")
})
public class SubscriptionJpaEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private ClientJpaEntity client;          // @ManyToOne so mapper can access client.uniqueCode

    @Column(name = "webhook_url", nullable = false, length = 500)
    private String webhookUrl;

    @Column(name = "auth_header_name", nullable = false, length = 100)
    private String authHeaderName;

    @Column(name = "auth_header_value", nullable = false, length = 500)
    private String authHeaderValue;

    @Column(nullable = false)
    private Boolean active = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "subscription_event_types",
                     joinColumns = @JoinColumn(name = "subscription_id"))
    @Column(name = "event_type", length = 50)
    @Enumerated(EnumType.STRING)
    private Set<EventType> eventTypes = new HashSet<>();
}
```

### `NotificationEventJpaEntity`
```java
@Entity
@SQLRestriction("deleted = false")
@Table(name = "notification_events", indexes = {
    @Index(name = "idx_ne_dispatch", columnList = "delivery_status, next_retry_at"),
    @Index(name = "idx_ne_query",    columnList = "client_id, delivery_status, created_date"),
    @Index(name = "idx_ne_deleted",  columnList = "deleted")
})
public class NotificationEventJpaEntity extends BaseEntity {

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private ClientJpaEntity client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private SubscriptionJpaEntity subscription;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "next_retry_at")
    private OffsetDateTime nextRetryAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Version
    @Column(nullable = false)
    private Long version = 0L;  // optimistic lock — Hibernate increments on every UPDATE
}
```

### Mappers — resolving uniqueCode via `@ManyToOne`

The `@ManyToOne` relationships let Hibernate resolve `clientUniqueCode` and `subscriptionUniqueCode`
without extra queries (loaded lazily; accessed only during the mapping step within the same session).

```java
// SubscriptionMapper.java
@Mapper(componentModel = "spring")
public interface SubscriptionMapper {
    @Mapping(target = "clientUniqueCode", source = "client.uniqueCode")
    Subscription toDomain(SubscriptionJpaEntity entity);

    @Mapping(target = "client",       ignore = true)   // set by repository before save
    @Mapping(target = "deleted",      ignore = true)
    @Mapping(target = "createdDate",  ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    SubscriptionJpaEntity toEntity(Subscription domain);
}

// NotificationEventMapper.java
@Mapper(componentModel = "spring")
public interface NotificationEventMapper {
    @Mapping(target = "clientUniqueCode",       source = "client.uniqueCode")
    @Mapping(target = "subscriptionUniqueCode", source = "subscription.uniqueCode")
    NotificationEvent toDomain(NotificationEventJpaEntity entity);

    @Mapping(target = "client",       ignore = true)   // set by repository before save
    @Mapping(target = "subscription", ignore = true)   // set by repository before save
    @Mapping(target = "deleted",      ignore = true)
    @Mapping(target = "createdDate",  ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    NotificationEventJpaEntity toEntity(NotificationEvent domain);
}
```

The repository implementations (`SubscriptionJpaRepository`, `NotificationEventJpaRepository`)
resolve `clientUniqueCode → ClientJpaEntity` and `subscriptionUniqueCode → SubscriptionJpaEntity`
before calling `toEntity()`, so the mapper never needs to do a lookup itself.

---

## Delivery Pipeline Design

```
POST /api/platform-events/ingest
         │
         ▼
 IngestPlatformEventUseCaseImpl
         │  findActiveByClientAndEventType()
         ▼
  SubscriptionRepository ──── found? ──── NO ──► 202 Accepted (discarded)
         │
        YES
         │
         ▼
  NotificationEventRepository.save() → PENDING
         │
         ▼
        201 Created

─────────────────────────────────────────────────────────────

 @Scheduled(fixedDelay=5s) + @SchedulerLock("notificationDispatcher")
         │
         ▼  Step 1 — crash recovery (within the lock, no parallel risk)
 resetStuckProcessing(processingTimeoutMinutes=5)
         │  UPDATE SET status=PENDING WHERE status=PROCESSING
         │  AND last_modified_date < NOW() - INTERVAL 5 MINUTE
         │
         ▼  Step 2 — claim next batch
 findPendingBatch(100) + markAllAsProcessing(ids)
         │
         ▼  Step 3 — async fan-out
 for each notification:
         │  @Async("webhookDeliveryExecutor")
         ▼
 DeliverNotificationService
         │
         ├── [Tx] fetch subscription → validate still active
         │
         ├── [no Tx] CircuitBreaker(clientUniqueCode).call → WebhookPort.post()
         │           timer: notifications.webhook.duration {event_type, client_unique_code}
         │
         │   ┌─────────────────────────────────────────────────────┐
         │   │  catch CallNotPermittedException (circuit OPEN)      │
         │   │  → [Tx] status=PENDING                               │
         │   │         nextRetryAt=NOW()+cbWaitDuration (30s)       │
         │   │         retryCount UNCHANGED ← budget preserved      │
         │   │         lastError="Circuit open for client {id}"     │
         │   └─────────────────────────────────────────────────────┘
         │
         │   ┌─────────────────────────────────────────────────────┐
         │   │  catch WebhookException (non-2xx / timeout)         │
         │   │  → retryCount++                                      │
         │   │  → [Tx] retryCount < maxRetries?                     │
         │   │         YES → status=PENDING, nextRetryAt=backoff    │
         │   │               counter: notifications.retried         │
         │   │         NO  → status=FAILED                          │
         │   │               counter: notifications.failed          │
         │   └─────────────────────────────────────────────────────┘
         │
         └── [Tx] 2xx SUCCESS
                  status=DELIVERED, deliveredAt=NOW()
                  counter: notifications.delivered {event_type, client_unique_code}
```

---

## Retry Formula

```java
// baseDelay=5s, maxDelay=60s, jitterFactor=0.2
long delaySeconds = (long) Math.min(
    retryProperties.baseDelay() * Math.pow(2, retryCount),
    retryProperties.maxDelay()
);
long jitter = (long) (Math.random() * delaySeconds * retryProperties.jitterFactor());
OffsetDateTime nextRetryAt = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(delaySeconds + jitter);
```

Effective delays with defaults: ~5 s → ~10 s → ~20 s (±20% jitter each).

---

## @Transactional Lifecycle & Consistency Guarantees

Transaction boundaries are critical for correctness and recoverability. This section documents every boundary, the race conditions it prevents, and the contract the system upholds under failures.

---

### Transaction map

| Component | Propagation | Reason |
|---|---|---|
| `CreateSubscriptionUseCaseImpl` | `REQUIRED` | Existence check + save atomic |
| `IngestPlatformEventUseCaseImpl` | `REQUIRED` | Subscription lookup + notification INSERT atomic; DB UNIQUE on `(event_id, client_id)` handles concurrent duplicates |
| `SeedPlatformEventsUseCaseImpl` | **No outer TX** — per-event `REQUIRED` via inner `seedEventSaver` bean | One failure must not roll back others (see §Bulk seed) |
| `ProcessPendingNotificationsUseCaseImpl` | `REQUIRED` | Recovery reset + batch claim atomic; TX commits before `@Async` dispatch |
| `ReplayNotificationEventUseCaseImpl` | `REQUIRED` | Optimistic lock (`@Version`) prevents concurrent replays |
| `DeliverNotificationService` | **No outer TX** | HTTP call must never hold a DB connection (see §Delivery phases) |
| `UpdateSubscriptionUseCaseImpl` | `REQUIRED` | Simple field update; last-write-wins acceptable |
| `DeleteSubscriptionUseCaseImpl` | `REQUIRED` | Soft delete is idempotent |

---

### 1. Dispatcher claim — TX must commit before @Async dispatch

The `@Async` dispatch must happen **after** `claimBatch()` returns and its transaction has committed. If workers were dispatched inside the `@Transactional` method, they would start before `PROCESSING` is visible in the DB and could re-read the same notifications as `PENDING`.

```
@Transactional claimBatch() {
    resetStuckProcessing()          // UPDATE PROCESSING → PENDING (crash recovery)
    batch = findPendingBatch()      // SELECT WHERE status=PENDING
    markAllAsProcessing(batch.ids)  // UPDATE → PROCESSING
    return batch
}
// ← TX commits here. PROCESSING is now in DB.

// @Async dispatch OUTSIDE the transaction (in the job, not the use case)
batch.forEach(deliverService::deliver)
```

State in DB at each moment:

| Moment | Status |
|---|---|
| Job starts | `PENDING` |
| `claimBatch()` returns | `PROCESSING` (committed) |
| Worker makes HTTP call | `PROCESSING` |
| Worker succeeds | `DELIVERED` (new commit) |
| Worker fails, retries remain | `PENDING` + `nextRetryAt` (new commit) |
| Worker fails, no retries | `FAILED` (new commit) |

---

### 2. Delivery — three-phase, no connection held during HTTP

`DeliverNotificationService.deliver()` is `@Async` — it runs on a separate thread with no inherited transaction context. Three distinct phases:

```
Phase 1: @Transactional(readOnly=true)
    fetch subscription, validate still active
    └─ TX commits, DB connection returned to pool

Phase 2: no transaction
    HTTP POST to webhook (may take seconds)
    timer: notifications.webhook.duration {event_type, client_unique_code}

Phase 3: @Transactional
    update NotificationEvent status (DELIVERED / PENDING+backoff / FAILED)
    └─ TX commits, DB connection returned to pool
```

Since Spring proxies only intercept calls made through the proxy (not `this.method()`), Phases 1 and 3 must live in a separate `@Service` bean injected into `DeliverNotificationService`.

**Phase 3 — two distinct exception paths:**

```java
try {
    WebhookResult result = webhookPort.post(url, authHeader, idempotencyKey, payload);
    // 2xx → DELIVERED
} catch (CallNotPermittedException e) {
    // Circuit breaker OPEN — HTTP call was never made
    // retryCount is NOT incremented — budget is preserved
    // nextRetryAt = NOW() + circuitBreakerWaitDuration
    // status = PENDING
} catch (WebhookCallException e) {
    // Actual delivery failure (non-2xx, timeout, connection error)
    // retryCount++
    // if retryCount < maxRetries → PENDING + backoff
    // else → FAILED
}
```

This distinction is critical: `retryCount` represents the number of times the webhook was actually called and failed. Circuit breaker rejections are transparent to the retry budget — the notification simply waits for the circuit to recover before the next attempt.

**At-least-once delivery contract**: if the app crashes between Phase 2 (HTTP call succeeded) and Phase 3 (status update not yet committed), crash recovery resets `PROCESSING → PENDING` and the notification is re-delivered. The receiver uses `X-Idempotency-Key: {notificationUniqueCode}` to deduplicate. This is intentional — at-most-once would require distributed transactions.

---

### 3. Replay — optimistic lock with `@Version`

`NotificationEventJpaEntity` has a `@Version Long version` field. Two concurrent replay requests both reading `status=FAILED`:

```
Thread A: SELECT version=3, status=FAILED → valid
Thread B: SELECT version=3, status=FAILED → valid
Thread A: UPDATE SET status=PENDING WHERE version=3 → succeeds, version becomes 4
Thread B: UPDATE SET status=PENDING WHERE version=3 → fails (version is now 4)
          └─ ObjectOptimisticLockingFailureException → HTTP 409
```

No explicit lock needed — Hibernate manages this transparently.

The `version` column must be added to the `notification_events` migration and to the `NotificationEvent` domain record.

---

### 4. Bulk seed — independent transaction per event

If the entire seed runs in one transaction and event #7 fails (duplicate `eventId`, unknown client),
events 1–6 silently roll back.

Fix: no outer `@Transactional` on `SeedPlatformEventsUseCaseImpl`. Each event is persisted via an
inner `@Service` bean whose method is `@Transactional(propagation = REQUIRED)`, creating its own
independent transaction. Note: seed does **not** call `IngestPlatformEventUseCaseImpl` — it creates
`NotificationEvent` records directly in their final state (DELIVERED/FAILED):

```java
// SeedPlatformEventsUseCaseImpl — no @Transactional
for (SeedEvent event : events) {
    try {
        seedEventSaver.save(event); // own TX per call — @Transactional REQUIRED
        created++;
    } catch (ConflictException e) {
        discarded++; // duplicate eventId+clientId — skip silently
    } catch (NotFoundException e) {
        discarded++; // unknown clientId or no active subscription — skip silently
    }
}
```

One failure does not affect the others.

---

### 5. Subscription deletion — behavior with in-flight notifications

When a subscription is soft-deleted, the `@SQLRestriction("deleted = false")` makes it invisible to all future JPA queries. The impact on notifications:

| Notification state | Behavior |
|---|---|
| `PROCESSING` (worker in Phase 2) | Delivery completes using subscription data already fetched in Phase 1. Correct — the delivery was already committed to before deletion. |
| `PENDING` (not yet dispatched) | Next dispatcher run picks it up. Phase 1 of the delivery worker queries the subscription — not found. Worker marks notification as `FAILED` with `lastError = "Subscription no longer active"`. |

This behavior must be documented in the `DELETE /api/clients/{code}/subscriptions/{code}` API contract.

---

## Infrastructure Configuration

### `AsyncConfig`
```java
@Configuration
@EnableAsync
@RequiredArgsConstructor
public class AsyncConfig {

    private final AsyncProperties props;

    @Bean("webhookDeliveryExecutor")
    public Executor webhookDeliveryExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.corePoolSize());
        executor.setMaxPoolSize(props.maxPoolSize());
        executor.setQueueCapacity(props.queueCapacity());
        executor.setThreadNamePrefix("webhook-");
        // Caller runs when queue is full — prevents silently dropping notifications
        executor.setRejectedExecutionHandler(new CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

### `CircuitBreakerConfig`
The registry is built programmatically so that per-client breakers created at runtime with
`circuitBreakerRegistry.circuitBreaker(clientUniqueCode)` all inherit the same config.
Using `CircuitBreakerRegistry.ofDefaults()` + YAML `instances.*` does NOT work for dynamically
named breakers — the YAML template is only applied to statically declared instance names.

```java
@Configuration
@EnableConfigurationProperties(CircuitBreakerProperties.class)
@RequiredArgsConstructor
public class CircuitBreakerConfig {

    private final CircuitBreakerProperties props;

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig config =
            io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                .slidingWindowSize(props.slidingWindowSize())
                .failureRateThreshold(props.failureRateThreshold())
                .waitDurationInOpenState(Duration.ofSeconds(props.waitDurationSeconds()))
                .permittedNumberOfCallsInHalfOpenState(props.permittedCallsInHalfOpen())
                .build();
        return CircuitBreakerRegistry.of(config);
    }
}

// infrastructure/config/CircuitBreakerProperties.java
@ConfigurationProperties("notifications.circuitbreaker")
public record CircuitBreakerProperties(
    int slidingWindowSize,
    float failureRateThreshold,
    long waitDurationSeconds,
    int permittedCallsInHalfOpen
) {}
```

The `WebhookAdapter` calls `circuitBreakerRegistry.circuitBreaker(clientUniqueCode)` to get or
create a per-client breaker. After 50% failure rate over 5 calls, the circuit opens for 30 seconds.

### `SchedulerConfig`
```java
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
public class SchedulerConfig {
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()   // uses DB clock — safe across nodes with clock skew
                .build()
        );
    }
}
```

### `WebClientConfig`
```java
@Configuration
public class WebClientConfig {
    @Bean
    public WebClient webhookWebClient(
            @Value("${notifications.webhook.connect-timeout-ms:5000}") int connectTimeout,
            @Value("${notifications.webhook.read-timeout-ms:10000}")  int readTimeout) {
        var httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
            .responseTimeout(Duration.ofMillis(readTimeout));
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }
}
```

---

## REST API Contracts

> All endpoints are prefixed with `/api`.

### Subscription endpoints

#### `POST /api/clients/{clientUniqueCode}/subscriptions`
```json
// Request
{
  "webhookUrl":      "https://client.com/webhook",
  "authHeaderName":  "X-Webhook-Secret",
  "authHeaderValue": "s3cr3t",
  "eventTypes":      ["credit_transfer", "debit_purchase"]
}
// Response 201
{
  "uniqueCode":     "uuid",
  "webhookUrl":     "https://client.com/webhook",
  "authHeaderName": "X-Webhook-Secret",
  "eventTypes":     ["credit_transfer", "debit_purchase"],
  "active":         true,
  "createdDate":    "2024-01-01T00:00:00Z"
}
// authHeaderValue is never returned
```

#### `GET  /api/clients/{clientUniqueCode}/subscriptions` → 200 / 404
#### `PUT  /api/clients/{clientUniqueCode}/subscriptions/{subscriptionUniqueCode}` → 200
#### `DELETE /api/clients/{clientUniqueCode}/subscriptions/{subscriptionUniqueCode}` → 204

---

### Platform event ingestion

#### `POST /api/platform-events/ingest`
```json
// Request
{
  "eventId":          "EVT001",
  "eventType":        "credit_transfer",
  "payload":          "Bank transfer received for $1,500.00",
  "clientUniqueCode": "client-uuid"
}
// Response 201 — notification created
{ "notificationUniqueCode": "uuid", "deliveryStatus": "pending" }

// Response 202 — no matching subscription, event discarded
```

#### `POST /api/platform-events`
Accepts the `notification_events.json` format directly. Creates notification events in their final
state (DELIVERED or FAILED) — bypasses the delivery pipeline. The company-provided JSON is the
canonical input format for this endpoint.

Mapping rules:
- `delivery_status: "completed"` → `DeliveryStatus.DELIVERED`, `deliveredAt = delivery_date`
- `delivery_status: "failed"` → `DeliveryStatus.FAILED`
- `subscription_id`: resolved by looking up any active subscription for the client; event is
  discarded (counted in `discarded`) if no active subscription exists for that client
- Duplicate `event_id` per client → discarded

```json
// Request — matches notification_events.json exactly
{
  "events": [
    {
      "event_id":        "EVT001",
      "event_type":      "credit_card_payment",
      "content":         "Credit card payment received for $150.00",
      "delivery_date":   "2024-03-15T09:30:22Z",
      "delivery_status": "completed",
      "client_id":       "CLIENT001"
    }
  ]
}
// Response 200
{ "created": 7, "discarded": 3 }
```

---

### Notification self-service API

#### `GET /api/notification_events?clientUniqueCode=uuid&deliveryStatus=failed&from=2024-01-01T00:00:00Z&to=2024-12-31T23:59:59Z&page=0&size=20`
> `page` default 0 — `size` default 20, max 100 (400 if exceeded)
```json
{
  "content": [{
    "uniqueCode":     "uuid",
    "eventId":        "EVT001",
    "eventType":      "credit_transfer",
    "deliveryStatus": "failed",
    "retryCount":     3,
    "lastError":      "HTTP 503",
    "createdDate":    "2024-01-01T00:00:00Z",
    "deliveredAt":    null
  }],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

#### `GET /api/notification_events/{notificationUniqueCode}`
```json
{
  "uniqueCode":       "uuid",
  "eventId":          "EVT001",
  "eventType":        "credit_transfer",
  "payload":          "Bank transfer received for $1,500.00",
  "deliveryStatus":   "failed",
  "retryCount":       3,
  "lastError":        "HTTP 503 Service Unavailable",
  "nextRetryAt":      null,
  "deliveredAt":      null,
  "createdDate":      "2024-01-01T00:00:00Z",
  "lastModifiedDate": "2024-01-01T00:05:20Z"
}
```

#### `POST /api/notification_events/{notificationUniqueCode}/replay` → 202 Accepted

---

## `application.yml` Additions

```yaml
notifications:
  dispatcher:
    delay-ms: 5000
    batch-size: 100
    processing-timeout-minutes: 5    # reset PROCESSING → PENDING after this window
  retry:
    max-attempts: 3
    base-delay-seconds: 5
    max-delay-seconds: 60
    jitter-factor: 0.2
  webhook:
    connect-timeout-ms: 5000
    read-timeout-ms: 10000
  async:
    core-pool-size: 10
    max-pool-size: 50
    queue-capacity: 500
  circuitbreaker:                     # used by CircuitBreakerProperties — programmatic config
    sliding-window-size: 5
    failure-rate-threshold: 50
    wait-duration-seconds: 30
    permitted-calls-in-half-open: 2

spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          time_zone: UTC              # OffsetDateTime stored as UTC DATETIME in MySQL

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus   # whitelist — never use "*"
  endpoint:
    health:
      show-details: when-authorized
    env:
      enabled: false                  # never expose env vars via actuator
  server:
    address: 127.0.0.1               # bind management port to localhost only
```

---

## Error Handling — `GlobalExceptionHandler`

| Exception | HTTP Status |
|---|---|
| `ResourceNotFoundException` | 404 Not Found |
| `ConflictException` | 409 Conflict |
| `ForbiddenException` | 403 Forbidden |
| `MethodArgumentNotValidException` | 400 Bad Request (field-level errors) |
| `HttpMessageNotReadableException` | 400 Bad Request (invalid JSON / unknown enum) |
| Unhandled `Exception` | 500 Internal Server Error |

Consistent error envelope:
```json
{ "status": 409, "error": "Conflict", "message": "Client already has an active subscription" }
```
