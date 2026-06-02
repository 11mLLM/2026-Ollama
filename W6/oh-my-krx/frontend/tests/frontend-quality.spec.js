import { expect, test } from "@playwright/test";
import { fallbackWorkspace } from "../src/data/fallbackData.js";

const APP_URL = process.env.APP_URL || "http://localhost:5173";

const chartRows = fallbackWorkspace.chart.rows;
const tradeZones = fallbackWorkspace.zones;
const stockOptions = [
  { code: "005930", name: "삼성전자", market: "KOSPI", rate: "+1.55%" },
  { code: "000660", name: "SK하이닉스", market: "KOSPI", rate: "+2.10%" },
  { code: "035420", name: "NAVER", market: "KOSPI", rate: "-0.42%" }
];
const dailyBriefSample = {
  date: "2026-05-05",
  effectiveDate: "20260504",
  topGainer: "KBI메탈",
  topLoser: "루닛",
  mostMentioned: "PI첨단소재",
  kospiPick: "대원전선우",
  kosdaqPick: "KBI메탈",
  rawNotes: "Source: pykrx(KRX OHLCV 전일대비 계산) + naver(board posts)\neffective_date=20260504",
  topGainers: [
    { code: "024840", name: "KBI메탈", rate: 30.0 },
    { code: "322310", name: "오로스테크놀로지", rate: 30.0 },
    { code: "006345", name: "대원전선우", rate: 29.98 }
  ],
  topLosers: [
    { code: "328130", name: "루닛", rate: -49.82 },
    { code: "217620", name: "선샤인푸드", rate: -34.43 },
    { code: "261780", name: "차백신연구소", rate: -18.17 }
  ],
  mostMentionedTop: [
    { code: "178920", name: "PI첨단소재", count: 57 },
    { code: "448900", name: "한국피아이엠", count: 55 },
    { code: "259960", name: "크래프톤", count: 55 }
  ],
  kospiTopGainer: "대원전선우",
  kospiTopGainerCode: "006345",
  kospiTopGainerRate: 29.98,
  kospiTopLoser: "에스원",
  kospiTopLoserCode: "012750",
  kospiTopLoserRate: -9.06,
  kosdaqTopGainer: "KBI메탈",
  kosdaqTopGainerCode: "024840",
  kosdaqTopGainerRate: 30.0,
  kosdaqTopLoser: "루닛",
  kosdaqTopLoserCode: "328130",
  kosdaqTopLoserRate: -49.82,
  kospiTopGainers: [
    { code: "006345", name: "대원전선우", rate: 29.98 },
    { code: "007610", name: "선도전기", rate: 29.94 }
  ],
  kospiTopLosers: [
    { code: "012750", name: "에스원", rate: -9.06 },
    { code: "001250", name: "GS글로벌", rate: -8.71 }
  ],
  kosdaqTopGainers: [
    { code: "024840", name: "KBI메탈", rate: 30.0 },
    { code: "322310", name: "오로스테크놀로지", rate: 30.0 }
  ],
  kosdaqTopLosers: [
    { code: "328130", name: "루닛", rate: -49.82 },
    { code: "217620", name: "선샤인푸드", rate: -34.43 }
  ]
};

const featuredOptionsByCode = new Map([
  ["005930", { code: "005930", name: "삼성전자", market: "KOSPI" }],
  ["000660", { code: "000660", name: "SK하이닉스", market: "KOSPI" }],
  ...dailyBriefSample.kospiTopGainers.map((item) => [item.code, { ...item, market: "KOSPI" }]),
  ...dailyBriefSample.kospiTopLosers.map((item) => [item.code, { ...item, market: "KOSPI" }]),
  ...dailyBriefSample.kosdaqTopGainers.map((item) => [item.code, { ...item, market: "KOSDAQ" }]),
  ...dailyBriefSample.kosdaqTopLosers.map((item) => [item.code, { ...item, market: "KOSDAQ" }]),
  ...dailyBriefSample.mostMentionedTop.map((item) => [item.code, { ...item, market: "KRX" }])
]);

