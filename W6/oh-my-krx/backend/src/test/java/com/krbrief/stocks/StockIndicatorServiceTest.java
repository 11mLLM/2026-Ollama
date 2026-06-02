package com.krbrief.stocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StockIndicatorServiceTest {
  private final StockIndicatorService service = new StockIndicatorService();

  @Test
  void analyze_calculatesMovingAveragesAndTrendSnapshot() {
    List<StockChartDto.StockOhlcvDto> rows = new ArrayList<>();
    for (int i = 1; i <= 65; i++) {
      long close = 10_000L + (i * 100L);
      rows.add(new StockChartDto.StockOhlcvDto("2026-03-" + i, close - 50, close + 80, close - 120, close, 1_000L + i));
    }
    StockChartDto chart = new StockChartDto("005930", "삼성전자", "daily", "6M", "close", false, "2026-05-05", rows);

    StockIndicatorSnapshotDto snapshot = service.analyze(chart);

    assertEquals("2026-05-05", snapshot.basisDate());
    assertEquals(16_500L, snapshot.latestClose());
    assertEquals("above", snapshot.priceVsMa20().position());
    assertEquals("rising", snapshot.ma20Slope());
    assertNotNull(snapshot.movingAverages().ma60());
    assertTrue(snapshot.beginnerSummary().contains("20일선"));
    assertTrue(snapshot.caution().contains("거래량"));
  }

  @Test
  void analyze_handlesShortDataWithLowerConfidence() {
    StockChartDto chart =
        new StockChartDto(
            "005930",
            "삼성전자",
            "daily",
            "1M",
            "close",
            false,
            "2026-05-05",
            List.of(new StockChartDto.StockOhlcvDto("2026-05-05", 90L, 110L, 80L, 100L, 1_000L)));

    StockIndicatorSnapshotDto snapshot = service.analyze(chart);

    assertEquals("low", snapshot.confidence());
    assertEquals("unknown", snapshot.ma20Slope());
    assertEquals("near", snapshot.priceVsMa20().position());
  }
}
