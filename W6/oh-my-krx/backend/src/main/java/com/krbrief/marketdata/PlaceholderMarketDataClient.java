package com.krbrief.marketdata;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "marketdata.provider", havingValue = "placeholder", matchIfMissing = true)
public class PlaceholderMarketDataClient implements MarketDataClient {
  @Override
  public Optional<DailyMarketBrief> getDailyBrief(LocalDate date) {
    return Optional.of(
        new DailyMarketBrief(
            "TOP_GAINER_" + date,
            "TOP_LOSER_" + date,
            "MOST_MENTIONED_" + date,
            "KOSPI_PICK_" + date,
            "KOSDAQ_PICK_" + date,
            "placeholder",
            "TODO: implement real data fetch (gainers/losers/most mentioned/picks)"
        )
    );
  }
}