async function stubBackend(page) {
  await page.route("http://localhost:8080/api/**", async (route) => {
    const url = route.request().url();
    const json = (body) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });

    if (url.includes("/api/summaries/latest")) return json(dailyBriefSample);
    if (url.includes("/api/summaries?")) return json([dailyBriefSample]);
    if (url.includes("/api/stocks/universe")) {
      return json({ asOf: "2026-05-05", stocks: stockOptions });
    }
    if (url.includes("/api/portfolio")) {
      const method = route.request().method();
      if (method === "GET") {
        return json({
          items: [],
          summary: {
            totalWeight: 0,
            maxWeightStock: "-",
            maxWeight: 0,
            concentration: "아직 담긴 종목이 없습니다.",
            volatility: "종목을 담으면 변동성 점검을 시작합니다.",
            nextChecklist: []
          },
          source: "server_mysql_portfolio_sandbox",
          updatedAt: "2026-05-05T00:00:00Z"
        });
      }
      return json({
        items: [{
          code: "005930",
          name: "삼성전자",
          group: "반도체",
          rate: 1.55,
          count: null,
          weight: 10,
          riskNotes: ["서버 리스크 점검: 동일 섹터 집중 여부를 함께 확인해야 합니다."],
          nextChecklist: ["삼성전자의 최근 이벤트와 거래량 급증 여부 확인", "비중이 손실 허용 범위에 맞는지 점검"],
          recentEvents: [{
            date: "2026-03-02",
            type: "volume_spike",
            severity: "medium",
            title: "거래량 동반 돌파",
            explanation: "거래량이 평균보다 크게 늘었습니다."
          }]
        }],
        summary: {
          totalWeight: 10,
          maxWeightStock: "삼성전자",
          maxWeight: 10,
          concentration: "비중이 한 종목에 과도하게 몰리지는 않았습니다.",
          volatility: "큰 변동률 종목은 아직 적습니다.",
          nextChecklist: ["비중이 가장 큰 종목의 최근 이벤트 확인"]
        },
        source: "server_mysql_portfolio_sandbox",
        updatedAt: "2026-05-05T00:00:00Z"
      });
    }
    if (url.includes("/chart")) {
      const requestUrl = new URL(url);
      const interval = requestUrl.searchParams.get("interval") || "daily";
      const code = url.match(/\/api\/stocks\/(\d{6})\/chart/)?.[1] || "005930";
      const option = featuredOptionsByCode.get(code) || stockOptions.find((item) => item.code === code) || stockOptions[0];
      return json({ code, name: option.name, interval, asOf: "2026-05-05", data: chartRows });
    }
    if (url.includes("/trade-zones")) {
      const code = url.match(/\/api\/stocks\/(\d{6})\/trade-zones/)?.[1] || "005930";
      const option = featuredOptionsByCode.get(code) || stockOptions.find((item) => item.code === code) || stockOptions[0];
      return json({
        code,
        name: option.name,
        basisDate: "2026-05-05",
        confidence: "86%",
        zones: tradeZones
      });
    }
    if (url.includes("/events")) {
      return json({
        events: [{
          date: "2026-03-02",
          type: "positive",
          title: "거래량 동반 돌파",
          reason: "가격 상승과 거래량 증가가 함께 나타났습니다.",
          opposite: "다음 거래일에 거래량 없이 종가가 밀리면 실패 돌파일 수 있습니다.",
          confidence: "근거 수준 보통",
          sourceLimit: "공시 원문 확인 전에는 확정 원인으로 보지 않습니다."
        }]
      });
    }
    if (url.includes("/learning/terms")) {
      return json([{
        id: "volume", term: "거래량", coreSummary: "주식이 거래된 수량", longExplanation: "관심의 크기"
      }]);
    }
    if (url.includes("/ai/chat")) {
      return json({
        structured: {
          conclusion: "가격 흐름은 우상향",
          prediction: "20일선 지지",
          evidence: ["20일선 지지"]
        },
        confidence: "86%"
      });
    }
    return json({});
  });
}

