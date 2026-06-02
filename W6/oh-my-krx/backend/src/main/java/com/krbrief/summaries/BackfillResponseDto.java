package com.krbrief.summaries;

import java.time.LocalDate;
import java.util.List;

public record BackfillResponseDto(
    LocalDate from,
    LocalDate to,
    int totalDays,
    int successCount,
    int lowConfidenceCount,
    int failCount,
    List<BackfillResultDto> results) {}
