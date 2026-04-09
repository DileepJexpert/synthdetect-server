# SynthDetect — API Reference

_Last updated: 2026-04-09_

Base URL: `http://localhost:8080` (local) — replace with your production host.

All responses are JSON. Errors follow the shape:

```json
{
  "timestamp": "2026-04-09T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "email must be a valid address",
  "path": "/auth/register"
}
```

Interactive docs live at `/swagger-ui.html` when the server is running.

---

## 1. Authentication

All `/v1/**` endpoints require **one** of:

| Scheme | Header | Used by |
|--------|--------|---------|
| API key | `Authorization: Bearer sd_live_...` | Server-to-server |
| JWT    | `Authorization: Bearer <accessToken>` | First-party frontend |

Public endpoints (no auth): `/auth/register`, `/auth/login`, `/auth/refresh`,
`/auth/verify-email`, `/auth/forgot-password`, `/auth/reset-password`,
`/auth/resend-verification`, `/actuator/health`, `/swagger-ui/**`.

---

## 2. Auth Endpoints

### `POST /auth/register`
Create a new account (status `PENDING` until email verified).

```json
{ "email": "alice@acme.com", "password": "Password123!", "companyName": "Acme" }
```
**201** `{ "userId": "...", "status": "PENDING" }`

### `POST /auth/login`
```json
{ "email": "alice@acme.com", "password": "Password123!" }
```
**200** `{ "accessToken": "...", "refreshToken": "...", "expiresIn": 86400 }`

### `POST /auth/refresh`
```json
{ "refreshToken": "..." }
```
Rotates the refresh token — old one is blacklisted.

### `POST /auth/logout`   🔒 JWT
```json
{ "refreshToken": "..." }
```
Blacklists both access and refresh tokens. **204**.

### `GET /auth/verify-email?token=...`
Marks the user's email as verified and activates the account.

### `POST /auth/resend-verification`
```json
{ "email": "alice@acme.com" }
```

### `POST /auth/forgot-password`
```json
{ "email": "alice@acme.com" }
```
Always returns **202** to prevent user enumeration.

### `POST /auth/reset-password`
```json
{ "token": "...", "newPassword": "NewPassword123!" }
```

---

## 3. User Profile

### `GET /v1/user/profile`   🔒
**200**
```json
{
  "id": "uuid",
  "email": "alice@acme.com",
  "companyName": "Acme",
  "plan": "STARTER",
  "status": "ACTIVE",
  "emailVerified": true
}
```

---

## 4. API Keys

### `POST /v1/keys`   🔒 JWT
```json
{ "name": "Prod Server", "environment": "live", "scopes": ["detect","webhook"] }
```
**201** `{ "id": "uuid", "key": "sd_live_..." }` ← shown **once**.

### `GET /v1/keys`   🔒
Lists the caller's keys (no plaintext).

### `DELETE /v1/keys/{id}`   🔒
Revokes an API key — cache is evicted immediately.

---

## 5. Detection

### `POST /v1/detect/image`   🔒
Detect from a publicly reachable URL.

```json
{
  "imageUrl": "https://example.com/photo.jpg",
  "webhookUrl": "https://app.example.com/hooks/sd",
  "jurisdiction": "india_it_rules_2026",
  "flagIfSynthetic": true
}
```

**200**
```json
{
  "id": "uuid",
  "type": "IMAGE",
  "status": "COMPLETED",
  "probability": 0.87,
  "verdict": "LIKELY_SYNTHETIC",
  "signals": [
    { "name": "transformer_classifier", "score": 0.91, "weight": 0.6 },
    { "name": "fft_high_freq",          "score": 0.78, "weight": 0.2 },
    { "name": "noise_residual",         "score": 0.82, "weight": 0.2 }
  ],
  "contentHash": "3a5f...",
  "createdAt": "2026-04-09T10:00:00Z"
}
```

### `POST /v1/detect/image/upload`   🔒
Multipart upload (field name: `file`, ≤ 20 MB, jpeg/png/webp/gif/bmp).
```bash
curl -X POST http://localhost:8080/v1/detect/image/upload \
  -H "Authorization: Bearer sd_live_..." \
  -F "file=@photo.jpg"
```

### `POST /v1/detect/text`   🔒
```json
{
  "text": "Passage to classify (min 50 chars).",
  "language": "en",
  "webhookUrl": "https://...",
  "flagIfSynthetic": false
}
```

### `POST /v1/detect/batch`   🔒
Up to 50 items (image URLs or text). Returns a list of per-item results.

### `GET /v1/detect/{id}`   🔒
Fetch a previously run detection by id.

### `GET /v1/detect?type=&from=&to=&limit=`   🔒
Paginated history, filtered by type / date range.

---

## 6. Usage

