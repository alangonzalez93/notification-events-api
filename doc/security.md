# Security Analysis — OWASP API Security Top 10

Three vulnerabilities applicable to this API, with their attack scenarios and proposed mitigations.

---

## API1:2023 — Broken Object Level Authorization (BOLA)

### Why this applies

The self-service API exposes resources by identifier:

```
GET  /api/notification_events/{uniqueCode}
POST /api/notification_events/{uniqueCode}/replay
```

`uniqueCode` values are UUIDs — non-sequential but not secret. A client that knows or guesses a `uniqueCode` belonging to another client can read that client's notification payload or trigger an unwanted replay, because the current implementation has no ownership check beyond the identifier itself.

### Attack scenario

```
# Attacker authenticates as CLIENT-A
# Obtains a uniqueCode belonging to CLIENT-B (e.g. from a support ticket, log leak, or enumeration)

GET /api/notification_events/b7f3a1c9-...
→ 200 OK { "clientUniqueCode": "CLIENT-B", "payload": "Transfer $50,000 to account #9876" }

POST /api/notification_events/b7f3a1c9-.../replay
→ 202 Accepted   ← triggers an unintended re-delivery to CLIENT-B's webhook
```

### Proposed mitigation

Every endpoint that returns or mutates a `NotificationEvent` must validate that the resource belongs to the authenticated caller before returning a response. The authenticated identity must come from the security context — never from a query parameter or request body that the caller controls.

```
if (!event.clientUniqueCode().equals(authenticatedClientCode)) {
    throw ForbiddenException → HTTP 403
}
```

For the list endpoint (`GET /notification_events`), any `clientUniqueCode` supplied as a query parameter must be ignored; the query must be scoped exclusively to the authenticated client's code.

---

## API2:2023 — Broken Authentication

### Why this applies

The application has no authentication layer configured. Every endpoint — including `POST /api/platform-events/ingest` — is reachable without credentials. Spring Boot's default, without `spring-boot-starter-security` on the classpath and a `SecurityFilterChain` configured, is to either require HTTP Basic with a random password (dev mode) or allow all requests depending on the version and configuration.

### Attack scenario

```
# No credentials required
GET /api/notification_events?deliveryStatus=DELIVERED
→ 200 OK  [full list of all clients' transactions]

POST /api/platform-events/ingest
{ "eventId": "FAKE-001", "clientUniqueCode": "CLIENT-B", "payload": "Fraudulent credit" }
→ 201 Created   ← arbitrary data injected into the pipeline
```

### Proposed mitigation

Add `spring-boot-starter-security` and configure a `SecurityFilterChain` with stateless session policy and two distinct protection zones:

| Zone | Consumers | Mechanism |
|---|---|---|
| `POST /api/platform-events/**` | Cobre Platform (internal service) | API key or mTLS with `ROLE_PLATFORM` |
| `GET/POST /api/notification_events/**` | Cobre Clients (external) | Bearer token / OAuth2 with `ROLE_CLIENT` |
| `GET /actuator/health` | Load balancer, uptime monitors | Public |
| `GET /actuator/**` (all others) | Ops team only | `ROLE_OPS` or internal network restriction |

For this challenge, HTTP Basic with hardcoded roles is sufficient as a demo; production would require OAuth2 / JWT with short-lived tokens issued by an identity provider (Keycloak, Auth0, etc.).

---

## API8:2023 — Security Misconfiguration

### Why this applies

Spring Boot Actuator is enabled. The current `application.yml` already whitelists the exposed endpoints (`health, info, metrics, prometheus`) and disables `/actuator/env`, which is the right direction. However two gaps remain:

1. `management.endpoint.health.show-details: always` — the health endpoint reveals internal component details (DB connection pool state, disk space, custom health indicators) to anyone that can reach management port 8081, with no authentication required.
2. `management.server.address` is commented out — in production, port 8081 should be bound to `127.0.0.1` or a private VPC interface so it is never reachable from the public internet.

### Attack scenario

```
# Attacker reaches management port directly (e.g. misconfigured load balancer, SSRF)
GET :8081/actuator/health
→ 200 OK {
    "status": "UP",
    "components": {
      "db": { "status": "UP", "details": { "database": "MySQL", "validationQuery": "..." } },
      "diskSpace": { "status": "UP", "details": { "total": 107374182400, "free": 43210 } }
    }
  }
```

Even without `env` or `heapdump`, infrastructure topology leaks are useful for reconnaissance.

### Proposed mitigation

Three complementary controls:

1. Change `show-details` to `when-authorized` so health detail is only visible to authenticated ops users.
2. Uncomment `management.server.address: 127.0.0.1` for every non-local environment.
3. Add a `SecurityFilterChain` scoped to the management port that requires `ROLE_OPS` for all `/actuator/**` paths except `/actuator/health`.

```yaml
# application.yml — production-ready actuator config
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized   # not always
    env:
      enabled: false
  server:
    port: 8081
    address: 127.0.0.1               # internal network only
```

---

## Summary

| OWASP ID | Vulnerability | Attack vector | Proposed mitigation |
|---|---|---|---|
| API1:2023 | BOLA — client reads/replays another client's notification | Know or guess a `uniqueCode` | Ownership check against authenticated security context on every resource endpoint |
| API2:2023 | Broken Authentication — all endpoints publicly accessible | Unauthenticated HTTP request | `SecurityFilterChain` with stateless session, role-based zones per endpoint group |
| API8:2023 | Security Misconfiguration — Actuator health leaks infra details | Direct request to management port | `show-details: when-authorized`, bind port to `127.0.0.1`, ops-role gate on `/actuator/**` |

> None of these mitigations are implemented in the current codebase — this is intentional per the challenge scope, which asks to identify and propose, not to implement. The delivery pipeline and self-service API are the implementation focus.

---

## Additional Observations

**API7:2023 — SSRF via webhookUrl**: `POST /api/subscriptions` accepts any URL. The server makes HTTP requests to that URL during delivery, so an attacker could register `http://169.254.169.254/` (cloud metadata) or internal addresses to probe the private network. Mitigation: validate on subscription creation that the URL uses HTTPS and does not resolve to a private/loopback/link-local address.

**Secrets at rest — auth_header_value**: The webhook authentication token is stored as plaintext `VARCHAR` in the `subscriptions` table. Any DB replica, backup, or log with query output exposes it. Mitigation: encrypt the column via a JPA `AttributeConverter` backed by AES-256-GCM with a KMS-managed key, or store a Vault/Secrets Manager reference and resolve the value only at delivery time.

**API4:2023 — Unrestricted Resource Consumption**: `POST /api/platform-events/ingest` has no rate limit. A caller can flood the pipeline with thousands of `PENDING` notifications in seconds, degrading delivery throughput without exploiting any other vulnerability. Mitigation: per-client rate limiting at the API Gateway or via `bucket4j` in Spring.
