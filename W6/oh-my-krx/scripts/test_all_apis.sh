#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
DATE_TODAY="$(TZ=Asia/Seoul date +%F)"
FROM="${FROM:-$DATE_TODAY}"
TO="${TO:-$FROM}"

pass() { echo "[PASS] $*"; }
fail() { echo "[FAIL] $*"; exit 1; }

request() {
  local method="$1"; shift
  local url="$1"; shift
  curl -sS -X "$method" "$url" "$@"
}

status_code() {
  local method="$1"; shift
  local url="$1"; shift
  curl -sS -o /tmp/krbrief_resp.json -w "%{http_code}" -X "$method" "$url" "$@"
}

contains_field() {
  local file="$1"; shift
  local field="$1"; shift
  grep -q "\"$field\"" "$file"
}

echo "== API smoke test start =="

# 0) health
code=$(status_code GET "$BASE_URL/actuator/health")
[[ "$code" == "200" ]] || fail "health expected 200, got $code"
contains_field /tmp/krbrief_resp.json status || fail "health missing status field"
pass "GET /actuator/health"

# 1) POST /api/summaries/generate/today
code=$(status_code POST "$BASE_URL/api/summaries/generate/today")
[[ "$code" == "200" ]] || fail "generate/today expected 200, got $code"
contains_field /tmp/krbrief_resp.json date || fail "generate/today missing date"
pass "POST /api/summaries/generate/today"

# 2) POST /api/summaries/{date}/generate
code=$(status_code POST "$BASE_URL/api/summaries/$DATE_TODAY/generate")
[[ "$code" == "200" ]] || fail "generate/{date} expected 200, got $code"
contains_field /tmp/krbrief_resp.json topGainer || fail "generate/{date} missing topGainer"
pass "POST /api/summaries/{date}/generate"

# 3) GET /api/summaries/{date}
code=$(status_code GET "$BASE_URL/api/summaries/$DATE_TODAY")
[[ "$code" == "200" ]] || fail "get/{date} expected 200, got $code"
contains_field /tmp/krbrief_resp.json rawNotes || fail "get/{date} missing rawNotes"
pass "GET /api/summaries/{date}"

# 4) GET /api/summaries/latest
code=$(status_code GET "$BASE_URL/api/summaries/latest")
[[ "$code" == "200" ]] || fail "latest expected 200, got $code"
contains_field /tmp/krbrief_resp.json date || fail "latest missing date"
pass "GET /api/summaries/latest"

# 5) GET /api/summaries/stats
code=$(status_code GET "$BASE_URL/api/summaries/stats")
[[ "$code" == "200" ]] || fail "stats expected 200, got $code"
contains_field /tmp/krbrief_resp.json totalCount || fail "stats missing totalCount"
pass "GET /api/summaries/stats"

# 6) GET /api/summaries/insights
code=$(status_code GET "$BASE_URL/api/summaries/insights?from=$FROM&to=$TO")
[[ "$code" == "200" ]] || fail "insights expected 200, got $code"
contains_field /tmp/krbrief_resp.json generatedDays || fail "insights missing generatedDays"
pass "GET /api/summaries/insights"

# 7) GET /api/summaries?from&to
code=$(status_code GET "$BASE_URL/api/summaries?from=$FROM&to=$TO")
[[ "$code" == "200" ]] || fail "list expected 200, got $code"
# list may be [] but should be JSON array
head -c 1 /tmp/krbrief_resp.json | grep -q '\[' || fail "list expected array JSON"
pass "GET /api/summaries?from&to"

# 8) PUT /api/summaries/{date}/archive (soft delete)
code=$(status_code PUT "$BASE_URL/api/summaries/$DATE_TODAY/archive")
[[ "$code" == "200" ]] || fail "archive expected 200, got $code"
contains_field /tmp/krbrief_resp.json archivedAt || fail "archive missing archivedAt"
pass "PUT /api/summaries/{date}/archive"

