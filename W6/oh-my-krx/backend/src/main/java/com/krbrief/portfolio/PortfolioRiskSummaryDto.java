package com.krbrief.portfolio;

import java.util.List;

public record PortfolioRiskSummaryDto(
    Double totalWeight,
    String maxWeightStock,
    Double maxWeight,
    String concentration,
    String volatility,
    List<String> nextChecklist) {}
