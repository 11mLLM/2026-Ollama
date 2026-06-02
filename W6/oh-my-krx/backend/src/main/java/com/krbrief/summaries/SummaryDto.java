package com.krbrief.summaries;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SummaryDto(
    LocalDate date,
    Boolean marketClosed,
    String marketClosedReason,
    java.util.List<String> marketClosedEvidenceLinks,
    String topGainer,
    String topLoser,
    String rawTopGainer,
    String rawTopLoser,
    String filteredTopGainer,
    String filteredTopLoser,
    String rankingWarning,
    List<AnomalyDto> anomalies,
    String mostMentioned,
    String kospiPick,
    String kosdaqPick,
    String rawNotes,
    Instant createdAt,
    Instant updatedAt,
    Instant archivedAt,
    SummaryVerificationLinks verification,
    LeaderExplanations leaderExplanations,
    AfterMarketAiReport afterMarketAiReport,
    String content,
    Instant generatedAt,
    String effectiveDate,
    List<LeaderEntryDto> topGainers,
    List<LeaderEntryDto> topLosers,
    List<MostMentionedEntryDto> mostMentionedTop,
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
    List<LeaderEntryDto> kospiTopGainers,
    List<LeaderEntryDto> kospiTopLosers,
    List<LeaderEntryDto> kosdaqTopGainers,
    List<LeaderEntryDto> kosdaqTopLosers) {

  private static final ObjectMapper JSON = new ObjectMapper();

  public static SummaryDto from(DailySummary s) {
    String rawNotes = s.getRawNotes();
    boolean isMarketClosed = rawNotes != null && rawNotes.contains("Source: market_closed");
    String marketClosedReason = null;
    java.util.List<String> marketClosedEvidenceLinks = java.util.List.of();
    if (isMarketClosed && rawNotes != null) {
      int idx = rawNotes.indexOf("휴장일:");
      if (idx >= 0) {
        int end = rawNotes.indexOf('\n', idx);
        marketClosedReason = end < 0 ? rawNotes.substring(idx) : rawNotes.substring(idx, end);
      }

      int linkIdx = rawNotes.indexOf("휴장 근거:");
      if (linkIdx >= 0) {
        int end = rawNotes.indexOf('\n', linkIdx);
        String line = end < 0 ? rawNotes.substring(linkIdx) : rawNotes.substring(linkIdx, end);
        int colon = line.indexOf(':');
        if (colon >= 0 && colon + 1 < line.length()) {
          String rest = line.substring(colon + 1).trim();
          if (!rest.isBlank()) {
            String[] parts = rest.split("\\s*\\|\\s*");
            java.util.List<String> links = new java.util.ArrayList<>();
            for (String p : parts) {
              String u = p.trim();
              if (!u.isBlank()) {
                links.add(u);
              }
            }
            marketClosedEvidenceLinks = links;
          }
        }
      }
    }

    String topGainer = publicSummaryValue(s.getTopGainer());
    String topLoser = publicSummaryValue(s.getTopLoser());
    String filteredTopGainer = publicSummaryValue(firstNonBlank(s.getFilteredTopGainer(), topGainer));
    String filteredTopLoser = publicSummaryValue(firstNonBlank(s.getFilteredTopLoser(), topLoser));
    String mostMentioned = publicSummaryValue(s.getMostMentioned());
    String kospiPick = publicSummaryValue(s.getKospiPick());
    String kosdaqPick = publicSummaryValue(s.getKosdaqPick());

    SummaryVerificationLinks verification =
        SummaryVerificationLinks.from(
            s.getDate(),
            s.getDate() == null ? "" : "/api/summaries/" + s.getDate() + "/verification/krx",
            topGainer,
            topLoser,
            mostMentioned,
            kospiPick,
            kosdaqPick,
            rawNotes);
    List<AnomalyDto> anomalies = SummaryAnomalyCodec.decode(s.getAnomaliesText());

    List<LeaderEntryDto> topGainers = deserializeLeaderEntries(s.getTopGainersJson());
    List<LeaderEntryDto> topLosers = deserializeLeaderEntries(s.getTopLosersJson());
    List<MostMentionedEntryDto> mostMentionedTop = deserializeMostMentionedEntries(s.getMostMentionedTopJson());
    List<LeaderEntryDto> kospiTopGainers = deserializeLeaderEntries(s.getKospiTopGainersJson());
    List<LeaderEntryDto> kospiTopLosers = deserializeLeaderEntries(s.getKospiTopLosersJson());
    List<LeaderEntryDto> kosdaqTopGainers = deserializeLeaderEntries(s.getKosdaqTopGainersJson());
    List<LeaderEntryDto> kosdaqTopLosers = deserializeLeaderEntries(s.getKosdaqTopLosersJson());

    return new SummaryDto(
        s.getDate(),
        isMarketClosed,
        marketClosedReason,
        marketClosedEvidenceLinks,
        topGainer,
        topLoser,
        topGainer,
        topLoser,
        filteredTopGainer,
        filteredTopLoser,
        s.getRankingWarning(),
        anomalies,
        mostMentioned,
        kospiPick,
        kosdaqPick,
        rawNotes,
        s.getCreatedAt(),
        s.getUpdatedAt(),
        s.getArchivedAt(),
        verification,
        SummaryLeaderExplanations.build(
            s.getDate(), topGainer, topLoser, anomalies, rawNotes, verification),
        buildAfterMarketAiReport(s, isMarketClosed, topGainers, topLosers, mostMentionedTop, kospiTopGainers, kosdaqTopGainers),
        s.renderContent(),
        s.getUpdatedAt(),
        s.getEffectiveDate(),
        topGainers,
        topLosers,
        mostMentionedTop,
        s.getKospiTopGainer(),
        s.getKospiTopLoser(),
        s.getKosdaqTopGainer(),
        s.getKosdaqTopLoser(),
        s.getKospiTopGainerCode(),
        s.getKospiTopLoserCode(),
        s.getKosdaqTopGainerCode(),
        s.getKosdaqTopLoserCode(),
        s.getKospiTopGainerRate(),
        s.getKospiTopLoserRate(),
        s.getKosdaqTopGainerRate(),
        s.getKosdaqTopLoserRate(),
        kospiTopGainers,
        kospiTopLosers,
        kosdaqTopGainers,
        kosdaqTopLosers);
  }

  private static AfterMarketAiReport buildAfterMarketAiReport(
      DailySummary s,
      boolean marketClosed,
      List<LeaderEntryDto> topGainers,
      List<LeaderEntryDto> topLosers,
      List<MostMentionedEntryDto> mostMentionedTop,
      List<LeaderEntryDto> kospiTopGainers,
      List<LeaderEntryDto> kosdaqTopGainers) {
    if (marketClosed) {
      return new AfterMarketAiReport(
          "rule_based_ai_ready",
          "매일 장후 시장 요약 리포트",
          "휴장",
          List.of("해당 날짜는 휴장일이라 상승/하락 랭킹을 만들지 않습니다."),
          "장후 리포트는 다음 개장일 데이터가 들어오면 다시 생성됩니다.",
          List.of("다음 개장일 거래대금", "주요 뉴스 공시", "전 거래일 대비 갭 발생 여부"),
          List.of("휴장일에는 가격 데이터가 없어 로컬 LLM 코멘트도 제한됩니다."));
    }

    String mood = marketMood(s);
    List<String> keyPoints = new java.util.ArrayList<>();
    addPoint(keyPoints, "시장 최대 상승 후보", leaderText(first(topGainers), s.getTopGainer()));
    addPoint(keyPoints, "시장 최대 하락 후보", leaderText(first(topLosers), s.getTopLoser()));
    addPoint(keyPoints, "KOSPI 상승 1위", leaderText(first(kospiTopGainers), s.getKospiTopGainer()));
    addPoint(keyPoints, "KOSDAQ 상승 1위", leaderText(first(kosdaqTopGainers), s.getKosdaqTopGainer()));
    if (!mostMentionedTop.isEmpty()) {
      MostMentionedEntryDto top = mostMentionedTop.get(0);
      keyPoints.add("토론 관심은 " + safe(top.name(), s.getMostMentioned()) + "에 집중되었습니다.");
    } else if (s.getMostMentioned() != null && !s.getMostMentioned().isBlank()) {
      keyPoints.add("토론 관심은 " + s.getMostMentioned() + "에 집중되었습니다.");
    }
    if (keyPoints.isEmpty()) {
      keyPoints.add("저장 브리프의 대표 지표가 부족해 차트와 뉴스 확인이 필요합니다.");
    }

    return new AfterMarketAiReport(
        "rule_based_ai_ready",
        "매일 장후 시장 요약 리포트",
        mood,
        keyPoints,
        afterMarketComment(s, mood),
        List.of(
            "다음 거래일 시초가가 장후 분위기를 따라가는지 확인",
            "상승 1위와 하락 1위의 뉴스 원문 확인",
            "관심 종목의 20일선과 거래량 유지 여부 확인"),
        List.of("이 항목은 저장 브리프 기반 규칙형 코멘트이며, Ollama 모델 설정 시 로컬 LLM이 더 자연스럽게 보강합니다."));
  }

  private static LeaderEntryDto first(List<LeaderEntryDto> rows) {
    return rows == null || rows.isEmpty() ? null : rows.get(0);
  }

  private static void addPoint(List<String> out, String label, String value) {
    if (value == null || value.isBlank() || "-".equals(value)) return;
    out.add(label + "는 " + value + "입니다.");
  }

  private static String leaderText(LeaderEntryDto entry, String fallback) {
    if (entry == null) return fallback == null ? "" : fallback;
    String rate = entry.rate() == null ? "" : " " + String.format("%.2f%%", entry.rate());
    return safe(entry.name(), fallback) + rate;
  }

  private static String marketMood(DailySummary s) {
    double up = maxAbs(s.getKospiTopGainerRate(), s.getKosdaqTopGainerRate());
    double down = maxAbs(s.getKospiTopLoserRate(), s.getKosdaqTopLoserRate());
    if (down >= 10 && down >= up) return "위험 관리 우선";
    if (up >= 10) return "관심 확대";
    return "선별 접근";
  }

  private static double maxAbs(Double a, Double b) {
    return Math.max(Math.abs(a == null ? 0.0 : a), Math.abs(b == null ? 0.0 : b));
  }

  private static String afterMarketComment(DailySummary s, String mood) {
    String top = safe(s.getKospiTopGainer(), s.getKosdaqTopGainer(), s.getTopGainer());
    String loser = safe(s.getKospiTopLoser(), s.getKosdaqTopLoser(), s.getTopLoser());
    if ("위험 관리 우선".equals(mood)) {
      return "장후 기준 하락 리스크가 더 눈에 띕니다. " + loser + " 같은 약세 후보의 원인을 먼저 확인하고, 보유 종목은 지지선 이탈 여부를 확인해야 합니다.";
    }
    if ("관심 확대".equals(mood)) {
      return "장후 기준 강한 상승 후보가 있습니다. " + top + "처럼 급등한 종목은 바로 따라가기보다 다음 거래일 거래량 유지와 눌림 여부를 확인해야 합니다.";
    }
    return "장후 기준 시장 방향이 한쪽으로 강하게 쏠리지는 않았습니다. " + top + "과 " + loser + "의 원인을 비교하면서 선별 접근이 필요합니다.";
  }

  private static String safe(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank() && !"-".equals(value)) return value;
    }
    return "확인 필요";
  }

  private static List<LeaderEntryDto> deserializeLeaderEntries(String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      return JSON.readValue(json, new TypeReference<List<LeaderEntryDto>>() {});
    } catch (JsonProcessingException e) {
      return List.of();
    }
  }

  private static List<MostMentionedEntryDto> deserializeMostMentionedEntries(String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      return JSON.readValue(json, new TypeReference<List<MostMentionedEntryDto>>() {});
    } catch (JsonProcessingException e) {
      return List.of();
    }
  }

  private static String firstNonBlank(String primary, String fallback) {
    if (primary != null && !primary.isBlank()) {
      return primary;
    }
    return fallback == null ? "" : fallback;
  }

  private static boolean isInternalPlaceholder(String value) {
    if (value == null) return true;
    String text = value.trim();
    return text.isBlank()
        || text.equals("-")
        || text.matches("(?i)^(TOP_GAINER|TOP_LOSER|MOST_MENTIONED|KOSPI_PICK|KOSDAQ_PICK)_\\d{4}-\\d{2}-\\d{2}$");
  }

  private static String publicSummaryValue(String value) {
    return isInternalPlaceholder(value) ? "-" : value;
  }

  public record AnomalyDto(
      String symbol,
      String name,
      double rate,
      List<String> flags,
      String oneLineReason) {}

  public record LeaderExplanations(LeaderExplanation topGainer, LeaderExplanation topLoser) {}

  public record LeaderExplanation(String level, String summary, List<String> evidenceLinks) {}

  public record AfterMarketAiReport(
      String mode,
      String title,
      String mood,
      List<String> keyPoints,
      String llmComment,
      List<String> nextWatch,
      List<String> limitations) {}

  public record LeaderEntryDto(String code, String name, Double rate) {}

  public record MostMentionedEntryDto(String code, String name, Integer count) {}
}
