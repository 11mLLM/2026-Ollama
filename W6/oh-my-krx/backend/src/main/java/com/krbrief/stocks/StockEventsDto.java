package com.krbrief.stocks;

import java.util.List;

public record StockEventsDto(
    String code,
    String name,
    String from,
    String to,
    List<StockEventDto> events) {

  public StockEventsDto withDerivedNarratives() {
    if (events == null) {
      return this;
    }
    return new StockEventsDto(code, name, from, to, events.stream().map(StockEventsDto::deriveNarrative).toList());
  }

  public record StockEventDto(
      String date,
      String type,
      String severity,
      Double priceChangeRate,
      Double volumeChangeRate,
      String title,
      String explanation,
      List<String> evidenceLinks,
      List<EvidenceSourceDto> evidenceSources,
      List<CausalScoreDto> causalScores,
      String sentimentForPrice,
      String sentiment,
      List<String> positiveReasons,
      List<String> negativeReasons,
      List<String> neutralReasons,
      List<String> goodNewsReasons,
      List<String> badNewsReasons,
      String whyItMatters,
      String oppositeInterpretation,
      List<String> oppositeSignals,
      String evidenceLevel,
      String sourceLimitations,
      List<String> whyItMoved,
      List<String> verificationChecklist) {

    public StockEventDto(
        String date,
        String type,
        String severity,
        Double priceChangeRate,
        Double volumeChangeRate,
        String title,
        String explanation,
        List<String> evidenceLinks,
        List<EvidenceSourceDto> evidenceSources,
        List<CausalScoreDto> causalScores) {
      this(
          date,
          type,
          severity,
          priceChangeRate,
          volumeChangeRate,
          title,
          explanation,
          evidenceLinks,
          evidenceSources,
          causalScores,
          null,
          null,
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          null,
          null,
          List.of(),
          null,
          null,
          List.of(),
          List.of());
    }
  }

  public record EvidenceSourceDto(
      String type,
      String title,
      String url,
      String description) {}

  public record CausalScoreDto(
      String sourceType,
      String label,
      Integer score,
      String confidence,
      String basis,
      String interpretation,
      Integer signalCount,
      List<String> matchedSignals,
      List<String> causalFactors,
      String causalDirection,
      String evidenceLevel,
      String signalSummary,
      List<String> signalOrigins,
      List<String> signalUrls) {}

  private static StockEventDto deriveNarrative(StockEventDto event) {
    if (event == null) {
      return null;
    }
    String sentiment = firstNonBlank(event.sentimentForPrice(), event.sentiment(), derivedSentiment(event));
    List<String> positiveReasons = nonEmpty(event.positiveReasons(), positiveReasons(event, sentiment));
    List<String> negativeReasons = nonEmpty(event.negativeReasons(), negativeReasons(event, sentiment));
    List<String> neutralReasons = nonEmpty(event.neutralReasons(), neutralReasons(event, sentiment));
    List<String> oppositeSignals = nonEmpty(event.oppositeSignals(), oppositeSignals(event, sentiment));
    String evidenceLevel = firstNonBlank(event.evidenceLevel(), bestEvidenceLevel(event));
    String limitations = firstNonBlank(event.sourceLimitations(), sourceLimitations(event));
    return new StockEventDto(
        event.date(),
        event.type(),
        event.severity(),
        event.priceChangeRate(),
        event.volumeChangeRate(),
        event.title(),
        event.explanation(),
        safeList(event.evidenceLinks()),
        safeList(event.evidenceSources()),
        safeList(event.causalScores()),
        sentiment,
        sentiment,
        positiveReasons,
        negativeReasons,
        neutralReasons,
        nonEmpty(event.goodNewsReasons(), positiveReasons),
        nonEmpty(event.badNewsReasons(), negativeReasons),
        firstNonBlank(event.whyItMatters(), whyItMatters(event, sentiment)),
        firstNonBlank(event.oppositeInterpretation(), oppositeInterpretation(event, sentiment)),
        oppositeSignals,
        evidenceLevel,
        limitations,
        nonEmpty(event.whyItMoved(), whyItMoved(event)),
        nonEmpty(event.verificationChecklist(), verificationChecklist(event)));
  }

  private static String derivedSentiment(StockEventDto event) {
    boolean positiveCausal = hasCausalDirection(event, "positive");
    boolean negativeCausal = hasCausalDirection(event, "negative");
    double price = number(event.priceChangeRate());
    double volume = number(event.volumeChangeRate());
    boolean positivePrice = price > 0 && volume >= 120;
    boolean negativePrice = price < 0 && volume >= 120;
    if ((positivePrice || positiveCausal) && (negativePrice || negativeCausal)) return "mixed";
    if (positivePrice || positiveCausal || price >= 5) return "positive";
    if (negativePrice || negativeCausal || price <= -5) return "negative";
    if (Math.abs(price) < 1.0 && volume < 120) return "neutral";
    return "mixed";
  }

  private static List<String> positiveReasons(StockEventDto event, String sentiment) {
    List<String> reasons = new java.util.ArrayList<>();
    if (number(event.priceChangeRate()) > 0) {
      reasons.add("가격 상승이 확인되어 시장 참여자가 긍정적으로 반응했을 후보 근거가 있습니다.");
    }
    if (number(event.volumeChangeRate()) >= 120) {
      reasons.add("거래량이 20일 평균보다 커져 단순 가격 움직임보다 참여 강도가 붙은 이벤트일 수 있습니다.");
    }
    if (hasCausalDirection(event, "positive")) {
      reasons.add("뉴스, 공시, 토론 텍스트 신호 중 긍정 방향 원인 후보가 포함되어 있습니다.");
    }
    if (reasons.isEmpty() && "positive".equals(sentiment)) {
      reasons.add("가격과 거래량 조합상 호재 후보로 볼 수 있지만 추가 출처 확인이 필요합니다.");
    }
    return reasons;
  }

  private static List<String> negativeReasons(StockEventDto event, String sentiment) {
    List<String> reasons = new java.util.ArrayList<>();
    if (number(event.priceChangeRate()) < 0) {
      reasons.add("가격 하락이 확인되어 단기 충격이나 추세 훼손 가능성을 점검해야 합니다.");
    }
    if (number(event.volumeChangeRate()) >= 180 && number(event.priceChangeRate()) <= 0) {
      reasons.add("하락일 거래량 급증은 매도 압력이 커졌을 후보 신호입니다.");
    }
    if (hasCausalDirection(event, "negative")) {
      reasons.add("뉴스, 공시, 토론 텍스트 신호 중 부정 방향 원인 후보가 포함되어 있습니다.");
    }
    if ("positive".equals(sentiment) || "mixed".equals(sentiment)) {
      reasons.add("급등 후 거래량이 줄거나 다음 거래일 종가가 밀리면 단기 차익 실현 신호로 바뀔 수 있습니다.");
    }
    return reasons;
  }

  private static List<String> neutralReasons(StockEventDto event, String sentiment) {
    if ("neutral".equals(sentiment)) {
      return List.of("가격 변화와 거래량 변화가 제한적이어서 방향을 확정하기 어렵습니다.");
    }
    return List.of("뉴스나 공시 본문 확인이 약하면 확정 원인이 아니라 후보 근거로만 봐야 합니다.");
  }

  private static String whyItMatters(StockEventDto event, String sentiment) {
    return "이 이벤트는 가격 변화율 "
        + text(event.priceChangeRate())
        + "%, 거래량 변화율 "
        + text(event.volumeChangeRate())
        + "%가 함께 나타난 날이라 "
        + sentiment
        + " 후보로 분류하고 다음 거래일 확인이 필요합니다.";
  }

  private static String oppositeInterpretation(StockEventDto event, String sentiment) {
    if ("positive".equals(sentiment)) {
      return "호재처럼 보여도 다음 거래일 거래량 없이 종가가 밀리면 실패 돌파나 단기 차익 실현으로 해석될 수 있습니다.";
    }
    if ("negative".equals(sentiment)) {
      return "악재처럼 보여도 장중 충격 뒤 종가 회복과 거래량 안정이 나오면 일시적 변동으로 해석될 수 있습니다.";
    }
    return "혼합 또는 중립 신호는 공시, 뉴스, 다음 거래일 가격 반응을 확인하기 전까지 확정 원인으로 쓰면 안 됩니다.";
  }

  private static List<String> oppositeSignals(StockEventDto event, String sentiment) {
    if ("positive".equals(sentiment)) {
      return List.of("거래량 없는 재상승", "다음 거래일 종가 약세", "호재 뉴스 뒤 저항선 돌파 실패");
    }
    if ("negative".equals(sentiment)) {
      return List.of("다음 거래일 종가 회복", "하락 거래량 진정", "공시 원문에서 일회성 비용으로 확인");
    }
    return List.of("출처 본문 부족", "가격과 거래량 방향 불일치", "다음 거래일 확인 전 판단 보류");
  }

  private static List<String> whyItMoved(StockEventDto event) {
    List<String> reasons = new java.util.ArrayList<>();
    reasons.add("가격 변화율 " + text(event.priceChangeRate()) + "%가 이벤트 조건에 걸렸습니다.");
    reasons.add("거래량 변화율 " + text(event.volumeChangeRate()) + "%로 참여 강도를 함께 봅니다.");
    if (!safeList(event.causalScores()).isEmpty()) {
      reasons.add("출처별 원인 점수는 후보 근거이며 확정 원인은 아닙니다.");
    }
    return reasons;
  }

  private static List<String> verificationChecklist(StockEventDto event) {
    return List.of("DART 공시 확인", "동일 날짜 뉴스 확인", "다음 거래일 거래량 유지 여부 확인", "지지선과 저항선 반응 확인");
  }

  private static String bestEvidenceLevel(StockEventDto event) {
    return safeList(event.causalScores()).stream()
        .map(CausalScoreDto::evidenceLevel)
        .filter(value -> value != null && !value.isBlank())
        .findFirst()
        .orElse(safeList(event.evidenceSources()).isEmpty() ? "low" : "candidate");
  }

  private static String sourceLimitations(StockEventDto event) {
    if (safeList(event.evidenceSources()).isEmpty() && safeList(event.causalScores()).isEmpty()) {
      return "뉴스/공시 본문 근거가 부족하므로 가격/거래량 기반 후보 신호로만 봐야 합니다.";
    }
    return "제공된 뉴스/공시/DART/가격 근거는 후보 근거이며 원문 확인 전 확정 원인으로 쓰면 안 됩니다.";
  }

  private static boolean hasCausalDirection(StockEventDto event, String direction) {
    return safeList(event.causalScores()).stream().anyMatch(score -> direction.equals(score.causalDirection()));
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) return value;
    }
    return "";
  }

  private static <T> List<T> nonEmpty(List<T> current, List<T> fallback) {
    return current == null || current.isEmpty() ? safeList(fallback) : current;
  }

  private static <T> List<T> safeList(List<T> values) {
    return values == null ? List.of() : values;
  }

  private static double number(Double value) {
    return value == null ? 0 : value;
  }

  private static String text(Double value) {
    return value == null ? "-" : String.format(java.util.Locale.ROOT, "%.2f", value);
  }
}
