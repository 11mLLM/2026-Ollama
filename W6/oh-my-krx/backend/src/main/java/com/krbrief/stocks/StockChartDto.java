package com.krbrief.stocks;

import java.util.List;

public record StockChartDto(
    String code,
    String name,
    String interval,
    String range,
    String priceBasis,
    Boolean adjusted,
    String asOf,
    List<StockOhlcvDto> data) {
  public record StockOhlcvDto(String date, Long open, Long high, Long low, Long close, Long volume) {}
}
