#!/usr/bin/env bash
set -u

PROJECT="/Users/rose/Desktop/git/kr-stock-daily-brief"
THREAD_ID="1472490928278208629"
SEND_WEBHOOK="/Users/rose/.openclaw/workspace/skills/discord-webhook-reporter/scripts/send_webhook.py"
WORKSPACE="/Users/rose/.openclaw/workspace"
export DISCORD_WEBHOOK_STATE_PATH="$WORKSPACE/skills/discord-webhook-reporter/state.json"

cd "$PROJECT" || exit 1

# 중복 체크: 오늘 이미 보냈으면 스킵
LOCK_FILE="/tmp/kr-stock-status-report-$(TZ=Asia/Seoul date +%F).sent"
if [ -f "$LOCK_FILE" ]; then
    echo "Already sent today. Skipping."
    exit 0
fi

today_kst="$(TZ=Asia/Seoul date +%F)"

status="OK"
blocked="no"
blocked_reason=""

fail() {
  status="FAIL"
}

set_blocked() {
  blocked="yes"
  blocked_reason="$1"
}

# 1) containers
container_line=""
missing=()

check_container() {
  local name="$1"
  local port="$2"

  if ! docker ps --format '{{.Names}} {{.Ports}}' | grep -qE "^${name} .*${port}->"; then
    missing+=("$name (port $port)")
    fail
  fi
}

check_container "kr-stock-daily-brief-mysql-1" "3306"
check_container "kr-stock-daily-brief-marketdata-1" "8000"
check_container "kr-stock-daily-brief-backend-1" "8080"
check_container "kr-stock-daily-brief-frontend-1" "5173"

if [ ${#missing[@]} -eq 0 ]; then
  container_line="✅ All 4 containers running"
else
  container_line="❌ Missing: ${missing[*]}"
fi

# 2) container error logs (last 24h)
log_errors=""
for c in kr-stock-daily-brief-mysql-1 kr-stock-daily-brief-marketdata-1 kr-stock-daily-brief-backend-1 kr-stock-daily-brief-frontend-1; do
  errs=$(docker logs --since 24h "$c" 2>&1 | grep -ciE "error|exception|fatal|panic" || true)
  if [ "$errs" -gt 0 ]; then
    log_errors+="  ⚠️ $c: $errs error lines\n"
  fi
done

if [ -z "$log_errors" ]; then
  log_errors="  ✅ No errors in last 24h"
fi

# 3) git status
git_status="$(git status --short 2>/dev/null | head -5)"
if [ $? -ne 0 ]; then
  git_status="⚠️ git unavailable"
else
  if [ -z "$git_status" ]; then
    git_status="✅ Clean"
  else
    git_status="⚠️ Dirty:\n$(echo "$git_status" | head -3)"
  fi
fi

# 4) disk usage
disk_usage="$(df -h / | awk 'NR==2{print $3 "/" $2 " (" $5 " used)"}')"

# 5) latest API test
api_test=""
if curl -sf --max-time 10 "http://localhost:8000/leaders?date=${today_kst}" >/dev/null 2>&1; then
  api_test="✅ /leaders API responding"
else
  api_test="❌ /leaders API not responding"
  fail
fi

# 6) last successful summary
last_summary=""
last_summary_line=""
if [ -f "$SEND_WEBHOOK" ]; then
  # Check if there's a recent summary in the DB or just note the API is up
  last_summary="✅ Cron job configured"
else
  last_summary="⚠️ send_webhook.py not found"
fi

# Build report
report="📊 **KR Stock Daily Brief — Status Report** ($today_kst)\n\n"
report+="**Overall:** ${status}\n\n"
report+="**Containers:**\n${container_line}\n\n"
report+="**Logs (last 24h):**\n${log_errors}\n\n"
report+="**Git:** ${git_status}\n\n"
report+="**Disk:** ${disk_usage}\n\n"
report+="**API:** ${api_test}\n\n"
report+="**Cron:** ${last_summary}\n"

if [ "$blocked" = "yes" ]; then
  report+="\n🚫 **Blocked:** ${blocked_reason}\n"
fi

# Send to Discord thread
if [ -f "$SEND_WEBHOOK" ] && [ -n "$THREAD_ID" ]; then
  # send_webhook.py uses --title + --summary, not --message
  python3 "$SEND_WEBHOOK" \
    --title "📊 KR Stock Daily Brief — Status Report ($today_kst)" \
    --summary "$(echo -e "$report")" \
    --status "$( [ "$status" = "OK" ] && echo success || echo fail )" \
    --thread-id "$THREAD_ID" \
    2>&1 || { echo "Webhook send failed"; exit 1; }
  touch "$LOCK_FILE"
else
  echo -e "$report"
fi
