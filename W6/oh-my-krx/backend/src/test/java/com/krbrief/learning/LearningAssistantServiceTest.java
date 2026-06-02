package com.krbrief.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LearningAssistantServiceTest {

  @Test
  void answer_returnsAiReadyShapeWithoutTradingInstruction() {
    LearningAssistantService service = new LearningAssistantService(new LearningTermCatalog());

    LearningAssistantResponse response =
        service.answer(new LearningAssistantRequest("등락률이 높으면 좋은 종목인가요?", "2026-05-03", "price-change-rate"));

    assertEquals("rule_based_learning_preview", response.mode());
    assertEquals("/api/ai/chat", response.futureAiEndpoint());
    assertFalse(response.matchedTerms().isEmpty());
    assertTrue(response.answer().contains("차트에서 보는 법"));
    assertTrue(response.answer().contains("시나리오 예시"));
    assertTrue(response.matchedTerms().get(0).coreSummary().contains("등락률"));
    assertTrue(response.answer().contains("특정 종목을 지금 사거나 팔라는 뜻이 아닙니다"));
    assertTrue(response.limitations().stream().anyMatch(x -> x.contains("매수")));
  }
}
