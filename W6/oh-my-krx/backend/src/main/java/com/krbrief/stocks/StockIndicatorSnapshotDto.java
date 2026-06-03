package com.krbrief.stocks;

public record StockIndicatorSnapshotDto(
    String basisDate,
    Long latestClose,
    MovingAveragesDto movingAverages,
    PriceVsMa20Dto priceVsMa20,
    String ma20Slope,
    String ma60Slope,
    String trendStage,
    String volumeStrength,
    Long supportLevel,
    Long resistanceLevel,
    Double rangePosition,
    String beginnerExplanation,
    String beginnerSummary,
    String caution,
    String confidence) {

  public record MovingAveragesDto(Double ma5, Double ma20, Double ma60) {}

  public record PriceVsMa20Dto(String position, Double distanceRate, String beginnerExplanation) {}
}
