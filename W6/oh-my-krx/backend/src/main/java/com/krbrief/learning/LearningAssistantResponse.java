package com.krbrief.learning;

import java.util.List;

public record LearningAssistantResponse(
    String mode,
    String answer,
    String confidence,
    List<LearningTermDto> matchedTerms,
    List<Source> sources,
    List<String> limitations,
    List<String> nextQuestions,
    String futureAiEndpoint) {

  public record Source(String title, String type, String url) {}
}
