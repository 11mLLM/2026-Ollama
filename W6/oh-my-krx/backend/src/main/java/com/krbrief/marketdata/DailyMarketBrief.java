package com.krbrief.marketdata;

import java.util.List;

/** Daily market brief data used to generate a DailySummary. */
public record DailyMarketBrief(
    String topGainer,
    String topLoser,
    String filteredTopGainer,
    String filteredTopLoser,
    String mostMentioned,
    String kospiPick,
    String kosdaqPick,
    String source,
    String notes,
    List<AnomalyCandidate> anomalies,
    String rankingWarning,
    String effectiveDate,
    List<LeaderEntry> topGainers,
    List<LeaderEntry> topLosers,
    List<MostMentionedEntry> mostMentionedTop,
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
    List<LeaderEntry> kospiTopGainers,
    List<LeaderEntry> kospiTopLosers,
    List<LeaderEntry> kosdaqTopGainers,
    List<LeaderEntry> kosdaqTopLosers) {

  public DailyMarketBrief(
      String topGainer,
      String topLoser,
      String mostMentioned,
      String kospiPick,
      String kosdaqPick,
      String source,
      String notes) {
    this(
        topGainer,
        topLoser,
        topGainer,
        topLoser,
        mostMentioned,
        kospiPick,
        kosdaqPick,
        source,
        notes,
        List.of(),
        "",
        null,
        List.of(),
        List.of(),
        List.of(),
        null, null, null, null,
        null, null, null, null,
        null, null, null, null,
        List.of(), List.of(), List.of(), List.of());
  }

  public DailyMarketBrief(
      String topGainer,
      String topLoser,
      String filteredTopGainer,
      String filteredTopLoser,
      String mostMentioned,
      String kospiPick,
      String kosdaqPick,
      String source,
      String notes,
      List<AnomalyCandidate> anomalies,
      String rankingWarning) {
    this(
        topGainer,
        topLoser,
        filteredTopGainer,
        filteredTopLoser,
        mostMentioned,
        kospiPick,
        kosdaqPick,
        source,
        notes,
        anomalies,
        rankingWarning,
        null,
        List.of(),
        List.of(),
        List.of(),
        null, null, null, null,
        null, null, null, null,
        null, null, null, null,
        List.of(), List.of(), List.of(), List.of());
  }

  public record AnomalyCandidate(
      String symbol,
      String name,
      double rate,
      List<String> flags,
      String oneLineReason) {}

  public record LeaderEntry(String code, String name, Double rate) {}

  public record MostMentionedEntry(String code, String name, Integer count) {}
}