### `GET /v1/usage`   🔒
Current month stats for the caller.
```json
{
  "month": "2026-04",
  "plan": "STARTER",
  "limit": 10000,
  "used": 2341,
  "remaining": 7659,
  "resetsAt": "2026-05-01T00:00:00Z"
}
```

### `GET /v1/usage/history?months=6`   🔒
Historical monthly rollups.

---

## 7. Webhooks

### `POST /v1/webhooks`   🔒
```json
{
  "url": "https://example.com/hooks/sd",
  "events": ["detection.completed", "quota.warning", "quota.exceeded"]
}
```
**201** returns `{ "id": "...", "secret": "whsec_..." }` — secret shown once.

### `GET /v1/webhooks`   🔒
List all webhooks for the user with health counters.

### `DELETE /v1/webhooks/{id}`   🔒

### `GET /v1/webhooks/{id}/deliveries?limit=50`   🔒
Recent delivery attempts with response codes and durations.

**Signature verification** (server-side, to validate an inbound webhook):

```python
import hmac, hashlib
expected = "sha256=" + hmac.new(secret.encode(), body, hashlib.sha256).hexdigest()
assert hmac.compare_digest(expected, request.headers["X-SynthDetect-Signature"])
```

---

## 8. Compliance

### `POST /v1/compliance/takedown`   🔒
```json
{
  "contentUrl": "https://example.com/bad.jpg",
  "reason": "deepfake of real person",
  "reporterEmail": "reporter@example.com"
}
```
**202** `{ "reportId": "...", "deadline": "2026-04-09T13:00:00Z" }` (3-hour SLA).

### `GET /v1/compliance/reports?status=OPEN`   🔒
List takedown reports for the caller.

---

## 9. Admin   (🔒 JWT + `role=ADMIN`)

All routes under `/v1/admin/**` are gated by `@PreAuthorize("hasRole('ADMIN')")`.

| Method & Path | Purpose |
|--------------|---------|
| `GET /v1/admin/stats` | User counts by status, detections in last 24h |
| `GET /v1/admin/users?status=` | List users |
| `PATCH /v1/admin/users/{id}` | Update plan / status |
| `PATCH /v1/admin/users/{id}/role` | Assign `USER` / `ADMIN` |
| `GET /v1/admin/detections/flagged` | Detections with probability > threshold |

---

## 10. Operational

| Path | Description |
|------|-------------|
| `GET /actuator/health` | Liveness + DB + Redis health |
| `GET /actuator/info` | Build info |
| `GET /actuator/metrics` | Micrometer metrics |
| `GET /actuator/prometheus` | Prometheus scrape endpoint |
| `GET /swagger-ui.html` | Swagger UI |
| `GET /v3/api-docs` | OpenAPI JSON |

---

## 11. Rate Limits & Quotas

| Plan | Requests/month | RPM |
|------|---------------:|----:|
| FREE | 500 | 30 |
| STARTER | 10,000 | 120 |
| BUSINESS | 100,000 | 600 |
| ENTERPRISE | unlimited | 3,000 |

Rate-limited responses include:
```
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 120
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1712659200
Retry-After: 27
```

Quota-exceeded responses:
```
HTTP/1.1 402 Payment Required
{ "code": "QUOTA_EXCEEDED", "message": "Monthly quota exhausted" }
```

---

## 12. Webhook Event Schemas

### `detection.completed`
```json
{
  "event": "detection.completed",
  "deliveredAt": "2026-04-09T10:00:00Z",
  "data": {
    "id": "uuid",
    "type": "IMAGE",
    "probability": 0.87,
    "verdict": "LIKELY_SYNTHETIC",
    "contentHash": "3a5f..."
  }
}
```

### `quota.warning` / `quota.exceeded`
```json
{
  "event": "quota.warning",
  "deliveredAt": "2026-04-09T10:00:00Z",
  "data": {
    "month": "2026-04",
    "plan": "STARTER",
    "limit": 10000,
    "used": 8000,
    "percent": 80
  }
}
```

All events carry `X-SynthDetect-Event` and `X-SynthDetect-Signature` headers.

---

## 13. Error Codes

| Code | HTTP | Meaning |
|------|-----:|---------|
| `VALIDATION_FAILED` | 400 | DTO constraint violation |
| `UNAUTHORIZED` | 401 | Missing / invalid / blacklisted token |
| `FORBIDDEN` | 403 | Authenticated but not allowed (e.g. non-admin on admin route) |
| `NOT_FOUND` | 404 | Resource does not exist or not owned by caller |
| `QUOTA_EXCEEDED` | 402 | Monthly plan limit reached |
| `RATE_LIMITED` | 429 | Per-minute RPM exceeded |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | MIME not in allowlist |
| `PAYLOAD_TOO_LARGE` | 413 | Upload > 20 MB |
| `ML_ENGINE_UNAVAILABLE` | 503 | Python service down / timeout |
| `INTERNAL_ERROR` | 500 | Unexpected server error |
