package com.krbrief.search;

import com.krbrief.stocks.StockResearchClient;
import com.krbrief.stocks.StockUniverseDto;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
final class MarketStockUniverseProvider implements StockUniverseProvider {
  private static final Logger log = LoggerFactory.getLogger(MarketStockUniverseProvider.class);
  private static final Duration CACHE_TTL = Duration.ofHours(6);

  private final StockResearchClient client;
  private volatile CachedUniverse cache = new CachedUniverse(Instant.EPOCH, List.of());

  MarketStockUniverseProvider(StockResearchClient client) {
    this.client = client;
  }

  @Override
  public List<SearchResultDto> list() {
    Instant now = Instant.now();
    CachedUniverse snapshot = cache;
    if (!snapshot.items().isEmpty() && Duration.between(snapshot.loadedAt(), now).compareTo(CACHE_TTL) < 0) {
      return snapshot.items();
    }

    try {
      StockUniverseDto universe = client.universe(null, 5000);
      List<SearchResultDto> items = mergeWithBaseline(mapUniverse(universe));
      if (items.size() >= StockUniverseCatalog.baseline().size()) {
        cache = new CachedUniverse(now, items);
        return items;
      }
    } catch (RuntimeException e) {
      log.warn("KRX stock universe unavailable; using baseline search universe: {}", e.getMessage());
    }
    return StockUniverseCatalog.baseline();
  }

  private List<SearchResultDto> mapUniverse(StockUniverseDto universe) {
    if (universe == null || universe.stocks() == null) {
      return List.of();
    }
    return universe.stocks().stream()
        .filter(item -> !isBlank(item.code()) && !isBlank(item.name()))
        .map(item -> stock(item.code(), item.name(), item.market(), universe.asOf()))
        .toList();
  }

  private List<SearchResultDto> mergeWithBaseline(List<SearchResultDto> marketItems) {
    Map<String, SearchResultDto> merged = new LinkedHashMap<>();
    for (SearchResultDto item : StockUniverseCatalog.baseline()) {
      merged.put(item.id(), item);
    }
    for (SearchResultDto item : marketItems) {
      merged.putIfAbsent(item.id(), item);
    }
    return List.copyOf(merged.values());
  }

  private SearchResultDto stock(String code, String name, String market, String asOf) {
    String safeMarket = isBlank(market) ? "KRX" : market;
    return new SearchResultDto(
        "stock-" + code,
        "stock",
        name,
        code,
        safeMarket,
        "상장",
        List.of(safeMarket, "KRX", "상장 종목"),
        "KRX 상장 종목 universe에서 조회한 종목입니다. 차트와 AI 설명으로 흐름을 확인할 수 있습니다."
            + (isBlank(asOf) ? "" : " 기준일: " + asOf),
        "krx_stock_universe",
        code,
        name,
        null);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private record CachedUniverse(Instant loadedAt, List<SearchResultDto> items) {}
}
