package com.krbrief.summaries;

import java.time.Instant;
import java.time.LocalDate;

public record SummaryStatsDto(long totalCount, LocalDate latestDate, Instant latestUpdatedAt) {}
