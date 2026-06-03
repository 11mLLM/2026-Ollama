package com.krbrief.search;

import com.krbrief.learning.LearningTermCatalog;
import com.krbrief.learning.LearningTermDto;
import com.krbrief.summaries.DailySummaryService;
import com.krbrief.summaries.SummaryDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SearchService {
  private static final int DEFAULT_LIMIT = 8;
  private static final int MAX_LIMIT = 20;

  private final DailySummaryService summaries;
  private final LearningTermCatalog terms;
  private final StockUniverseProvider stockUniverse;
  private final SearchTaxonomyProvider searchTaxonomy;

  public SearchService(
      DailySummaryService summaries,
      LearningTermCatalog terms,
      StockUniverseProvider stockUniverse,
      SearchTaxonomyProvider searchTaxonomy) {
    this.summaries = summaries;
    this.terms = terms;
    this.stockUniverse = stockUniverse;
    this.searchTaxonomy = searchTaxonomy;
  }

  public List<SearchResultDto> search(String query, Integer limit) {
    String q = normalize(query);
    if (q.isBlank()) {
      return List.of();
    }

    int safeLimit = normalizeLimit(limit);
    Map<String, SearchResultDto> results = new LinkedHashMap<>();
    addLatestSummaryStocks(results);
    addBaselineStockUniverse(results);
    addSeedCatalog(results);
    addLearningTerms(results, query);

    return results.values().stream()
        .filter(item -> searchableText(item).contains(q))
        .sorted(Comparator.comparingInt(item -> score(item, q)))
        .limit(safeLimit)
        .toList();
  }

  private void addLatestSummaryStocks(Map<String, SearchResultDto> results) {
    summaries.latest().map(SummaryDto::from).ifPresent(summary -> {
      addLeaderEntries(results, "오늘 움직인 종목", "상승 TOP3", summary.topGainers());
      addLeaderEntries(results, "오늘 움직인 종목", "하락 TOP3", summary.topLosers());
      addMentionedEntries(results, summary.mostMentionedTop());
    });
  }

  private void addLeaderEntries(
      Map<String, SearchResultDto> results,
      String market,
      String group,
      List<SummaryDto.LeaderEntryDto> entries) {
    if (entries == null) return;
    for (SummaryDto.LeaderEntryDto entry : entries) {
      if (entry == null || isBlank(entry.code()) || isBlank(entry.name())) continue;
      String id = "stock-" + entry.code();
      results.putIfAbsent(
          id,
          new SearchResultDto(
              id,
              "stock",
              entry.name(),
              entry.code(),
              market,
              formatRate(entry.rate()),
              List.of(group, entry.code()),
              group + "에 포함된 종목입니다. 차트와 AI 설명으로 근거를 확인할 수 있습니다.",
              "latest_summary",
              entry.code(),
              entry.name(),
              null));
    }
  }

  private void addMentionedEntries(Map<String, SearchResultDto> results, List<SummaryDto.MostMentionedEntryDto> entries) {
    if (entries == null) return;
    for (SummaryDto.MostMentionedEntryDto entry : entries) {
      if (entry == null || isBlank(entry.code()) || isBlank(entry.name())) continue;
      String id = "stock-" + entry.code();
      results.putIfAbsent(
          id,
          new SearchResultDto(
              id,
              "stock",
              entry.name(),
              entry.code(),
              "관심 종목",
              entry.count() == null ? "-" : entry.count() + "건",
              List.of("언급 TOP3", entry.code()),
              "오늘 언급량이 높게 잡힌 종목입니다. 차트와 이벤트를 함께 확인할 수 있습니다.",
              "latest_summary",
              entry.code(),
              entry.name(),
              null));
    }
  }

  private void addBaselineStockUniverse(Map<String, SearchResultDto> results) {
    for (SearchResultDto item : stockUniverse.list()) {
      results.putIfAbsent(item.id(), item);
    }
  }

  private void addSeedCatalog(Map<String, SearchResultDto> results) {
    for (SearchResultDto item : searchTaxonomy.list()) {
      results.putIfAbsent(item.id(), item);
    }
  }

  private void addLearningTerms(Map<String, SearchResultDto> results, String query) {
    for (LearningTermDto term : terms.list(query, null, 20)) {
      String id = "term-" + term.id();
      results.putIfAbsent(
          id,
          new SearchResultDto(
              id,
              "term",
              term.term(),
              term.category(),
              "용어",
              "학습",
              compactTags(term.category(), term.relatedTerms()),
              term.coreSummary(),
              "learning_terms",
              null,
              null,
              term.id()));
    }
  }

  private List<String> compactTags(String category, List<String> related) {
    List<String> out = new ArrayList<>();
    if (!isBlank(category)) out.add(category);
    if (related != null) {
      related.stream().filter(v -> !isBlank(v)).limit(2).forEach(out::add);
    }
    return out;
  }

  private int score(SearchResultDto item, String q) {
    String type = safe(item.type());
    String source = safe(item.source());
    String title = normalize(item.title());
    String code = normalize(item.code());
    boolean taxonomy = "theme".equals(type) || "industry".equals(type) || "market".equals(type) || "term".equals(type);
    if (title.equals(q) || code.equals(q)) return 0;
    if (taxonomy && "search_taxonomy_baseline".equals(source) && title.contains(q)) return 1;
    if ("stock".equals(type) && "stock_universe_baseline".equals(source) && tagMatches(item.tags(), q)) return 2;
    if ("stock".equals(type) && (title.contains(q) || code.contains(q))) return 3;
    if (taxonomy && title.contains(q)) return 4;
    if (taxonomy && tagMatches(item.tags(), q)) return 5;
    if ("stock".equals(type) && tagMatches(item.tags(), q)) return 6;
    if (normalize(item.summary()).contains(q)) return 7;
    return 8;
  }

  private boolean tagMatches(List<String> tags, String q) {
    if (tags == null) return false;
    return tags.stream().map(this::normalize).anyMatch(tag -> tag.contains(q));
  }

  private String searchableText(SearchResultDto item) {
    return normalize(String.join(" ", safe(item.title()), safe(item.code()), safe(item.market()), safe(item.rate()),
        safe(item.summary()), String.join(" ", item.tags() == null ? List.of() : item.tags())));
  }

  private int normalizeLimit(Integer limit) {
    if (limit == null || limit <= 0) return DEFAULT_LIMIT;
    return Math.min(limit, MAX_LIMIT);
  }

  private String formatRate(Double rate) {
    if (rate == null || !Double.isFinite(rate)) return "-";
    return String.format(Locale.ROOT, "%+.2f%%", rate);
  }

  private String normalize(String value) {
    return safe(value).trim().toLowerCase(Locale.ROOT).replace(" ", "");
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
