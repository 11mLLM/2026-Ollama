package com.krbrief.learning;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class LearningAssistantService {
  private final LearningTermCatalog catalog;

  public LearningAssistantService(LearningTermCatalog catalog) {
    this.catalog = catalog;
  }

  public LearningAssistantResponse answer(LearningAssistantRequest request) {
    String question = clean(request == null ? null : request.question());
    String contextDate = clean(request == null ? null : request.contextDate());
    String termId = clean(request == null ? null : request.termId());
    List<LearningTermDto> matchedTerms = catalog.match(question, termId);

    String answer = buildAnswer(question, contextDate, matchedTerms);
    String confidence = matchedTerms.isEmpty() ? "low" : "medium";
    List<LearningAssistantResponse.Source> sources =
        List.of(
            new LearningAssistantResponse.Source(
                "앱 내부 초보자 용어 사전", "internal_glossary", "/api/learning/terms"));
    List<String> limitations =
        List.of(
            "현재 응답은 LLM이 아니라 내부 용어 사전 기반의 규칙형 학습 응답입니다.",
            "개별 종목의 매수, 매도, 가격, 시점 판단을 직접 지시하지 않습니다.",
            "실제 투자 판단 전에는 공시, 뉴스, 재무제표, 본인의 손실 가능성을 함께 확인해야 합니다.");

    return new LearningAssistantResponse(
        "rule_based_learning_preview",
        answer,
        confidence,
        matchedTerms,
        sources,
        limitations,
        nextQuestions(matchedTerms),
        "/api/ai/chat");
  }

  private String buildAnswer(String question, String contextDate, List<LearningTermDto> matchedTerms) {
    StringBuilder sb = new StringBuilder();
    if (question == null || question.isBlank()) {
      sb.append("질문을 입력하면 오늘 브리프를 읽는 데 필요한 용어부터 쉽게 풀어 설명합니다.");
    } else {
      sb.append("질문: ").append(question).append("\n\n");
    }

    if (contextDate != null && !contextDate.isBlank()) {
      sb.append("기준일: ").append(contextDate).append("\n\n");
    }

    sb.append("핵심 설명\n");
    for (LearningTermDto term : matchedTerms) {
      sb.append("- ").append(term.term()).append(": ").append(term.coreSummary()).append("\n");
    }

    sb.append("\n자세히 보면\n");
    for (LearningTermDto term : matchedTerms) {
      sb.append("- ").append(term.term()).append(": ").append(term.longExplanation()).append("\n");
    }

    sb.append("\n초보자 체크리스트\n");
    for (LearningTermDto term : matchedTerms) {
      sb.append("- ").append(term.term()).append(": ").append(term.beginnerCheck()).append("\n");
    }

    sb.append("\n차트에서 보는 법\n");
    for (LearningTermDto term : matchedTerms) {
      sb.append("- ").append(term.term()).append(": ").append(term.chartUsage()).append("\n");
    }

    sb.append("\n주의할 점\n");
    for (LearningTermDto term : matchedTerms) {
      sb.append("- ").append(term.term()).append(": ").append(term.commonMisunderstanding()).append("\n");
    }

    sb.append("\n시나리오 예시\n");
    for (LearningTermDto term : matchedTerms) {
      sb.append("- ").append(term.term()).append(": ").append(term.scenario()).append("\n");
    }

    sb.append("\n이 답변은 이해를 돕기 위한 설명이며, 특정 종목을 지금 사거나 팔라는 뜻이 아닙니다.");
    return sb.toString();
  }

  private List<String> nextQuestions(List<LearningTermDto> matchedTerms) {
    List<String> questions = new ArrayList<>();
    for (LearningTermDto term : matchedTerms) {
      for (String question : term.exampleQuestions()) {
        if (!questions.contains(question)) {
          questions.add(question);
        }
        if (questions.size() >= 4) {
          return questions;
        }
      }
    }
    return questions;
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }
}
