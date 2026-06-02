package com.krbrief.ai;

import java.time.Instant;

public record AiChatInteractionDto(
    Long id,
    String stockCode,
    String stockName,
    String question,
    String responseMode,
    String provider,
    String model,
    String confidence,
    String basisDate,
    String answerPreview,
    Instant createdAt) {
  static AiChatInteractionDto from(AiChatInteraction item) {
    return new AiChatInteractionDto(
        item.getId(),
        item.getStockCode(),
        item.getStockName(),
        item.getQuestion(),
        item.getResponseMode(),
        item.getProvider(),
        item.getModel(),
        item.getConfidence(),
        item.getBasisDate(),
        item.getAnswerPreview(),
        item.getCreatedAt());
  }
}
