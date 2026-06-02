package com.krbrief.search;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {
  private final SearchService service;

  public SearchController(SearchService service) {
    this.service = service;
  }

  @GetMapping
  public List<SearchResultDto> search(
      @RequestParam(value = "query", required = false) String query,
      @RequestParam(value = "limit", required = false) Integer limit) {
    return service.search(query, limit);
  }
}
