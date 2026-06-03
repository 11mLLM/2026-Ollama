package com.krbrief.stocks;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StockController.class)
class StockControllerTest {
  @Autowired MockMvc mvc;

  @MockBean StockResearchClient client;

  @MockBean StockTradeZoneService tradeZoneService;

  @Test
  void chart_returnsGatewayResponse() throws Exception {
    when(client.chart(eq("005930"), eq("6M"), eq("daily")))
        .thenReturn(
            new StockChartDto(
                "005930",
                "삼성전자",
                "daily",
                "6M",
                "close",
                false,
                "2026-05-01",
                List.of(new StockChartDto.StockOhlcvDto("2026-05-01", 100L, 110L, 90L, 105L, 1000L))));

    mvc.perform(get("/api/stocks/005930/chart?range=6M&interval=daily"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("005930"))
        .andExpect(jsonPath("$.data[0].close").value(105));
  }

  @Test
  void events_returnsGatewayResponse() throws Exception {
    when(client.events(eq("005930"), eq(LocalDate.parse("2026-04-01")), eq(LocalDate.parse("2026-05-01"))))
        .thenReturn(
            new StockEventsDto(
                "005930",
                "삼성전자",
                "2026-04-01",
                "2026-05-01",
                List.of(
                    new StockEventsDto.StockEventDto(
                        "2026-04-12",
                        "volume_spike",
                        "medium",
                        3.2,
                        230.5,
                        "거래량 급증",
                        "최근 20거래일 평균 대비 거래량이 크게 증가했습니다.",
                        List.of("https://finance.naver.com/item/main.naver?code=005930"),
                        List.of(
                            new StockEventsDto.EvidenceSourceDto(
                                "news",
                                "네이버 뉴스 검색",
                                "https://search.naver.com/search.naver?where=news&query=%EC%82%BC%EC%84%B1%EC%A0%84%EC%9E%90",
                                "가격/거래량 변화와 같은 시점의 뉴스 후보를 확인합니다.")),
                        List.of(
                            new StockEventsDto.CausalScoreDto(
                                "price_history",
                                "네이버 일별 시세",
                                86,
                                "high",
                                "등락률 +3.20%, 20일 평균 대비 거래량 230%",
                                "가격/거래량 원자료에서 이벤트 자체가 확인됩니다.",
                                2,
                                List.of("공시", "거래량"),
                                List.of("수급/거래량"),
                                "positive",
                                "market_data",
                                "삼성전자 공시와 거래량 관련 뉴스 텍스트",
                                List.of("article_body"),
                                List.of("https://www.digitaltoday.co.kr/news/articleView.html?idxno=619374")))))));

    mvc.perform(get("/api/stocks/005930/events?from=2026-04-01&to=2026-05-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.events[0].type").value("volume_spike"))
        .andExpect(jsonPath("$.events[0].evidenceLinks").isArray())
        .andExpect(jsonPath("$.events[0].evidenceSources").isArray())
        .andExpect(jsonPath("$.events[0].evidenceSources[0].type").value("news"))
        .andExpect(jsonPath("$.events[0].causalScores").isArray())
        .andExpect(jsonPath("$.events[0].causalScores[0].sourceType").value("price_history"))
        .andExpect(jsonPath("$.events[0].causalScores[0].score").value(86))
        .andExpect(jsonPath("$.events[0].causalScores[0].signalCount").value(2))
        .andExpect(jsonPath("$.events[0].causalScores[0].matchedSignals[0]").value("공시"))
        .andExpect(jsonPath("$.events[0].causalScores[0].causalFactors[0]").value("수급/거래량"))
        .andExpect(jsonPath("$.events[0].causalScores[0].causalDirection").value("positive"))
        .andExpect(jsonPath("$.events[0].causalScores[0].evidenceLevel").value("market_data"))
        .andExpect(jsonPath("$.events[0].causalScores[0].signalOrigins[0]").value("article_body"))
        .andExpect(jsonPath("$.events[0].causalScores[0].signalUrls[0]").value("https://www.digitaltoday.co.kr/news/articleView.html?idxno=619374"))
        .andExpect(jsonPath("$.events[0].sentimentForPrice").value("positive"))
        .andExpect(jsonPath("$.events[0].positiveReasons").isArray())
        .andExpect(jsonPath("$.events[0].negativeReasons").isArray())
        .andExpect(jsonPath("$.events[0].oppositeInterpretation").isNotEmpty())
        .andExpect(jsonPath("$.events[0].sourceLimitations").isNotEmpty());
  }

  @Test
  void tradeZones_returnsComputedDecisionContract() throws Exception {
    when(tradeZoneService.tradeZones(eq("005930"), eq("6M"), eq("daily"), eq("neutral")))
        .thenReturn(
            new StockTradeZonesDto(
                "005930",
                "삼성전자",
                "daily",
                "6M",
                "2026-05-01",
                "neutral",
                "medium-high",
                List.of(
                    new StockTradeZonesDto.TradeZoneDto(
                        "buy_review",
                        "매수 검토 구간",
                        100L,
                        110L,
                        "20일선 회복과 거래량 증가가 함께 보일 때 검토",
                        "20일 평균 거래량",
                        "거래량 없는 상승",
                        "medium-high",
                        "2026-05-01",
                        "가격과 거래량을 함께 확인합니다.")),
                List.of("기준일: 2026-05-01")));

    mvc.perform(get("/api/stocks/005930/trade-zones?range=6M&interval=daily&riskMode=neutral"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.zones[0].type").value("buy_review"))
        .andExpect(jsonPath("$.zones[0].condition").isNotEmpty())
        .andExpect(jsonPath("$.evidence").isArray());
  }

  @Test
  void universe_returnsGatewayResponse() throws Exception {
    when(client.universe(eq("유한양행"), eq(5)))
        .thenReturn(
            new StockUniverseDto(
                "2026-05-01",
                "pykrx_market_ticker_list",
                2800,
                1,
                "",
                List.of(new StockUniverseDto.StockUniverseItemDto("000100", "유한양행", "KOSPI"))));

    mvc.perform(get("/api/stocks/universe").param("query", "유한양행").param("limit", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("pykrx_market_ticker_list"))
        .andExpect(jsonPath("$.stocks[0].code").value("000100"));
  }

  @Test
  void sectors_returnsGatewayResponse() throws Exception {
    when(client.sectors(eq("의료"), eq(5)))
        .thenReturn(
            new StockSectorUniverseDto(
                "2026-05-01",
                "pykrx_market_sector_classifications",
                20,
                1,
                "",
                List.of(
                    new StockSectorUniverseDto.StockSectorDto(
                        "의료·정밀기기",
                        "industry",
                        "KRX",
                        List.of("KOSDAQ"),
                        120,
                        0.42,
                        List.of(
                            new StockSectorUniverseDto.StockSectorMemberDto(
                                "214150", "클래시스", "KOSDAQ", 3500000000000L, 1.2)),
                        "KRX 업종 분류 기준 120개 상장 종목이 포함됩니다."))));

    mvc.perform(get("/api/stocks/sectors").param("query", "의료").param("limit", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("pykrx_market_sector_classifications"))
        .andExpect(jsonPath("$.sectors[0].name").value("의료·정밀기기"))
        .andExpect(jsonPath("$.sectors[0].topStocks[0].code").value("214150"));
  }

  @Test
  void themes_returnsGatewayResponse() throws Exception {
    when(client.themes(eq("전선"), eq(5)))
        .thenReturn(
            new StockThemeUniverseDto(
                "2026-05-01",
                "naver_finance_theme",
                260,
                1,
                List.of(
                    new StockThemeUniverseDto.StockThemeDto(
                        "전선",
                        "theme",
                        "테마",
                        "+9.20%",
                        "+7.40%",
                        7,
                        0,
                        1,
                        List.of("KBI메탈", "대원전선"),
                        "Naver Finance 테마 시세 기준입니다."))));

    mvc.perform(get("/api/stocks/themes").param("query", "전선").param("limit", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("naver_finance_theme"))
        .andExpect(jsonPath("$.themes[0].name").value("전선"))
        .andExpect(jsonPath("$.themes[0].leaders[0]").value("KBI메탈"));
  }

  @Test
  void invalidInputs_return400() throws Exception {
    mvc.perform(get("/api/stocks/abc/chart")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/stocks/005930/chart?range=2Y")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/stocks/005930/trade-zones?riskMode=reckless")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/stocks/005930/events?from=2026-05-01&to=2026-04-01"))
        .andExpect(status().isBadRequest());
  }
}
