# SynthDetect — Project Status

_Last updated: 2026-04-09_

This document tracks what has been built, what is production-ready, and what
remains before a public launch. It is the single source of truth for scope.

---

## Legend

| Symbol | Meaning |
|--------|---------|
| ✅ | Implemented and production-grade |
| 🟡 | Implemented but needs hardening / additional work |
| ⏳ | Planned — not yet started |
| ❌ | Intentionally out of scope |

---

## 1. Platform Services

| # | Component | Status | Notes |
|---|-----------|:-----:|-------|
| 1 | `api-gateway` (Spring Boot 3.3 / Java 21) | ✅ | REST API, auth, rate limiting, orchestration |
| 2 | `ml-engine` (FastAPI / Python 3.11) | ✅ | HuggingFace transformers + heuristic fallback |
| 3 | PostgreSQL 16 + Flyway migrations V1–V8 | ✅ | 8 migrations covering all domains |
| 4 | Redis 7 | ✅ | Rate limits, API key cache, JWT blacklist |
| 5 | Docker Compose stack | ✅ | Full stack boots with `docker compose up` |
| 6 | SMTP email (Spring Mail) | ✅ | Verification, password reset, compliance alerts |

---

## 2. Authentication & Authorization

| Feature | Status | Notes |
|---------|:-----:|-------|
| User registration (BCrypt) | ✅ | `POST /auth/register` |
| Login → JWT access + refresh | ✅ | `POST /auth/login` |
| `jti` claim on all tokens | ✅ | UUID per token for blacklist |
| Refresh token rotation | ✅ | Used token is blacklisted |
| JWT blacklist (Redis) | ✅ | `TokenBlacklistService`, TTL = remaining lifetime |
| Logout endpoint | ✅ | `POST /auth/logout` blacklists both tokens |
| API key auth (`sd_live_` / `sd_test_`) | ✅ | SHA-256 hash, Redis 5-min cache |
| API key revocation | ✅ | Immediate cache eviction |
| Email verification | ✅ | Token in `email_tokens` table |
| Password reset | ✅ | Secure random token + expiry |
| Resend verification | ✅ | `POST /auth/resend-verification` |
| Role-based access (`USER`, `ADMIN`) | ✅ | `user_role` enum in DB |
| `@EnableMethodSecurity` + `@PreAuthorize` | ✅ | Applied to `AdminController` |
| Route-level admin guard | ✅ | `SecurityConfig` uses `hasRole("ADMIN")` |

---

## 3. Detection Engine

| Feature | Status | Notes |
|---------|:-----:|-------|
| `POST /v1/detect/image` (URL input) | ✅ | WebClient → `ml-engine` |
| `POST /v1/detect/image/upload` (multipart) | ✅ | 20 MB limit, MIME validation |
| `POST /v1/detect/text` | ✅ | Language-aware detection |
| Signal breakdown persistence | ✅ | `detection_signals` table |
| Detection history | ✅ | `DetectionRequest` + indexes |
| SHA-256 `content_hash` storage | ✅ | For audit / dedup / compliance |
| HuggingFace image classifier | ✅ | `umm-maybe/AI-image-detector` |
| Image heuristics (FFT, noise, colour) | ✅ | OpenCV fallback |
| HuggingFace text classifier | ✅ | RoBERTa |
| Text heuristics (perplexity, burstiness, TTR) | ✅ | Pure-Python fallback |
| Async webhook callback on completion | ✅ | HMAC-signed |
| Batch detection | 🟡 | DTOs exist; endpoint wired in controller, needs load testing |
| Video detection (frame sampling) | ⏳ | Placeholder enum value |
| Audio detection | ⏳ | Placeholder enum value |
| Model circuit breaker (Resilience4j) | ⏳ | Currently only timeout + retry |

---

## 4. Usage Quotas & Rate Limiting

| Feature | Status | Notes |
|---------|:-----:|-------|
| Monthly quota enforcement | ✅ | `usage_stats` rollup table |
| Quota check gate (detection pre-check) | ✅ | `UsageService.checkAndIncrementQuota` |
| `quota.warning` webhook at 80% | ✅ | Fires once per month |
| `quota.exceeded` webhook at 100% | ✅ | Fires once per month |
| Monthly quota reset job | ✅ | Cron: 1st of month 00:01 UTC |
| Per-user RPM rate limiting | ✅ | `RateLimitFilter` + Redis counter |
| `X-RateLimit-*` response headers | ✅ | Remaining, reset, retry-after |
| Plan-based quota tiers | ✅ | FREE/STARTER/BUSINESS/ENTERPRISE |

---

## 5. Webhooks