async function expectChartSvgPainted(page) {
  const svg = page.locator(".recharts-wrapper svg").first();
  await expect(svg).toBeVisible();

  const paint = await svg.evaluate((node) => ({
    primitives: node.querySelectorAll("path,line,circle,rect,text").length,
    circles: node.querySelectorAll("circle").length,
    text: node.textContent || ""
  }));

  expect(paint.primitives).toBeGreaterThan(8);
  expect(paint.circles).toBeGreaterThan(0);
  expect(paint.text).toContain("매수 검토");
}

async function openApp(page) {
  page.on('console', msg => {
    if (msg.type() === 'error') console.error('BROWSER ERROR:', msg.text());
  });
  page.on('pageerror', err => console.error('PAGE ERROR:', err));

  await stubBackend(page);
  await page.goto(`${APP_URL}/`, { waitUntil: "networkidle" });
  await page.waitForSelector('[data-testid="chart-toolbar"]');
}

function intersects(a, b) {
  if (!a || !b) return false;
  return !(a.x + a.width <= b.x || b.x + b.width <= a.x || a.y + a.height <= b.y || b.y + b.height <= a.y);
}

async function readResponsiveMetrics(page) {
  return page.evaluate(() => {
    const rect = (el) => {
      if (!el) return null;
      const r = el.getBoundingClientRect();
      return {
        x: Math.round(r.x),
        y: Math.round(r.y),
        width: Math.round(r.width),
        height: Math.round(r.height)
      };
    };
    const byButtonText = (text) => Array.from(document.querySelectorAll('button')).find((button) => button.textContent?.trim().includes(text));
    const aiButton = document.querySelector('button[aria-label="AI 요약 펼치기"], button[aria-label="AI 요약 접기"]');
    const toolbar = document.querySelector('[data-testid="chart-toolbar"]');
    const visibleElements = Array.from(document.querySelectorAll('body *')).filter((el) => {
      const r = el.getBoundingClientRect();
      const style = getComputedStyle(el);
      return r.width > 1 && r.height > 1 && style.display !== 'none' && style.visibility !== 'hidden';
    });
    const textOverflow = visibleElements.filter((el) => {
      const style = getComputedStyle(el);
      return style.whiteSpace === 'nowrap' && el.scrollWidth > el.clientWidth + 1;
    });
    const viewportOverflow = visibleElements.filter((el) => {
      const r = el.getBoundingClientRect();
      const isChartPaint = ['g', 'circle', 'path', 'rect'].includes(el.tagName.toLowerCase()) && el.closest('.recharts-wrapper');
      return !isChartPaint && (r.left < -1 || r.top < -1 || r.right > window.innerWidth + 1 || r.bottom > window.innerHeight + 1);
    });

    return {
      toolbar: rect(toolbar),
      aiCard: rect(aiButton?.parentElement),
      learning: rect(document.querySelector('button[aria-label="Toggle Learning Mode"]')),
      portfolio: rect(document.querySelector('button[aria-label="Open Portfolio"]')),
      interval: rect(byButtonText('1일')?.parentElement),
      stockSelector: rect(byButtonText('기업 선택')),
      guide: rect(byButtonText('차트 가이드')),
      aiCondition: rect(byButtonText('AI 검토 조건')),
      textOverflowCount: textOverflow.length,
      viewportOverflowCount: viewportOverflow.length
    };
  });
}

