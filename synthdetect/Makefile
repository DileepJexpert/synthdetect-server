# SynthDetect — Local Development Makefile
# Usage: make <target>
# Run from the synthdetect/ directory

.PHONY: help up down up-full logs ps build run test clean reset

COMPOSE_DEV = docker compose -f docker-compose.yml -f docker-compose.dev.yml

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

## ── Infrastructure ────────────────────────────────────────────────────────────

up: ## Start PostgreSQL + Redis only (run app locally)
	$(COMPOSE_DEV) up postgres redis -d
	@echo "✓ Postgres on :5432  |  Redis on :6379"
	@echo "→ Now run: make run"

down: ## Stop infrastructure containers
	$(COMPOSE_DEV) down

up-full: ## Start everything in Docker (infra + app)
	docker compose up --build -d
	@echo "✓ API Gateway on :8080  |  Swagger: http://localhost:8080/swagger-ui.html"

ps: ## Show container status
	$(COMPOSE_DEV) ps

logs: ## Tail infrastructure logs
	$(COMPOSE_DEV) logs -f postgres redis

## ── Application ───────────────────────────────────────────────────────────────

run: ## Run Spring Boot app with dev profile
	cd api-gateway && ./gradlew bootRun --args='--spring.profiles.active=dev'

build: ## Build the JAR (skip tests)
	cd api-gateway && ./gradlew build -x test
	@echo "✓ JAR: api-gateway/build/libs/api-gateway-1.0.0-SNAPSHOT.jar"

test: ## Run all tests
	cd api-gateway && ./gradlew test

## ── Utilities ─────────────────────────────────────────────────────────────────

health: ## Check API health
	@curl -s http://localhost:8080/actuator/health | python3 -m json.tool 2>/dev/null || curl -s http://localhost:8080/actuator/health

reset: ## Stop containers and wipe volumes (fresh DB)
	$(COMPOSE_DEV) down -v
	@echo "✓ All data wiped. Run 'make up' to start fresh."

clean: ## Remove build artifacts
	cd api-gateway && ./gradlew clean