| Feature | Status | Notes |
|---------|:-----:|-------|
| Webhook CRUD endpoints | ✅ | `/v1/webhooks` |
| HMAC-SHA256 payload signing | ✅ | `X-SynthDetect-Signature: sha256=<hex>` |
| Delivery persistence | ✅ | `webhook_deliveries` table w/ payload, status, duration |
| Health counters | ✅ | Consecutive failures tracked |
| Auto-disable after 10 failures | ✅ | Sets status = `FAILED` |
| Retry with exponential backoff | 🟡 | Single attempt today; retry queue planned |
| Delivery replay UI | ⏳ | Admin endpoint exists, no UI |

---

## 6. Compliance (India IT Rules 2026)

| Feature | Status | Notes |
|---------|:-----:|-------|
| Takedown request endpoint | ✅ | `POST /v1/compliance/takedown` |
| 3-hour deadline SLA tracking | ✅ | `takedown_deadline` on `compliance_reports` |
| Deadline scheduler (every 15 min) | ✅ | Warns < 30 min, escalates past-deadline |
| Email alerts to account owner | ✅ | `EmailService.sendComplianceAlert` |
| Multi-language content tagging | 🟡 | Enum exists (en, hi, ta, te, bn, mr, kn, gu); detection is English-biased |
| DPIIT reporting export (CSV/JSON) | ⏳ | |
| Digital India Act readiness | ⏳ | |

---

## 7. Admin & Operations

| Feature | Status | Notes |
|---------|:-----:|-------|
| Admin stats endpoint | ✅ | User counts by status, detections/24h |
| User management (suspend, plan change) | ✅ | `PATCH /v1/admin/users/{id}` |
| Role assignment | ✅ | `PATCH /v1/admin/users/{id}/role` |
| Flagged detections view | ✅ | `GET /v1/admin/detections/flagged` |
| Scheduled jobs | ✅ | Quota reset, deadline alerts, token cleanup |
| Actuator + Prometheus metrics | ✅ | `/actuator/prometheus` |
| Structured logging | 🟡 | JSON via Logback, no correlation ID yet |
| Distributed tracing (OTel) | ⏳ | Not wired |
| Error monitoring (Sentry / Rollbar) | ⏳ | |

---

## 8. Security Hardening

| Control | Status | Notes |
|---------|:-----:|-------|
| BCrypt password hashing | ✅ | Strength 10 |
| JWT HS256 w/ 256-bit secret | ✅ | Secret via env var |
| CORS allowlist via env var | ✅ | No more `*` wildcard |
| Security response headers | ✅ | HSTS, X-Frame-Options, CSP, Referrer-Policy |
| SQL injection safe (JPA params) | ✅ | No string concatenation |
| Multipart MIME + size validation | ✅ | `ImageUploadService` |
| Secrets in env vars only | ✅ | No hard-coded credentials |
| Dependency scanning (Dependabot) | ⏳ | |
| SAST (CodeQL, Snyk) | ⏳ | |
| Penetration test | ⏳ | |

---

## 9. Developer Experience

| Item | Status | Notes |
|------|:-----:|-------|
| OpenAPI / Swagger UI | ✅ | `/swagger-ui.html` |
| `OpenApiConfig` | ✅ | Basic metadata |
| OpenAPI security schemes documented | 🟡 | Bearer + API key not yet annotated on ops |
| Postman collection | ⏳ | |
| SDKs (TS, Python, Java) | ⏳ | |
| CI/CD pipeline | ⏳ | No `.github/workflows` yet |
| Contract tests | ⏳ | |

---

## 10. Outstanding Gaps (priority ordered)

### Critical → High
1. **CI/CD pipeline** — GitHub Actions for build, Flyway validation, Docker image push.
2. **Integration test suite** — Testcontainers-based tests for auth, detection, quota.
3. **Distributed tracing** — OpenTelemetry exporter → Tempo / Jaeger.
4. **Correlation IDs** — `X-Request-Id` propagated through logs and ML-engine calls.
5. **Circuit breaker on ML-engine** — Resilience4j wrapper on `MlEngineClient`.

### Medium
6. OpenAPI security scheme annotations on every operation.
7. Webhook retry queue with exponential backoff (Redis ZSET or DB-backed).
8. DPIIT reporting export (CSV + JSON).
9. Batch detection load testing + 429 backpressure.
10. Admin UI (separate SPA or Thymeleaf).

### Low
11. Video detection (frame sampling + temporal consistency).
12. Audio detection (spectrogram-based classifier).
13. SDK generation from OpenAPI spec.
14. Multi-language text detection accuracy improvements.
15. GDPR data-export / right-to-be-forgotten endpoints.

---

## 11. Metrics Snapshot (as of last push)

| Metric | Value |
|--------|------:|
| Java source files | ~90 |
| Python source files | 11 |
| Flyway migrations | 8 |
| REST endpoints | ~45 |
| DB tables | 11 |
| Scheduled jobs | 3 |
| Spring Security filters | 3 (APIKey → JWT → RateLimit) |
