package com.krbrief.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krbrief.learning.LearningTermCatalog;
import com.krbrief.summaries.DailySummary;
import com.krbrief.summaries.DailySummaryService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SearchServiceTest {
  private final StockUniverseProvider baselineUniverse = StockUniverseCatalog::baseline;
  private final SearchTaxonomyProvider baselineTaxonomy = SearchTaxonomyCatalog::baseline;

  @Test
  void search_returnsThemeAndLearningResults() {
    SearchService service = new SearchService(
        mock(DailySummaryService.class), new LearningTermCatalog(), baselineUniverse, baselineTaxonomy);

    var themeResults = service.search("반도체", 10);
    var termResults = service.search("PER", 10);

    assertTrue(themeResults.stream().anyMatch(item -> item.type().equals("theme") && item.title().equals("반도체")));
    assertTrue(termResults.stream().anyMatch(item ->
        item.type().equals("term") && item.termId().equals("per") && item.source().equals("learning_terms")));
  }

  @Test
  void search_includesLatestSummaryStocks() {
    DailySummary latest = new DailySummary(LocalDate.of(2026, 5, 3));
    latest.setTopGainersJson("[{\"code\":\"005930\",\"name\":\"삼성전자\",\"rate\":3.2}]");
    DailySummaryService summaries = mock(DailySummaryService.class);
    when(summaries.latest()).thenReturn(Optional.of(latest));

    SearchService service = new SearchService(summaries, new LearningTermCatalog(), baselineUniverse, baselineTaxonomy);

    var results = service.search("삼성전자", 10);

    assertFalse(results.isEmpty());
    assertTrue(results.stream().anyMatch(item ->
        item.type().equals("stock") && item.stockCode().equals("005930") && item.source().equals("latest_summary")));
  }

  @Test
  void search_includesRepresentativeStocksWithoutLatestSummary() {
    DailySummaryService summaries = mock(DailySummaryService.class);
    when(summaries.latest()).thenReturn(Optional.empty());
    SearchService service = new SearchService(summaries, new LearningTermCatalog(), baselineUniverse, baselineTaxonomy);

    var byName = service.search("삼성전자", 10);
    var byCode = service.search("005930", 10);

    assertTrue(byName.stream().anyMatch(item -> item.type().equals("stock") && item.stockCode().equals("005930")));
    assertTrue(byCode.stream().anyMatch(item -> item.type().equals("stock") && item.title().equals("삼성전자")));
  }

  @Test
  void search_includesThemeRelatedRepresentativeStocks() {
    DailySummaryService summaries = mock(DailySummaryService.class);
    when(summaries.latest()).thenReturn(Optional.empty());
    SearchService service = new SearchService(summaries, new LearningTermCatalog(), baselineUniverse, baselineTaxonomy);

    var results = service.search("반도체", 10);

    assertTrue(results.stream().anyMatch(item -> item.type().equals("theme") && item.title().equals("반도체")));
    assertTrue(results.stream().anyMatch(item -> item.type().equals("stock") && item.stockCode().equals("005930")));
    assertTrue(results.stream().anyMatch(item -> item.type().equals("stock") && item.stockCode().equals("000660")));
  }

  @Test
  void search_coversRepresentativeIndustryThemeAndCompanyQueries() {
    DailySummaryService summaries = mock(DailySummaryService.class);
    when(summaries.latest()).thenReturn(Optional.empty());
    SearchService service = new SearchService(summaries, new LearningTermCatalog(), baselineUniverse, baselineTaxonomy);

    assertTrue(service.search("SK하이닉스", 10).stream()
        .anyMatch(item -> item.type().equals("stock") && item.stockCode().equals("000660")));
    assertTrue(service.search("현대차", 10).stream()
        .anyMatch(item -> item.type().equals("stock") && item.stockCode().equals("005380")));
    assertTrue(service.search("NAVER", 10).stream()
        .anyMatch(item -> item.type().equals("stock") && item.stockCode().equals("035420")));
    assertTrue(service.search("네이버", 10).stream()
        .anyMatch(item -> item.type().equals("stock") && item.stockCode().equals("035420")));
    assertTrue(service.search("카카오", 10).stream()
        .anyMatch(item -> item.type().equals("stock") && item.stockCode().equals("035720")));
    assertTrue(service.search("2차전지", 10).stream()
        .anyMatch(item -> item.type().equals("theme") && item.title().equals("2차전지")));
    assertTrue(service.search("금융", 10).stream()
        .anyMatch(item -> item.type().equals("industry") && item.title().equals("증권/금융")));
    assertTrue(service.search("금융", 8).stream()
        .limit(3)
        .anyMatch(item -> item.type().equals("industry") && item.title().equals("증권/금융")));
    assertTrue(service.search("바이오", 10).stream()
        .anyMatch(item -> item.type().equals("theme") && item.title().equals("바이오")));
    assertTrue(service.search("조선", 10).stream()
        .anyMatch(item -> item.type().equals("theme") && item.title().equals("조선")));
    assertTrue(service.search("거래량", 10).stream()
        .anyMatch(item -> item.type().equals("term") && item.termId().equals("volume")));
    assertTrue(service.search("PER", 10).stream()
        .anyMatch(item -> item.type().equals("term") && item.termId().equals("per")));
    assertTrue(service.search("DART", 10).stream()
        .anyMatch(item -> item.type().equals("term") && item.termId().equals("dart")));
  }

  @Test
  void search_usesKrxUniverseProviderOutsideRepresentativeBaseline() {
    DailySummaryService summaries = mock(DailySummaryService.class);
    when(summaries.latest()).thenReturn(Optional.empty());
    StockUniverseProvider krxUniverse = () -> List.of(new SearchResultDto(
        "stock-000100",
        "stock",
        "유한양행",
        "000100",
        "KOSPI",
        "상장",
        List.of("KOSPI", "KRX", "상장 종목"),
        "KRX 상장 종목 universe에서 조회한 종목입니다.",
        "krx_stock_universe",
        "000100",
        "유한양행",
        null));
    SearchService service = new SearchService(summaries, new LearningTermCatalog(), krxUniverse, baselineTaxonomy);

    assertTrue(service.search("유한양행", 10).stream()
        .anyMatch(item -> item.type().equals("stock")
            && item.stockCode().equals("000100")
            && item.source().equals("krx_stock_universe")));
  }

  @Test
  void search_usesKrxSectorTaxonomyProviderOutsideRepresentativeBaseline() {
    DailySummaryService summaries = mock(DailySummaryService.class);
    when(summaries.latest()).thenReturn(Optional.empty());
    SearchTaxonomyProvider krxTaxonomy = () -> List.of(new SearchResultDto(
        "industry-krx-medical",
        "industry",
        "의료·정밀기기",
        "IND",
        "KRX",
        "+0.42%",
        List.of("KOSDAQ", "KRX 업종", "클래시스, 파마리서치"),
        "KRX 업종 분류 기준 100개 상장 종목이 포함됩니다.",
        "krx_sector_classification",
        null,
        null,
        null));
    SearchService service = new SearchService(summaries, new LearningTermCatalog(), baselineUniverse, krxTaxonomy);

    assertTrue(service.search("의료·정밀기기", 10).stream()
        .anyMatch(item -> item.type().equals("industry")
            && item.title().equals("의료·정밀기기")
            && item.source().equals("krx_sector_classification")));
  }

  @Test
  void search_usesExternalThemeTaxonomyProviderOutsideRepresentativeBaseline() {
    DailySummaryService summaries = mock(DailySummaryService.class);
    when(summaries.latest()).thenReturn(Optional.empty());
    SearchTaxonomyProvider themeTaxonomy = () -> List.of(new SearchResultDto(
        "theme-naver-wire",
        "theme",
        "전선",
        "THEME",
        "테마",
        "+9.20%",
        List.of("Naver 테마", "KBI메탈, 대원전선"),
        "Naver Finance 테마 시세 기준입니다.",
        "naver_theme_taxonomy",
        null,
        null,
        null));
    SearchService service = new SearchService(summaries, new LearningTermCatalog(), baselineUniverse, themeTaxonomy);

    assertTrue(service.search("전선", 10).stream()
        .anyMatch(item -> item.type().equals("theme")
            && item.title().equals("전선")
            && item.source().equals("naver_theme_taxonomy")));
  }
}
