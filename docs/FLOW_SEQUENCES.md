# SynthDetect — Flow Sequences

_Last updated: 2026-04-09_

Sequence diagrams (Mermaid) for the most important user journeys.
Render these in any GitHub viewer or VS Code with the Mermaid plugin.

---

## 1. User Registration + Email Verification

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant GW as api-gateway
    participant DB as PostgreSQL
    participant ET as email_tokens
    participant MAIL as SMTP

    U->>GW: POST /auth/register {email, password, company}
    GW->>GW: BCrypt.hash(password)
    GW->>DB: INSERT users (status=PENDING, email_verified=false)
    DB-->>GW: userId
    GW->>GW: token = SecureRandom + Base64
    GW->>ET: INSERT email_token (EMAIL_VERIFICATION, 24h)
    GW->>MAIL: sendVerification(email, token)
    GW-->>U: 201 Created {userId, status=PENDING}

    Note over U: user clicks link in email
    U->>GW: GET /auth/verify-email?token=...
    GW->>ET: find token
    GW->>DB: UPDATE users SET email_verified=true, status=ACTIVE
    GW->>ET: DELETE token
    GW-->>U: 200 OK "email verified"
```

---

## 2. Login → JWT Access + Refresh

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant GW as api-gateway
    participant DB as PostgreSQL
    participant JWT as JwtService

    U->>GW: POST /auth/login {email, password}
    GW->>DB: SELECT user WHERE email=?
    GW->>GW: BCrypt.matches(password, hash)
    alt credentials ok
        GW->>JWT: sign ACCESS {sub=userId, jti=uuid, exp=24h}
        GW->>JWT: sign REFRESH {sub=userId, jti=uuid, type=refresh, exp=7d}
        GW-->>U: 200 {accessToken, refreshToken, expiresIn}
    else bad credentials
        GW-->>U: 401 Unauthorized
    end
```

---

## 3. Refresh Token Rotation (with blacklist)

```mermaid
sequenceDiagram
    autonumber
    actor U as Client
    participant GW as api-gateway
    participant JWT as JwtService
    participant R as Redis

    U->>GW: POST /auth/refresh {refreshToken}
    GW->>JWT: parse + validate (type == refresh)
    GW->>R: GET jwt:blacklist:{jti}
    alt blacklisted
        GW-->>U: 401 Unauthorized
    else ok
        GW->>R: SET jwt:blacklist:{oldJti} ttl=remaining
        GW->>JWT: sign new ACCESS + REFRESH
        GW-->>U: 200 {accessToken, refreshToken}
    end
```

---

## 4. API Key Creation

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant GW as api-gateway
    participant DB as PostgreSQL

    U->>GW: POST /v1/keys {name, env, scopes}
    Note over GW: Authenticated by JWT
    GW->>GW: plaintext = "sd_live_" + 32-char base62
    GW->>GW: hash = SHA-256(plaintext)
    GW->>DB: INSERT api_keys {userId, hash, prefix, scopes}
    GW-->>U: 201 {id, key=plaintext}  ← shown ONCE
```

---

## 5. Authenticated Detection Request (API Key Path)

```mermaid
sequenceDiagram
    autonumber
    actor Cli as Client
    participant AK as ApiKeyAuthFilter
    participant JF as JwtAuthFilter
    participant RL as RateLimitFilter
    participant DC as DetectionController
    participant DS as DetectionService
    participant US as UsageService
    participant ML as MlEngineClient
    participant PY as ml-engine (FastAPI)
    participant DB as PostgreSQL
    participant R as Redis
    participant WH as WebhookService

    Cli->>AK: POST /v1/detect/image  (Authorization: sd_live_...)
    AK->>R: GET api_key:{hash}
    alt cache hit
        R-->>AK: ApiKey
    else miss
        AK->>DB: SELECT api_keys
        AK->>R: SETEX api_key:{hash} 300
    end
    AK->>AK: SecurityContext.setAuth(userId, ROLE_USER)
    AK->>JF: forward
    JF->>RL: forward
    RL->>R: INCR rate_limit:{userId}:{minute}
    alt under limit
        RL->>DC: forward
    else over limit
        RL-->>Cli: 429 Too Many Requests
    end

    DC->>DS: detectImage(userId, req)
    DS->>US: checkAndIncrementQuota(userId)
    alt quota exceeded
        US-->>DS: throw QuotaExceededException
        DS-->>Cli: 402 Payment Required
    end
    DS->>DB: INSERT detection_requests (PROCESSING, contentHash=sha256(url))
    DS->>ML: POST ml-engine /v1/detect/image
    ML->>PY: HTTP call
    PY->>PY: transformer + heuristics
    PY-->>ML: {probability, signals[]}
    ML-->>DS: MlDetectionResult
    DS->>DB: UPDATE detection_requests SET status=COMPLETED
    DS->>DB: INSERT detection_signals rows
    DS->>WH: deliverAsync "detection.completed"
    DS-->>DC: DetectionResponse
    DC-->>Cli: 200 {id, probability, signals, verdict}

    par async webhook
        WH->>WH: sign HMAC-SHA256(secret, payload)
        WH->>Cli: POST webhook_url + X-SynthDetect-Signature
        WH->>DB: INSERT webhook_deliveries
    end
