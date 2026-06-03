package com.krbrief.stocks;

import java.util.List;

public record StockSectorUniverseDto(
    String asOf,
    String source,
    Integer totalCount,
    Integer count,
    String adjustmentNote,
    List<StockSectorDto> sectors) {
  public record StockSectorDto(
      String name,
      String type,
      String market,
      List<String> markets,
      Integer memberCount,
      Double rate,
      List<StockSectorMemberDto> topStocks,
      String summary) {}

  public record StockSectorMemberDto(String code, String name, String market, Long marketCap, Double rate) {}
}