# restore same date by regenerate
code=$(status_code POST "$BASE_URL/api/summaries/$DATE_TODAY/generate")
[[ "$code" == "200" ]] || fail "regen after archive expected 200, got $code"
pass "POST /api/summaries/{date}/generate after archive"

# 9) POST /api/summaries/backfill
code=$(status_code POST "$BASE_URL/api/summaries/backfill?from=$FROM&to=$TO")
[[ "$code" == "200" ]] || fail "backfill expected 200, got $code"
contains_field /tmp/krbrief_resp.json successCount || fail "backfill missing successCount"
pass "POST /api/summaries/backfill"

# 10) Negative test: invalid date
code=$(status_code GET "$BASE_URL/api/summaries/2026-02-29")
[[ "$code" == "400" ]] || fail "invalid date expected 400, got $code"
contains_field /tmp/krbrief_resp.json error || fail "invalid date missing error field"
pass "GET /api/summaries/{invalid-date} returns 400 JSON"

# 11) Learning terms
code=$(status_code GET "$BASE_URL/api/learning/terms?query=PER&limit=5")
[[ "$code" == "200" ]] || fail "learning terms expected 200, got $code"
contains_field /tmp/krbrief_resp.json plainDefinition || fail "learning terms missing plainDefinition"
contains_field /tmp/krbrief_resp.json coreSummary || fail "learning terms missing coreSummary"
contains_field /tmp/krbrief_resp.json longExplanation || fail "learning terms missing longExplanation"
contains_field /tmp/krbrief_resp.json chartUsage || fail "learning terms missing chartUsage"
contains_field /tmp/krbrief_resp.json commonMisunderstanding || fail "learning terms missing commonMisunderstanding"
contains_field /tmp/krbrief_resp.json scenario || fail "learning terms missing scenario"
contains_field /tmp/krbrief_resp.json relatedQuestions || fail "learning terms missing relatedQuestions"
pass "GET /api/learning/terms"

# 12) Learning assistant
code=$(status_code POST "$BASE_URL/api/learning/assistant" \
  -H "Content-Type: application/json" \
  -d '{"question":"거래량이 왜 중요해?","contextDate":"'"$DATE_TODAY"'","termId":"volume"}')
[[ "$code" == "200" ]] || fail "learning assistant expected 200, got $code"
contains_field /tmp/krbrief_resp.json futureAiEndpoint || fail "learning assistant missing futureAiEndpoint"
contains_field /tmp/krbrief_resp.json limitations || fail "learning assistant missing limitations"
contains_field /tmp/krbrief_resp.json coreSummary || fail "learning assistant missing matched term coreSummary"
contains_field /tmp/krbrief_resp.json relatedQuestions || fail "learning assistant missing matched term relatedQuestions"
grep -q '차트에서 보는 법' /tmp/krbrief_resp.json || fail "learning assistant missing chart usage section"
pass "POST /api/learning/assistant"

# 13) Stock chart
code=$(status_code GET "$BASE_URL/api/stocks/005930/chart?range=6M&interval=daily")
[[ "$code" == "200" ]] || fail "stock chart expected 200, got $code"
contains_field /tmp/krbrief_resp.json data || fail "stock chart missing data"
contains_field /tmp/krbrief_resp.json asOf || fail "stock chart missing asOf"
pass "GET /api/stocks/{code}/chart"

