# Design — Project Base Setup

## Stack

| Component | Technology | Rationale |
|---|---|---|
| Language | Java 21 | Virtual threads (Project Loom) for later async delivery; LTS release |
| Framework | Spring Boot 3.x | Required by challenge; native support for Actuator, JPA, WebClient |
| Build tool | Maven | Required by challenge; standard in enterprise Java |
| ORM | Spring Data JPA + Hibernate | Standard Spring persistence stack |
| Database | MySQL 8.0 | Required by challenge; production-grade RDBMS |
| Migrations | Flyway | Versioned, auditable schema changes; integrates natively with Spring Boot |
| Containerization | Docker + Docker Compose | One-command startup for evaluators |
| Observability | Spring Boot Actuator | Health checks and metrics out of the box |
| Testing | JUnit 5 + Testcontainers | Real MySQL in tests — no mocking the database |
| Boilerplate | Lombok | Eliminates getter/setter/constructor noise from JPA entities |
| Mapping | MapStruct | Compile-time type-safe mapper generation — zero reflection, fast, IDE-friendly |

---

## Hexagonal Architecture

The core rule: **the domain knows nothing about Spring, JPA, or any framework**. Dependencies point inward — infrastructure depends on the domain, never the reverse.

```
┌─────────────────────────────────────────────────────────────┐
│                     Infrastructure                          │
│                                                             │
│   ┌─────────────┐   ┌──────────────┐   ┌───────────────┐  │
│   │ REST (web)  │   │ Persistence  │   │ Scheduler /   │  │
│   │ Controllers │   │ JPA Entities │   │ Webhook       │  │
│   └──────┬──────┘   └──────┬───────┘   └───────┬───────┘  │
│          │                 │                    │           │
└──────────┼─────────────────┼────────────────────┼───────────┘
           │   driving        │   driven           │
           ▼   ports          ▼   ports            │
┌──────────────────────────────────────────────────┼───────────┐
│                     Domain                       │           │
│                                                  │           │
│   port.in            port.out          model     │           │
│   (use case          (repository       (pure     │           │
│    interfaces)        interfaces)       POJOs)   │           │
│                                                  │           │
│   application.usecase                            │           │
│   (use case implementations)                     │           │
└──────────────────────────────────────────────────────────────┘
```

---

## Package Structure

```
src/main/java/com/cobre/notifications/
├── domain/
│   ├── model/
│   │   └── Client.java                        ← pure Java record, no annotations
│   ├── port/
│   │   ├── in/                                ← driving ports (use case interfaces)
│   │   └── out/
│   │       └── ClientRepository.java          ← driven port (repository interface)
│   └── exception/
│       └── ResourceNotFoundException.java
├── application/
│   └── usecase/                               ← use case implementations (future phases)
└── infrastructure/
    ├── persistence/
    │   ├── entity/
    │   │   ├── BaseEntity.java                ← @MappedSuperclass
    │   │   └── ClientJpaEntity.java           ← @Entity, extends BaseEntity
    │   ├── repository/
    │   │   └── ClientJpaRepository.java       ← Spring Data JPA, implements ClientRepository
    │   └── mapper/
    │       └── ClientMapper.java              ← ClientJpaEntity ↔ Client
    ├── web/
    │   ├── controller/                        ← REST controllers (future phases)
    │   └── dto/                               ← request/response DTOs (future phases)
    ├── scheduler/                             ← @Scheduled jobs (future phases)
    └── webhook/                               ← WebClient outbound adapter (future phases)

src/main/resources/
├── application.yml
└── db/migration/
    └── V1__create_clients.sql

src/test/java/com/cobre/notifications/
└── infrastructure/
    └── persistence/
        └── ClientJpaRepositoryTest.java

src/test/resources/
└── application-test.yml                       ← Testcontainers config
```

---

## BaseEntity — `@MappedSuperclass`

Lives in `infrastructure.persistence.entity`. All JPA entities extend it.

