#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PATTERN='지금 사라|지금 팔아라|매수하세요|매도하세요|반드시 매수|반드시 매도|확정 수익|수익 보장입니다|원금 보장입니다'

echo "== Investment language safety check =="

if rg -n --pcre2 "$PATTERN" \
  "$ROOT_DIR/frontend/src" \
  "$ROOT_DIR/backend/src" \
  "$ROOT_DIR/ai-service/app"; then
  echo "[FAIL] Direct investment instruction or guarantee wording found." >&2
  exit 1
fi

echo "[PASS] No direct buy/sell instruction or guarantee wording found in source."
