# Security Analysis — OWASP API Top 10

Three vulnerabilities relevant to the Notification Events API, with mitigations implemented in code and configuration.

---

## Vulnerability 1 — API1:2023 Broken Object Level Authorization (BOLA)

### Description

The self-service API exposes `/api/notification_events` and `/api/notification_events/{id}`. Without proper authorization checks, **Client A could query or replay Client B's notifications** simply by knowing or guessing a notification's `uniqueCode`.

### Attack scenario

```
# Attacker is authenticated as CLIENT001
# Guesses or enumerates a uniqueCode belonging to CLIENT002

GET /api/notification_events/a3f9b2c1-...
→ 200 OK  { "client_id": "CLIENT002", "payload": "Transfer $50,000 to account #9876" }

POST /api/notification_events/a3f9b2c1-.../replay
→ 202 Accepted  ← triggers unwanted re-delivery to CLIENT002's webhook
```

This is the **#1 API vulnerability** according to OWASP because APIs expose object IDs by design, and developers often forget to enforce ownership checks beyond authentication.

### Mitigation

Every query and mutation is scoped by `clientUniqueCode`, extracted from the authenticated context (not from the request body):

```java
// NotificationEventController
@GetMapping("/{code}")
public ResponseEntity<NotificationEventDetailResponse> getOne(
        @PathVariable String code,
        @AuthenticatedClient String authenticatedClientCode) {  // from security context

    NotificationEvent event = getUseCase.getByUniqueCode(code);

    if (!event.clientUniqueCode().equals(authenticatedClientCode)) {
        throw new ForbiddenException("Access denied");  // → HTTP 403
    }
    return ResponseEntity.ok(mapper.toDetailResponse(event));
}
```

The `GET /api/notification_events` list endpoint accepts `clientUniqueCode` as a query parameter but **ignores it** — the authenticated client code from the security context is always used as the filter:

```java
// GetNotificationEventsUseCaseImpl
PageResult<NotificationEvent> result = repository.findByClientUniqueCode(
    authenticatedClientCode,  // ← always from security context, never from user input
    filters.deliveryStatus(), filters.from(), filters.to(),
    filters.page(), filters.size()
);
```

---

## Vulnerability 2 — API2:2023 Broken Authentication

### Description

Without authentication, all endpoints are publicly accessible. An unauthenticated attacker could:

- Read all notification events for any client (data breach)
- Trigger replays for any client (operational disruption)
- Ingest arbitrary platform events (data poisoning)

### Attack scenario

```
# No credentials required
GET /api/notification_events?clientUniqueCode=CLIENT001
→ 200 OK  [full list of CLIENT001's transactions]

POST /api/platform-events/ingest
{ "eventId": "FAKE001", "eventType": "credit_deposit", "clientUniqueCode": "CLIENT001", "payload": "Fraudulent deposit" }
→ 201 Created
```

### Mitigation

