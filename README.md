# Notification Events API

Event notification service built with Java 21 + Spring Boot 3, hexagonal architecture, transactional outbox pattern, and non-blocking webhook delivery.

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
  webhook/    ← WebClient adapter (adapt HTTP out → domain)
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

> _Instructions will be added here once the implementation is complete._
>
> The short version will be: `docker compose up` → seed data → `curl` examples.

---

## Testing

> _Test execution instructions will be added here once the implementation is complete._
>
> The test suite uses Testcontainers (real MySQL) and WireMock (webhook simulation). No mocked repositories.

---

## AI usage

This project was built with Claude Code (Anthropic) as a development assistant.

**How it was used:**

- Spec writing: drafting `requirements.md`, `design.md`, and `tasks.md` for each feature, then reviewing and iterating on the design before any code was written
- Architecture decisions: evaluating tradeoffs (e.g., transactional outbox vs. event broker, ShedLock vs. Redis, at-least-once vs. at-most-once delivery)
- Security analysis: identifying OWASP vulnerabilities relevant to this API and drafting mitigations
- System design document: generating Mermaid diagrams and documenting scalability/resilience decisions

**What was not delegated to AI:**

- Final architectural decisions — every tradeoff in the design docs reflects explicit choices reviewed and approved before implementation
- Code review — all generated code was reviewed for correctness, security, and adherence to the hexagonal architecture contract

> Screenshots and detailed prompt logs will be added here before the submission deadline.
