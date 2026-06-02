package com.krbrief.summaries;

public record SummaryVerificationItem(
    String value, String sourceType, String sourceName, String directUrl, String note) {}
