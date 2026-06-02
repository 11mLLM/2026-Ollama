package com.krbrief.learning;

import java.util.List;

public record LearningTermDto(
    String id,
    String term,
    String category,
    String plainDefinition,
    String whyItMatters,
    String beginnerCheck,
    String caution,
    String coreSummary,
    String longExplanation,
    String chartUsage,
    String commonMisunderstanding,
    String scenario,
    List<String> relatedTerms,
    List<String> exampleQuestions,
    List<String> relatedQuestions) {}
