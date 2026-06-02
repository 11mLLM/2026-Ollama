#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
KEY="${PUBLIC_KEY_TEST:-secret}"

ok() { echo "[OK] $*"; }
fail() { echo "[FAIL] $*"; exit 1; }

http_code() {
  curl -s -o /dev/null -w "%{http_code}" "$1" || true
}

wait_until_ready() {
  local tries=20
  local i=0
  while [ "$i" -lt "$tries" ]; do
    local c
    c=$(http_code "$BASE_URL/actuator/health")
    if [ "$c" = "200" ]; then
      return 0
    fi
    i=$((i + 1))
    sleep 1
  done
  return 1
}

echo "== PUBLIC_KEY OFF test =="
docker compose up -d --build >/dev/null
wait_until_ready || fail "backend not ready after compose up (PUBLIC_KEY off)"
code=$(http_code "$BASE_URL/api/summaries/stats")
[[ "$code" == "200" ]] || fail "PUBLIC_KEY off expected 200, got $code"
ok "PUBLIC_KEY off => stats 200"

echo "== PUBLIC_KEY ON test =="
PUBLIC_KEY="$KEY" docker compose up -d --build >/dev/null
wait_until_ready || fail "backend not ready after compose up (PUBLIC_KEY on)"
code_no_key=$(http_code "$BASE_URL/api/summaries/stats")
[[ "$code_no_key" == "401" || "$code_no_key" == "403" ]] || fail "PUBLIC_KEY on without key expected 401/403, got $code_no_key"
ok "PUBLIC_KEY on without key => $code_no_key"

code_with_key=$(http_code "$BASE_URL/api/summaries/stats?k=$KEY")
[[ "$code_with_key" == "200" ]] || fail "PUBLIC_KEY on with key expected 200, got $code_with_key"
ok "PUBLIC_KEY on with key => 200"

echo "== Restore PUBLIC_KEY OFF =="
PUBLIC_KEY="" docker compose up -d --build >/dev/null
wait_until_ready || fail "backend not ready after restore"
code_restored=$(http_code "$BASE_URL/api/summaries/stats")
[[ "$code_restored" == "200" ]] || fail "restore off expected 200, got $code_restored"
ok "restored PUBLIC_KEY off => 200"

echo "All PUBLIC_KEY QA checks passed."
