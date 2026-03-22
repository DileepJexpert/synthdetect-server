# SynthDetect API Gateway — Local Setup Guide

This guide covers everything needed to run the API Gateway locally using Docker Compose
for infrastructure (PostgreSQL + Redis), with the Spring Boot app running on your machine
(or fully in Docker if preferred).

---

## Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Java | 21+ | https://adoptium.net or `sdk install java 21-tem` |
| Docker | 24+ | https://docs.docker.com/get-docker/ |
| Docker Compose | v2+ | Included with Docker Desktop |
| Git | any | https://git-scm.com |

> **Gradle wrapper is included** — no need to install Gradle separately.

---

## 1. Clone the Repository

```bash
git clone https://github.com/DileepJexpert/synthdetect-server.git
cd synthdetect-server/synthdetect
```

---

## 2. Configure Environment Variables

Copy the example env file:

```bash
cp .env.example .env
```

Edit `.env` — the defaults work for local development:

```env
DB_PASSWORD=changeme
JWT_SECRET=synthdetect-default-jwt-secret-change-in-production-must-be-256-bits
ML_ENGINE_URL=http://localhost:8000
```

> **Note:** The ML Engine (Python deepfake detector) is a separate service not in this repo.
> The API gateway will start fine without it — only detection calls will fail.

---

## 3. Start Infrastructure (PostgreSQL + Redis)

This uses the dev override to start **only** the database and cache — not the app:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up postgres redis -d
```

Verify both are healthy:

```bash
docker compose ps
```

Expected output:
```
NAME                STATUS
synthdetect-postgres-1   Up (healthy)
synthdetect-redis-1      Up (healthy)
```

---

## 4. Run Database Migrations (Flyway — auto on startup)

Flyway runs automatically when the Spring Boot app starts. No manual steps needed.

Migrations are in:
```
api-gateway/src/main/resources/db/migration/
  V1__create_users_table.sql    ← users table + indexes
  V2__create_api_keys_table.sql ← api_keys table + indexes
```

---

## 5. Run the API Gateway

### Option A — Gradle (recommended for development)

```bash
cd api-gateway
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### Option B — Build JAR and run

```bash
cd api-gateway
./gradlew build -x test
java -jar build/libs/api-gateway-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
```

### Option C — Full Docker (app + infra together)

```bash
# From the synthdetect/ directory
docker compose up --build
```

---

## 6. Verify the Server is Running

```bash
curl http://localhost:8080/actuator/health
```

Expected:
```json
{"status":"UP"}
```

Swagger UI (interactive API docs):
```
http://localhost:8080/swagger-ui.html
```

---

## 7. Quick Smoke Test

### Register a user

```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Password123!",
    "companyName": "Test Corp"
  }'
```

### Login

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Password123!"
  }'
```

Save the `accessToken` from the response.

### Create an API Key

```bash
curl -X POST http://localhost:8080/v1/keys \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Test Key",
    "environment": "test",
    "scopes": ["detect"]
  }'
```

> The full key (e.g. `sd_test_...`) is shown **once** — store it securely.

### List API Keys

```bash
curl http://localhost:8080/v1/keys \
  -H "Authorization: Bearer <accessToken>"
```

### Call an Endpoint Using API Key

```bash
curl http://localhost:8080/v1/user/profile \
  -H "Authorization: Bearer sd_test_<your-key>"
```

---

## 8. Run Tests

```bash
cd api-gateway

# Unit tests only (no Docker needed)
./gradlew test

# All tests including Testcontainers integration tests (requires Docker)
./gradlew test --info
```

---

## 9. Stop Everything

```bash
# Stop infrastructure
docker compose -f docker-compose.yml -f docker-compose.dev.yml down

# Stop and delete volumes (wipes database)
docker compose -f docker-compose.yml -f docker-compose.dev.yml down -v
```

---

## Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `synthdetect` | Database name |
| `DB_USER` | `synthdetect` | Database user |
| `DB_PASSWORD` | `changeme` | Database password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `JWT_SECRET` | *(see .env.example)* | HMAC-SHA JWT signing key (min 256-bit) |
| `ML_ENGINE_URL` | `http://localhost:8000` | Python ML engine base URL |
| `SPRING_PROFILES_ACTIVE` | — | Set to `dev` for verbose SQL/security logging |

---

## Ports Used

| Port | Service |
|------|---------|
| 8080 | API Gateway (Spring Boot) |
| 5432 | PostgreSQL |
| 6379 | Redis |
| 8000 | ML Engine (external, not in this repo) |

---

## Troubleshooting

### "Connection refused" on startup
Make sure Postgres and Redis containers are healthy before starting the app:
```bash
docker compose ps
```

### Flyway migration error
If you see `Found non-empty schema(s)` error after a failed migration, reset the DB:
```bash
docker compose down -v && docker compose up postgres redis -d
```

### Port already in use
Find and stop the conflicting process:
```bash
# Linux/macOS
lsof -i :5432
lsof -i :8080

# Windows
netstat -ano | findstr :5432
```

### Java version mismatch
Verify Java 21 is active:
```bash
java -version
```
If using `sdkman`: `sdk use java 21-tem`

---

## Project Structure

```
synthdetect/
├── .env.example                  # Environment variable template
├── docker-compose.yml            # Full stack (postgres + redis + app)
├── docker-compose.dev.yml        # Dev override (infra only, app runs locally)
└── api-gateway/
    ├── build.gradle              # Dependencies (Spring Boot 3.3, JWT, Redis, Flyway)
    ├── Dockerfile
    └── src/
        ├── main/java/com/synthdetect/
        │   ├── SynthDetectApplication.java
        │   ├── auth/             # API key models, filters, services, controller
        │   ├── user/             # User model, DTOs, service, controller
        │   ├── config/           # Security, JWT, Redis, Rate limit, CORS config
        │   └── common/           # Exceptions, ApiResponse wrapper, utilities
        └── main/resources/
            ├── application.yml           # Base config
            ├── application-dev.yml       # Dev overrides (verbose logging)
            ├── application-prod.yml      # Prod overrides
            └── db/migration/             # Flyway SQL migrations
```