# 14) Stock events
EVENT_FROM="$(date -v-180d +%F 2>/dev/null || date -d '180 days ago' +%F)"
code=$(status_code GET "$BASE_URL/api/stocks/005930/events?from=$EVENT_FROM&to=$DATE_TODAY")
[[ "$code" == "200" ]] || fail "stock events expected 200, got $code"
contains_field /tmp/krbrief_resp.json events || fail "stock events missing events"
contains_field /tmp/krbrief_resp.json evidenceSources || fail "stock events missing evidenceSources"
contains_field /tmp/krbrief_resp.json causalScores || fail "stock events missing causalScores"
grep -q '"type":"news"' /tmp/krbrief_resp.json || fail "stock events missing news evidence source"
grep -q '"type":"disclosure"' /tmp/krbrief_resp.json || fail "stock events missing disclosure evidence source"
grep -q '"sourceType":"price_history"' /tmp/krbrief_resp.json || fail "stock events missing price_history causal score"
grep -q '"score":' /tmp/krbrief_resp.json || fail "stock events missing causal score value"
contains_field /tmp/krbrief_resp.json signalCount || fail "stock events missing causal signalCount"
contains_field /tmp/krbrief_resp.json signalSummary || fail "stock events missing causal signalSummary"
contains_field /tmp/krbrief_resp.json signalOrigins || fail "stock events missing causal signalOrigins"
contains_field /tmp/krbrief_resp.json signalUrls || fail "stock events missing causal signalUrls"
contains_field /tmp/krbrief_resp.json causalFactors || fail "stock events missing causalFactors"
contains_field /tmp/krbrief_resp.json causalDirection || fail "stock events missing causalDirection"
contains_field /tmp/krbrief_resp.json evidenceLevel || fail "stock events missing causal evidenceLevel"
pass "GET /api/stocks/{code}/events"

# 15) Stock trade zones
code=$(status_code GET "$BASE_URL/api/stocks/005930/trade-zones?range=6M&interval=daily&riskMode=neutral")
[[ "$code" == "200" ]] || fail "stock trade-zones expected 200, got $code"
contains_field /tmp/krbrief_resp.json zones || fail "stock trade-zones missing zones"
contains_field /tmp/krbrief_resp.json condition || fail "stock trade-zones missing condition"
contains_field /tmp/krbrief_resp.json beginnerExplanation || fail "stock trade-zones missing beginnerExplanation"
grep -q '최근 지지선' /tmp/krbrief_resp.json || fail "stock trade-zones missing support evidence"
grep -q '거래량 강도' /tmp/krbrief_resp.json || fail "stock trade-zones missing volume strength evidence"
pass "GET /api/stocks/{code}/trade-zones"

# 16) Portfolio sandbox
code=$(status_code GET "$BASE_URL/api/portfolio")
[[ "$code" == "200" ]] || fail "portfolio get expected 200, got $code"
contains_field /tmp/krbrief_resp.json items || fail "portfolio get missing items"
contains_field /tmp/krbrief_resp.json summary || fail "portfolio get missing summary"
contains_field /tmp/krbrief_resp.json source || fail "portfolio get missing source"
grep -q 'server_mysql_portfolio_sandbox' /tmp/krbrief_resp.json || fail "portfolio get missing server source"
pass "GET /api/portfolio"

cat > /tmp/krbrief_portfolio_payload.json <<JSON
{
  "code": "005930",
  "name": "삼성전자",
  "group": "반도체 대표 종목",
  "rate": 1.55,
  "count": null,
  "weight": 10
}
JSON
code=$(status_code POST "$BASE_URL/api/portfolio/items" \
  -H "Content-Type: application/json" \
  --data-binary @/tmp/krbrief_portfolio_payload.json)
[[ "$code" == "200" ]] || fail "portfolio upsert expected 200, got $code"
grep -q '"code":"005930"' /tmp/krbrief_resp.json || fail "portfolio upsert missing 005930"
contains_field /tmp/krbrief_resp.json riskNotes || fail "portfolio upsert missing riskNotes"
contains_field /tmp/krbrief_resp.json nextChecklist || fail "portfolio upsert missing nextChecklist"
contains_field /tmp/krbrief_resp.json recentEvents || fail "portfolio upsert missing recentEvents"
pass "POST /api/portfolio/items"

code=$(status_code PUT "$BASE_URL/api/portfolio/items/005930" \
  -H "Content-Type: application/json" \
  -d '{"weight":25}')
[[ "$code" == "200" ]] || fail "portfolio update expected 200, got $code"
grep -Eq '"weight"[[:space:]]*:[[:space:]]*25' /tmp/krbrief_resp.json || fail "portfolio update missing weight 25"
contains_field /tmp/krbrief_resp.json concentration || fail "portfolio update missing summary concentration"
pass "PUT /api/portfolio/items/{code}"