```java
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unique_code", nullable = false, unique = true, length = 36)
    private String uniqueCode;

    @Column(nullable = false)
    private Boolean deleted = false;

    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @Column(name = "last_modified_date", nullable = false)
    private LocalDateTime lastModifiedDate;

    @PrePersist
    void prePersist() {
        if (uniqueCode == null) uniqueCode = UUID.randomUUID().toString();
        createdDate = LocalDateTime.now();
        lastModifiedDate = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        lastModifiedDate = LocalDateTime.now();
    }
}
```

**Why `@PrePersist`/`@PreUpdate` over `@CreatedDate`/`@LastModifiedDate`:**
Spring Data auditing requires `@EnableJpaAuditing` and `AuditorAware` configuration. `@PrePersist`/`@PreUpdate` are pure JPA — simpler, no extra config, no Spring dependency in the entity lifecycle.

**Why `BIGINT` autoincrement (not UUID PK):**
`unique_code` (UUID) is exposed externally in API responses. The internal PK stays as `BIGINT` for join efficiency and index performance in MySQL.

---

## Client Domain Model

**`domain.model.Client`** — plain Java, no framework annotations:

```java
public record Client(
    Long id,
    String uniqueCode,
    String name,
    String email,
    Boolean deleted,
    LocalDateTime createdDate,
    LocalDateTime lastModifiedDate
) {}
```

**`domain.port.out.ClientRepository`** — interface in the domain:

```java
public interface ClientRepository {
    Client save(Client client);
    Optional<Client> findByUniqueCode(String uniqueCode);
    Optional<Client> findByEmail(String email);
    List<Client> findAllActive();
    void softDelete(String uniqueCode);
}
```

---

## Client JPA Entity

**`infrastructure.persistence.entity.ClientJpaEntity`** — extends `BaseEntity`:

```java
@Entity
@SQLRestriction("deleted = false")   // Hibernate 6 — auto-applies to every generated query
@Table(
    name = "clients",
    indexes = {
        @Index(name = "idx_clients_email", columnList = "email"),
        @Index(name = "idx_clients_deleted", columnList = "deleted")
    }
)
public class ClientJpaEntity extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;
}
```

**Why `@SQLRestriction` instead of `AndDeletedFalse` query suffixes:**
A single annotation on the entity guarantees the soft-delete filter is applied everywhere — `findAll()`, `findBy*`, custom JPQL, even `findById()`. There is no risk of a developer accidentally querying deleted records by forgetting to add the filter suffix.

**`infrastructure.persistence.mapper.ClientMapper`** — MapStruct interface (compile-time generated):

```java
@Mapper(componentModel = "spring")
public interface ClientMapper {
    Client toDomain(ClientJpaEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uniqueCode", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    ClientJpaEntity toEntity(Client domain);
}
```

**Why MapStruct over manual mapper:**
MapStruct generates the implementation at compile time — type-safe, zero reflection, IDE-navigable. A manual `@Component` mapper is error-prone and verbose. MapStruct also catches mapping mismatches as compile errors, not runtime NPEs.

**`infrastructure.persistence.repository.ClientJpaRepository`** — Spring Data + port adapter:

```java
@Repository
@RequiredArgsConstructor
public class ClientJpaRepository implements ClientRepository {

    private final ClientSpringDataRepository springDataRepository;
    private final ClientMapper mapper;

    // delegates to Spring Data, maps entity ↔ domain
}

// package-private — not part of the public API
interface ClientSpringDataRepository extends JpaRepository<ClientJpaEntity, Long> {
    Optional<ClientJpaEntity> findByUniqueCode(String uniqueCode);
    Optional<ClientJpaEntity> findByEmail(String email);
    // findAll() inherited — @SQLRestriction handles deleted=false automatically
}
```

---

## Flyway Migration

**`V1__create_clients.sql`:**

