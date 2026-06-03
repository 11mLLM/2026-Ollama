package com.krbrief.summaries;

import com.krbrief.marketdata.DailyMarketBrief;
import java.util.ArrayList;
import java.util.List;

final class SummaryAnomalyCodec {
  private SummaryAnomalyCodec() {}

  static String encode(List<DailyMarketBrief.AnomalyCandidate> anomalies) {
    if (anomalies == null || anomalies.isEmpty()) {
      return "";
    }
    List<String> lines = new ArrayList<>();
    for (DailyMarketBrief.AnomalyCandidate anomaly : anomalies) {
      String flags = anomaly.flags() == null || anomaly.flags().isEmpty() ? "" : String.join(",", anomaly.flags());
      lines.add(
          sanitize(anomaly.symbol())
              + "|"
              + sanitize(anomaly.name())
              + "|"
              + anomaly.rate()
              + "|"
              + sanitize(flags)
              + "|"
              + sanitize(anomaly.oneLineReason()));
    }
    return String.join("\n", lines);
  }

  static List<SummaryDto.AnomalyDto> decode(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    List<SummaryDto.AnomalyDto> out = new ArrayList<>();
    String[] lines = text.split("\\R");
    for (String line : lines) {
      if (line == null || line.isBlank()) {
        continue;
      }
      String[] parts = line.split("\\|", -1);
      if (parts.length < 5) {
        continue;
      }
      double rate;
      try {
        rate = Double.parseDouble(parts[2]);
      } catch (Exception e) {
        rate = 0.0;
      }
      List<String> flags = parts[3].isBlank() ? List.of() : List.of(parts[3].split(","));
      out.add(new SummaryDto.AnomalyDto(parts[0], parts[1], rate, flags, parts[4]));
    }
    return out;
  }

  private static String sanitize(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\n", " ").replace("\r", " ").replace("|", "/").trim();
  }
}
