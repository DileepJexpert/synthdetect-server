# SynthDetect — Roadmap

_Last updated: 2026-04-09_

This roadmap lists proposed new features and hardening work, grouped by
theme. See [STATUS.md](./STATUS.md) for what is already shipped.

---

## Theme 1 — Launch Readiness (near-term)

### 1.1 CI/CD pipeline
- GitHub Actions workflow: build → unit tests → Flyway validate → Docker
  image build → push to GHCR → deploy (optional staging).
- Caching for Gradle + Docker layers.
- Required checks on PRs.

### 1.2 Integration test suite (Testcontainers)
- Auth flow (register → verify → login → refresh → logout).
- Detection flow with a mocked ml-engine container.
- Quota + rate limit enforcement.
- Webhook signature verification round-trip.

### 1.3 Observability baseline
- OpenTelemetry Java agent → OTLP exporter.
- Correlation IDs: `X-Request-Id` ingress, propagated to ml-engine,
  added to every log line.
- Grafana dashboards: RPS, p95 latency, quota usage, webhook success rate.

### 1.4 Circuit breaker on ML calls
- Wrap `MlEngineClient` with Resilience4j circuit breaker + bulkhead.
- Configurable thresholds (50 % failure → OPEN for 30 s).
- Fallback: return `503` with `ml_engine_unavailable` code.

### 1.5 OpenAPI hardening
- Document Bearer + API key security schemes on every operation.
- Group tags by domain.
- Publish spec to `/v3/api-docs` and export to a versioned file.

---

## Theme 2 — Detection Capabilities

### 2.1 Video detection
- Accept `POST /v1/detect/video` with URL or multipart upload.
- ml-engine: sample N frames, run image detector, compute temporal
  consistency (flicker of probability = synthetic signal).
- Start with 1-minute clips, 720p.

### 2.2 Audio detection
- `POST /v1/detect/audio` with wav/mp3/ogg (≤ 50 MB).
- Spectrogram → CNN classifier (e.g. WavLM / AASIST).
- Language-agnostic.

### 2.3 Multi-language text accuracy
- Add Indic-language models (Muril, IndicBERT) to ml-engine.
- Auto-detect language if not supplied.
- Per-language calibration of the decision threshold.

### 2.4 Content-hash dedup
- On every detection, check if `content_hash` already exists for the
  user. If yes, return cached result without hitting ml-engine
  (configurable TTL).

### 2.5 Confidence calibration
- Publish a `confidence` field (HIGH / MEDIUM / LOW) alongside probability.
- Empirically calibrate against a labeled eval set.

---

## Theme 3 — Compliance & Trust

### 3.1 DPIIT reporting export
- `GET /v1/admin/compliance/export?from=&to=&format=csv|json`.
- Daily aggregate: takedowns submitted, met-deadline, breached, response time.

### 3.2 Digital India Act readiness
- Consent management endpoints for biometric content.
- Automatic PII redaction on text detection inputs before logging.

### 3.3 Audit log
- New table `audit_events`: who, what, when, IP, user-agent.
- Write on: login, key revoke, role change, takedown submit, admin actions.
- Append-only, 7-year retention.

### 3.4 Watermark / C2PA verification
- Parse C2PA manifest when present and surface provenance in the
  detection response.

---

## Theme 4 — Developer Experience

### 4.1 Official SDKs
- TypeScript (Node + browser)
- Python
- Java
- Generated from the OpenAPI spec via `openapi-generator`.

### 4.2 Postman collection
- Pre-configured environment vars for local + prod.
- Example flows: register → detect → webhook verify.

### 4.3 Playground UI
- Small static frontend (Vite + React) served at `/playground`.
- Drag-and-drop image upload, paste-text, shows probability + signals.

### 4.4 CLI (`synthdetect-cli`)
- `synthdetect detect image ./photo.jpg`
- `synthdetect keys list`
- `synthdetect webhooks tail` (streams delivery log)

### 4.5 Sandbox keys
- `sd_test_*` keys do not count against quota and use a fast mock model,
  for safe development.

---

## Theme 5 — Scale & Reliability

### 5.1 Durable webhook retry queue
- Redis ZSET keyed by `deliverAt`, worker pulls and retries.
- Exponential backoff: 30 s, 2 m, 10 m, 1 h, 6 h, 24 h.
- Dead-letter to `webhook_deliveries` with `giveup=true`.

### 5.2 Async detection for large batches
- Submit batch → returns `jobId` immediately.
- Worker processes in background, fires webhook per item.
- Poll `GET /v1/detect/jobs/{id}`.

### 5.3 Horizontal ml-engine scaling
- Put ml-engine behind its own load balancer.
- Add a Redis-backed request queue for GPU nodes.
- Auto-scale on queue depth.

### 5.4 Read replicas
- Point `/v1/usage`, `/v1/detect` (GET), admin reports at a read replica.
- Hikari multi-datasource config.

### 5.5 Multi-region deployment
- Active-passive with cross-region PostgreSQL logical replication.
- Redis Enterprise active-active for JWT blacklist.

---

## Theme 6 — Security Hardening

### 6.1 Dependency scanning
- Dependabot + GitHub security advisories enabled.
- Weekly snyk scan job in CI.

### 6.2 SAST
- CodeQL workflow on every PR.
- Gradle `spotbugs` + `checkstyle`.

### 6.3 DAST
- OWASP ZAP baseline scan against a staging deployment on every release.

### 6.4 Secret scanning
- Pre-commit hook (`gitleaks`) + GitHub secret scanning alerts.

### 6.5 Penetration test
- External vendor before v1.0 GA.

### 6.6 Key rotation
- JWT signing key rotation: dual-key window.
- API key leak detection: if a plaintext key appears in a GitHub commit,
  auto-revoke.

---

## Theme 7 — Admin & Ops UI

### 7.1 Admin SPA
- Next.js app at `admin.synthdetect.ai`.
- Pages: users, flagged detections, compliance queue, webhook health,
  system stats.
- Auth via JWT + `hasRole('ADMIN')`.

### 7.2 On-call runbooks
- `docs/runbooks/ml-engine-down.md`
- `docs/runbooks/postgres-failover.md`
- `docs/runbooks/webhook-backlog.md`

### 7.3 Terraform modules
- Reproducible infra: VPC, RDS, ElastiCache, ECS Fargate, ALB, Route53.

---

## Proposed Sequencing

| Sprint | Scope |
|:-----:|-------|
| **S1** | CI/CD, integration tests, OpenTelemetry, circuit breaker, OpenAPI hardening |
| **S2** | Durable webhook retry, content-hash dedup, audit log, correlation IDs |
| **S3** | Video detection, confidence calibration, admin SPA |
| **S4** | SDKs (TS + Python), Postman, playground, CLI |
| **S5** | DPIIT export, Indic-language text accuracy, C2PA parsing |
| **S6** | Audio detection, multi-region, Terraform, pen-test |

_Sequencing is advisory and should be revisited with product/engineering._
