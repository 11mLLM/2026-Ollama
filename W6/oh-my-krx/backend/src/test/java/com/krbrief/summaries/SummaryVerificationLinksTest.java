package com.krbrief.summaries;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SummaryVerificationLinksTest {

  @Test
  void buildsPrimaryArtifactAnchorsAndNoSearchLinks() {
    SummaryVerificationLinks links =
        SummaryVerificationLinks.from(
            LocalDate.of(2026, 2, 15),
            "/api/summaries/2026-02-15/verification/krx",
            "A",
            "B",
            "C",
            "D",
            "E",
            "codes: topGainer=111111, topLoser=222222, mostMentioned=333333, kospiPick=444444, kosdaqPick=555555");

    assertEquals("", links.topGainerSearch());
    assertEquals("", links.topLoserSearch());
    assertEquals("", links.mostMentionedSearch());
    assertEquals("", links.kospiPickSearch());
    assertEquals("", links.kosdaqPickSearch());

    assertTrue(links.topGainerItem().directUrl().contains("/verification/krx#topGainer"));
    assertTrue(links.topLoserItem().directUrl().contains("/verification/krx#topLoser"));

    assertTrue(links.topGainerDateSearch().contains("item/sise_day.naver?code=111111"));
    assertTrue(links.topLoserDateSearch().contains("item/sise_day.naver?code=222222"));
    assertTrue(links.mostMentionedDateSearch().contains("item/sise_day.naver?code=333333"));
    assertTrue(links.kospiPickDateSearch().contains("item/sise_day.naver?code=444444"));
    assertTrue(links.kosdaqPickDateSearch().contains("item/sise_day.naver?code=555555"));
  }

  @Test
  void handlesMissingCodesWithoutSearchFallback() {
    SummaryVerificationLinks links =
        SummaryVerificationLinks.from(
            LocalDate.of(2026, 2, 15),
            "/api/summaries/2026-02-15/verification/krx",
            "A",
            "B",
            "C",
            "D",
            "E",
            "");

    assertEquals("", links.topGainerDateSearch());
    assertEquals("", links.topLoserDateSearch());
    assertEquals("", links.mostMentionedDateSearch());
    assertEquals("", links.kospiPickDateSearch());
    assertEquals("", links.kosdaqPickDateSearch());

    assertTrue(links.mostMentionedItem().directUrl().contains("/verification/krx#mostMentioned"));
    assertTrue(links.kospiPickItem().directUrl().contains("/verification/krx#kospiPick"));
    assertTrue(links.kosdaqPickItem().directUrl().contains("/verification/krx#kosdaqPick"));
  }
}
