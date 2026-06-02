package com.krbrief.portfolio;

import java.time.Instant;
import java.util.List;

public record PortfolioResponse(
    List<PortfolioItemDto> items,
    PortfolioRiskSummaryDto summary,
    String source,
    Instant updatedAt) {}
