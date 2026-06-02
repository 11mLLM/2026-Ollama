package com.krbrief.marketdata;

public record DailyLeaders(
    String topGainer,
    String topLoser,
    String source,
    String notes
) {}
