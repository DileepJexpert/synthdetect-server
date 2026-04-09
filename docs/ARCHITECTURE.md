# SynthDetect — System Architecture

_Last updated: 2026-04-09_

SynthDetect is a synthetic-media detection platform that lets developers
programmatically classify whether an image or text was produced by a human
or a generative model. It is designed to meet **India IT Rules 2026**
compliance (3-hour takedown SLAs) and scale from hobby projects to
enterprise workloads.

---

## 1. High-Level View

```
                          ┌─────────────────┐
                          │   End Users /   │
                          │   Web & Mobile  │
                          │   Applications  │
                          └────────┬────────┘
                                   │ HTTPS (JSON / multipart)
                                   ▼
┌──────────────────────────────────────────────────────────────┐
│                      api-gateway                             │
│                  (Spring Boot 3.3 / Java 21)                 │
│                                                              │
│   Filters:  ApiKeyAuthFilter → JwtAuthFilter → RateLimit    │
│                                                              │
│   Domains:  auth · user · detection · usage · webhook       │
│             compliance · admin · scheduler                   │
└────┬──────────────────┬───────────────────┬──────────┬───────┘
     │                  │                   │          │
     │ JDBC (Hikari)    │ Lettuce (Redis)   │ WebClient│ SMTP
     ▼                  ▼                   ▼          ▼
┌──────────┐      ┌──────────┐      ┌──────────┐  ┌─────────┐
│PostgreSQL│      │  Redis   │      │ml-engine │  │  SMTP   │
│   16     │      │    7     │      │(FastAPI) │  │(Gmail)  │
│          │      │          │      │          │  │         │
│ 11 tables│      │ quota    │      │ Hugging- │  │ verify  │
│ Flyway   │      │ jwt bl   │      │ Face     │  │ reset   │
│ V1–V8    │      │ key cache│      │ heur.    │  │ alerts  │
└──────────┘      └──────────┘      └──────────┘  └─────────┘
```

---

## 2. Service Responsibilities

### 2.1 `api-gateway` (Spring Boot 3.3 / Java 21)

The single entry point for all client traffic.

| Layer | Concern |
|-------|---------|
| **Filters** | Authenticate (API key / JWT), enforce rate limits, add security headers |
| **Controllers** | Expose REST endpoints, validate DTOs |
| **Services** | Business rules, quota gates, orchestrate ML calls |
| **Repositories** | JPA access to PostgreSQL |
| **Schedulers** | `@Scheduled` jobs for quota reset, compliance deadlines, token cleanup |
| **Config** | Security filter chain, Redis, CORS, JWT, OpenAPI |

**Key design choices**
- Stateless — horizontal scaling by running N identical pods behind a load balancer.
- `open-in-view: false` — no lazy loading outside transactions.
- Two auth flows (API key for server-to-server, JWT for first-party clients).
- Three-filter pipeline: **API key → JWT → rate limit**. Rate limiting runs
  last so it can read the authenticated principal.

### 2.2 `ml-engine` (FastAPI / Python 3.11)

A separate process that owns the heavy ML models. Keeping it separate means:
- Java process is lean (~512 MB RAM).
- Models can be updated / retrained without redeploying the gateway.
- Python-native libraries (torch, transformers, OpenCV) without JNI pain.

| Endpoint | Purpose |
|----------|---------|
| `POST /v1/detect/image` | Classify an image (URL input) |
| `POST /v1/detect/text` | Classify a text passage |
| `GET /health` | Liveness probe |
| `GET /metrics` | Prometheus metrics |

**Detection strategy** — two independent signals combined:

1. **Transformer classifier** — HuggingFace pipeline
   (`umm-maybe/AI-image-detector` for images, RoBERTa for text).
2. **Heuristic fallback** — runs even when the model is unavailable:
   - **Images**: FFT frequency spectrum, noise residuals, colour saturation bias.
   - **Text**: perplexity, burstiness, type-token ratio, punctuation patterns.

