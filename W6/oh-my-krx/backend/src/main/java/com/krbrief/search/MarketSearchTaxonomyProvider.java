package com.krbrief.search;

import com.krbrief.stocks.StockResearchClient;
import com.krbrief.stocks.StockSectorUniverseDto;
import com.krbrief.stocks.StockThemeUniverseDto;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MarketSearchTaxonomyProvider implements SearchTaxonomyProvider {
  private static final Logger log = LoggerFactory.getLogger(MarketSearchTaxonomyProvider.class);
  private static final Duration CACHE_TTL = Duration.ofHours(6);

  private final StockResearchClient client;
  private volatile CacheEntry cache;

  public MarketSearchTaxonomyProvider(StockResearchClient client) {
    this.client = client;
  }

  @Override
  public List<SearchResultDto> list() {
    CacheEntry current = cache;
    if (current != null && current.expiresAt().isAfter(Instant.now())) {
      return current.items();
    }

    List<SearchResultDto> merged = mergeWithBaseline();
    cache = new CacheEntry(merged, Instant.now().plus(CACHE_TTL));
    return merged;
  }

  private List<SearchResultDto> mergeWithBaseline() {
    Map<String, SearchResultDto> out = new LinkedHashMap<>();
    for (SearchResultDto item : SearchTaxonomyCatalog.baseline()) {
      out.put(item.id(), item);
    }

    addSectors(out);
    addThemes(out);
    addNaverThemeFallbacks(out);
    return List.copyOf(out.values());
  }

  private void addSectors(Map<String, SearchResultDto> out) {
    StockSectorUniverseDto res;
    try {
      res = client.sectors(null, 500);
    } catch (RuntimeException e) {
      log.warn("Falling back to baseline industry taxonomy after sector classification load failed: {}", e.toString());
      return;
    }
    if (res == null || res.sectors() == null) {
      return;
    }

    for (StockSectorUniverseDto.StockSectorDto sector : res.sectors()) {
      if (sector == null || isBlank(sector.name())) continue;
      SearchResultDto item = toSearchResult(sector, res.asOf());
      out.putIfAbsent(item.id(), item);
    }
  }

  private void addThemes(Map<String, SearchResultDto> out) {
    StockThemeUniverseDto res;
    try {
      res = client.themes(null, 500);
    } catch (RuntimeException e) {
      log.warn("Falling back to baseline theme taxonomy after Naver theme load failed: {}", e.toString());
      return;
    }
    if (res == null || res.themes() == null) {
      return;
    }

    for (StockThemeUniverseDto.StockThemeDto theme : res.themes()) {
      if (theme == null || isBlank(theme.name())) continue;
      SearchResultDto item = toSearchResult(theme, res.asOf());
      out.putIfAbsent(item.id(), item);
    }
  }

  private SearchResultDto toSearchResult(StockSectorUniverseDto.StockSectorDto sector, String asOf) {
    String topNames = topStockNames(sector.topStocks());
    String title = sector.name();
    String summary = isBlank(sector.summary())
        ? "KRX 업종 분류 기준 상장 종목 묶음입니다."
        : sector.summary();
    if (!isBlank(asOf)) {
      summary = summary + " 기준일: " + asOf + ".";
    }

    List<String> tags = new ArrayList<>();
    if (sector.markets() != null) {
      sector.markets().stream().filter(v -> !isBlank(v)).limit(2).forEach(tags::add);
    }
    tags.add("KRX 업종");
    if (!isBlank(topNames)) {
      tags.add(topNames);
    }

    return new SearchResultDto(
        "industry-krx-" + Integer.toHexString(title.hashCode()),
        "industry",
        title,
        "IND",
        isBlank(sector.market()) ? "KRX" : sector.market(),
        formatRate(sector.rate()),
        tags,
        summary,
        "krx_sector_classification",
        null,
        null,
        null);
  }

  private SearchResultDto toSearchResult(StockThemeUniverseDto.StockThemeDto theme, String asOf) {
    String title = theme.name();
    String leaders = topThemeLeaders(theme.leaders());
    String summary = isBlank(theme.summary())
        ? "Naver Finance 테마 시세 기준 테마입니다."
        : theme.summary();
    if (!isBlank(asOf)) {
      summary = summary + " 기준일: " + asOf + ".";
    }

    List<String> tags = new ArrayList<>();
    tags.add("Naver 테마");
    if (!isBlank(theme.threeDayRate())) {
      tags.add("3일 " + theme.threeDayRate());
    }
    if (!isBlank(leaders)) {
      tags.add(leaders);
    }

    return new SearchResultDto(
        themeId(title),
        "theme",
        title,
        "THEME",
        "테마",
        isBlank(theme.rate()) ? "Naver 테마" : theme.rate(),
        tags,
        summary,
        "naver_theme_taxonomy",
        null,
        null,
        null);
  }

  private void addNaverThemeFallbacks(Map<String, SearchResultDto> out) {
    String title = "전선";
    out.putIfAbsent(
        themeId(title),
        new SearchResultDto(
            themeId(title),
            "theme",
            title,
            "THEME",
            "테마",
            "Naver 테마",
            List.of("Naver 테마", "전력설비", "전력망", "구리", "대한전선, 가온전선"),
            "Naver Finance 테마별 시세에서 확인하는 전력망/전선 관련 테마입니다. 실시간 테마 목록이 제한될 때도 통합 검색 진입점을 유지합니다.",
            "naver_theme_taxonomy",
            null,
            null,
            null));
  }

  private String themeId(String title) {
    return "theme-naver-" + Integer.toHexString(title.hashCode());
  }

  private String topStockNames(List<StockSectorUniverseDto.StockSectorMemberDto> stocks) {
    if (stocks == null || stocks.isEmpty()) return "";
    return stocks.stream()
        .filter(item -> item != null && !isBlank(item.name()))
        .limit(3)
        .map(StockSectorUniverseDto.StockSectorMemberDto::name)
        .reduce((a, b) -> a + ", " + b)
        .orElse("");
  }

  private String topThemeLeaders(List<String> leaders) {
    if (leaders == null || leaders.isEmpty()) return "";
    return leaders.stream()
        .filter(value -> !isBlank(value))
        .limit(2)
        .reduce((a, b) -> a + ", " + b)
        .orElse("");
  }

  private String formatRate(Double value) {
    if (value == null || !Double.isFinite(value)) return "KRX 업종";
    return String.format(Locale.ROOT, "%+.2f%%", value);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private record CacheEntry(List<SearchResultDto> items, Instant expiresAt) {}
}
