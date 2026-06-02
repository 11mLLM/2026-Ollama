package com.krbrief.stocks;

import java.util.List;

public record StockThemeUniverseDto(
    String asOf,
    String source,
    Integer totalCount,
    Integer count,
    List<StockThemeDto> themes) {
  public record StockThemeDto(
      String name,
      String type,
      String market,
      String rate,
      String threeDayRate,
      Integer risingCount,
      Integer flatCount,
      Integer fallingCount,
      List<String> leaders,
      String summary) {}
}
