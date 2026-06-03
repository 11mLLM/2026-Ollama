package com.krbrief.summaries;

import java.time.LocalDate;

public record BackfillResultDto(
    LocalDate date,
    String status,
    String reason,
    String sourceUsed,
    String confidence) {}
