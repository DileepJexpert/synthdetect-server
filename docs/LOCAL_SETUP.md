# SynthDetect — Local Setup Guide

_Last updated: 2026-04-09_

Everything you need to run the full SynthDetect stack
(**api-gateway** + **ml-engine** + **PostgreSQL** + **Redis**) on your
laptop.

---

## 1. Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Java | **21+** | `sdk install java 21-tem` or https://adoptium.net |
| Docker | 24+ | https://docs.docker.com/get-docker/ |
| Docker Compose | v2+ | Bundled with Docker Desktop |
| Git | any | https://git-scm.com |
| Python (optional, for running ml-engine outside Docker) | 3.11+ | https://python.org |

> Gradle wrapper (`./gradlew`) is committed — no Gradle install required.

**RAM / Disk**

- At least **6 GB free RAM** (ml-engine loads HuggingFace models).
- ~4 GB disk for the `ml_model_cache` Docker volume.

---

## 2. Clone the Repo

```bash
git clone https://github.com/DileepJexpert/synthdetect-server.git
cd synthdetect-server
```

---

## 3. Environment Variables

Create `synthdetect/.env` (Docker Compose reads it automatically):

```env
# Database
DB_PASSWORD=changeme

# JWT — must be ≥ 256 bits (32+ chars)
JWT_SECRET=local-dev-jwt-secret-change-in-production-must-be-256-bits!!

# CORS — comma-separated allowlist
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173

# ML engine
ML_ENGINE_URL=http://ml-engine:8000

# SMTP (optional — leave blank to disable email sends locally)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=
```

---

## 4. Choose a Run Mode

### Mode A — Full Docker stack (easiest)

Boots Postgres + Redis + ml-engine + api-gateway in one command.

```bash
cd synthdetect
docker compose up --build
```

First boot takes ~5 minutes because Docker downloads HuggingFace models
into `ml_model_cache`. Subsequent boots use the cached volume.

Wait for these lines:
```
ml-engine    | Uvicorn running on http://0.0.0.0:8000
api-gateway  | Started SynthDetectApplication
```

Smoke test:
```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8000/health
```

---

### Mode B — Infra in Docker, gateway local (best for Java dev)

Runs Postgres + Redis + ml-engine in Docker, api-gateway on your host for
fast iteration / debugger attach.

```bash
cd synthdetect
docker compose up -d postgres redis ml-engine
```

Then:
```bash
cd api-gateway
./gradlew bootRun --args='--spring.profiles.active=dev'
```

The app starts on `http://localhost:8080` with verbose SQL / security
logs.

---

### Mode C — Everything local (no Docker)

1. Install PostgreSQL 16 and create DB:
   ```bash
   createdb synthdetect
   psql -c "CREATE USER synthdetect WITH PASSWORD 'changeme';"
   psql -c "GRANT ALL ON DATABASE synthdetect TO synthdetect;"
   ```
2. Install Redis 7 and run `redis-server`.
3. Start ml-engine:
   ```bash
   cd synthdetect/ml-engine
   python -m venv .venv && source .venv/bin/activate
   pip install -r requirements.txt
   uvicorn app.main:app --reload --port 8000
   ```
4. Start api-gateway:
   ```bash
   cd synthdetect/api-gateway
   DB_HOST=localhost REDIS_HOST=localhost ML_ENGINE_URL=http://localhost:8000 \
     ./gradlew bootRun --args='--spring.profiles.active=dev'
   ```

---

## 5. Verify Everything Works

### 5.1 Health checks

```bash
curl http://localhost:8080/actuator/health       # Spring Boot
curl http://localhost:8000/health                # FastAPI ml-engine
```

Both should return `{"status":"UP"}` / `{"status":"ok"}`.

### 5.2 Swagger UI

Open in browser:
```
http://localhost:8080/swagger-ui.html
```

### 5.3 End-to-end smoke test