code=$(status_code DELETE "$BASE_URL/api/portfolio/items/005930")
[[ "$code" == "200" ]] || fail "portfolio delete expected 200, got $code"
contains_field /tmp/krbrief_resp.json summary || fail "portfolio delete missing summary"
pass "DELETE /api/portfolio/items/{code}"

# 17) Stock universe
code=$(status_code GET "$BASE_URL/api/stocks/universe" --get --data-urlencode "query=유한양행" --data-urlencode "limit=5")
[[ "$code" == "200" ]] || fail "stock universe expected 200, got $code"
grep -q '"code":"000100"' /tmp/krbrief_resp.json || fail "stock universe missing 유한양행 000100"
contains_field /tmp/krbrief_resp.json totalCount || fail "stock universe missing totalCount"
pass "GET /api/stocks/universe"

# 18) Stock sectors
code=$(status_code GET "$BASE_URL/api/stocks/sectors" --get --data-urlencode "query=의료·정밀기기" --data-urlencode "limit=5")
[[ "$code" == "200" ]] || fail "stock sectors expected 200, got $code"
grep -q '"name":"의료·정밀기기"' /tmp/krbrief_resp.json || fail "stock sectors missing 의료·정밀기기"
contains_field /tmp/krbrief_resp.json totalCount || fail "stock sectors missing totalCount"
pass "GET /api/stocks/sectors"

# 19) Stock themes
code=$(status_code GET "$BASE_URL/api/stocks/themes" --get --data-urlencode "query=전선" --data-urlencode "limit=5")
[[ "$code" == "200" ]] || fail "stock themes expected 200, got $code"
grep -q '"name":"전선"' /tmp/krbrief_resp.json || fail "stock themes missing 전선"
contains_field /tmp/krbrief_resp.json totalCount || fail "stock themes missing totalCount"
pass "GET /api/stocks/themes"

# 20) AI status
code=$(status_code GET "$BASE_URL/api/ai/status")
[[ "$code" == "200" ]] || fail "ai status expected 200, got $code"
contains_field /tmp/krbrief_resp.json provider || fail "ai status missing provider"
contains_field /tmp/krbrief_resp.json configured || fail "ai status missing configured"
contains_field /tmp/krbrief_resp.json availableProviders || fail "ai status missing availableProviders"
contains_field /tmp/krbrief_resp.json fallbackMode || fail "ai status missing fallbackMode"
AI_CONFIGURED=false
if grep -q '"configured":true' /tmp/krbrief_resp.json; then
  AI_CONFIGURED=true
fi
pass "GET /api/ai/status"

# 21) AI chat
cat > /tmp/krbrief_ai_chat_payload.json <<JSON
{
  "question": "삼성전자 차트를 초보자 관점으로 설명해줘",
  "contextDate": "$DATE_TODAY",
  "stockCode": "005930",
  "stockName": "삼성전자",
  "searchResult": {
    "type": "stock",
    "title": "삼성전자",
    "stockCode": "005930",
    "summary": "반도체와 메모리 업황을 함께 확인해야 하는 대표 종목입니다."
  },
  "chart": {
    "interval": "daily",
    "range": "6M",
    "asOf": "$DATE_TODAY",
    "latest": { "date": "$DATE_TODAY", "close": 75000 },
    "tradeZones": []
  },
  "events": [
    {
      "date": "$DATE_TODAY",
      "type": "volume_spike",
      "severity": "medium",
      "title": "거래량 급증",
      "priceChangeRate": 2.3,
      "volumeChangeRate": 230.1,
      "explanation": "가격과 거래량이 동시에 커진 이벤트입니다.",
      "evidenceSources": [
        {
          "type": "news",
          "title": "네이버 뉴스 검색",
          "url": "https://search.naver.com/search.naver?where=news&query=005930",
          "description": "가격/거래량 변화와 같은 시점의 뉴스 후보입니다."
        },
        {
          "type": "disclosure",
          "title": "DART 공시 검색",
          "url": "https://dart.fss.or.kr/",
          "description": "공식 공시 후보입니다."
        }
      ],
      "causalScores": [
        {
          "sourceType": "news",
          "label": "네이버 뉴스 검색",
          "score": 66,
          "confidence": "medium",
          "basis": "등락률과 거래량 증가",
          "interpretation": "뉴스 텍스트가 원인 후보를 보강합니다.",
          "signalCount": 2,
          "signalSummary": "반도체 업황과 거래량 변화가 함께 언급된 후보 기사",
          "causalFactors": ["수급/거래량", "업종/테마 모멘텀"],
          "causalDirection": "mixed",
          "evidenceLevel": "body",
          "signalOrigins": ["article_body", "search_result"],
          "signalUrls": ["https://example.com/news"]
        }
      ]
    }
  ],
  "terms": [
    {
      "term": "거래량",
      "plainDefinition": "하루 동안 거래된 주식 수입니다."
    }
  ]
}
JSON
code=$(status_code POST "$BASE_URL/api/ai/chat" \
  -H "Content-Type: application/json" \
  --data-binary @/tmp/krbrief_ai_chat_payload.json)
