package com.krbrief.summaries;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class SummaryApiTest {
  @Container
  static final MySQLContainer<?> mysql =
      new MySQLContainer<>("mysql:8.0")
          .withDatabaseName("testdb")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", mysql::getJdbcUrl);
    r.add("spring.datasource.username", mysql::getUsername);
    r.add("spring.datasource.password", mysql::getPassword);
  }

  @Autowired MockMvc mvc;

  @Test
  void generate_then_getByDate() throws Exception {
    mvc.perform(post("/api/summaries/2026-02-15/generate"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value("2026-02-15"))
        .andExpect(jsonPath("$.marketClosed").exists())
        .andExpect(jsonPath("$.topGainer").value("TOP_GAINER_2026-02-15"))
        .andExpect(jsonPath("$.rawTopGainer").value("TOP_GAINER_2026-02-15"))
        .andExpect(jsonPath("$.rawTopLoser").value("TOP_LOSER_2026-02-15"))
        .andExpect(jsonPath("$.filteredTopGainer").value("TOP_GAINER_2026-02-15"))
        .andExpect(jsonPath("$.filteredTopLoser").value("TOP_LOSER_2026-02-15"))
        .andExpect(jsonPath("$.rankingWarning").value(""))
        .andExpect(jsonPath("$.anomalies").isArray())
        .andExpect(jsonPath("$.verification.date").value("2026-02-15"))
        .andExpect(jsonPath("$.verification.primaryKrxArtifact").value("/api/summaries/2026-02-15/verification/krx"))
        .andExpect(jsonPath("$.verification.primarySourceTier").value(org.hamcrest.Matchers.containsString("Primary=KRX")))
        .andExpect(jsonPath("$.verification.secondarySourceTier").value(org.hamcrest.Matchers.containsString("Secondary=Naver")))
        .andExpect(jsonPath("$.verification.krxDataPortal").value("https://data.krx.co.kr/"))
        .andExpect(jsonPath("$.verification.topGainerDateSearch").value(org.hamcrest.Matchers.containsString("finance.naver.com/item/sise_day.naver")))
        .andExpect(jsonPath("$.verification.topLoserDateSearch").value(org.hamcrest.Matchers.containsString("finance.naver.com/item/sise_day.naver")))
        .andExpect(jsonPath("$.verification.mostMentionedDateSearch").value(org.hamcrest.Matchers.containsString("finance.naver.com/item/sise_day.naver")))
        .andExpect(jsonPath("$.verification.kospiPickDateSearch").value(org.hamcrest.Matchers.containsString("finance.naver.com/item/sise_day.naver")))
        .andExpect(jsonPath("$.verification.kosdaqPickDateSearch").value(org.hamcrest.Matchers.containsString("finance.naver.com/item/sise_day.naver")))
        .andExpect(jsonPath("$.verification.topGainerItem.value").value("TOP_GAINER_2026-02-15"))
        .andExpect(jsonPath("$.verification.topGainerItem.sourceType").value("official_computable"))
        .andExpect(jsonPath("$.verification.topGainerItem.sourceName").value("pykrx(KRX-based)"))
        .andExpect(jsonPath("$.verification.topLoserItem.sourceType").value("official_computable"))
        .andExpect(jsonPath("$.verification.mostMentionedItem.sourceType").value("derived_rule"))
        .andExpect(jsonPath("$.verification.kospiPickItem.sourceType").value("derived_rule"))
        .andExpect(jsonPath("$.verification.kosdaqPickItem.sourceType").value("derived_rule"))
        .andExpect(jsonPath("$.verification.verificationLimitations").value(org.hamcrest.Matchers.containsString("KRX")))
        .andExpect(jsonPath("$.leaderExplanations.topGainer.level").exists())
        .andExpect(jsonPath("$.leaderExplanations.topGainer.summary").exists())
        .andExpect(jsonPath("$.leaderExplanations.topGainer.evidenceLinks").isArray())
        .andExpect(jsonPath("$.leaderExplanations.topLoser.level").exists())
        .andExpect(jsonPath("$.content").exists())
        .andExpect(jsonPath("$.generatedAt").exists())
        .andExpect(jsonPath("$.effectiveDate").exists())
        .andExpect(jsonPath("$.topGainers").isArray())
        .andExpect(jsonPath("$.topLosers").isArray())
        .andExpect(jsonPath("$.mostMentionedTop").isArray());

    mvc.perform(get("/api/summaries/2026-02-15"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value("2026-02-15"))
        .andExpect(jsonPath("$.marketClosed").exists());

    mvc.perform(get("/api/summaries/2026-02-15/verification/krx"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tradeDate").value("2026-02-15"))
        .andExpect(jsonPath("$.status").value("unverified"))
        .andExpect(jsonPath("$.unverifiedReason").value(org.hamcrest.Matchers.containsString("krx_reference_unavailable")))
        .andExpect(jsonPath("$.rawSourceIdentity.datasetCode").value("MDCSTAT01501"))
        .andExpect(jsonPath("$.computedTopGainerTopLoserBasis.metric").value("등락률(%)"))
        .andExpect(jsonPath("$.evidenceRecords[0].field").value("topGainer"))
        .andExpect(jsonPath("$.evidenceRecords[1].field").value("topLoser"));
  }

  @Test
  void generate_onMarketClosedDay_returnsMarketClosedStatus() throws Exception {
    mvc.perform(post("/api/summaries/2026-02-14/generate"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value("2026-02-14"))
        .andExpect(jsonPath("$.marketClosed").exists());
  }

  @Test
  void list_range() throws Exception {
    mvc.perform(post("/api/summaries/2026-02-01/generate")).andExpect(status().isOk());
    mvc.perform(post("/api/summaries/2026-02-02/generate")).andExpect(status().isOk());

    mvc.perform(get("/api/summaries?from=2026-02-01&to=2026-02-28"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].date").value("2026-02-01"));
  }

  @Test
  void pykrxLeadersResponse_withNewFields_deserializesWithoutError() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    String json = """
        {
          "date": "2026-02-20",
          "effectiveDate": "2026-02-20",
          "marketClosed": false,
          "marketClosedReason": null,
          "evidenceLinks": [],
          "topGainer": "삼성전자",
          "topLoser": "LG에너지솔루션",
          "rawTopGainer": "삼성전자",
          "rawTopLoser": "LG에너지솔루션",
          "filteredTopGainer": "삼성전자",
          "filteredTopLoser": "LG에너지솔루션",
          "mostMentioned": "SK하이닉스",
          "kospiPick": "현대차",
          "kosdaqPick": "에코프로비엠",
          "topGainerCode": "005930",
          "topLoserCode": "373220",
          "rawTopGainerCode": "005930",
          "rawTopLoserCode": "373220",
          "filteredTopGainerCode": "005930",
          "filteredTopLoserCode": "373220",
          "mostMentionedCode": "000660",
          "kospiPickCode": "005380",
          "kosdaqPickCode": "247540",
          "topGainers": [
            {"code": "005930", "name": "삼성전자", "rate": 5.2},
            {"code": "000660", "name": "SK하이닉스", "rate": 4.8}
          ],
          "topLosers": [
            {"code": "373220", "name": "LG에너지솔루션", "rate": -3.5}
          ],
          "mostMentionedTop": [
            {"code": "000660", "name": "SK하이닉스", "count": 15}
          ],
          "anomalies": [],
          "rankingWarning": null,
          "source": "pykrx",
          "notes": "test notes"
        }
        """;

    var node = mapper.readTree(json);
    assert node.has("effectiveDate");
    assert node.has("topGainers");
    assert node.has("topLosers");
    assert node.has("mostMentionedTop");
    assert node.get("topGainers").isArray();
    assert node.get("topLosers").isArray();
    assert node.get("mostMentionedTop").isArray();
  }

  // ===== Future date blocking tests =====

  @Test
  void generate_futureDate_returns400() throws Exception {
    // Use a date far in the future to ensure it's always after todaySeoul()
    mvc.perform(post("/api/summaries/2099-01-01/generate"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("future_date_not_allowed"));
  }

  @Test
  void archive_futureDate_returns400() throws Exception {
    // Archive requires admin key, but future date check happens first
    mvc.perform(put("/api/summaries/2099-01-01/archive"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("future_date_not_allowed"));
  }

  @Test
  void backfill_futureFromDate_returns400() throws Exception {
    // Backfill requires admin key, but future date check should still work
    mvc.perform(post("/api/summaries/backfill?from=2099-01-01&to=2099-01-05"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("future_date_not_allowed"));
  }

  @Test
  void backfill_futureToDate_returns400() throws Exception {
    // Backfill where 'to' is in the future should also fail
    mvc.perform(post("/api/summaries/backfill?from=2026-02-01&to=2099-01-01"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("future_date_not_allowed"));
  }
}