```bash
# 1. register
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"dev@local.test","password":"Password123!","companyName":"Local"}'

# 2. login
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"dev@local.test","password":"Password123!"}' \
  | jq -r .accessToken)

# 3. create an API key
KEY=$(curl -s -X POST http://localhost:8080/v1/keys \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"local","environment":"test","scopes":["detect"]}' \
  | jq -r .key)

# 4. run a text detection
curl -X POST http://localhost:8080/v1/detect/text \
  -H "Authorization: Bearer $KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "text": "The quick brown fox jumps over the lazy dog. This is a simple piece of text used to test the detection pipeline end to end.",
    "language": "en"
  }'
```

You should see a JSON response with a probability and per-signal breakdown.

---

## 6. Database Access

```bash
docker compose exec postgres psql -U synthdetect -d synthdetect
```

Useful queries:
```sql
SELECT id, email, status, role, plan FROM users;
SELECT id, type, status, probability, created_at FROM detection_requests ORDER BY created_at DESC LIMIT 10;
SELECT key, value, ttl FROM redis_keyspace;  -- not real; use redis-cli
```

Redis:
```bash
docker compose exec redis redis-cli
> KEYS rate_limit:*
> KEYS jwt:blacklist:*
```

---

## 7. Running Tests

A minimal test suite ships with the repo (JUnit 5). Tests are not wired
into CI yet.

```bash
cd synthdetect/api-gateway
./gradlew test
```

---

## 8. Common Problems

### "Connection refused" when gateway starts
Postgres / Redis / ml-engine not ready. Wait for healthchecks:
```bash
docker compose ps
```

### ml-engine takes forever on first boot
It is downloading ~2 GB of HuggingFace model weights. Wait for:
```
ml-engine  | INFO   Warmed up model: umm-maybe/AI-image-detector
```
Subsequent boots use the `ml_model_cache` volume and start in seconds.

### "Found non-empty schema(s)" Flyway error
A previous migration failed. Wipe volumes and restart:
```bash
docker compose down -v
docker compose up --build
```

### Port conflict on 5432 / 6379 / 8080 / 8000
Something else is already bound. Either stop the other process or
change the host-side port in `docker-compose.yml`.

### "Invalid JWT secret" on startup
`JWT_SECRET` must be ≥ 32 characters. Update `.env` and restart.

### Java 21 not active
```bash
java -version      # should print 21.x.x
# with sdkman:
sdk use java 21-tem
```

### Out-of-memory on ml-engine
Increase Docker Desktop memory limit to ≥ 6 GB (Settings → Resources).

---

## 9. Useful Commands

```bash
# tail gateway logs only
docker compose logs -f api-gateway

# tail ml-engine logs only
docker compose logs -f ml-engine

# rebuild a single service
docker compose up -d --build api-gateway

# stop everything, keep data
docker compose down

# stop and wipe database + model cache
docker compose down -v

# Flyway validate without restart
cd api-gateway && ./gradlew flywayInfo
```

---

## 10. Ports

| Port | Service |
|------|---------|
| 8080 | api-gateway (Spring Boot) |
| 8000 | ml-engine (FastAPI) |
| 5432 | PostgreSQL |
| 6379 | Redis |

---

## 11. Project Layout (top level)

```
synthdetect-server/
├── README.md                    ← project overview
├── docs/                        ← all documentation (this folder)
│   ├── README.md
│   ├── STATUS.md
│   ├── ARCHITECTURE.md
│   ├── FLOW_SEQUENCES.md
│   ├── LOCAL_SETUP.md           ← (this file)
│   ├── API_REFERENCE.md
│   └── ROADMAP.md
└── synthdetect/
    ├── docker-compose.yml       ← full stack
    ├── docker-compose.dev.yml   ← infra only
    ├── api-gateway/             ← Spring Boot 3.3 / Java 21
    │   ├── build.gradle
    │   ├── Dockerfile
    │   └── src/
    │       ├── main/java/com/synthdetect/...
    │       └── main/resources/
    │           ├── application.yml
    │           └── db/migration/V1..V8
    └── ml-engine/               ← FastAPI / Python 3.11
        ├── Dockerfile
        ├── requirements.txt
        └── app/
            ├── main.py
            ├── core/
            ├── models/
            ├── routers/
            └── services/
```
