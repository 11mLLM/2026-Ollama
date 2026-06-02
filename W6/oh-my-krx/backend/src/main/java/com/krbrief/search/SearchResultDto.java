package com.krbrief.search;

import java.util.List;

public record SearchResultDto(
    String id,
    String type,
    String title,
    String code,
    String market,
    String rate,
    List<String> tags,
    String summary,
    String source,
    String stockCode,
    String stockName,
    String termId) {}
