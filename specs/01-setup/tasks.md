# Tasks — Project Base Setup

Each task references the requirement it satisfies. Tasks marked with `[P]` within a phase can be executed in parallel.

---

## Phase 1 — Project Scaffold

- [x] **1.1** Generar proyecto Spring Boot 3.x con Maven via Spring Initializr (Java 21, dependencias: Spring Web, Spring Data JPA, Flyway, MySQL Driver, Spring Boot Actuator, Testcontainers) → satisface US-1.1
- [x] **1.2** Definir estructura de paquetes hexagonal vacía (`domain`, `application`, `infrastructure`) con `.gitkeep` → satisface US-1.1
- [x] **1.3** Configurar `application.yml` con propiedades de datasource por variable de entorno, `ddl-auto: validate`, Flyway habilitado, Actuator expuesto → satisface US-1.1, US-4.1, US-6.1
- [x] **1.4** Crear `.env.example` con todas las variables requeridas documentadas → satisface US-1.1, US-5.1

---

## Phase 2 — BaseEntity `[P]`

> Prerequisito: Phase 1 completa.

- [x] **2.1** Implementar `infrastructure.persistence.entity.BaseEntity` (`@MappedSuperclass`, `@PrePersist`, `@PreUpdate`, UUID auto-generado, soft delete) → satisface US-2.1
- [x] **2.2** `[P]` Crear migración Flyway `V1__create_clients.sql` (tabla `clients`, PK, unique constraints, indexes) → satisface US-4.1

---

## Phase 3 — Client `[P]`

> Prerequisito: Phase 2 completa.

- [x] **3.1** `[P]` Implementar `domain.model.Client` (Java record puro, sin anotaciones de framework) → satisface US-3.1
- [x] **3.2** `[P]` Implementar `domain.port.out.ClientRepository` (interface en el dominio) → satisface US-3.1
- [x] **3.3** `[P]` Implementar `domain.exception.ResourceNotFoundException` → satisface US-3.1
- [x] **3.4** Implementar `infrastructure.persistence.entity.ClientJpaEntity` (extiende `BaseEntity`, `@SQLRestriction("deleted = false")`, mapea tabla `clients`) → satisface US-3.1
- [x] **3.5** Implementar `infrastructure.persistence.mapper.ClientMapper` (MapStruct interface — generación compile-time, `@Mapper(componentModel = "spring")`) → satisface US-3.1
- [x] **3.6** Implementar `infrastructure.persistence.repository.ClientJpaRepository` (implementa `ClientRepository`, delega a Spring Data) → satisface US-3.1

---

## Phase 4 — Docker `[P]`

> Prerequisito: Phase 1 completa.

- [x] **4.1** `[P]` Escribir `Dockerfile` multi-stage (stage builder con Maven 3.9 + Java 21, stage runtime con JRE 21, usuario no-root) → satisface US-5.1
- [x] **4.2** `[P]` Escribir `docker-compose.yml` con servicios `mysql` y `api`, healthcheck en MySQL, `depends_on` con condition `service_healthy` → satisface US-5.1

---

## Phase 5 — Test Infrastructure

> Prerequisito: Phase 3 completa.

- [x] **5.1** Implementar `IntegrationTestBase` con `@Testcontainers`, `MySQLContainer` estático (reusado entre tests), `@DynamicPropertySource` → satisface US-7.1
- [x] **5.2** Implementar `ClientJpaRepositoryTest` con los tres casos: persist+retrieve, soft delete excluido de queries activas, violación de constraint en email duplicado → satisface US-7.1

---

## Phase 6 — Validación

> Prerequisito: Phases 4 y 5 completas.

- [ ] **6.1** Ejecutar `docker compose up` y verificar que MySQL y API levantan sin errores
- [ ] **6.2** Verificar que Flyway corre las migraciones automáticamente al iniciar la API
- [ ] **6.3** Verificar `GET /actuator/health` retorna 200 con MySQL conectado
- [ ] **6.4** Ejecutar `mvn test` y verificar que los tres tests de `ClientJpaRepositoryTest` pasan
- [ ] **6.5** Verificar que `CHALLENGE.md`, `CLAUDE.md`, `notification_events.json` y archivos `.pdf` no aparecen en `git status`
