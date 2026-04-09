# SynthDetect

> Synthetic-media detection API for images and text — built for **India IT
> Rules 2026** compliance with 3-hour takedown SLAs.

SynthDetect is a production-grade platform that lets developers programmatically
classify whether an image or text was produced by a human or a generative model.
It bundles a Spring Boot API gateway, a Python ML engine, PostgreSQL, and Redis
into a single stack you can run on your laptop or deploy to Kubernetes.

---

## Table of Contents

- [Features](#features)
- [Architecture at a Glance](#architecture-at-a-glance)
- [Quick Start](#quick-start)
- [Repository Layout](#repository-layout)
- [Documentation](#documentation)
- [Tech Stack](#tech-stack)
- [Status](#status)
- [Branch Strategy](#branch-strategy)
- [License](#license)

---

## Features

- **Image & text detection** — HuggingFace transformers + heuristic fallback
- **Dual auth** — API keys (`sd_live_*`) for server-to-server, JWT for first-party
- **Rate limiting** — per-user RPM via Redis, plan-aware
- **Monthly quotas** — with `quota.warning` (80 %) and `quota.exceeded` (100 %) webhooks
- **HMAC-SHA256 signed webhooks** — `X-SynthDetect-Signature` on every delivery
- **India IT Rules 2026 compliance** — 3-hour takedown SLA with scheduler alerts
- **Email verification + password reset** — full lifecycle
- **JWT blacklist** — logout, refresh rotation, Redis-backed revocation
- **Admin RBAC** — `hasRole('ADMIN')` at route and method level
- **Scheduled jobs** — quota reset, compliance deadlines, token cleanup
- **Flyway migrations** — 8 migrations, audit-ready schema
- **Swagger UI + Actuator + Prometheus metrics**

See [docs/STATUS.md](./docs/STATUS.md) for the full feature matrix.

---

## Architecture at a Glance

```
┌─────────────┐      ┌─────────────────────┐      ┌──────────────┐
│  Client     │──────│     api-gateway     │──────│  ml-engine   │
│  (web /     │ HTTP │  (Spring Boot 3.3)  │ HTTP │  (FastAPI)   │
│   mobile)   │      │       Java 21       │      │  Python 3.11 │
└─────────────┘      └──────┬──────────────┘      └──────────────┘
                            │
                     ┌──────┴──────┐
                     ▼             ▼
                ┌──────────┐  ┌─────────┐
                │PostgreSQL│  │  Redis  │
                │    16    │  │    7    │
                └──────────┘  └─────────┘
```

Full details in [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md) and the
sequence diagrams in [docs/FLOW_SEQUENCES.md](./docs/FLOW_SEQUENCES.md).

---

## Quick Start

```bash
# 1. Clone
git clone https://github.com/DileepJexpert/synthdetect-server.git
cd synthdetect-server/synthdetect

# 2. Create .env
cat > .env <<'EOF'
DB_PASSWORD=changeme
JWT_SECRET=local-dev-jwt-secret-change-in-production-must-be-256-bits!!
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173
EOF

# 3. Boot the full stack
docker compose up --build
```

Health checks:
```bash
curl http://localhost:8080/actuator/health   # api-gateway
curl http://localhost:8000/health            # ml-engine
```

Swagger UI: <http://localhost:8080/swagger-ui.html>

End-to-end smoke test and alternate run modes (infra-only, fully local)
in [docs/LOCAL_SETUP.md](./docs/LOCAL_SETUP.md).

---

## Repository Layout

```
synthdetect-server/
├── README.md                    ← you are here
├── docs/                        ← all documentation
│   ├── README.md                ← docs index
│   ├── STATUS.md                ← feature matrix / what's done
│   ├── ARCHITECTURE.md          ← system design
│   ├── FLOW_SEQUENCES.md        ← mermaid sequence diagrams
│   ├── LOCAL_SETUP.md           ← running locally
│   ├── API_REFERENCE.md         ← REST API reference
│   └── ROADMAP.md               ← proposed future work
└── synthdetect/
    ├── docker-compose.yml       ← full stack
    ├── docker-compose.dev.yml   ← infra-only override
    ├── api-gateway/             ← Spring Boot 3.3 / Java 21
    │   ├── build.gradle
    │   ├── Dockerfile
    │   └── src/main/java/com/synthdetect/
    │       ├── auth/            ← API key + JWT filters, services
    │       ├── user/            ← accounts, login, email verify
    │       ├── detection/       ← core detection orchestration
    │       ├── usage/           ← quota enforcement
    │       ├── webhook/         ← outbound event delivery
    │       ├── compliance/      ← India IT Rules 2026
    │       ├── admin/           ← admin endpoints (RBAC)
    │       ├── scheduler/       ← cron jobs
    │       ├── config/          ← security, JWT, Redis, CORS
    │       └── common/          ← exceptions, utilities
    └── ml-engine/               ← FastAPI / Python 3.11
        ├── Dockerfile
        ├── requirements.txt
        └── app/
            ├── main.py
            ├── routers/         ← /v1/detect/{image,text}, /health
            └── services/        ← image_detector.py, text_detector.py
```

---

## Documentation

| Doc | Purpose |
|-----|---------|
| [docs/STATUS.md](./docs/STATUS.md) | What is built and what is pending |
| [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md) | Services, data model, security, deployment |
| [docs/FLOW_SEQUENCES.md](./docs/FLOW_SEQUENCES.md) | Mermaid sequence diagrams |
| [docs/LOCAL_SETUP.md](./docs/LOCAL_SETUP.md) | Running on your laptop |
| [docs/API_REFERENCE.md](./docs/API_REFERENCE.md) | All REST endpoints |
| [docs/ROADMAP.md](./docs/ROADMAP.md) | Proposed future features |

---

## Tech Stack

**api-gateway**
- Java 21, Spring Boot 3.3.5, Spring Security 6
- JJWT 0.12.6, BCrypt, Spring Data JPA, Hibernate
- Flyway, PostgreSQL driver, Lettuce (Redis), Spring WebFlux (ml client)
- Spring Mail, Micrometer + Prometheus, springdoc-openapi
- Gradle 8 (wrapper)

**ml-engine**
- Python 3.11, FastAPI, Uvicorn
- torch, transformers (HuggingFace), Pillow, OpenCV
- structlog, prometheus-fastapi-instrumentator

**Infrastructure**
- PostgreSQL 16, Redis 7, Docker Compose

---

## Status

Currently a feature-complete **release-candidate**. All core flows (auth,
detection, quotas, webhooks, compliance, admin) are wired and hardened.
Remaining work before a public launch:

1. CI/CD pipeline (GitHub Actions)
2. Integration test suite (Testcontainers)
3. Distributed tracing (OpenTelemetry)
4. Circuit breaker on ml-engine calls
5. Durable webhook retry queue

Detailed matrix: [docs/STATUS.md](./docs/STATUS.md).
Future roadmap: [docs/ROADMAP.md](./docs/ROADMAP.md).

---

## Branch Strategy

Development happens on feature branches prefixed with `claude/`. The
current active branch is **`claude/review-synthdetect-server-9RtOK`**.

---

## License

Proprietary — all rights reserved. Contact the repository owner for
licensing questions.
