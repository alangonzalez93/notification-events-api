# Requirements — Project Base Setup

## Context

This spec covers the foundation of the Notification Events API: project structure, stack configuration,
base entity model, the `client` entity, database migrations, containerized runtime, and test infrastructure.
It represents the state of the system **prior** to implementing notification delivery or the self-service API.

---

## User Stories

### 1. Project Structure and Configuration

**US-1.1** — As a developer, I want a Java project structured with modern production standards so that
the codebase reflects a hexagonal (ports & adapters) architecture and is easy to onboard.

**Acceptance Criteria:**
- The project shall use **Java 21**, **Spring Boot 3.x**, and **Maven** as build tool.
- The package structure shall enforce hexagonal architecture boundaries:
  - `domain.model` — pure Java domain objects (no Spring, no JPA annotations)
  - `domain.port.in` — use case interfaces (driving ports)
  - `domain.port.out` — repository and external service interfaces (driven ports)
  - `application.usecase` — use case implementations
  - `infrastructure.persistence.entity` — JPA entities and `@MappedSuperclass` base
  - `infrastructure.persistence.repository` — Spring Data JPA repositories
  - `infrastructure.persistence.mapper` — mappers between JPA entities and domain models
  - `infrastructure.web.controller` — REST controllers
  - `infrastructure.web.dto` — request/response DTOs
  - `infrastructure.scheduler` — scheduled jobs
  - `infrastructure.webhook` — outbound HTTP client
- The system shall load all configuration from `application.yml` with environment variable overrides.
- IF a required configuration property is missing at startup, THEN the system shall fail fast with a descriptive error.
- The project shall include an `.env.example` documenting all required environment variables with safe placeholder values.

---

### 2. Base Entity

**US-2.1** — As a developer, I want all JPA entities to inherit a common set of audit columns so that
every table has a consistent schema without repeating column definitions.

**Acceptance Criteria:**
- The system shall define a `@MappedSuperclass` named `BaseEntity` in `infrastructure.persistence.entity` with the following columns:

  | Field | Column | Type | Constraints |
  |---|---|---|---|
  | `id` | `id` | BIGINT | PK, auto-increment, NOT NULL |
  | `uniqueCode` | `unique_code` | VARCHAR(36) | NOT NULL, UNIQUE |
  | `deleted` | `deleted` | BOOLEAN | NOT NULL, default false |
  | `createdDate` | `created_date` | DATETIME | NOT NULL |
  | `lastModifiedDate` | `last_modified_date` | DATETIME | NOT NULL |

- `uniqueCode` shall be populated automatically with a UUID v4 before persist if not set.
- `createdDate` shall be set automatically on first persist using `@PrePersist`.
- `lastModifiedDate` shall be updated automatically on every update using `@PreUpdate`.
- All concrete entities in the system shall extend `BaseEntity` and must NOT redefine these columns.

---

### 3. Client Entity

**US-3.1** — As the platform, I want to persist client records so that notification events can be
associated with a specific client and validated before delivery.

**Acceptance Criteria:**
- The system shall store a `clients` table with the following domain columns (audit columns inherited from `BaseEntity`):

  | Field | Column | Type | Constraints |
  |---|---|---|---|
  | `name` | `name` | VARCHAR(150) | NOT NULL |
  | `email` | `email` | VARCHAR(255) | NOT NULL, UNIQUE |

- The domain model `Client` in `domain.model` shall be a plain Java record or class with no JPA annotations.
- A `ClientJpaEntity` in `infrastructure.persistence.entity` shall extend `BaseEntity` and map to the `clients` table.
- A `ClientMapper` in `infrastructure.persistence.mapper` shall convert between `ClientJpaEntity` and `Client`.
- A `ClientRepository` port interface shall be defined in `domain.port.out`.
- A `ClientJpaRepository` (Spring Data) in `infrastructure.persistence.repository` shall implement the port.
- WHEN a client with a duplicate `email` is persisted, THEN the system shall throw a constraint violation (no silent override).

---

### 4. Database and Migrations

**US-4.1** — As a developer, I want versioned database migrations so that schema changes are
reproducible and auditable across all environments.

**Acceptance Criteria:**
- The system shall use **Flyway** for all schema management — no `spring.jpa.hibernate.ddl-auto` create/update in any non-test profile.
- WHEN migrations are run against a clean database, the system shall create all tables, indexes, and constraints without errors.
- Migration files shall follow the naming convention `V{version}__{description}.sql` (e.g., `V1__create_clients.sql`).
- The initial migration shall create the `clients` table including all `BaseEntity` columns, primary key, unique constraints, and indexes.
- `unique_code` shall have a UNIQUE index.
- `email` shall have a UNIQUE index.
- `deleted` shall have an index to support efficient soft-delete filtering queries.

---

### 5. Containerized Environment

**US-5.1** — As an operator, I want to start the entire system with a single command so I can evaluate
and test the project without manual setup.

**Acceptance Criteria:**
- WHEN `docker compose up` is executed, the system shall start all required services: **MySQL 8** and the **Spring Boot API**.
- WHEN the API container starts, the system shall run Flyway migrations automatically before accepting traffic.
- The system shall expose the API on port **8080** by default.
- The system shall configure MySQL with a dedicated database, user, and password defined via environment variables.
- WHEN MySQL is not yet ready, the API container shall wait and retry rather than crash immediately (health check + `depends_on` condition).
- The `Dockerfile` shall use a **multi-stage build**: one stage to build the JAR with Maven, one stage to run it on a minimal JRE image.
- The project shall include a `.env.example` with all variables needed to run `docker compose up` locally.

---

### 6. Observability — Health Check

**US-6.1** — As an operator, I want a health endpoint so I can verify the system is running and
connected to its dependencies.

**Acceptance Criteria:**
- The system shall expose `GET /actuator/health` via Spring Boot Actuator.
- WHEN both the API and MySQL are healthy, the endpoint shall return HTTP 200.
- WHEN MySQL is unreachable, the endpoint shall return HTTP 503.
- The system shall suppress verbose Hibernate SQL logging in non-development profiles.
- The system shall emit structured, readable logs with timestamp, level, and message.

---

### 7. Test Infrastructure

**US-7.1** — As a developer, I want an integration test setup that runs against a real MySQL instance
so that tests reflect production behavior without mocking the database.

**Acceptance Criteria:**
- The system shall use **Testcontainers** to spin up a MySQL 8 container for integration tests.
- EACH test shall run within a transaction that is rolled back after the test — no shared state between tests.
- Flyway migrations shall run automatically against the Testcontainers MySQL before tests execute.
- The system shall include at least one integration test for `ClientJpaRepository` covering:
  - Persist and retrieve by `uniqueCode`
  - Soft delete (set `deleted = true`) and verify it is excluded from standard queries
  - Duplicate `email` constraint violation
- Test dependencies (`testcontainers`, `junit-jupiter`) shall be declared in a dedicated Maven test scope.