for (const viewport of [
  { name: "desktop", width: 1440, height: 1000 },
  { name: "mobile", width: 390, height: 900 }
]) {
  test(`home shows chart, stock selector, and AI card on ${viewport.name}`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await openApp(page);

    await expect(page.locator('input[placeholder="종목명, 테마, 용어 검색..."]')).toHaveCount(0);
    await expect(page.locator('.recharts-responsive-container')).toBeVisible();
    await expectChartSvgPainted(page);

    await expect(page.getByTestId("chart-toolbar")).toBeVisible();
    await expect(page.getByRole("button", { name: /1일/ })).toBeVisible();
    await expect(page.getByRole("button", { name: /1주/ })).toBeVisible();
    await expect(page.getByRole("button", { name: /1개월/ })).toBeVisible();
    await expect(page.getByTestId("interval-status")).toContainText("1일");
    await expect(page.getByRole("button", { name: /기업 선택/ })).toBeVisible();
    await expect(page.getByRole("button", { name: /차트 가이드/ })).toBeVisible();
    await expect(page.getByRole("button", { name: /AI 검토 조건/ })).toBeVisible();
    await expect(page.locator('button[aria-label="Toggle Learning Mode"]')).toBeVisible();

    await page.getByRole("button", { name: /기업 선택/ }).click();
    const stockPanel = page.getByTestId("stock-selector-panel");
    await expect(stockPanel).toContainText("삼성전자");
    await expect(stockPanel).toContainText("SK하이닉스");
    await expect(stockPanel).toContainText("KOSPI 상승 1위");
    await expect(stockPanel).toContainText("KOSPI 하락 1위");
    await expect(stockPanel).toContainText("KOSDAQ 상승 1위");
    await expect(stockPanel).toContainText("KOSDAQ 하락 1위");
    await expect(stockPanel).toContainText("KOSPI 픽");
    await expect(stockPanel).toContainText("KOSDAQ 픽");
    await expect(stockPanel).toContainText("최다 언급");
    await expect(stockPanel).toContainText("DB에 저장하려면 포트폴리오 샌드박스에 담아야 합니다");
    await expect(stockPanel.getByRole("option")).toHaveCount(9);
    await page.mouse.click(viewport.width - 20, viewport.height - 20);
    await expect(page.getByTestId("stock-selector-panel")).toHaveCount(0);

    await page.getByRole("button", { name: /차트 가이드/ }).click();
    const guidePanel = page.getByTestId("chart-guide-panel");
    await expect(guidePanel.getByText("5일선", { exact: true })).toBeVisible();
    await expect(guidePanel.getByText("20일선", { exact: true })).toBeVisible();
    await expect(guidePanel.getByText("60일선", { exact: true })).toBeVisible();
    await page.getByRole("button", { name: "이벤트 해석" }).click();
    await expect(guidePanel.locator("text=호재 후보")).toBeVisible();

    await page.getByRole("button", { name: /AI 검토 조건/ }).click();
    const conditionPanel = page.getByTestId("chart-ai-condition-panel");
    await expect(conditionPanel.getByText("매수 검토", { exact: true })).toBeVisible();
    await expect(conditionPanel.getByText("관망", { exact: true })).toBeVisible();
    await expect(conditionPanel.getByText("매도 검토", { exact: true })).toBeVisible();
    await expect(conditionPanel.getByText("리스크 관리", { exact: true })).toBeVisible();
    await expect(conditionPanel).toContainText("교육용 보조");

    await expect(page.locator('button[aria-label="AI 요약 펼치기"]')).toBeVisible();
    await expect(page.locator('text=매매 검토 시점')).toHaveCount(0);
    await page.click('button[aria-label="AI 요약 펼치기"]');
    await expect(page.locator('text=조건별 확인 순서')).toBeVisible();
    await expect(page.locator('text=좋게 볼 이유와 주의할 이유')).toBeVisible();
    await expect(page.locator('text=신뢰도: 86%')).toBeVisible();
    await expect(page.locator('button[aria-label="Open Portfolio"]')).toBeVisible();
  });
}

test("daily weekly monthly controls refetch and keep chart painted", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await openApp(page);

  for (const [label, interval] of [
    ["1일", "daily"],
    ["1주", "weekly"],
    ["1개월", "monthly"]
  ]) {
    await page.getByRole("button", { name: label }).click();
    await expect(page.getByRole("button", { name: label })).toHaveAttribute("aria-pressed", "true");
    await expect(page.getByTestId("interval-status")).toContainText(label);
    await expectChartSvgPainted(page);
  }
});

