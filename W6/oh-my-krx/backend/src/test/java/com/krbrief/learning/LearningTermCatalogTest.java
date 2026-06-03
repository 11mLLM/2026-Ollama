package com.krbrief.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LearningTermCatalogTest {

  @Test
  void list_canSearchBeginnerTerms() {
    LearningTermCatalog catalog = new LearningTermCatalog();

    var terms = catalog.list("PER", null, 10);

    assertFalse(terms.isEmpty());
    assertTrue(terms.stream().anyMatch(term -> term.id().equals("per")));
    assertTrue(terms.stream().anyMatch(term -> term.plainDefinition().contains("주당순이익")));
    assertTrue(terms.stream().anyMatch(term -> term.coreSummary().contains("주당순이익")));
    assertTrue(terms.stream().anyMatch(term -> !term.longExplanation().isBlank()));
    assertTrue(terms.stream().anyMatch(term -> !term.chartUsage().isBlank()));
    assertTrue(terms.stream().anyMatch(term -> !term.commonMisunderstanding().isBlank()));
    assertTrue(terms.stream().anyMatch(term -> !term.scenario().isBlank()));
  }

  @Test
  void list_containsExpandedBeginnerCurriculum() {
    LearningTermCatalog catalog = new LearningTermCatalog();

    var terms = catalog.list(null, null, 120);

    assertTrue(terms.size() >= 40);
    assertTrue(terms.stream().anyMatch(term -> term.id().equals("stop-loss")));
    assertTrue(terms.stream().anyMatch(term -> term.id().equals("etf")));
    assertTrue(terms.stream().anyMatch(term -> term.id().equals("bid-ask")));
    assertTrue(terms.stream().anyMatch(term -> term.exampleQuestions().size() >= 3));
    assertTrue(terms.stream().anyMatch(term -> term.relatedQuestions().equals(term.exampleQuestions())));
  }

  @Test
  void list_canSearchNewTradingTerms() {
    LearningTermCatalog catalog = new LearningTermCatalog();

    var terms = catalog.list("괴리율", null, 10);

    assertFalse(terms.isEmpty());
    assertTrue(terms.stream().anyMatch(term -> term.id().equals("nav-disparity")));
    assertTrue(terms.stream().anyMatch(term -> term.term().contains("NAV")));
  }

  @Test
  void match_usesPreferredTermFirst() {
    LearningTermCatalog catalog = new LearningTermCatalog();

    var terms = catalog.match("거래량은 왜 중요해요?", "per");

    assertFalse(terms.isEmpty());
    assertEquals("per", terms.get(0).id());
    assertTrue(terms.stream().anyMatch(t -> t.id().equals("volume")));
  }
}
