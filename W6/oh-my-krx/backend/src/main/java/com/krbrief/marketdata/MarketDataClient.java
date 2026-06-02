package com.krbrief.marketdata;

import java.time.LocalDate;
import java.util.Optional;

public interface MarketDataClient {
  /**
   * @return best-effort market brief for the given market date.
   */
  Optional<DailyMarketBrief> getDailyBrief(LocalDate date);
}
