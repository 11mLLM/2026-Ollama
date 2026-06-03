package com.krbrief.learning;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learning")
public class LearningController {
  private final LearningTermCatalog catalog;
  private final LearningAssistantService assistantService;

  public LearningController(LearningTermCatalog catalog, LearningAssistantService assistantService) {
    this.catalog = catalog;
    this.assistantService = assistantService;
  }

  @GetMapping("/terms")
  public List<LearningTermDto> terms(
      @RequestParam(value = "query", required = false) String query,
      @RequestParam(value = "category", required = false) String category,
      @RequestParam(value = "limit", required = false) Integer limit) {
    return catalog.list(query, category, limit);
  }

  @GetMapping("/terms/{id}")
  public ResponseEntity<LearningTermDto> term(@PathVariable("id") String id) {
    return catalog.findById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping("/assistant")
  public LearningAssistantResponse assistant(@RequestBody(required = false) LearningAssistantRequest request) {
    return assistantService.answer(request);
  }
}
