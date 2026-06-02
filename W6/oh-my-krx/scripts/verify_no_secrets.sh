#!/usr/bin/env bash
set -euo pipefail

echo "== Secret safety check =="

tracked_env_files="$(
  git ls-files \
    '.env' '.env.*' \
    'backend/.env' 'backend/.env.*' \
    'frontend/.env' 'frontend/.env.*' \
    'ai-service/.env' 'ai-service/.env.*' \
    'marketdata-python/.env' 'marketdata-python/.env.*' \
    2>/dev/null | grep -Ev '(^|/)\.env\.example$' || true
)"

if [[ -n "$tracked_env_files" ]]; then
  echo "[FAIL] Tracked environment files are not allowed:"
  echo "$tracked_env_files"
  exit 1
fi

secret_pattern='-----BEGIN ((RSA|DSA|EC|OPENSSH) )?PRIVATE KEY-----|AKIA[0-9A-Z]{16}|sk-[A-Za-z0-9_-]{32,}'

tracked_scan_files=/tmp/krbrief_secret_files.z
git ls-files -z -- \
  . \
  ':(exclude)docs/**' \
  ':(exclude)scripts/verify_no_secrets.sh' \
  ':(exclude)frontend/package-lock.json' \
  >"$tracked_scan_files"

scan_result=1
: >/tmp/krbrief_secret_scan.txt
while IFS= read -r -d '' file; do
  if rg -n -I -e "$secret_pattern" -- "$file" >>/tmp/krbrief_secret_scan.txt; then
    scan_result=0
  else
    file_scan_result=$?
    if [[ "$file_scan_result" != "1" ]]; then
      scan_result="$file_scan_result"
      break
    fi
  fi
done <"$tracked_scan_files"

if [[ "$scan_result" == "0" ]]; then
  echo "[FAIL] Potential secret material found:"
  cat /tmp/krbrief_secret_scan.txt
  exit 1
fi

if [[ "$scan_result" != "1" ]]; then
  echo "[FAIL] Secret scanner failed:"
  cat /tmp/krbrief_secret_scan.txt
  exit 1
fi

echo "[PASS] No tracked env files or obvious secret tokens found."
