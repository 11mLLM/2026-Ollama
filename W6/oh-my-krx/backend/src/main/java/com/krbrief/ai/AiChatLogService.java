package com.krbrief.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krbrief.portfolio.PortfolioItem;
import com.krbrief.portfolio.PortfolioItemRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiChatLogService {
  private final AiChatInteractionRepository repository;
  private final PortfolioItemRepository portfolioItemRepository;
  private final ObjectMapper objectMapper;

  public AiChatLogService(
      AiChatInteractionRepository repository,
      PortfolioItemRepository portfolioItemRepository,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.portfolioItemRepository = portfolioItemRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public Map<String, Object> save(Map<String, Object> request, Map<String, Object> response) {
    try {
      Map<String, Object> context = map(request.get("context"));
      Map<String, Object> retrieval = map(response.get("retrieval"));
      Map<String, Object> llm = map(retrieval.get("llm"));
      String answer = text(response.get("answer"));
      AiChatInteraction saved =
          repository.save(
              new AiChatInteraction(
                  firstNonBlank(text(request.get("stockCode")), text(context.get("stockCode")), text(context.get("code"))),
                  firstNonBlank(text(request.get("stockName")), text(context.get("stockName"))),
                  text(request.get("question")),
                  text(response.get("mode")),
                  text(llm.get("provider")),
                  text(llm.get("model")),
                  text(response.get("confidence")),
                  text(response.get("basisDate")),
                  preview(answer),
                  answer,
                  json(response.get("sources")),
                  json(response.get("limitations")),
                  json(response)));
      return Map.of(
          "saved",
          true,
          "id",
          saved.getId(),
          "table",
          "ai_chat_interactions",
          "note",
          "AI 응답은 생성 직후 DB에 감사 로그로 저장됩니다. 기업 선택 자체와 차트 데이터는 저장하지 않습니다.");
    } catch (RuntimeException e) {
      return Map.of(
          "saved",
          false,
          "table",
          "ai_chat_interactions",
          "note",
          "AI 응답 저장에 실패했지만 화면 응답은 유지했습니다.");
    }
  }

  @Transactional(readOnly = true)
  public List<AiChatInteractionDto> history(String stockCode) {
    String safeCode = text(stockCode);
    if (safeCode.matches("^\\d{6}$")) {
      return repository.findTop20ByStockCodeOrderByCreatedAtDesc(safeCode).stream()
          .map(AiChatInteractionDto::from)
          .toList();
    }
    return repository.findTop20ByOrderByCreatedAtDesc().stream().map(AiChatInteractionDto::from).toList();
  }

  @Transactional(readOnly = true)
  public Optional<Map<String, Object>> latestOllamaInsight(String stockCode) {
    String safeCode = text(stockCode);
    if (!safeCode.matches("^\\d{6}$")) {
      return Optional.empty();
    }
    return repository
        .findFirstByStockCodeAndResponseModeInOrderByCreatedAtDesc(
            safeCode, List.of("ollama_llm", "ollama_fallback_rule_based"))
        .flatMap(this::storedOllamaResponse);
  }

  private Optional<Map<String, Object>> storedOllamaResponse(AiChatInteraction item) {
    String responseJson = text(item.getResponseJson());
    if (responseJson.isBlank()) {
      return Optional.empty();
    }
    Map<String, Object> stored = readJson(responseJson);
    if (stored.isEmpty()) {
      return Optional.empty();
    }
    LinkedHashMap<String, Object> response = new LinkedHashMap<>(stored);
    LinkedHashMap<String, Object> storage = new LinkedHashMap<>();
    storage.put("saved", true);
    storage.put("cached", true);
    storage.put("id", item.getId());
    storage.put("table", "ai_chat_interactions");
    storage.put("stockCode", item.getStockCode());
    storage.put("responseMode", item.getResponseMode());
    storage.put("provider", item.getProvider());
    storage.put("model", item.getModel());
    storage.put("basisDate", item.getBasisDate());
    storage.put("createdAt", item.getCreatedAt());
    storage.put("note", "저장된 Ollama 상담 응답을 먼저 재사용했습니다. 새 계산 결과가 도착하면 화면이 갱신됩니다.");
    reconcilePersonalContext(response, item.getStockCode(), storage);
    response.put("storage", storage);
    return Optional.of(response);
  }

  private void reconcilePersonalContext(
      LinkedHashMap<String, Object> response, String stockCode, LinkedHashMap<String, Object> storage) {
    LinkedHashMap<String, Object> stockAdvice = map(response.get("stockAdvice"));
    if (stockAdvice.isEmpty()) {
      return;
    }
    LinkedHashMap<String, Object> personalRisk = map(stockAdvice.get("personalRisk"));
    String status = text(personalRisk.get("status"));
    if (status.isBlank() || "not_saved".equals(status)) {
      return;
    }

    Optional<PortfolioItem> currentItem = portfolioItemRepository.findById(text(stockCode));
    if (currentItem.isPresent() && averagePriceMatches(personalRisk, currentItem.get())) {
      return;
    }

    LinkedHashMap<String, Object> currentPersonalRisk = new LinkedHashMap<>();
    currentPersonalRisk.put("status", "not_saved");
    currentPersonalRisk.put("statusLabel", "샌드박스 미저장");
    currentPersonalRisk.put(
        "summary",
        "현재 포트폴리오 샌드박스의 평균단가와 저장된 AI 답변의 개인 기준이 달라 개인 조건 판단은 제외했습니다.");
    currentPersonalRisk.put("actionLine", "평균단가를 저장한 뒤 AI 새로 계산을 누르면 현재 조건으로 다시 반영합니다.");
    currentPersonalRisk.put("checklist", List.of("평균단가를 저장합니다.", "AI 새로 계산을 누릅니다.", "내 기준 손익 문장을 다시 확인합니다."));

    LinkedHashMap<String, Object> personalAdjustment = map(stockAdvice.get("personalAdjustment"));
    personalAdjustment.put("applied", false);
    personalAdjustment.put("contextApplied", false);
    personalAdjustment.put("sourceDecision", stockAdvice.getOrDefault("decision", ""));
    personalAdjustment.put("finalDecision", stockAdvice.getOrDefault("decision", ""));
    personalAdjustment.put("statusLabel", "개인 조건 제외");
    personalAdjustment.put(
        "summary",
        "저장된 AI 답변의 평균단가 기준이 현재 샌드박스 상태와 맞지 않아 개인화 판단을 숨겼습니다.");
    personalAdjustment.put("actionLine", "새 계산을 실행하면 현재 저장값 기준으로 다시 판단합니다.");
    personalAdjustment.put("tone", "neutral");

    stockAdvice.put("personalRisk", currentPersonalRisk);
    stockAdvice.put("personalAdjustment", personalAdjustment);
    stockAdvice.put(
        "summary",
        "저장된 Ollama 답변입니다. 현재 샌드박스 상태와 맞지 않는 개인 평균단가 판단은 제외했습니다. 새 계산을 누르면 최신 조건으로 다시 계산합니다.");
    response.put("stockAdvice", stockAdvice);

    LinkedHashMap<String, Object> portfolioGuidance = map(response.get("portfolioGuidance"));
    if (!portfolioGuidance.isEmpty()) {
      portfolioGuidance.put("summary", currentPersonalRisk.get("summary"));
      portfolioGuidance.put("positionDiagnostics", currentPersonalRisk);
      response.put("portfolioGuidance", portfolioGuidance);
    }

    storage.put("personalContextStale", true);
    storage.put(
        "note",
        "저장된 Ollama 상담 응답을 먼저 재사용했습니다. 다만 현재 포트폴리오 샌드박스와 맞지 않는 개인 평균단가 판단은 제외했습니다.");
  }

  private boolean averagePriceMatches(Map<String, Object> personalRisk, PortfolioItem currentItem) {
    Double currentAveragePrice = currentItem.getAveragePrice();
    double storedAveragePrice = number(personalRisk.get("averagePrice"));
    if (currentAveragePrice == null) {
      return !Double.isFinite(storedAveragePrice);
    }
    if (!Double.isFinite(storedAveragePrice)) {
      return false;
    }
    return Math.abs(currentAveragePrice - storedAveragePrice) <= 1.0d;
  }

  @SuppressWarnings("unchecked")
  private LinkedHashMap<String, Object> map(Object value) {
    if (value instanceof Map<?, ?> raw) {
      LinkedHashMap<String, Object> out = new LinkedHashMap<>();
      raw.forEach((key, item) -> out.put(String.valueOf(key), item));
      return out;
    }
    return new LinkedHashMap<>();
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? List.of() : value);
    } catch (JsonProcessingException e) {
      return "[]";
    }
  }

  private Map<String, Object> readJson(String value) {
    try {
      return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {});
    } catch (JsonProcessingException e) {
      return Map.of();
    }
  }

  private String preview(String answer) {
    String compact = answer.replaceAll("\\s+", " ").trim();
    return compact.length() <= 500 ? compact : compact.substring(0, 497) + "...";
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) return value;
    }
    return "";
  }

  private double number(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    try {
      return Double.parseDouble(text(value));
    } catch (NumberFormatException e) {
      return Double.NaN;
    }
  }

  private String text(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }
}