test("stock selector switches to SK hynix and closes on outside click", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await openApp(page);

  await page.getByRole("button", { name: /기업 선택/ }).click();
  await expect(page.getByTestId("stock-selector-panel")).toContainText("SK하이닉스");

  const skRequest = page.waitForRequest((request) =>
    request.url().includes("/api/stocks/000660/chart")
  );
  await page.getByRole("option", { name: /SK하이닉스/ }).click();
  await skRequest;
  await expect(page.locator("h1", { hasText: "SK하이닉스" })).toBeVisible();
  await expect(page.getByTestId("stock-selector-panel")).toHaveCount(0);

  await page.getByRole("button", { name: /기업 선택/ }).click();
  await expect(page.getByTestId("stock-selector-panel")).toBeVisible();
  await page.mouse.click(20, 900);
  await expect(page.getByTestId("stock-selector-panel")).toHaveCount(0);
});

test("stock selector uses daily brief featured categories", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await openApp(page);

  await page.getByRole("button", { name: /기업 선택/ }).click();
  const gainerRequest = page.waitForRequest((request) =>
    request.url().includes("/api/stocks/006345/chart")
  );
  await page.getByRole("option", { name: /KOSPI 상승 1위/ }).click();
  await gainerRequest;
  await expect(page.locator("h1", { hasText: "대원전선우" })).toBeVisible();
});

for (const viewport of [
  { name: "desktop-1440", width: 1440, height: 1000 },
  { name: "desktop-1280", width: 1280, height: 900 },
  { name: "tablet-1024", width: 1024, height: 768 },
  { name: "mobile-390", width: 390, height: 900 }
]) {
  test(`responsive layout has no clipping or action overlap on ${viewport.name}`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await openApp(page);

    const metrics = await readResponsiveMetrics(page);
    expect(metrics.textOverflowCount).toBe(0);
    expect(metrics.viewportOverflowCount).toBe(0);
    expect(intersects(metrics.learning, metrics.portfolio)).toBe(false);
    expect(intersects(metrics.learning, metrics.interval)).toBe(false);
    expect(intersects(metrics.portfolio, metrics.interval)).toBe(false);
    expect(intersects(metrics.toolbar, metrics.aiCard)).toBe(false);
  });
}

test("portfolio sandbox opens and works", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await openApp(page);

  await page.click('button[aria-label="Open Portfolio"]');

  // Sandbox sheet should be open
  const sandboxSheet = page.getByTestId('portfolio-sandbox-sheet');
  await expect(sandboxSheet.locator('h2', { hasText: '포트폴리오 샌드박스' })).toBeVisible();
  await expect(sandboxSheet.getByTestId('portfolio-stock-info')).toContainText('005930');
  await expect(sandboxSheet.getByTestId('portfolio-stock-info')).toContainText('삼성전자');
  await expect(sandboxSheet.locator('text=가상 비중 설정 (%)')).toBeVisible();
  await expect(sandboxSheet.locator('text=학습용 가상 샌드박스')).toBeVisible();

  // Click Add
  const saveRequest = page.waitForRequest((request) =>
    request.url().includes('/api/portfolio/items') && request.method() === 'POST'
  );
  await page.click('button:has-text("가상 포트폴리오에 담기")');
  await saveRequest;

  // AI review section appears
  await expect(page.locator('text=AI 포트폴리오 점검')).toBeVisible();
  await expect(page.locator('text=서버 리스크 점검')).toBeVisible();
  await expect(page.locator('text=총 가상 비중')).toBeVisible();
});

test("admin sheet opens on double click", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await openApp(page);

  // Double click top left secret toggle
  const secretToggle = page.locator('button[aria-label="Open Admin"]');
  await secretToggle.dblclick();

  // Admin sheet should be open
  await expect(page.locator('h2', { hasText: '관리자 콘솔' })).toBeVisible();
});
