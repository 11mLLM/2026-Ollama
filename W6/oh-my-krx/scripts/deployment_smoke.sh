#!/usr/bin/env bash
set -euo pipefail

BACKEND_URL="${DEPLOY_BACKEND_URL:-${DEPLOY_API_BASE_URL:-}}"
FRONTEND_URL="${DEPLOY_FRONTEND_URL:-${DEPLOY_BASE_URL:-}}"
PUBLIC_KEY_VALUE="${DEPLOY_PUBLIC_KEY:-${PUBLIC_KEY:-}}"

pass() { echo "[PASS] $*"; }
fail() { echo "[FAIL] $*"; exit 1; }

trim_url() {
  local value="$1"
  value="${value%/}"
  printf "%s" "$value"
}

contains_field() {
  local file="$1"; shift
  local field="$1"; shift
  grep -q "\"$field\"" "$file"
}

if [[ -z "$BACKEND_URL" && -z "$FRONTEND_URL" ]]; then
  cat <<'USAGE'
[FAIL] Deployment smoke requires DEPLOY_BACKEND_URL and/or DEPLOY_FRONTEND_URL.

Examples:
  DEPLOY_BACKEND_URL=https://api.example.com make deploy-smoke
  DEPLOY_FRONTEND_URL=https://app.example.com DEPLOY_BACKEND_URL=https://api.example.com make deploy-smoke
USAGE
  exit 1
fi

if [[ -n "$BACKEND_URL" ]]; then
  BACKEND_URL="$(trim_url "$BACKEND_URL")"

  code=$(curl -sS -o /tmp/krbrief_deploy_backend_health.json -w "%{http_code}" "$BACKEND_URL/actuator/health")
  [[ "$code" == "200" ]] || fail "backend health expected 200, got $code"
  contains_field /tmp/krbrief_deploy_backend_health.json status || fail "backend health missing status"
  grep -q '"UP"' /tmp/krbrief_deploy_backend_health.json || fail "backend health is not UP"
  pass "deployment backend health"

  code=$(curl -sS -o /tmp/krbrief_deploy_ai_status.json -w "%{http_code}" "$BACKEND_URL/api/ai/status")
  [[ "$code" == "200" ]] || fail "ai status expected 200, got $code"
  contains_field /tmp/krbrief_deploy_ai_status.json provider || fail "ai status missing provider"
  contains_field /tmp/krbrief_deploy_ai_status.json configured || fail "ai status missing configured"
  contains_field /tmp/krbrief_deploy_ai_status.json fallbackMode || fail "ai status missing fallbackMode"
  pass "deployment ai status"

  code=$(curl -sS -G -o /tmp/krbrief_deploy_search.json -w "%{http_code}" \
    "$BACKEND_URL/api/search" \
    --data-urlencode "query=삼성전자" \
    --data-urlencode "limit=5")
  [[ "$code" == "200" ]] || fail "search expected 200, got $code"
  grep -q '"stockCode":"005930"' /tmp/krbrief_deploy_search.json || fail "search missing Samsung Electronics 005930"
  pass "deployment search contract"
fi

if [[ -n "$FRONTEND_URL" ]]; then
  FRONTEND_URL="$(trim_url "$FRONTEND_URL")"

  code=$(curl -sS -o /tmp/krbrief_deploy_frontend_health.json -w "%{http_code}" "$FRONTEND_URL/health")
  [[ "$code" == "200" ]] || fail "frontend health expected 200, got $code"
  contains_field /tmp/krbrief_deploy_frontend_health.json status || fail "frontend health missing status"
  pass "deployment frontend health"

  if [[ -n "$PUBLIC_KEY_VALUE" ]]; then
    code=$(curl -sS -G -o /tmp/krbrief_deploy_frontend_home.html -w "%{http_code}" \
      "$FRONTEND_URL/" \
      --data-urlencode "k=$PUBLIC_KEY_VALUE")
  else
    code=$(curl -sS -o /tmp/krbrief_deploy_frontend_home.html -w "%{http_code}" "$FRONTEND_URL/")
  fi
  [[ "$code" == "200" ]] || fail "frontend home expected 200, got $code"
  grep -q '한국 주식 AI 리서치' /tmp/krbrief_deploy_frontend_home.html || fail "frontend home missing app title"
  pass "deployment frontend home"
fi

echo "== Deployment smoke done: ALL PASS =="