[[ "$code" == "200" ]] || fail "ai chat expected 200, got $code"
contains_field /tmp/krbrief_resp.json mode || fail "ai chat missing mode"
contains_field /tmp/krbrief_resp.json limitations || fail "ai chat missing limitations"
contains_field /tmp/krbrief_resp.json oppositeSignals || fail "ai chat missing oppositeSignals"
contains_field /tmp/krbrief_resp.json structured || fail "ai chat missing structured answer"
contains_field /tmp/krbrief_resp.json conclusion || fail "ai chat missing structured conclusion"
contains_field /tmp/krbrief_resp.json risks || fail "ai chat missing structured risks"
contains_field /tmp/krbrief_resp.json retrieval || fail "ai chat missing retrieval"
contains_field /tmp/krbrief_resp.json storage || fail "ai chat missing storage"
contains_field /tmp/krbrief_resp.json sourceCount || fail "ai chat missing retrieval sourceCount"
contains_field /tmp/krbrief_resp.json grounding || fail "ai chat missing grounding"
contains_field /tmp/krbrief_resp.json sourceCoverage || fail "ai chat missing grounding sourceCoverage"
contains_field /tmp/krbrief_resp.json supportedClaims || fail "ai chat missing grounding supportedClaims"
contains_field /tmp/krbrief_resp.json missingEvidence || fail "ai chat missing grounding missingEvidence"
grep -q '"id":"search-result"' /tmp/krbrief_resp.json || fail "ai chat missing search-result retrieval document"
grep -q '"event-1-evidence-1"' /tmp/krbrief_resp.json || fail "ai chat missing event evidence retrieval document"
grep -q '"event-1-causal-1"' /tmp/krbrief_resp.json || fail "ai chat missing causal retrieval document"
if [[ "$AI_CONFIGURED" == "true" ]]; then
  grep -q '"mode":"rag_llm"' /tmp/krbrief_resp.json || fail "ai chat configured LLM did not return rag_llm mode"
  grep -q '"used":true' /tmp/krbrief_resp.json || fail "ai chat configured LLM was not used"
fi
pass "POST /api/ai/chat"

code=$(status_code GET "$BASE_URL/api/ai/chat/history?stockCode=005930")
[[ "$code" == "200" ]] || fail "ai chat history expected 200, got $code"
grep -q '"stockCode":"005930"' /tmp/krbrief_resp.json || fail "ai chat history missing 005930 saved entry"
pass "GET /api/ai/chat/history"

cat > /tmp/krbrief_ai_chat_minimal_payload.json <<JSON
{
  "question": "삼성전자는 왜 움직였는지 초보자에게 설명해줘"
}
JSON
code=$(status_code POST "$BASE_URL/api/ai/chat" \
  -H "Content-Type: application/json" \
  --data-binary @/tmp/krbrief_ai_chat_minimal_payload.json)
