package com.krbrief.stocks;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StockIndicatorService {
  public StockIndicatorSnapshotDto analyze(StockChartDto chart) {
    List<StockChartDto.StockOhlcvDto> data = chart.data() == null ? List.of() : chart.data();
    if (data.isEmpty()) {
      return new StockIndicatorSnapshotDto(
          chart.asOf(),
          null,
          new StockIndicatorSnapshotDto.MovingAveragesDto(null, null, null),
          new StockIndicatorSnapshotDto.PriceVsMa20Dto("unknown", null, "차트 데이터가 부족해 20일선 위치를 계산할 수 없습니다."),
          "unknown",
          "unknown",
          "unknown",
          "unknown",
          null,
          null,
          null,
          "차트 데이터가 부족해 이동평균선 해석을 보류합니다.",
          "데이터가 더 쌓이면 이동평균선과 거래량을 함께 해석할 수 있습니다.",
          "이동평균선은 과거 가격 기반이라 신호가 늦을 수 있습니다.",
          "low");
    }

    StockChartDto.StockOhlcvDto latest = data.get(data.size() - 1);
    long close = value(latest.close());
    String basisDate = chart.asOf() == null || chart.asOf().isBlank() ? latest.date() : chart.asOf();
    Double ma5 = movingAverage(data, 5);
    Double ma20 = movingAverage(data, 20);
    Double ma60 = movingAverage(data, 60);
    String ma20Slope = slope(data, 20);
    String ma60Slope = slope(data, 60);
    Double distanceRate = ma20 == null || ma20 <= 0 ? null : round2((close - ma20) / ma20 * 100.0);
    String pricePosition = pricePosition(distanceRate);
    double volumeRatio = volumeRatio(data);
    String volumeStrength = volumeStrength(volumeRatio);
    List<StockChartDto.StockOhlcvDto> sample = sample(data, 20);
    long support = sample.stream().mapToLong(row -> value(row.low())).filter(v -> v > 0).min().orElse(Math.round(close * 0.95));
    long resistance = sample.stream().mapToLong(row -> value(row.high())).filter(v -> v > 0).max().orElse(Math.round(close * 1.05));
    double range = Math.max(1, resistance - support);
    double rangePosition = round2(Math.max(0, Math.min(100, (close - support) / range * 100.0)));
    String trendStage = trendStage(close, ma20, ma60, ma20Slope, rangePosition);

    return new StockIndicatorSnapshotDto(
        basisDate,
        close,
        new StockIndicatorSnapshotDto.MovingAveragesDto(round2(ma5), round2(ma20), round2(ma60)),
        new StockIndicatorSnapshotDto.PriceVsMa20Dto(pricePosition, distanceRate, priceVsMa20Explanation(pricePosition, distanceRate)),
        ma20Slope,
        ma60Slope,
        trendStage,
        volumeStrength,
        support,
        resistance,
        rangePosition,
        beginnerExplanation(pricePosition, trendStage, volumeStrength),
        beginnerSummary(close, ma20, support, resistance, volumeRatio),
        "이동평균선은 과거 가격으로 계산되므로 5일선, 20일선, 60일선만 보고 결론내리지 말고 거래량, 지지선, 저항선, 이벤트를 함께 확인해야 합니다.",
        confidence(data.size()));
  }

  private static List<StockChartDto.StockOhlcvDto> sample(List<StockChartDto.StockOhlcvDto> data, int period) {
    return data.subList(Math.max(0, data.size() - period), data.size());
  }

  private static Double movingAverage(List<StockChartDto.StockOhlcvDto> data, int period) {
    List<StockChartDto.StockOhlcvDto> rows = sample(data, period);
    double average = rows.stream().mapToLong(row -> value(row.close())).filter(v -> v > 0).average().orElse(Double.NaN);
    return Double.isNaN(average) ? null : average;
  }

  private static String slope(List<StockChartDto.StockOhlcvDto> data, int period) {
    if (data.size() < Math.min(period, 20) + 5) {
      return "unknown";
    }
    Double current = movingAverage(data, period);
    Double previous = movingAverage(data.subList(0, data.size() - 5), period);
    if (current == null || previous == null || previous <= 0) {
      return "unknown";
    }
    double diff = (current - previous) / previous * 100.0;
    if (diff >= 1.0) return "rising";
    if (diff <= -1.0) return "falling";
    return "flat";
  }

  private static double volumeRatio(List<StockChartDto.StockOhlcvDto> data) {
    List<StockChartDto.StockOhlcvDto> rows = sample(data, 20);
    double avg = rows.stream().mapToLong(row -> value(row.volume())).filter(v -> v > 0).average().orElse(0);
    long latestVolume = value(data.get(data.size() - 1).volume());
    return avg <= 0 ? 0 : latestVolume / avg * 100.0;
  }

  private static String pricePosition(Double distanceRate) {
    if (distanceRate == null) return "unknown";
    if (distanceRate >= 2.0) return "above";
    if (distanceRate <= -2.0) return "below";
    return "near";
  }

  private static String volumeStrength(double ratio) {
    if (ratio >= 140) return "strong";
    if (ratio >= 80) return "normal";
    if (ratio > 0) return "weak";
    return "unknown";
  }

  private static String trendStage(long close, Double ma20, Double ma60, String ma20Slope, double rangePosition) {
    if (ma20 == null) return "unknown";
    if (close < ma20 * 0.98 && ("falling".equals(ma20Slope) || rangePosition < 30)) {
      return "downtrend";
    }
    if (ma60 != null && close > ma20 && ma20 > ma60 && "rising".equals(ma20Slope)) {
      return rangePosition >= 85 ? "uptrend_extension" : "uptrend_pullback";
    }
    if (ma60 != null && close > ma20 && ma20 < ma60) {
      return "downtrend_rebound";
    }
    if (Math.abs(close - ma20) / ma20 <= 0.02) {
      return "sideways";
    }
    return close >= ma20 ? "uptrend_pullback" : "downtrend_rebound";
  }

  private static String priceVsMa20Explanation(String position, Double distanceRate) {
    if ("above".equals(position)) {
      return "현재가는 20일선 위에 있어 단기 추세가 유지되는 쪽으로 볼 수 있지만, 이미 오른 자리인지 거래량과 저항선을 함께 봐야 합니다.";
    }
    if ("below".equals(position)) {
      return "현재가는 20일선 아래에 있어 회복 확인 전에는 성급한 매수 검토보다 관망과 리스크 관리가 우선입니다.";
    }
    if ("near".equals(position)) {
      return "현재가는 20일선 근처라 방향이 아직 확정되지 않았고, 종가와 거래량이 어느 쪽으로 붙는지 확인해야 합니다.";
    }
    return "20일선과 현재가의 거리를 계산할 근거가 부족합니다.";
  }

  private static String beginnerExplanation(String position, String trendStage, String volumeStrength) {
    return String.format(
        "5일선은 단기 흐름, 20일선은 약 한 달 평균 흐름, 60일선은 중기 흐름을 봅니다. 현재 20일선 위치는 %s, 추세 단계는 %s, 거래량 강도는 %s입니다.",
        position, trendStage, volumeStrength);
  }

  private static String beginnerSummary(long close, Double ma20, long support, long resistance, double volumeRatio) {
    String maText = ma20 == null ? "20일선 계산이 제한적" : String.format("20일선 %.0f원 대비 현재가 %d원", ma20, close);
    return String.format(
        "%s입니다. 최근 지지선은 %d원, 저항선은 %d원이고 거래량은 20일 평균 대비 %.0f%%라서 추세와 과열을 함께 봐야 합니다.",
        maText, support, resistance, volumeRatio);
  }

  private static String confidence(int dataSize) {
    if (dataSize >= 60) return "medium-high";
    if (dataSize >= 20) return "medium";
    return "low";
  }

  private static Double round2(Double value) {
    return value == null ? null : round2(value.doubleValue());
  }

  private static double round2(double value) {
    return Math.round(value * 100.0) / 100.0;
  }

  private static long value(Long value) {
    return value == null ? 0L : value;
  }
}
