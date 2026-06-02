package com.krbrief.marketdata;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Free/best-effort market data from Naver Finance pages.
 *
 * <p>Internal use only. Naver may change HTML at any time.
 */
@Component
@ConditionalOnProperty(name = "marketdata.provider", havingValue = "naver")
public class NaverMarketDataClient implements MarketDataClient {
  private final RestClient http;

  public NaverMarketDataClient() {
    this.http =
        RestClient.builder()
            .baseUrl("https://finance.naver.com")
            .defaultHeader("User-Agent", "Mozilla/5.0")
            .build();
  }

  @Override
  public Optional<DailyMarketBrief> getDailyBrief(LocalDate date) {
    // Naver pages are for "today"; for MVP we accept that limitation.
    // If date != today, we still return best-effort values.
    try {
      List<Entry> kospiRise = fetchRise(0);
      List<Entry> kosdaqRise = fetchRise(1);
      List<Entry> kospiFall = fetchFall(0);
      List<Entry> kosdaqFall = fetchFall(1);

      Entry topGainer =
          maxByRate(List.of(kospiRise, kosdaqRise)).orElse(new Entry("-", 0.0, 0L));
      Entry topLoser =
          minByRate(List.of(kospiFall, kosdaqFall)).orElse(new Entry("-", 0.0, 0L));

      Entry kospiPick = maxByVolume(kospiRise).orElse(new Entry("-", 0.0, 0L));
      Entry kosdaqPick = maxByVolume(kosdaqRise).orElse(new Entry("-", 0.0, 0L));

      // "Most mentioned" v1: highest volume among top movers lists.
      Entry most =
          maxByVolume(merge(kospiRise, kosdaqRise, kospiFall, kosdaqFall))
              .orElse(new Entry("-", 0.0, 0L));

      return Optional.of(
          new DailyMarketBrief(
              topGainer.name,
              topLoser.name,
              most.name,
              kospiPick.name,
              kosdaqPick.name,
              "naver(finance.naver.com)",
              "v1 규칙: 상승/하락 상위 페이지 기반. mostMentioned=거래량 최대(상위리스트 내)."));
    } catch (Exception e) {
      return Optional.of(
          new DailyMarketBrief(
              "-",
              "-",
              "-",
              "-",
              "-",
              "naver_error",
              e.getClass().getSimpleName() + ": " + e.getMessage()));
    }
  }

  private List<Entry> fetchRise(int sosok) {
    String html =
        http
            .get()
            .uri(uriBuilder -> uriBuilder.path("/sise/sise_rise.nhn").queryParam("sosok", sosok).build())
            .accept(MediaType.TEXT_HTML)
            .retrieve()
            .body(String.class);
    return parseType2Table(html);
  }

  private List<Entry> fetchFall(int sosok) {
    String html =
        http
            .get()
            .uri(uriBuilder -> uriBuilder.path("/sise/sise_fall.nhn").queryParam("sosok", sosok).build())
            .accept(MediaType.TEXT_HTML)
            .retrieve()
            .body(String.class);
    return parseType2Table(html);
  }

  private static List<Entry> parseType2Table(String html) {
    if (html == null) return List.of();
    Document doc = Jsoup.parse(html);
    Element table = doc.selectFirst("table.type_2");
    if (table == null) return List.of();

    List<Entry> out = new ArrayList<>();
    Elements rows = table.select("tr");
    for (Element tr : rows) {
      Element a = tr.selectFirst("a.tltle");
      if (a == null) continue;

      String name = a.text().trim();
      Elements tds = tr.select("td");
      if (tds.size() < 6) continue;

      // td[4] = rate like +29.96%
      String rateText = tds.get(4).text().replace("%", "").replace(",", "").trim();
      double rate = parseSignedDouble(rateText);

      // td[5] = volume
      String volText = tds.get(5).text().replace(",", "").trim();
      long vol = parseLong(volText);

      out.add(new Entry(name, rate, vol));
    }
    return out;
  }

  private static Optional<Entry> maxByRate(List<List<Entry>> lists) {
    return lists.stream().flatMap(List::stream).max(Comparator.comparingDouble(e -> e.rate));
  }

  private static Optional<Entry> minByRate(List<List<Entry>> lists) {
    return lists.stream().flatMap(List::stream).min(Comparator.comparingDouble(e -> e.rate));
  }

  private static Optional<Entry> maxByVolume(List<Entry> list) {
    return list.stream().max(Comparator.comparingLong(e -> e.volume));
  }

  private static List<Entry> merge(List<Entry>... lists) {
    List<Entry> out = new ArrayList<>();
    for (List<Entry> l : lists) out.addAll(l);
    return out;
  }

  private static double parseSignedDouble(String s) {
    if (s == null || s.isBlank()) return 0.0;
    try {
      return Double.parseDouble(s.replace("+", ""));
    } catch (Exception e) {
      return 0.0;
    }
  }

  private static long parseLong(String s) {
    if (s == null || s.isBlank()) return 0L;
    try {
      return Long.parseLong(s);
    } catch (Exception e) {
      return 0L;
    }
  }

  private static final class Entry {
    final String name;
    final double rate;
    final long volume;

    Entry(String name, double rate, long volume) {
      this.name = name;
      this.rate = rate;
      this.volume = volume;
    }
  }
}
