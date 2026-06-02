package com.krbrief.stocks;

import java.util.List;

public record StockUniverseDto(
    String asOf,
    String source,
    Integer totalCount,
    Integer count,
    String adjustmentNote,
    List<StockUniverseItemDto> stocks) {
  public record StockUniverseItemDto(String code, String name, String market) {}
}
