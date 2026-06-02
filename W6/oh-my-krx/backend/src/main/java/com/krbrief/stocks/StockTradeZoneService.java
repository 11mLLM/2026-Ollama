package com.krbrief.stocks;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StockTradeZoneService {
  private final StockResearchClient client;
  private final StockIndicatorService indicatorService;

  public StockTradeZoneService(StockResearchClient client, StockIndicatorService indicatorService) {
    this.client = client;
    this.indicatorService = indicatorService;
  }

  public StockTradeZonesDto tradeZones(String code, String range, String interval, String riskMode) {
    StockChartDto chart = client.chart(code, range, interval);
    return tradeZonesFromChart(chart, range, interval, riskMode);
  }

  public StockTradeZonesDto tradeZonesFromChart(
      StockChartDto chart, String range, String interval, String riskMode) {
    if (chart == null) {
      throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "marketdata_empty_chart_response");
    }
    List<StockChartDto.StockOhlcvDto> data = chart.data() == null ? List.of() : chart.data();
    if (data.isEmpty()) {
      throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "marketdata_empty_chart_response");
    }

    StockChartDto.StockOhlcvDto latest = data.get(data.size() - 1);
    long close = value(latest.close());
    String basisDate = chart.asOf() == null || chart.asOf().isBlank() ? latest.date() : chart.asOf();
    MarketSignals signals = marketSignals(data, close);
    StockIndicatorSnapshotDto indicator = indicatorService.analyze(chart);
    String confidence = data.size() >= 60 && signals.avgVolume20() > 0 ? "medium-high" : "medium";
    List<String> indicatorEvidence = indicatorEvidence(indicator, signals);

    String buyCondition =
        switch (riskMode) {
          case "aggressive" -> "20일 평균 종가 회복과 거래량 강도 100% 이상이 동시에 보이면 소액 분할 진입을 검토";
          case "conservative" -> "저항선 돌파 후 2거래일 이상 종가와 거래량이 유지될 때까지 대기";
          default -> "20일 평균 종가 회복, 거래량 강도 개선, 최근 지지선 방어가 동시에 보일 때 매수 검토";
        };

    List<StockTradeZonesDto.TradeZoneDto> zones =
        List.of(
            zone(
                "buy_review",
                "매수 검토 구간",
                Math.max(Math.round(signals.avgClose20() * 0.995), Math.round(close * 0.985)),
                Math.min(Math.round(signals.resistance() * 1.005), Math.round(close * 1.030)),
                buyCondition,
                String.format(
                    "20일 평균 종가 %.0f, 최근 저항선 %d, 거래량 강도 %.0f%%를 함께 사용",
                    signals.avgClose20(), signals.resistance(), signals.volumeRatio()),
                "가격은 회복하지만 거래량 강도가 100% 미만이면 신뢰도를 낮춤",
                confidence,
                basisDate,
                "가격만 보지 말고 거래량이 같이 늘어나는지 확인합니다.",
                "20일선 회복, 최근 저항선 돌파, 거래량 강도가 동시에 맞을 때만 매수 검토 신뢰도가 올라갑니다.",
                indicatorEvidence,
                "종가가 20일선 아래로 다시 밀리거나 거래량이 평균 이하로 줄면 매수 검토를 보류합니다.",
                "20일선 위 종가 유지와 거래량 재확대가 이어지면 관심 후보로 유지합니다.",
                "호재가 있어도 저항선 아래에서 거래량이 마르면 추격보다 관망이 낫습니다.",
                List.of("20일선 위 종가인지 확인", "거래량이 20일 평균보다 많은지 확인", "저항선 돌파 후 종가가 유지되는지 확인")),
            zone(
                "split_buy",
                "분할매수 검토 구간",
                Math.round(signals.support() * 0.995),
                Math.min(Math.round(signals.avgClose20() * 0.995), Math.round(close * 0.985)),
                "지지선 근처에서 하락 속도가 줄고 반등 거래량이 붙을 때만 나누어 검토",
                String.format("최근 지지선 %d, 20일 평균 종가 %.0f, 거래량 강도 %.0f%% 기준", signals.support(), signals.avgClose20(), signals.volumeRatio()),
                "지지선 이탈 후 거래량이 커지면 분할보다 관망이 우선",
                confidence,
                basisDate,
                "한 번에 들어가지 않고 가격 확인 지점을 여러 번 나눕니다.",
                "지지선 근처에서 하락 속도가 줄고 반등 거래량이 붙는지 나누어 확인하는 구간입니다.",
                indicatorEvidence,
                "지지선 이탈 후 회복하지 못하거나 하락일 거래량이 커지면 분할 검토를 멈춥니다.",
                "지지선 방어와 5일선 회복이 함께 나오면 작은 단위의 검토가 가능합니다.",
                "지지선이 깨진 뒤에도 계속 사면 분할매수가 아니라 기준 없는 물타기가 됩니다.",
                List.of("1차 확인 가격 기록", "손실 중단 기준 기록", "반등 거래량 유무 확인")),
            zone(
                "watch",
                "관망 구간",
                Math.min(Math.round(signals.avgClose20() * 0.990), Math.round(close * 0.980)),
                Math.max(Math.round(signals.avgClose20() * 1.010), Math.round(close * 1.030)),
                "가격 방향과 거래량 방향이 엇갈리면 새 신호가 확인될 때까지 대기",
                String.format("지지-저항 범위 내 위치 %.0f%%, 20일 평균 거래량 %.0f 기준", signals.rangePosition(), signals.avgVolume20()),
                "거래량 없는 상승 또는 종가가 저가 부근에서 끝나는 흐름",
                confidence,
                basisDate,
                "잘 모를 때 쉬는 것도 전략입니다. 방향이 분명해질 때까지 기다립니다.",
                "가격과 거래량, 이동평균선 방향이 엇갈리면 새 조건이 확인될 때까지 관찰 우선입니다.",
                indicatorEvidence,
                "명확한 거래량 동반 돌파나 지지선 이탈이 나오면 관망 판단을 다시 갱신합니다.",
                "전고점 돌파와 거래량 유지가 함께 보이면 매수 검토 구간으로 이동할 수 있습니다.",
                "지지선 이탈과 하락 거래량 증가가 함께 나오면 리스크 관리가 우선입니다.",
                List.of("방향이 애매한 이유 쓰기", "다음 확인 가격 정하기", "호재와 가격 반응을 분리해서 보기")),
            zone(
                "sell_review",
                "매도 검토 구간",
                Math.max(Math.round(signals.resistance() * 0.990), Math.round(close * 1.030)),
                Math.max(Math.round(signals.resistance() * 1.030), Math.round(close * 1.120)),
                "급등 후 거래량 둔화, 긴 윗꼬리, 직전 고점 돌파 실패가 겹칠 때 일부 차익 실현 검토",
                String.format("최근 저항선 %d, 거래량 강도 %.0f%%, 범위 위치 %.0f%% 기준", signals.resistance(), signals.volumeRatio(), signals.rangePosition()),
                "거래량이 강하게 유지되고 고점 돌파가 이어지면 성급한 매도 해석을 낮춤",
                confidence,
                basisDate,
                "이익이 났을 때도 왜 파는지 조건을 정해야 합니다.",
                "저항선 부근에서 거래량이 줄고 윗꼬리가 반복되면 일부 차익 실현 검토 근거가 생깁니다.",
                indicatorEvidence,
                "고점 돌파 후 거래량이 유지되고 종가가 강하면 매도 검토 신호를 낮춥니다.",
                "상승 후 과열이 식으며 20일선 위를 유지하면 일부만 점검하고 추세를 더 볼 수 있습니다.",
                "급등 뒤 거래량 둔화와 긴 윗꼬리가 반복되면 이익 보호 기준을 세워야 합니다.",
                List.of("저항선 근처인지 확인", "거래량 둔화 여부 확인", "일부 차익과 전량 매도를 구분")),
            zone(
                "risk_management",
                "리스크 관리 구간",
                Math.round(signals.support() * 0.970),
                Math.round(signals.support()),
                "전저점 이탈 또는 하락일 거래량 급증 시 비중 축소와 재진입 기준 재설정",
                String.format("최근 지지선 %d, 전저점 이탈 여부, 하락일 거래량 급증 여부 기준", signals.support()),
                "일시적 장중 이탈 후 종가 회복이면 다음 거래일까지 확인",
                confidence,
                basisDate,
                "손실을 어디까지 허용할지 미리 정하는 구간입니다.",
                "지지선 이탈, 20일선 하향 이탈, 하락일 거래량 급증이 겹치면 손실 확대를 막는 기준을 우선합니다.",
                indicatorEvidence,
                "장중 이탈 뒤 종가가 회복되고 다음 거래일 거래량이 안정되면 리스크 판단을 재점검합니다.",
                "지지선 회복과 거래량 안정이 함께 나오면 관망 또는 분할 검토로 되돌릴 수 있습니다.",
                "하락 거래량이 커지면 손실 허용선을 늦추지 말고 기준을 다시 세워야 합니다.",
                List.of("전저점 이탈 여부 확인", "하락일 거래량 급증 확인", "재진입 조건과 중단 조건 분리")));

    List<String> evidence =
        List.of(
            "기준일: " + basisDate,
            "최근 종가: " + close,
            "최근 샘플 수: " + data.size(),
            "최근 지지선: " + signals.support(),
            "최근 저항선: " + signals.resistance(),
            "20일 평균 종가: " + Math.round(signals.avgClose20()),
            "20일 평균 거래량: " + Math.round(signals.avgVolume20()),
            "거래량 강도: " + Math.round(signals.volumeRatio()) + "%",
            "5일 이동평균선: " + numberText(indicator.movingAverages().ma5()),
            "20일 이동평균선: " + numberText(indicator.movingAverages().ma20()),
            "60일 이동평균선: " + numberText(indicator.movingAverages().ma60()),
            "20일선 위치: " + indicator.priceVsMa20().position(),
            "20일선 기울기: " + indicator.ma20Slope(),
            "현재 추세 단계: " + indicator.trendStage(),
            "지지-저항 범위 내 위치: " + Math.round(signals.rangePosition()) + "%",
            "판단 성향: " + riskMode);

    return new StockTradeZonesDto(
        chart.code(),
        chart.name(),
        interval,
        range,
        basisDate,
        riskMode,
        confidence,
        zones,
        evidence,
        indicator,
        decisionSummary(indicator, signals, riskMode, confidence));
  }

  private static StockTradeZonesDto.TradeZoneDto zone(
      String type,
      String label,
      long fromPrice,
      long toPrice,
      String condition,
      String evidence,
      String oppositeSignal,
      String confidence,
      String basisDate,
      String beginnerExplanation,
      String reasoning,
      List<String> indicatorEvidence,
      String invalidationSignal,
      String goodScenario,
      String badScenario,
      List<String> beginnerChecklist) {
    long low = Math.min(fromPrice, toPrice);
    long high = Math.max(fromPrice, toPrice);
    return new StockTradeZonesDto.TradeZoneDto(
        type,
        label,
        low,
        high,
        condition,
        evidence,
        oppositeSignal,
        confidence,
        basisDate,
        beginnerExplanation,
        reasoning,
        indicatorEvidence,
        invalidationSignal,
        goodScenario,
        badScenario,
        beginnerChecklist);
  }

  private static List<String> indicatorEvidence(StockIndicatorSnapshotDto indicator, MarketSignals signals) {
    return List.of(
        "5일선: " + priceText(indicator.movingAverages().ma5()) + "은 단기 흐름을 봅니다.",
        "20일선: " + priceText(indicator.movingAverages().ma20()) + "은 약 한 달 평균 흐름입니다.",
        "60일선: " + priceText(indicator.movingAverages().ma60()) + "은 중기 흐름입니다.",
        "현재가는 20일선 대비 " + indicator.priceVsMa20().position() + " 상태입니다.",
        "거래량 강도는 20일 평균 대비 " + Math.round(signals.volumeRatio()) + "%입니다.",
        "최근 지지선 " + signals.support() + "원과 저항선 " + signals.resistance() + "원을 함께 봅니다.");
  }

  private static StockTradeZonesDto.CurrentDecisionSummaryDto decisionSummary(
      StockIndicatorSnapshotDto indicator, MarketSignals signals, String riskMode, String confidence) {
    String state = currentState(indicator, signals);
    String summary =
        switch (state) {
          case "buy_review" -> "20일선 위 흐름과 거래량 개선이 보이지만 저항선 돌파 확인이 필요한 구간입니다.";
          case "sell_review" -> "저항선에 가까워져 추격보다 거래량 유지와 윗꼬리 여부를 확인해야 하는 구간입니다.";
          case "risk_management" -> "지지선 또는 20일선 방어가 약해질 수 있어 손실 허용 기준을 먼저 점검할 구간입니다.";
          default -> "가격과 거래량 신호가 엇갈려 새 확인 신호를 기다리는 관망 구간입니다.";
        };
    return new StockTradeZonesDto.CurrentDecisionSummaryDto(
        state,
        summary,
        "20일선 위 종가 유지, 거래량 20일 평균 이상, 저항선 돌파 후 유지가 함께 보일 때 매수 검토",
        "급등 후 거래량 둔화, 긴 윗꼬리, 직전 고점 돌파 실패가 겹칠 때 매도 검토",
        "가격 방향과 거래량 방향이 엇갈리거나 뉴스 근거가 약하면 관망",
        "전저점 또는 지지선 이탈과 하락일 거래량 급증이 겹치면 리스크 관리",
        "반대 신호는 거래량 없는 상승, 20일선 재이탈, 공시/뉴스 근거 부족입니다.",
        confidence,
        List.of(
            "현재가와 20일선 위치: " + indicator.priceVsMa20().position(),
            "20일선 기울기: " + indicator.ma20Slope(),
            "거래량 강도: " + indicator.volumeStrength(),
            "지지-저항 범위 내 위치: " + Math.round(signals.rangePosition()) + "%",
            "판단 성향: " + riskMode),
        indicator.beginnerSummary(),
        List.of(
            "투자 지시가 아니라 교육용 조건 설명입니다.",
            "이동평균선은 후행 지표라 거래량, 지지선, 저항선, 이벤트 확인이 필요합니다."));
  }

  private static String currentState(StockIndicatorSnapshotDto indicator, MarketSignals signals) {
    String position = indicator.priceVsMa20().position();
    if ("below".equals(position) || signals.rangePosition() <= 15) {
      return "risk_management";
    }
    if (signals.rangePosition() >= 85 && signals.volumeRatio() < 110) {
      return "sell_review";
    }
    if ("above".equals(position) && signals.volumeRatio() >= 100) {
      return "buy_review";
    }
    return "watch";
  }

  private static MarketSignals marketSignals(List<StockChartDto.StockOhlcvDto> data, long close) {
    int sampleSize = Math.min(data.size(), 20);
    List<StockChartDto.StockOhlcvDto> sample = data.subList(Math.max(0, data.size() - sampleSize), data.size());
    double avgVolume20 = sample.stream().mapToLong(row -> value(row.volume())).average().orElse(0);
    double avgClose20 = sample.stream().mapToLong(row -> value(row.close())).filter(v -> v > 0).average().orElse(close);
    long support = sample.stream().mapToLong(row -> value(row.low())).filter(v -> v > 0).min().orElse(Math.round(close * 0.95));
    long resistance = sample.stream().mapToLong(row -> value(row.high())).filter(v -> v > 0).max().orElse(Math.round(close * 1.05));
    long latestVolume = value(data.get(data.size() - 1).volume());
    double volumeRatio = avgVolume20 <= 0 ? 0 : latestVolume / avgVolume20 * 100.0;
    double range = Math.max(1, resistance - support);
    double rangePosition = Math.max(0, Math.min(100, (close - support) / range * 100.0));
    return new MarketSignals(support, resistance, avgClose20, avgVolume20, volumeRatio, rangePosition);
  }

  private static long value(Long value) {
    return value == null ? 0L : value;
  }

  private static String numberText(Double value) {
    return value == null ? "계산 제한" : String.valueOf(Math.round(value));
  }

  private static String priceText(Double value) {
    return value == null ? "계산 제한" : Math.round(value) + "원";
  }

  private record MarketSignals(
      long support,
      long resistance,
      double avgClose20,
      double avgVolume20,
      double volumeRatio,
      double rangePosition) {}
}
