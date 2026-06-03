package com.krbrief.stocks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StockTradeZoneServiceTest {
  @Test
  void tradeZones_includeSupportResistanceAndVolumeEvidence() {
    StockResearchClient client = mock(StockResearchClient.class);
    List<StockChartDto.StockOhlcvDto> rows = new ArrayList<>();
    for (int i = 1; i <= 25; i++) {
      long close = 68_000L + (i * 120L);
      rows.add(
          new StockChartDto.StockOhlcvDto(
              "2026-04-" + String.format("%02d", i),
              close - 300,
              close + 900,
              close - 1200,
              close,
              900_000L + (i * 10_000L)));
    }
    when(client.chart("005930", "6M", "daily"))
        .thenReturn(new StockChartDto("005930", "삼성전자", "daily", "6M", "close", false, "2026-04-25", rows));

    StockTradeZoneService service = new StockTradeZoneService(client, new StockIndicatorService());
    StockTradeZonesDto res = service.tradeZones("005930", "6M", "daily", "neutral");

    assertFalse(res.zones().isEmpty());
    assertTrue(res.indicatorSnapshot().beginnerSummary().contains("20일선"));
    assertTrue(res.currentDecisionSummary().buyReviewCondition().contains("매수 검토"));
    assertTrue(res.evidence().stream().anyMatch(line -> line.startsWith("최근 지지선:")));
    assertTrue(res.evidence().stream().anyMatch(line -> line.startsWith("최근 저항선:")));
    assertTrue(res.evidence().stream().anyMatch(line -> line.startsWith("거래량 강도:")));
    assertTrue(res.zones().stream().allMatch(zone -> zone.fromPrice() <= zone.toPrice()));
    assertTrue(res.zones().stream().anyMatch(zone -> zone.evidence().contains("거래량 강도")));
    assertTrue(res.zones().stream().allMatch(zone -> !zone.goodScenario().isBlank()));
  }
}
