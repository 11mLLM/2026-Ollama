.PHONY: up down rebuild logs ps backend-logs mysql-logs frontend-logs backend-test frontend-install frontend-quality quality backend-e2e health generate-today check-month latest qa ops-check llm-benchmark deploy-smoke ci-test-report ollama-up ollama-pull ollama-status

DOCKER_SOCK ?= /var/run/docker.sock

up:
	BUILDX_GIT_INFO=false docker compose up -d --build

down:
	docker compose down

rebuild:
	BUILDX_GIT_INFO=false docker compose build --no-cache

ps:
	docker compose ps

logs:
	docker compose logs -f --tail=200

backend-logs:
	docker compose logs -f --tail=200 backend

frontend-logs:
	docker compose logs -f --tail=200 frontend

mysql-logs:
	docker compose logs -f --tail=200 mysql

health:
	@for i in $$(seq 1 $${HEALTH_RETRIES:-120}); do \
		if curl -fsS "http://localhost:$${BACKEND_PORT:-8080}/actuator/health" >/dev/null 2>&1 && \
		   curl -fsS "http://localhost:$${FRONTEND_PORT:-5173}/health" >/dev/null 2>&1 && \
		   curl -fsS "http://localhost:$${MARKETDATA_PORT:-8000}/health" >/dev/null 2>&1 && \
		   curl -fsS "http://localhost:$${AI_SERVICE_PORT:-8100}/health" >/dev/null 2>&1; then \
			echo "Backend, frontend, marketdata, and ai-service health checks passed."; \
			exit 0; \
		fi; \
		sleep 2; \
	done; \
	echo "Health checks failed."; \
	docker compose ps; \
	exit 1

# Generates today's summary (Asia/Seoul date inside backend)
generate-today:
	curl -sS -X POST "http://localhost:$${BACKEND_PORT:-8080}/api/summaries/generate/today"

# Quick monthly check (example: make check-month MONTH=2026-02)
check-month:
	@if [ -z "$(MONTH)" ]; then echo "Usage: make check-month MONTH=YYYY-MM"; exit 1; fi
	@FROM="$(MONTH)-01"; \
	TO="$$(python3 -c 'import sys,datetime as d,calendar;y,m=map(int,sys.argv[1].split("-"));print(d.date(y,m,calendar.monthrange(y,m)[1]).isoformat())' "$(MONTH)")"; \
	curl -sS "http://localhost:$${BACKEND_PORT:-8080}/api/summaries?from=$${FROM}&to=$${TO}"

latest:
	curl -sS "http://localhost:$${BACKEND_PORT:-8080}/api/summaries/latest"

ollama-up:
	docker compose --profile ollama up -d ollama

ollama-pull:
	docker compose --profile ollama up -d ollama
	docker compose exec ollama ollama pull $${OLLAMA_MODEL:-llama3.1:latest}

ollama-status:
	curl -sS "http://localhost:$${BACKEND_PORT:-8080}/api/ai/status"

qa:
	$(MAKE) ops-check
	./scripts/test_all_apis.sh
	./scripts/qa_public_key.sh
	./scripts/verify_investment_language.sh

ops-check:
	docker compose config -q
	./scripts/verify_no_secrets.sh

llm-benchmark:
	./scripts/benchmark_llm_quality.py

deploy-smoke:
	./scripts/deployment_smoke.sh

ci-test-report:
	./scripts/ci_test_report.sh

frontend-install:
	@cd frontend && \
	for i in 1 2 3 4 5; do \
		chmod -R u+w node_modules 2>/dev/null || true; \
		rm -rf node_modules && break; \
		echo "node_modules deletion retry $$i/5"; \
		sleep $$i; \
	done; \
	if [ -d node_modules ]; then \
		echo "node_modules deletion failed after retries."; \
		exit 1; \
	fi; \
	npm ci --include=dev

frontend-quality:
	$(MAKE) frontend-install
	cd frontend && npm run build && npm audit && npm run test:e2e -- --reporter=line

quality:
	$(MAKE) ops-check
	cd backend && ./gradlew test
	$(MAKE) frontend-install
	cd frontend && npm run build && npm audit
	$(MAKE) up
	$(MAKE) health
	./scripts/verify_investment_language.sh
	./scripts/test_all_apis.sh
	$(MAKE) frontend-install
	cd frontend && npm run test:e2e -- --reporter=line

# Runs API tests against a disposable MySQL Testcontainer (inside the Gradle container).
backend-test:
	docker run --rm -t \
		-v "$$PWD/backend:/workspace" \
		-v "$(DOCKER_SOCK):/var/run/docker.sock" \
		-w /workspace \
		-e DOCKER_HOST=unix:///var/run/docker.sock \
		gradle:8.7-jdk17 \
		gradle test