```

---

## 6. Quota Threshold Event (80% & 100%)

```mermaid
sequenceDiagram
    autonumber
    participant DS as DetectionService
    participant US as UsageService
    participant DB as PostgreSQL
    participant WS as WebhookService
    participant Cli as Customer

    DS->>US: checkAndIncrementQuota(userId, "image")
    US->>DB: SELECT usage_stats WHERE user=? AND month=?
    US->>DB: UPDATE usage_stats SET requests=requests+1
    alt requests == 80% of plan AND warning_fired=false
        US->>DB: UPDATE usage_stats SET warning_fired=true
        US->>WS: deliverQuotaEvent("quota.warning", userId, 80%)
        WS->>Cli: POST webhook + HMAC
    else requests == 100% AND exceeded_fired=false
        US->>DB: UPDATE usage_stats SET exceeded_fired=true
        US->>WS: deliverQuotaEvent("quota.exceeded", userId, 100%)
        WS->>Cli: POST webhook + HMAC
        US-->>DS: throw QuotaExceededException
    else under
        US-->>DS: ok
    end
```

---

## 7. Webhook Delivery (HMAC-signed)

```mermaid
sequenceDiagram
    autonumber
    participant EV as Event source
    participant WS as WebhookService
    participant DB as PostgreSQL
    participant TG as Customer target

    EV->>WS: deliver(eventType, payload, userId)
    WS->>DB: SELECT webhooks WHERE user=? AND status=ACTIVE
    loop each matching webhook
        WS->>WS: body = JSON(payload)
        WS->>WS: sig = HMAC-SHA256(webhook.secret, body)
        WS->>TG: POST url<br/>X-SynthDetect-Signature: sha256=<hex><br/>X-SynthDetect-Event: detection.completed
        alt 2xx
            WS->>DB: INSERT webhook_deliveries (success=true)
            WS->>DB: UPDATE webhooks SET consecutive_failures=0
        else 4xx / 5xx / timeout
            WS->>DB: INSERT webhook_deliveries (success=false, response_status)
            WS->>DB: UPDATE webhooks SET consecutive_failures=consecutive_failures+1
            alt failures >= 10
                WS->>DB: UPDATE webhooks SET status=FAILED
            end
        end
    end
```

---

## 8. Compliance Takedown (India IT Rules 2026 · 3-hour SLA)

```mermaid
sequenceDiagram
    autonumber
    actor R as Reporter
    participant GW as api-gateway
    participant CS as ComplianceService
    participant DB as PostgreSQL
    participant J as ComplianceDeadlineJob
    participant M as EmailService
    actor O as Account owner

    R->>GW: POST /v1/compliance/takedown {url, reason}
    GW->>CS: submit(report)
    CS->>DB: INSERT compliance_reports (deadline = now + 3h, status=OPEN)
    CS->>M: sendComplianceAlert(owner, report)
    M->>O: email "Takedown required — 3 hours"
    CS-->>R: 202 Accepted {reportId, deadline}

    loop every 15 minutes (cron)
        J->>DB: SELECT open reports
        alt deadline < 30 min
            J->>M: sendComplianceAlert(owner, URGENT)
        else now > deadline
            J->>DB: UPDATE status=ESCALATED
            J->>M: sendComplianceAlert(owner + admin, BREACH)
        end
    end
```

---

## 9. Admin RBAC Flow (@PreAuthorize)

```mermaid
sequenceDiagram
    autonumber
    actor A as Actor
    participant JF as JwtAuthFilter
    participant DB as PostgreSQL
    participant MS as MethodSecurity
    participant AC as AdminController

    A->>JF: GET /v1/admin/stats (Bearer jwt)
    JF->>JF: parse + validate + check blacklist
    JF->>DB: SELECT user WHERE id=? (for role)
    alt role = ADMIN
        JF->>JF: set authorities [ROLE_USER, ROLE_ADMIN]
    else
        JF->>JF: set authorities [ROLE_USER]
    end
    JF->>MS: forward
    MS->>MS: @PreAuthorize("hasRole('ADMIN')")
    alt not admin
        MS-->>A: 403 Forbidden
    else admin
        MS->>AC: stats()
        AC-->>A: 200 {users, detections24h, ...}
    end
```

---

## 10. Logout (Blacklist Both Tokens)

```mermaid
sequenceDiagram
    autonumber
    actor U as Client
    participant GW as api-gateway
    participant JWT as JwtService
    participant R as Redis

    U->>GW: POST /auth/logout {refreshToken}<br/>Authorization: Bearer <access>
    GW->>JWT: extract jti + exp (access)
    GW->>R: SET jwt:blacklist:{accessJti} ttl=expRemaining
    GW->>JWT: extract jti + exp (refresh)
    GW->>R: SET jwt:blacklist:{refreshJti} ttl=expRemaining
    GW-->>U: 204 No Content
```

---

## 11. Scheduled Jobs Overview

```mermaid
flowchart LR
    subgraph Scheduler[scheduler package]
      A[ComplianceDeadlineJob<br/>every 15 min]
      B[QuotaResetJob<br/>cron: 1st of month 00:01]
      C[ExpiredTokenCleanupJob<br/>daily 03:00]
    end

    A -->|SELECT open reports| PG[(PostgreSQL)]
    A -->|sendComplianceAlert| MAIL[SMTP]
    B -->|INSERT usage_stats for new month| PG
    C -->|DELETE expired email_tokens| PG
```

---

## 12. Image Upload Path (Multipart)

```mermaid
sequenceDiagram
    autonumber
    actor U as Client
    participant DC as DetectionController
    participant IU as ImageUploadService
    participant DS as DetectionService
    participant ML as MlEngineClient

    U->>DC: POST /v1/detect/image/upload (multipart/form-data)
    DC->>IU: validateAndStore(file)
    IU->>IU: check MIME (jpeg, png, webp, gif, bmp)
    IU->>IU: check size <= 20 MB
    IU->>IU: write to /tmp/synthdetect-uploads/{uuid}.{ext}
    IU-->>DC: url = "file://..."
    DC->>DS: detectImage(url)
    DS->>ML: POST /v1/detect/image
    ML-->>DS: result
    DS-->>DC: DetectionResponse
    DC-->>U: 200 result
    Note over IU: file deleted after processing (finally block)
```