Results are merged into a single `0.0–1.0` probability plus a list of
per-signal breakdowns persisted by the gateway.

### 2.3 PostgreSQL 16

Single logical database (`synthdetect`) with schemas evolved via Flyway:

| Migration | Tables | Purpose |
|-----------|--------|---------|
| V1 | `users` | Accounts, plan, status |
| V2 | `api_keys` | Hashed keys, scopes, last-used |
| V3 | `detection_requests`, `detection_signals` | Detection history |
| V4 | `usage_stats` | Monthly rollups (one row per user+month) |
| V5 | `webhooks`, `webhook_deliveries` | Webhook configs + delivery log |
| V6 | `compliance_reports` | Takedown tickets, IT Rules 2026 deadlines |
| V7 | `email_tokens` | Email verification + password reset |
| V8 | `user_role` enum | Admin / user RBAC |

All tables use `uuid` primary keys and `created_at` / `updated_at` audit columns.

### 2.4 Redis 7

Used as a cache and ephemeral datastore — never as the system of record.

| Key pattern | Purpose | TTL |
|-------------|---------|-----|
| `rate_limit:{userId}:{minute}` | Per-minute RPM counter | 120 s |
| `api_key:{sha256}` | Cached `ApiKey` lookup | 300 s |
| `api_key:revoked:{sha256}` | Tombstone so revoked keys stay dead | 600 s |
| `jwt:blacklist:{jti}` | Revoked tokens | = remaining token lifetime |

If Redis is unavailable, the gateway falls back to a DB hit for API keys and
allows requests through the rate limiter (graceful degradation).

---

## 3. Package Structure (api-gateway)

```
com.synthdetect
├── SynthDetectApplication.java        ← @SpringBootApplication, @EnableScheduling
│
├── config/                            ← cross-cutting Spring config
│   ├── SecurityConfig.java            ← filter chain, CORS, method security
│   ├── JwtService.java                ← sign / verify / extract jti
│   ├── RedisConfig.java
│   ├── RateLimitConfig.java
│   ├── WebConfig.java                 ← CORS allowlist
│   ├── OpenApiConfig.java
│   ├── PasswordEncoderConfig.java
│   └── AsyncConfig.java
│
├── auth/                              ← API key + JWT authentication
│   ├── filter/ {ApiKey,Jwt,RateLimit}Filter
│   ├── service/
│   │   ├── ApiKeyService              ← CRUD, SHA-256 hashing
│   │   ├── ApiKeyAuthenticationService
│   │   ├── ApiKeyCacheService         ← Redis cache + revocation set
│   │   └── TokenBlacklistService      ← Redis JWT blacklist (jti)
│   ├── model/ {ApiKey, ApiKeyStatus, ApiKeyScope}
│   └── controller/ApiKeyController
│
├── user/                              ← accounts, login, profile
│   ├── model/ {User, UserRole, UserPlan, UserStatus, EmailToken}
│   ├── service/ {UserService, EmailService}
│   └── controller/UserController
│
├── detection/                         ← the core product
│   ├── model/ {DetectionRequest, DetectionSignal, DetectionType, Status}
│   ├── client/MlEngineClient          ← WebClient → Python service
│   ├── service/ {DetectionService, ImageUploadService}
│   └── controller/DetectionController
│
├── usage/                             ← monthly quotas + plan enforcement
│   ├── model/UsageStat
│   ├── service/UsageService           ← fires quota.warning @ 80 %, quota.exceeded @ 100 %
│   └── controller/UsageController
│
├── webhook/                           ← outbound event delivery
│   ├── model/ {Webhook, WebhookStatus, WebhookDelivery}
│   ├── service/WebhookService         ← HMAC-SHA256 signing, auto-disable
│   └── controller/WebhookController
│
├── compliance/                        ← India IT Rules 2026
│   ├── model/ {ComplianceReport, ComplianceAction}
│   ├── service/ComplianceService
│   └── controller/ComplianceController
│
├── admin/                             ← ops / support endpoints
│   └── controller/AdminController     ← @PreAuthorize("hasRole('ADMIN')")
│
├── scheduler/                         ← background jobs
│   ├── QuotaResetJob                  ← cron: 1st of month 00:01
│   ├── ComplianceDeadlineJob          ← every 15 min
│   └── ExpiredTokenCleanupJob         ← daily 03:00
│
└── common/                            ← shared plumbing
    ├── exception/ {ApiException, GlobalExceptionHandler, ...}
    ├── util/ {HashUtil, ContentValidator, IdGenerator}
    └── model/ApiResponse
```

