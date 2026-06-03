package com.krbrief.summaries;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class SummaryLeaderExplanations {
  private static final DateTimeFormatter DART_DATE = DateTimeFormatter.BASIC_ISO_DATE;

  private SummaryLeaderExplanations() {}

  static SummaryDto.LeaderExplanations build(
      LocalDate date,
      String topGainer,
      String topLoser,
      List<SummaryDto.AnomalyDto> anomalies,
      String rawNotes,
      SummaryVerificationLinks verification) {
    return new SummaryDto.LeaderExplanations(
        buildOne("topGainer", date, topGainer, anomalies, rawNotes, verification),
        buildOne("topLoser", date, topLoser, anomalies, rawNotes, verification));
  }

  private static SummaryDto.LeaderExplanation buildOne(
      String leaderKey,
      LocalDate date,
      String leaderName,
      List<SummaryDto.AnomalyDto> anomalies,
      String rawNotes,
      SummaryVerificationLinks verification) {
    String normalizedName = normalizeName(leaderName);
    List<String> flags = flagsForLeader(normalizedName, anomalies);
    boolean caution = hasContinuitySignal(flags);

    LinkedHashSet<String> links = new LinkedHashSet<>();
    String naverDirectLink = verificationItemLink(verification, leaderKey);
    addIfPresent(links, naverDirectLink);
    addIfPresent(links, explicitEvidenceLink(rawNotes, leaderKey));
    addIfPresent(links, buildDartSearchLink(normalizedName, date));

    String level;
    String summary;
    String particle = getSubjectParticle(normalizedName);
    if (normalizedName.equals("-") || normalizedName.isBlank()) {
      level = "info";
      summary = "해당 날짜의 데이터가 없습니다.";
    } else if (!naverDirectLink.isBlank()) {
      level = "confirmed";
      summary =
          normalizedName
              + particle + " 네이버 증권 페이지에서 확인할 수 있습니다."
              + " (감지 신호: "
              + signalText(flags)
              + ")";
    } else if (!explicitEvidenceLink(rawNotes, leaderKey).isBlank()) {
      level = "confirmed";
      summary =
          normalizedName
              + particle + " 관련 근거 링크가 확인되었습니다."
              + " (감지 신호: "
              + signalText(flags)
              + ")";
    } else if (caution) {
      level = "caution";
      summary =
          normalizedName
              + particle + " 값이 크게 튀는 신호가 있어 해석에 주의가 필요합니다."
              + " (감지 신호: "
              + signalText(flags)
              + ")";
    } else {
      level = "info";
      summary = normalizedName + particle + " 특별한 이상 신호가 없어 일반 순위로 표시했습니다.";
    }

    return new SummaryDto.LeaderExplanation(level, summary, new ArrayList<>(links));
  }

  private static String verificationItemLink(SummaryVerificationLinks verification, String leaderKey) {
    if (verification == null) return "";
    if ("topGainer".equals(leaderKey)) {
      return nullToEmpty(verification.topGainerDateSearch());
    }
    if ("topLoser".equals(leaderKey)) {
      return nullToEmpty(verification.topLoserDateSearch());
    }
    return "";
  }

  private static String explicitEvidenceLink(String rawNotes, String leaderKey) {
    return extractValue(rawNotes, "confirmedEvidence." + leaderKey);
  }

  private static String extractValue(String text, String key) {
    if (text == null || text.isBlank()) return "";
    String marker = key + "=";
    int idx = text.indexOf(marker);
    if (idx < 0) return "";
    int start = idx + marker.length();
    int end = text.indexOf('\n', start);
    if (end < 0) end = text.length();
    String value = text.substring(start, end).trim();
    return "-".equals(value) ? "" : value;
  }

  private static List<String> flagsForLeader(String leaderName, List<SummaryDto.AnomalyDto> anomalies) {
    if (leaderName.isBlank() || anomalies == null) return List.of();
    for (SummaryDto.AnomalyDto anomaly : anomalies) {
      if (anomaly == null) continue;
      if (normalizeName(anomaly.name()).equals(leaderName)) {
        return anomaly.flags() == null ? List.of() : anomaly.flags();
      }
    }
    return List.of();
  }

  private static boolean hasContinuitySignal(List<String> flags) {
    if (flags == null || flags.isEmpty()) return false;
    return flags.contains("zero_volume_streak")
        || flags.contains("huge_gap")
        || flags.contains("suspicious_zero_volume_jump")
        || flags.contains("prior_close_zero");
  }

  private static String signalText(List<String> flags) {
    if (flags == null || flags.isEmpty()) {
      return "없음";
    }
    List<String> labels = new ArrayList<>();
    if (flags.contains("zero_volume_streak")) {
      labels.add("거래량 0 연속");
    }
    if (flags.contains("huge_gap")) {
      labels.add("대규모 수익률 갭");
    }
    if (flags.contains("suspicious_zero_volume_jump")) {
      labels.add("무거래 급등락");
    }
    if (flags.contains("prior_close_zero")) {
      labels.add("전일 종가 0 기준");
    }
    if (labels.isEmpty()) {
      return "없음";
    }
    return String.join(", ", labels);
  }

  private static String buildDartSearchLink(String companyName, LocalDate date) {
    if (companyName.isBlank() || "-".equals(companyName)) {
      return "";
    }
    LocalDate base = date == null ? LocalDate.now() : date;
    String start = base.minusDays(7).format(DART_DATE);
    String end = base.plusDays(7).format(DART_DATE);
    return "https://englishdart.fss.or.kr/dsbb001/main.do?auto=true&corporationType=&textCrpCik=&textCrpNm="
        + URLEncoder.encode(companyName, StandardCharsets.UTF_8)
        + "&startDate="
        + start
        + "&endDate="
        + end;
  }

  private static String normalizeName(String value) {
    if (value == null || value.isBlank()) {
      return "-";
    }
    return value.trim();
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static void addIfPresent(LinkedHashSet<String> links, String value) {
    if (value == null || value.isBlank()) return;
    links.add(value);
  }

  /**
   * Returns the appropriate Korean subject particle ("은" or "는") based on whether
   * the last character of the name has a jongseong (final consonant/batchim).
   *
   * Rule: 종성(받침)이 있으면 "은", 없으면 "는"
   * Examples:
   *   - "삼성전자" (ends with "자", no jongseong) → "는" → "삼성전자는"
   *   - "LG에너지솔루션" (ends with "션", has jongseong ㄴ) → "은" → "LG에너지솔루션은"
   */
  private static String getSubjectParticle(String name) {
    if (name == null || name.isEmpty()) {
      return "은"; // 기본값
    }
    int lastIdx = name.length() - 1;
    char lastChar = name.charAt(lastIdx);

    // 한글 범위 확인 (AC00-D7AF: 완성형 한글)
    if (lastChar >= 0xAC00 && lastChar <= 0xD7AF) {
      // 종성 인덱스 계산: (char - 0xAC00) % 28
      // 0이면 종성 없음, 1-27이면 종성 있음
      int jongseongIdx = (lastChar - 0xAC00) % 28;
      return jongseongIdx == 0 ? "는" : "은";
    }

    // 한글이 아닌 경우 (영어, 숫자 등) 기본값 "은" 사용
    return "은";
  }
}
