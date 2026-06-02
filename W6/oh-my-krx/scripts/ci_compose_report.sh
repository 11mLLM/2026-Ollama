#!/usr/bin/env bash
set -euo pipefail

escape_annotation() {
  local value="$1"
  value="${value//'%'/'%25'}"
  value="${value//$'\r'/'%0D'}"
  value="${value//$'\n'/'%0A'}"
  printf "%s" "$value"
}

annotate_error() {
  local title="$1"; shift
  local message="$1"; shift
  echo "::error title=$title::$(escape_annotation "$message")"
}

annotate_notice() {
  local title="$1"; shift
  local message="$1"; shift
  echo "::notice title=$title::$(escape_annotation "$message")"
}

echo "== Compose health diagnostics =="

check_url() {
  local name="$1"; shift
  local url="$1"; shift
  local output="/tmp/krbrief_ci_${name}_health.txt"
  local code
  code="$(curl -sS -m 8 -o "$output" -w "%{http_code}" "$url" || true)"
  local body
  body="$(cat "$output" 2>/dev/null || true)"
  if [[ "$code" == "200" ]]; then
    annotate_notice "$name health" "$url -> HTTP $code $body"
    echo "[PASS] $name health $url"
  else
    annotate_error "$name health" "$url -> HTTP ${code:-curl_failed} ${body:-no body}"
    echo "[FAIL] $name health $url HTTP ${code:-curl_failed}"
  fi
}

check_url backend "http://localhost:${BACKEND_PORT:-8080}/actuator/health"
check_url frontend "http://localhost:${FRONTEND_PORT:-5173}/health"
check_url marketdata "http://localhost:${MARKETDATA_PORT:-8000}/health"
check_url ai-service "http://localhost:${AI_SERVICE_PORT:-8100}/health"

compose_ps="$(docker compose ps 2>&1 || true)"
echo "$compose_ps"
annotate_notice "docker compose ps" "$(printf "%s" "$compose_ps" | head -c 3000)"

for service in mysql backend frontend marketdata ai-service; do
  logs="$(docker compose logs --tail=80 "$service" 2>&1 || true)"
  echo "$logs"
  if echo "$logs" | grep -Eiq 'error|exception|failed|unhealthy|denied|refused|timeout'; then
    annotate_error "$service logs" "$(printf "%s" "$logs" | tail -c 3000)"
  else
    annotate_notice "$service logs" "$(printf "%s" "$logs" | tail -c 1800)"
  fi
done