[[ "$code" == "200" ]] || fail "ai chat minimal expected 200, got $code"
contains_field /tmp/krbrief_resp.json retrieval || fail "ai chat minimal missing retrieval"
contains_field /tmp/krbrief_resp.json sourceCount || fail "ai chat minimal missing sourceCount"
contains_field /tmp/krbrief_resp.json grounding || fail "ai chat minimal missing grounding"
contains_field /tmp/krbrief_resp.json limitations || fail "ai chat minimal missing limitations"
grep -Eq '"sourceCount"[[:space:]]*:[[:space:]]*[1-9]' /tmp/krbrief_resp.json || fail "ai chat minimal did not use auto-enriched retrieval"
grep -q '"id":"search-result"' /tmp/krbrief_resp.json || fail "ai chat minimal missing auto-enriched search-result"
pass "POST /api/ai/chat auto-enriches minimal context"

# 22) Unified search
code=$(status_code GET "$BASE_URL/api/search?query=%EB%B0%98%EB%8F%84%EC%B2%B4&limit=5")
[[ "$code" == "200" ]] || fail "search expected 200, got $code"
contains_field /tmp/krbrief_resp.json source || fail "search missing source"
contains_field /tmp/krbrief_resp.json id || fail "search missing id"
contains_field /tmp/krbrief_resp.json type || fail "search missing type"
contains_field /tmp/krbrief_resp.json title || fail "search missing title"
contains_field /tmp/krbrief_resp.json code || fail "search missing code"
contains_field /tmp/krbrief_resp.json market || fail "search missing market"
contains_field /tmp/krbrief_resp.json rate || fail "search missing rate"
contains_field /tmp/krbrief_resp.json tags || fail "search missing tags"
contains_field /tmp/krbrief_resp.json summary || fail "search missing summary"
contains_field /tmp/krbrief_resp.json stockCode || fail "search missing stockCode"
contains_field /tmp/krbrief_resp.json stockName || fail "search missing stockName"
contains_field /tmp/krbrief_resp.json termId || fail "search missing termId"
pass "GET /api/search"

code=$(status_code GET "$BASE_URL/api/search?query=%EC%82%BC%EC%84%B1%EC%A0%84%EC%9E%90&limit=5")
[[ "$code" == "200" ]] || fail "search samsung expected 200, got $code"
grep -q '"stockCode":"005930"' /tmp/krbrief_resp.json || fail "search samsung missing 005930"
pass "GET /api/search finds 삼성전자"

search_expect_contains() {
  local query="$1"; shift
  local expected="$1"; shift
  code=$(status_code GET "$BASE_URL/api/search" --get --data-urlencode "query=$query" --data-urlencode "limit=8")
  [[ "$code" == "200" ]] || fail "search $query expected 200, got $code"
  grep -q "$expected" /tmp/krbrief_resp.json || fail "search $query missing $expected"
  pass "GET /api/search finds $query"
}

search_expect_contains "SK하이닉스" '"stockCode":"000660"'
search_expect_contains "현대차" '"stockCode":"005380"'
search_expect_contains "네이버" '"stockCode":"035420"'
search_expect_contains "NAVER" '"stockCode":"035420"'
search_expect_contains "카카오" '"stockCode":"035720"'
search_expect_contains "반도체" '"title":"반도체"'
search_expect_contains "2차전지" '"title":"2차전지"'
search_expect_contains "금융" '"title":"증권/금융"'
search_expect_contains "바이오" '"title":"바이오"'
search_expect_contains "거래량" '"source":"learning_terms"'
search_expect_contains "거래량" '"termId":"volume"'
search_expect_contains "PER" '"source":"learning_terms"'
search_expect_contains "PER" '"termId":"per"'
search_expect_contains "DART" '"source":"learning_terms"'
search_expect_contains "DART" '"termId":"dart"'
search_expect_contains "유한양행" '"stockCode":"000100"'
search_expect_contains "의료·정밀기기" '"source":"krx_sector_classification"'
search_expect_contains "전선" '"source":"naver_theme_taxonomy"'

echo "== API smoke test done: ALL PASS =="