```sql
CREATE TABLE clients (
    id                 BIGINT          NOT NULL AUTO_INCREMENT,
    unique_code        VARCHAR(36)     NOT NULL,
    deleted            BOOLEAN         NOT NULL DEFAULT FALSE,
    created_date       DATETIME        NOT NULL,
    last_modified_date DATETIME        NOT NULL,
    name               VARCHAR(150)    NOT NULL,
    email              VARCHAR(255)    NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT uq_clients_unique_code UNIQUE (unique_code),
    CONSTRAINT uq_clients_email       UNIQUE (email)
);

CREATE INDEX idx_clients_deleted ON clients (deleted);
```

---

## Application Configuration

**`application.yml`:**

```yaml
spring:
  application:
    name: notification-events-api
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: validate          # Flyway owns the schema — Hibernate only validates
    show-sql: false
    open-in-view: false
  flyway:
    enabled: true
    locations: classpath:db/migration

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
```

**`application-test.yml`** (overrides for Testcontainers — datasource URL injected programmatically):

```yaml
spring:
  flyway:
    enabled: true
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
```

---

## Docker

### Services (`docker-compose.yml`)

```
┌───────────────────────┐       ┌───────────────────────┐
│         mysql         │       │          api           │
│       MySQL 8         │◄──────│   Spring Boot :8080    │
│        :3306          │       │  (runs Flyway on start)│
└───────────────────────┘       └───────────────────────┘
```

### Dockerfile (multi-stage)

```
Stage 1 — builder:
  image: maven:3.9-eclipse-temurin-21
  - copy pom.xml + src
  - mvn package -DskipTests
  - produces: target/notification-events-api.jar

Stage 2 — runtime:
  image: eclipse-temurin:21-jre-jammy
  - copy jar from builder
  - non-root user
  - EXPOSE 8080
  - ENTRYPOINT ["java", "-jar", "notification-events-api.jar"]
```

### Environment variables (`.env.example`)

```bash
# Database
DB_URL=jdbc:mysql://mysql:3306/notification_events_api?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USERNAME=cobre
DB_PASSWORD=cobre_pass
DB_ROOT_PASSWORD=root_pass
DB_NAME=notification_events_api

# App
LOG_LEVEL=INFO
```

---

## Test Infrastructure

### Testcontainers setup

A shared `@SpringBootTest` base class spins up a single MySQL container for the entire test suite (reused across tests via `@Container` + `static`). `@Transactional` is declared on each test subclass so the rollback scope is explicit per test class.

```java
@SpringBootTest(webEnvironment = NONE)
@Testcontainers
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("notification_events_api_test")
            .withUsername("test")
            .withPassword("test")
            .withStartupTimeoutSeconds(300)
            .withConnectTimeoutSeconds(300)
            .withCommand("--innodb-buffer-pool-size=32M", "--skip-name-resolve", "--character-set-server=utf8mb4");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    protected EntityManager entityManager;
}
```

**Why `--innodb-buffer-pool-size=32M`:** MySQL 8.0 defaults to a 128 MB InnoDB buffer pool which causes 60–120 s initialization in Docker Desktop. Reducing it to 32 MB cuts startup to ~15 s with no functional impact for tests.

### ClientJpaRepository integration tests

| Test | Verifies |
|---|---|
| `persistAndFindByUniqueCode` | entity saved, `uniqueCode` auto-generated, retrieved correctly |
| `softDelete_excludedFromActiveQueries` | `deleted=true` entity not returned by `findAllActive()` |
| `duplicateEmail_throwsConstraintViolation` | UNIQUE constraint on `email` enforced at DB level |

---

## Health Check

#### `GET /actuator/health`

```
Response 200 → { "status": "UP",   "components": { "db": { "status": "UP" } } }
Response 503 → { "status": "DOWN", "components": { "db": { "status": "DOWN" } } }
```

Spring Boot Actuator's built-in `DataSourceHealthIndicator` handles the MySQL connectivity check automatically.
