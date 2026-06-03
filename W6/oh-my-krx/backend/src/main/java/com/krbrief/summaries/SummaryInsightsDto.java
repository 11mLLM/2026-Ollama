package com.krbrief.summaries;

import java.time.LocalDate;

public record SummaryInsightsDto(
    LocalDate from,
    LocalDate to,
    long totalDays,
    long generatedDays,
    long missingDays,
    String topMostMentioned,
    long topMostMentionedCount) {}