Spring Security configuration with two distinct protection zones:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)           // REST API — no session
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public: health check and API docs only
                .requestMatchers(
                    "/actuator/health",
                    "/swagger-ui/**",
                    "/v3/api-docs/**"
                ).permitAll()
                // Internal: platform event ingestion requires service-to-service token
                .requestMatchers("/api/platform-events/**").hasRole("PLATFORM")
                // Client-facing: self-service API requires client authentication
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults());           // replace with JWT/OAuth2 in prod
        return http.build();
    }
}
```

**Authentication per zone:**

| Zone | Consumers | Auth mechanism |
|---|---|---|
| `/api/platform-events/**` | Cobre Platform (internal) | Bearer token / API key with `ROLE_PLATFORM` |
| `/api/notification_events/**` | Cobre Clients (external) | Bearer token / API key with `ROLE_CLIENT` |
| `/actuator/health` | Load balancer, monitoring | Public (health only, no sensitive data) |
| `/actuator/**` (all others) | Ops team | Restricted to internal network or `ROLE_OPS` |

> **Note for this challenge**: basic auth is used for simplicity. Production would use OAuth2 / JWT with short-lived tokens and a proper identity provider.

---

## Vulnerability 3 — API8:2023 Security Misconfiguration — Actuator Endpoints Exposed

### Description

Spring Boot Actuator exposes operational endpoints: `/actuator/env`, `/actuator/beans`, `/actuator/heapdump`, `/actuator/loggers`, `/actuator/metrics`. If exposed without restriction, attackers gain:

- **`/actuator/env`**: all environment variables including DB credentials, API keys, and webhook auth values
- **`/actuator/heapdump`**: a full JVM heap dump — can be parsed offline to extract plaintext secrets from memory
- **`/actuator/beans`**: full Spring application context — useful for mapping attack surface

### Attack scenario

```
# No auth required in a misconfigured app
GET /actuator/env
→ 200 OK {
    "propertySources": [{
      "properties": {
        "spring.datasource.password": { "value": "prod_db_password_123" },
        "notifications.auth-header-value": { "value": "webhook_secret_key" }
      }
    }]
  }

GET /actuator/heapdump
→ 200 OK  [binary heap dump download — extract secrets with strings or heap analysis tools]
```

### Mitigation

**`application.yml`** — expose only health and info; disable sensitive endpoints:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus    # whitelist approach
        # DO NOT use include: "*" — that exposes env, heapdump, beans, etc.
  endpoint:
    health:
      show-details: when-authorized               # hide component details from anonymous
    env:
      enabled: false                              # never expose env
  server:
    port: 8081                                    # separate management port
    address: 127.0.0.1                            # bind to localhost only (internal network)
```

**`SecurityConfig`** — additional layer to restrict management port to ops role:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/actuator/health").permitAll()
    .requestMatchers("/actuator/**").hasRole("OPS")  // ops team only
    .anyRequest().authenticated()
)
```

**Defense in depth checklist:**

| Control | Implementation |
|---|---|
| Whitelist endpoints | `management.endpoints.web.exposure.include` — explicit list only |
| Bind to internal interface | `management.server.address=127.0.0.1` |
| Require authentication | `hasRole("OPS")` on all `/actuator/**` except health |
| Disable env endpoint | `management.endpoint.env.enabled=false` |
| Mask sensitive properties | Spring Boot auto-masks `password`, `secret`, `key` in `/actuator/env` |
| Separate port | `management.server.port=8081` — not exposed in public load balancer |

---

## Summary

| OWASP ID | Vulnerability | Mitigation |
|---|---|---|
| API1:2023 | BOLA — client accessing another client's data | Authorization check in use case using security context, not user input |
| API2:2023 | Broken Authentication — unauthenticated access | Spring Security with role-based protection per endpoint zone |
| API8:2023 | Security Misconfiguration — Actuator exposure | Whitelist-only exposure, internal port binding, OPS role required |

---

## Implementation Notes

Security is not implemented in the current codebase — the focus of this challenge is the delivery
pipeline and the self-service API. The following would be required to harden the service for
production:

### Spring Security setup
- Add `spring-boot-starter-security` to `pom.xml`
- Implement `SecurityFilterChain` with STATELESS session policy
- Define three roles: `PLATFORM` (ingest endpoints), `CLIENT` (self-service API), `OPS` (actuator)
- Use HTTP Basic for a quick demo setup; replace with **JWT / OAuth2** (e.g., Keycloak, Auth0) in production

### BOLA prevention
- Extract `clientUniqueCode` from the `SecurityContext` in every controller method — never from a user-supplied query parameter or request body
- The authenticated client code becomes the immutable filter for all queries to `NotificationEventRepository` and `SubscriptionRepository`

### Actuator hardening
Apply the following to `application.yml` (already in the spec):
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    env:
      enabled: false
  server:
    address: 127.0.0.1
```
