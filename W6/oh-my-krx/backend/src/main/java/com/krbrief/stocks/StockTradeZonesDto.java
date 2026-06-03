package com.krbrief.stocks;

import java.util.List;

public record StockTradeZonesDto(
    String code,
    String name,
    String interval,
    String range,
    String basisDate,
    String riskMode,
    String confidence,
    List<TradeZoneDto> zones,
    List<String> evidence,
    StockIndicatorSnapshotDto indicatorSnapshot,
    CurrentDecisionSummaryDto currentDecisionSummary) {

  public StockTradeZonesDto(
      String code,
      String name,
      String interval,
      String range,
      String basisDate,
      String riskMode,
      String confidence,
      List<TradeZoneDto> zones,
      List<String> evidence) {
    this(code, name, interval, range, basisDate, riskMode, confidence, zones, evidence, null, null);
  }

  public record TradeZoneDto(
      String type,
      String label,
      Long fromPrice,
      Long toPrice,
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

    public TradeZoneDto(
        String type,
        String label,
        Long fromPrice,
        Long toPrice,
        String condition,
        String evidence,
        String oppositeSignal,
        String confidence,
        String basisDate,
        String beginnerExplanation) {
      this(
          type,
          label,
          fromPrice,
          toPrice,
          condition,
          evidence,
          oppositeSignal,
          confidence,
          basisDate,
          beginnerExplanation,
          evidence,
          List.of(),
          oppositeSignal,
          beginnerExplanation,
          oppositeSignal,
          List.of());
    }
  }

  public record CurrentDecisionSummaryDto(
      String state,
      String summary,
      String buyReviewCondition,
      String sellReviewCondition,
      String watchCondition,
      String riskCondition,
      String oppositeSignal,
      String confidence,
      List<String> why,
      String beginnerExplanation,
      List<String> limitations) {}
}