---

## 4. Data Model (ER Sketch)

```
  users ──1─┬──*── api_keys
            │
            ├──*── detection_requests ──*── detection_signals
            │
            ├──*── usage_stats            (unique: user_id + month)
            │
            ├──*── webhooks ──*── webhook_deliveries
            │
            ├──*── compliance_reports
            │
            └──*── email_tokens           (EMAIL_VERIFICATION / PASSWORD_RESET)
```

Indexes are created on foreign keys, `created_at`, and on
`content_hash` for dedup lookups.

---

## 5. Security Model

| Layer | Control |
|-------|---------|
| Transport | HTTPS (terminated at reverse proxy / ALB) |
| Auth — server | `Authorization: Bearer sd_live_…` API key (SHA-256 hashed at rest) |
| Auth — client | `Authorization: Bearer <jwt>` access token w/ `jti` claim |
| Authorization | `SecurityContext` principal + `@PreAuthorize` method security |
| Revocation | Redis blacklist (JWT) + cache eviction (API key) |
| Passwords | BCrypt (strength 10) |
| Secrets | JWT HS256 256-bit key from `JWT_SECRET` env |
| Headers | HSTS, X-Frame-Options: DENY, CSP, Referrer-Policy |
| CORS | Explicit origin allowlist from `CORS_ALLOWED_ORIGINS` |
| Rate limiting | Per-user RPM via Redis fixed-window counter |
| Multipart | 20 MB max, MIME sniffing |
| Webhooks | HMAC-SHA256 signature in `X-SynthDetect-Signature` |

---

## 6. Observability

| Concern | Tool |
|---------|------|
| Health | `/actuator/health` (liveness + readiness) |
| Metrics | `/actuator/prometheus` (Micrometer) |
| Logs | Logback JSON (stdout → container log driver) |
| Tracing | ⏳ OpenTelemetry planned |
| Alerts | External (Prometheus Alertmanager / Grafana) |

The ML engine exposes its own `/metrics` endpoint via
`prometheus-fastapi-instrumentator`.

---

## 7. Deployment Topology

```
                    ┌────────────────┐
                    │   ALB / Nginx  │
                    └────────┬───────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
         api-gw-1       api-gw-2       api-gw-N        ← stateless, scale-out
              │              │              │
              └──────┬───────┴───────┬──────┘
                     ▼               ▼
                PostgreSQL       Redis (cluster/ha)
                     ▲
                     │
         ┌───────────┴──────────┐
         │  ml-engine replicas  │         ← CPU or GPU nodes
         │    (FastAPI)         │
         └──────────────────────┘
```

Both services are containerised; prod deployments use Kubernetes
(Deployment + HPA) or ECS Fargate. The gateway is CPU-bound and scales
on request rate; the ML engine scales on queue depth / latency.

---

## 8. Failure Modes & Resilience

| Failure | Behaviour |
|---------|-----------|
| Redis down | API key lookup falls back to DB; rate limit open-fails |
| ML engine down | Detection endpoints return `503` + webhook not fired |
| PostgreSQL down | `/actuator/health` reports `DOWN`; LB removes from pool |
| Webhook target down | Logged + retried next delivery; auto-disable after 10 fails |
| JWT signing key rotation | New signing key deployed; old tokens expire naturally |

Planned additions: Resilience4j circuit breaker around `MlEngineClient`
and a durable retry queue for webhook deliveries.
