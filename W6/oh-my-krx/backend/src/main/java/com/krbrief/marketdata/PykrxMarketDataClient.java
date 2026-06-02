package com.krbrief.marketdata;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "marketdata.provider", havingValue = "pykrx")
public class PykrxMarketDataClient implements MarketDataClient {
  private final RestClient http;

  public PykrxMarketDataClient(@Value("${marketdata.baseUrl:http://marketdata:8000}") String baseUrl) {
    this.http = RestClient.builder().baseUrl(baseUrl).build();
  }

  @Override
  public Optional<DailyMarketBrief> getDailyBrief(LocalDate date) {
    try {
      PykrxLeadersResponse res =
          http
              .get()
              .uri(uriBuilder -> uriBuilder.path("/leaders").queryParam("date", date).build())
              .accept(MediaType.APPLICATION_JSON)
              .retrieve()
              .body(PykrxLeadersResponse.class);

      if (res == null) return Optional.empty();
      return Optional.of(
          new DailyMarketBrief(
              blankToDash(res.rawTopGainer(), res.topGainer()),
              blankToDash(res.rawTopLoser(), res.topLoser()),
              blankToDash(res.filteredTopGainer(), res.rawTopGainer(), res.topGainer()),
              blankToDash(res.filteredTopLoser(), res.rawTopLoser(), res.topLoser()),
              res.mostMentioned(),
              res.kospiPick(),
              res.kosdaqPick(),
              res.source(),
              res.notes(),
              res.anomalies() == null ? java.util.List.of() : res.anomalies(),
              nullToEmpty(res.rankingWarning()),
              nullToEmpty(res.effectiveDate()),
              res.topGainers() == null ? java.util.List.of() : res.topGainers(),
              res.topLosers() == null ? java.util.List.of() : res.topLosers(),
              res.mostMentionedTop() == null ? java.util.List.of() : res.mostMentionedTop(),
              res.kospiTopGainer(),
              res.kospiTopLoser(),
              res.kosdaqTopGainer(),
              res.kosdaqTopLoser(),
              res.kospiTopGainerCode(),
              res.kospiTopLoserCode(),
              res.kosdaqTopGainerCode(),
              res.kosdaqTopLoserCode(),
              res.kospiTopGainerRate(),
              res.kospiTopLoserRate(),
              res.kosdaqTopGainerRate(),
              res.kosdaqTopLoserRate(),
              res.kospiTopGainers() == null ? java.util.List.of() : res.kospiTopGainers(),
              res.kospiTopLosers() == null ? java.util.List.of() : res.kospiTopLosers(),
              res.kosdaqTopGainers() == null ? java.util.List.of() : res.kosdaqTopGainers(),
              res.kosdaqTopLosers() == null ? java.util.List.of() : res.kosdaqTopLosers()));
    } catch (Exception e) {
      return Optional.of(
          new DailyMarketBrief(
              "TOP_GAINER_" + date,
              "TOP_LOSER_" + date,
              "TOP_GAINER_" + date,
              "TOP_LOSER_" + date,
              "-",
              "-",
              "-",
              "pykrx_error",
              "Failed to fetch leaders from marketdata service: " + e.getMessage(),
              java.util.List.of(),
              ""));
    }
  }

  private static String blankToDash(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return "-";
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  public record PykrxLeadersResponse(
      String date,
      String effectiveDate,
      String topGainer,
      String topLoser,
      String rawTopGainer,
      String rawTopLoser,
      String filteredTopGainer,
      String filteredTopLoser,
      String mostMentioned,
      String kospiPick,
      String kosdaqPick,
      String topGainerCode,
      String topLoserCode,
      String rawTopGainerCode,
      String rawTopLoserCode,
      String filteredTopGainerCode,
      String filteredTopLoserCode,
      String mostMentionedCode,
      String kospiPickCode,
      String kosdaqPickCode,
      java.util.List<DailyMarketBrief.LeaderEntry> topGainers,
      java.util.List<DailyMarketBrief.LeaderEntry> topLosers,
      java.util.List<DailyMarketBrief.MostMentionedEntry> mostMentionedTop,
      java.util.List<DailyMarketBrief.AnomalyCandidate> anomalies,
      String rankingWarning,
      String source,
      String notes,
      String kospiTopGainer,
      String kospiTopLoser,
      String kosdaqTopGainer,
      String kosdaqTopLoser,
      String kospiTopGainerCode,
      String kospiTopLoserCode,
      String kosdaqTopGainerCode,
      String kosdaqTopLoserCode,
      Double kospiTopGainerRate,
      Double kospiTopLoserRate,
      Double kosdaqTopGainerRate,
      Double kosdaqTopLoserRate,
      java.util.List<DailyMarketBrief.LeaderEntry> kospiTopGainers,
      java.util.List<DailyMarketBrief.LeaderEntry> kospiTopLosers,
      java.util.List<DailyMarketBrief.LeaderEntry> kosdaqTopGainers,
      java.util.List<DailyMarketBrief.LeaderEntry> kosdaqTopLosers) {}
}
