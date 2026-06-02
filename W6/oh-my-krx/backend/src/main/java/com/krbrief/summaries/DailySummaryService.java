package com.krbrief.summaries;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krbrief.marketdata.DailyMarketBrief;
import com.krbrief.marketdata.MarketDataClient;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class DailySummaryService {
  private static final Logger log = LoggerFactory.getLogger(DailySummaryService.class);
  private static final ObjectMapper JSON = new ObjectMapper();

  private final DailySummaryRepository repo;
  private final MarketDataClient marketData;
  private final RestClient pykrxHttp;
  private final String provider;
  private final com.krbrief.discord.DiscordPoster discord;

  public DailySummaryService(
      DailySummaryRepository repo,
      MarketDataClient marketData,
      com.krbrief.discord.DiscordPoster discord,
      @Value("${marketdata.baseUrl:http://marketdata:8000}") String marketDataBaseUrl,
      @Value("${marketdata.provider:placeholder}") String provider) {
    this.repo = repo;
    this.marketData = marketData;
    this.discord = discord;
    this.pykrxHttp = RestClient.builder().baseUrl(marketDataBaseUrl).build();
    this.provider = provider;
  }

  public List<DailySummary> list(LocalDate from, LocalDate to) {
    return repo.findAllByDateBetweenAndArchivedAtIsNullOrderByDateAsc(from, to);
  }

  public Optional<DailySummary> get(LocalDate date) {
    return repo.findByDateAndArchivedAtIsNull(date);
  }

  public boolean existsAny(LocalDate date) {
    return repo.existsById(date);
  }

  public Optional<DailySummary> latest() {
    return repo.findTopByArchivedAtIsNullOrderByDateDesc();
  }

  public Optional<KrxVerificationArtifactDto> krxVerificationArtifact(LocalDate date) {
    Optional<DailySummary> found = repo.findByDateAndArchivedAtIsNull(date);
    if (found.isEmpty()) {
      return Optional.empty();
    }

    DailySummary row = found.get();
    Optional<DailyMarketBrief> reference = loadPykrxLeaders(date);
    String status = "unverified";
    String reason = "krx_reference_unavailable: failed to fetch pykrx leaders for " + date;
    String refTopGainer = "";
    String refTopLoser = "";

    if (reference.isPresent()) {
      DailyMarketBrief ref = reference.get();
      refTopGainer = nullToEmpty(ref.topGainer());
      refTopLoser = nullToEmpty(ref.topLoser());
      boolean gainerMatched = same(row.getTopGainer(), ref.topGainer());
      boolean loserMatched = same(row.getTopLoser(), ref.topLoser());
      status = gainerMatched && loserMatched ? "verified" : "unverified";
      reason =
          status.equals("verified")
              ? ""
              : "mismatch: topGainer or topLoser differs from KRX reference";
    }

    return Optional.of(
        new KrxVerificationArtifactDto(
            date,
            java.time.Instant.now(),
            status,
            reason,
            new KrxVerificationArtifactDto.SourceIdentity(
                "KRX 전종목 등락률",
                "MDCSTAT01501",
                "https://data.krx.co.kr/contents/MDC/MDI/mdiLoader/index.cmd?menuId=MDC0201020101"),
            new KrxVerificationArtifactDto.ComputationBasis(
                "등락률(%)",
                "topGainer = max(등락률)",
                "topLoser = min(등락률)",
                "KRX 등락률 데이터셋 기준. 참조값은 pykrx leaders 브리지를 통해 수집됨."),
            List.of(
                new KrxVerificationArtifactDto.VerificationEvidenceRecord(
                    "topGainer",
                    nullToEmpty(row.getTopGainer()),
                    refTopGainer,
                    !refTopGainer.isBlank() && same(row.getTopGainer(), refTopGainer)),
                new KrxVerificationArtifactDto.VerificationEvidenceRecord(
                    "topLoser",
                    nullToEmpty(row.getTopLoser()),
                    refTopLoser,
                    !refTopLoser.isBlank() && same(row.getTopLoser(), refTopLoser)))));
  }

  public SummaryStatsDto stats() {
    Optional<DailySummary> latest = repo.findTopByArchivedAtIsNullOrderByDateDesc();
    return new SummaryStatsDto(
        repo.countByArchivedAtIsNull(),
        latest.map(DailySummary::getDate).orElse(null),
        latest.map(DailySummary::getUpdatedAt).orElse(null));
  }

  public SummaryInsightsDto insights(LocalDate from, LocalDate to) {
    List<DailySummary> rows = repo.findAllByDateBetweenAndArchivedAtIsNullOrderByDateAsc(from, to);
    long totalDays = ChronoUnit.DAYS.between(from, to) + 1;
    long generatedDays = rows.size();
    long missingDays = Math.max(0, totalDays - generatedDays);

    Map<String, Long> mentionCounts = new HashMap<>();
    for (DailySummary row : rows) {
      String m = row.getMostMentioned();
      if (m == null || m.isBlank()) continue;
      mentionCounts.put(m, mentionCounts.getOrDefault(m, 0L) + 1);
    }

    String top = null;
    long topCount = 0L;
    for (Map.Entry<String, Long> e : mentionCounts.entrySet()) {
      if (e.getValue() > topCount) {
        top = e.getKey();
        topCount = e.getValue();
      }
    }

    return new SummaryInsightsDto(from, to, totalDays, generatedDays, missingDays, top, topCount);
  }

  @Transactional
  public DailySummary generate(LocalDate date) {
    DailySummary s = repo.findById(date).orElseGet(() -> new DailySummary(date));
    s.setArchivedAt(null);

    DailyMarketBrief brief = loadBriefWithVerification(date, 2);

    // Keep missing market data visibly empty for users. Do not persist internal placeholders.
    String topGainer = summaryValueOrDash(brief.topGainer());
    String topLoser = summaryValueOrDash(brief.topLoser());
    String filteredTopGainer =
        isMissingSummaryValue(brief.filteredTopGainer())
            ? topGainer
            : brief.filteredTopGainer();
    String filteredTopLoser =
        isMissingSummaryValue(brief.filteredTopLoser())
            ? topLoser
            : brief.filteredTopLoser();

    s.setTopGainer(topGainer);
    s.setTopLoser(topLoser);
    s.setFilteredTopGainer(filteredTopGainer);
    s.setFilteredTopLoser(filteredTopLoser);
    s.setMostMentioned(summaryValueOrDash(brief.mostMentioned()));
    s.setKospiPick(summaryValueOrDash(brief.kospiPick()));
    s.setKosdaqPick(summaryValueOrDash(brief.kosdaqPick()));

    s.setRawNotes(
        "Source: "
            + brief.source()
            + "\n"
            + (brief.notes() == null ? "" : brief.notes() + "\n"));
    s.setRankingWarning(brief.rankingWarning());
    s.setAnomaliesText(SummaryAnomalyCodec.encode(brief.anomalies()));

    s.setEffectiveDate(brief.effectiveDate());
    s.setTopGainersJson(serializeJson(brief.topGainers()));
    s.setTopLosersJson(serializeJson(brief.topLosers()));
    s.setMostMentionedTopJson(serializeJson(brief.mostMentionedTop()));

    s.setKospiTopGainer(brief.kospiTopGainer());
    s.setKospiTopLoser(brief.kospiTopLoser());
    s.setKosdaqTopGainer(brief.kosdaqTopGainer());
    s.setKosdaqTopLoser(brief.kosdaqTopLoser());
    s.setKospiTopGainerCode(brief.kospiTopGainerCode());
    s.setKospiTopLoserCode(brief.kospiTopLoserCode());
    s.setKosdaqTopGainerCode(brief.kosdaqTopGainerCode());
    s.setKosdaqTopLoserCode(brief.kosdaqTopLoserCode());
    s.setKospiTopGainerRate(brief.kospiTopGainerRate());
    s.setKospiTopLoserRate(brief.kospiTopLoserRate());
    s.setKosdaqTopGainerRate(brief.kosdaqTopGainerRate());
    s.setKosdaqTopLoserRate(brief.kosdaqTopLoserRate());
    s.setKospiTopGainersJson(serializeJson(brief.kospiTopGainers()));
    s.setKospiTopLosersJson(serializeJson(brief.kospiTopLosers()));
    s.setKosdaqTopGainersJson(serializeJson(brief.kosdaqTopGainers()));
    s.setKosdaqTopLosersJson(serializeJson(brief.kosdaqTopLosers()));

    DailySummary saved = repo.save(s);

    // Discord webhook auto-post (best-effort, does not affect persistence).
    // Post only once per date (dedupe via discordPostedAt).
    if (discord != null && discord.enabled() && saved.getDiscordPostedAt() == null) {
      com.krbrief.discord.DiscordPoster.Result r = discord.post(saved);
      if (r.ok()) {
        saved.setDiscordPostedAt(r.postedAt());
        saved.setDiscordMessageId(r.messageId());
        saved.setDiscordChannelId(r.channelId());
        saved.setDiscordThreadId(r.threadId());
        saved = repo.save(saved);
      }
    }

    return saved;
  }

  private String serializeJson(Object obj) {
    if (obj == null) return null;
    try {
      return JSON.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      log.warn("JSON serialization failed: {}", e.getMessage());
      return null;
    }
  }

  @Transactional
  public Optional<DailySummary> archive(LocalDate date) {
    Optional<DailySummary> found = repo.findById(date);
    if (found.isEmpty()) return Optional.empty();
    DailySummary s = found.get();
    s.setArchivedAt(java.time.Instant.now());
    return Optional.of(repo.save(s));
  }

  @Transactional
  public BackfillResponseDto backfill(LocalDate from, LocalDate to) {
    java.util.ArrayList<BackfillResultDto> results = new java.util.ArrayList<>();
    int success = 0;
    int lowConfidence = 0;
    int fail = 0;

    LocalDate cur = from;
    while (!cur.isAfter(to)) {
      try {
        DailySummary saved = generate(cur);
        String notes = saved.getRawNotes() == null ? "" : saved.getRawNotes();
        String sourceUsed = sourceUsedFromNotes(notes);
        String confidence = confidenceFor(sourceUsed);
        if ("low".equals(confidence)) {
          String reason =
              "naver".equals(sourceUsed)
                  ? "historical accuracy limited for current naver v1 source"
                  : "fallback source used due to marketdata fetch failure";
          results.add(
              new BackfillResultDto(cur, "low_confidence", reason, sourceUsed, confidence));
          lowConfidence++;
        } else {
          results.add(new BackfillResultDto(cur, "success", "", sourceUsed, confidence));
          success++;
        }
      } catch (Exception e) {
        String reason = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ":" + e.getMessage());
        results.add(new BackfillResultDto(cur, "fail", reason, "fallback", "low"));
        fail++;
      }
      cur = cur.plusDays(1);
    }

    int total = success + lowConfidence + fail;
    return new BackfillResponseDto(from, to, total, success, lowConfidence, fail, results);
  }

  private DailyMarketBrief loadBriefWithVerification(LocalDate date, int maxRetries) {
    if ("placeholder".equalsIgnoreCase(provider)) {
      return loadBriefWithRetry(date, maxRetries);
    }

    Optional<DailyMarketBrief> reference = loadReferenceBrief(date, maxRetries);
    if (reference.isEmpty()) {
      // If reference is unavailable (e.g., upstream pykrx/marketdata failure), do not fail the whole
      // generation. Fall back to best-effort provider output without verification.
      log.warn(
          "verification_reference_unavailable: cannot fetch date-specific leaders for {}; using unverified primary",
          date);
      return loadBriefWithRetry(date, maxRetries);
    }

    // Historical summaries should prioritize the pykrx reference directly so
    // anomaly filtering/reasons are preserved in persisted API responses.
    if (date.isBefore(todaySeoul())) {
      return appendVerification(reference.get(), "reference(pykrx)", 1, true, "historical_reference");
    }

    List<String> primaryReasons = new ArrayList<>();
    for (int attempt = 1; attempt <= 2; attempt++) {
      Optional<DailyMarketBrief> candidate = loadPrimaryCandidate(date, maxRetries);
      if (candidate.isPresent()) {
        VerificationResult v = verify(candidate.get(), reference.get());
        if (v.ok()) {
          return appendVerification(candidate.get(), "primary", attempt, true, v.reason());
        }
        primaryReasons.add("attempt=" + attempt + ": " + v.reason());
      } else {
        primaryReasons.add("attempt=" + attempt + ": empty_response");
      }
    }

    List<String> fallbackReasons = new ArrayList<>();
    for (int attempt = 1; attempt <= 2; attempt++) {
      Optional<DailyMarketBrief> candidate = loadPykrxLeaders(date);
      if (candidate.isPresent()) {
        VerificationResult v = verify(candidate.get(), reference.get());
        if (v.ok()) {
          return appendVerification(candidate.get(), "fallback(pykrx)", attempt, true, v.reason());
        }
        fallbackReasons.add("attempt=" + attempt + ": " + v.reason());
      } else {
        fallbackReasons.add("attempt=" + attempt + ": empty_response");
      }
    }

    String reason =
        "verification_failed: primary="
            + String.join("; ", primaryReasons)
            + " | fallback="
            + String.join("; ", fallbackReasons);

    // Do not fail the scheduler run entirely when verification is inconclusive.
    // Prefer returning the reference(pykrx) brief with verification notes attached.
    log.warn("{}; using reference(pykrx) as safe fallback for date={}", reason, date);
    return appendVerification(reference.get(), "reference(pykrx)", 0, false, reason);
  }

  private Optional<DailyMarketBrief> loadReferenceBrief(LocalDate date, int maxRetries) {
    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      Optional<DailyMarketBrief> pykrx = loadPykrxLeaders(date);
      if (pykrx.isPresent()) return pykrx;
    }
    return Optional.empty();
  }

  private Optional<DailyMarketBrief> loadPrimaryCandidate(LocalDate date, int maxRetries) {
    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        Optional<DailyMarketBrief> found = marketData.getDailyBrief(date);
        if (found.isPresent()) {
          return found;
        }
      } catch (Exception e) {
        log.warn(
            "primary marketdata fetch failed: date={}, attempt={}, reason={}",
            date,
            attempt,
            e.getClass().getSimpleName() + ":" + (e.getMessage() == null ? "" : e.getMessage()));
      }
    }
    return Optional.empty();
  }

  private DailyMarketBrief loadBriefWithRetry(LocalDate date, int maxRetries) {
    LocalDate today = todaySeoul();
    if (date.isBefore(today)) {
      Optional<DailyMarketBrief> pykrx = loadPykrxLeaders(date);
      if (pykrx.isPresent()) {
        return pykrx.get();
      }
    }

    String lastReason = "unknown";

    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        Optional<DailyMarketBrief> found = marketData.getDailyBrief(date);
        if (found.isPresent()) {
          if (attempt > 0) {
            log.info("marketdata recovered after retry: date={}, attempt={}", date, attempt);
          }
          return found.get();
        }
        lastReason = "empty_response";
      } catch (Exception e) {
        lastReason = e.getClass().getSimpleName() + ":" + (e.getMessage() == null ? "" : e.getMessage());
        log.warn("marketdata fetch failed: date={}, attempt={}, reason={}", date, attempt, lastReason);
      }
    }

    return new DailyMarketBrief(
        "TOP_GAINER_" + date,
        "TOP_LOSER_" + date,
        "MOST_MENTIONED_" + date,
        "KOSPI_PICK_" + date,
        "KOSDAQ_PICK_" + date,
        "fallback",
        "marketdata unavailable after retries; reason=" + lastReason);
  }

  private Optional<DailyMarketBrief> loadPykrxLeaders(LocalDate date) {
    try {
      PykrxLeadersResponse res =
          pykrxHttp
              .get()
              .uri(uriBuilder -> uriBuilder.path("/leaders").queryParam("date", date).build())
              .accept(MediaType.APPLICATION_JSON)
              .retrieve()
              .body(PykrxLeadersResponse.class);

      if (res == null) {
        return Optional.empty();
      }

      // 휴장일 처리: marketClosed 플래그 확인
      if (Boolean.TRUE.equals(res.marketClosed())) {
        log.info("Market closed for date={}, reason={}", date, res.marketClosedReason());
        String links =
            res.evidenceLinks() == null || res.evidenceLinks().isEmpty()
                ? ""
                : "\n휴장 근거: " + String.join(" | ", res.evidenceLinks());
        return Optional.of(
            new DailyMarketBrief(
                "-",
                "-",
                "-",
                "-",
                "-",
                "-",
                "-",
                "market_closed",
                "휴장일: "
                    + (res.marketClosedReason() == null
                        ? "해당 날짜는 증권시장 휴장일입니다."
                        : res.marketClosedReason())
                    + links,
                java.util.List.of(),
                "휴장일로 인해 랭킹 데이터가 없습니다."));
      }

      if (isBlank(res.topGainer()) || isBlank(res.topLoser())) {
        return Optional.empty();
      }

      // machine-parseable effective_date line (YYYYMMDD format)
      String effectiveDateNote = "";
      if (res.effectiveDate() != null && !res.effectiveDate().isBlank()) {
        effectiveDateNote = "effective_date=" + res.effectiveDate().replace("-", "");
      }

      String codeNotes =
          "codes: topGainer="
              + nullToEmpty(res.topGainerCode())
              + ", topLoser="
              + nullToEmpty(res.topLoserCode())
              + ", mostMentioned="
              + nullToEmpty(res.mostMentionedCode())
              + ", kospiPick="
              + nullToEmpty(res.kospiPickCode())
              + ", kosdaqPick="
              + nullToEmpty(res.kosdaqPickCode());

      StringBuilder notesBuilder = new StringBuilder();
      if (res.notes() != null && !res.notes().isBlank()) {
        notesBuilder.append(res.notes()).append("\n");
      }
      if (!effectiveDateNote.isBlank()) {
        notesBuilder.append(effectiveDateNote).append("\n");
      }
      notesBuilder.append(codeNotes);
      String notes = notesBuilder.toString();

      return Optional.of(
          new DailyMarketBrief(
              blankToDash(res.rawTopGainer(), res.topGainer()),
              blankToDash(res.rawTopLoser(), res.topLoser()),
              blankToDash(res.filteredTopGainer(), res.rawTopGainer(), res.topGainer()),
              blankToDash(res.filteredTopLoser(), res.rawTopLoser(), res.topLoser()),
              blankToDash(res.mostMentioned()),
              blankToDash(res.kospiPick()),
              blankToDash(res.kosdaqPick()),
              "pykrx",
              notes,
              res.anomalies() == null ? java.util.List.of() : res.anomalies(),
              nullToEmpty(res.rankingWarning()),
              res.effectiveDate(),
              res.topGainers() == null ? java.util.List.of() : res.topGainers(),
              res.topLosers() == null ? java.util.List.of() : res.topLosers(),
              res.mostMentionedTop() == null ? java.util.List.of() : res.mostMentionedTop(),
              res.kospiTopGainer(),
              res.kospiTopLoser(),
              res.kosdaqTopGainer(),
              res.kosdaqTopLoser(),
              res.kospiTopGainerCode(),
              res.kospiTopLoserCode(),
              res.kosdaqTopGainerCode(),
              res.kosdaqTopLoserCode(),
              res.kospiTopGainerRate(),
              res.kospiTopLoserRate(),
              res.kosdaqTopGainerRate(),
              res.kosdaqTopLoserRate(),
              res.kospiTopGainers() == null ? java.util.List.of() : res.kospiTopGainers(),
              res.kospiTopLosers() == null ? java.util.List.of() : res.kospiTopLosers(),
              res.kosdaqTopGainers() == null ? java.util.List.of() : res.kosdaqTopGainers(),
              res.kosdaqTopLosers() == null ? java.util.List.of() : res.kosdaqTopLosers()));
    } catch (Exception e) {
      log.info(
          "pykrx leaders unavailable: date={}, reason={}",
          date,
          e.getClass().getSimpleName() + ":" + (e.getMessage() == null ? "" : e.getMessage()));
      return Optional.empty();
    }
  }

  private VerificationResult verify(DailyMarketBrief candidate, DailyMarketBrief reference) {
    List<String> diffs = new ArrayList<>();

    if (!same(candidate.topGainer(), reference.topGainer())) {
      diffs.add("topGainer candidate='" + candidate.topGainer() + "' ref='" + reference.topGainer() + "'");
    }
    if (!same(candidate.topLoser(), reference.topLoser())) {
      diffs.add("topLoser candidate='" + candidate.topLoser() + "' ref='" + reference.topLoser() + "'");
    }
    // mostMentioned is derived from Naver board posts (heuristic/partial collection) and can be
    // non-deterministic across calls even for the same date. Exclude it from strict verification.
    if (!same(candidate.kospiPick(), reference.kospiPick())) {
      diffs.add("kospiPick candidate='" + candidate.kospiPick() + "' ref='" + reference.kospiPick() + "'");
    }
    if (!same(candidate.kosdaqPick(), reference.kosdaqPick())) {
      diffs.add(
          "kosdaqPick candidate='" + candidate.kosdaqPick() + "' ref='" + reference.kosdaqPick() + "'");
    }

    if (diffs.isEmpty()) {
      return new VerificationResult(true, "match=100%");
    }
    return new VerificationResult(false, String.join(", ", diffs));
  }

  private DailyMarketBrief appendVerification(
      DailyMarketBrief brief, String stage, int attempt, boolean matched, String reason) {
    String extra =
        "verification: stage="
            + stage
            + ", attempt="
            + attempt
            + ", matched="
            + matched
            + ", reason="
            + reason;
    String notes = brief.notes() == null || brief.notes().isBlank() ? extra : brief.notes() + "\n" + extra;
    return new DailyMarketBrief(
        brief.topGainer(),
        brief.topLoser(),
        brief.filteredTopGainer(),
        brief.filteredTopLoser(),
        brief.mostMentioned(),
        brief.kospiPick(),
        brief.kosdaqPick(),
        brief.source(),
        notes,
        brief.anomalies(),
        brief.rankingWarning(),
        brief.effectiveDate(),
        brief.topGainers(),
        brief.topLosers(),
        brief.mostMentionedTop(),
        brief.kospiTopGainer(),
        brief.kospiTopLoser(),
        brief.kosdaqTopGainer(),
        brief.kosdaqTopLoser(),
        brief.kospiTopGainerCode(),
        brief.kospiTopLoserCode(),
        brief.kosdaqTopGainerCode(),
        brief.kosdaqTopLoserCode(),
        brief.kospiTopGainerRate(),
        brief.kospiTopLoserRate(),
        brief.kosdaqTopGainerRate(),
        brief.kosdaqTopLoserRate(),
        brief.kospiTopGainers(),
        brief.kospiTopLosers(),
        brief.kosdaqTopGainers(),
        brief.kosdaqTopLosers());
  }

  private boolean same(String a, String b) {
    return (a == null ? "" : a.trim()).equals(b == null ? "" : b.trim());
  }

  private String sourceUsedFromNotes(String notes) {
    if (notes.contains("Source: pykrx") || notes.contains("Source: pykrx(")) {
      return "pykrx";
    }
    if (notes.contains("Source: finance-datareader") || notes.contains("Source: finance-datareader(")) {
      return "fdr";
    }
    if (notes.contains("Source: naver(")) {
      return "naver";
    }
    return "fallback";
  }

  private String confidenceFor(String sourceUsed) {
    if ("pykrx".equals(sourceUsed) || "fdr".equals(sourceUsed)) {
      return "high";
    }
    return "low";
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private static boolean isMissingSummaryValue(String s) {
    return s == null
        || s.isBlank()
        || "-".equals(s.trim())
        || s.matches("(?i)^(TOP_GAINER|TOP_LOSER|MOST_MENTIONED|KOSPI_PICK|KOSDAQ_PICK)_\\d{4}-\\d{2}-\\d{2}$");
  }

  private static String summaryValueOrDash(String s) {
    return isMissingSummaryValue(s) ? "-" : s;
  }

  private static String blankToDash(String s) {
    return isBlank(s) ? "-" : s;
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  private static String blankToDash(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return "-";
  }

  private record VerificationResult(boolean ok, String reason) {}

  private record PykrxLeadersResponse(
      String date,
      String effectiveDate,
      Boolean marketClosed,
      String marketClosedReason,
      java.util.List<String> evidenceLinks,
      String topGainer,
      String topLoser,
      String rawTopGainer,
      String rawTopLoser,
      String filteredTopGainer,
      String filteredTopLoser,
      String mostMentioned,
      String kospiPick,
      String kosdaqPick,
      String topGainerCode,
      String topLoserCode,
      String rawTopGainerCode,
      String rawTopLoserCode,
      String filteredTopGainerCode,
      String filteredTopLoserCode,
      String mostMentionedCode,
      String kospiPickCode,
      String kosdaqPickCode,
      java.util.List<DailyMarketBrief.LeaderEntry> topGainers,
      java.util.List<DailyMarketBrief.LeaderEntry> topLosers,
      java.util.List<DailyMarketBrief.MostMentionedEntry> mostMentionedTop,
      java.util.List<DailyMarketBrief.AnomalyCandidate> anomalies,
      String rankingWarning,
      String source,
      String notes,
      String kospiTopGainer,
      String kospiTopLoser,
      String kosdaqTopGainer,
      String kosdaqTopLoser,
      String kospiTopGainerCode,
      String kospiTopLoserCode,
      String kosdaqTopGainerCode,
      String kosdaqTopLoserCode,
      Double kospiTopGainerRate,
      Double kospiTopLoserRate,
      Double kosdaqTopGainerRate,
      Double kosdaqTopLoserRate,
      java.util.List<DailyMarketBrief.LeaderEntry> kospiTopGainers,
      java.util.List<DailyMarketBrief.LeaderEntry> kospiTopLosers,
      java.util.List<DailyMarketBrief.LeaderEntry> kosdaqTopGainers,
      java.util.List<DailyMarketBrief.LeaderEntry> kosdaqTopLosers) {}

  public LocalDate todaySeoul() {
    return LocalDate.now(ZoneId.of("Asia/Seoul"));
  }
}
