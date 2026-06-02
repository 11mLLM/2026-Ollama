package com.krbrief.portfolio;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {
  private final PortfolioService service;

  public PortfolioController(PortfolioService service) {
    this.service = service;
  }

  @GetMapping
  public PortfolioResponse get() {
    return service.get();
  }

  @PostMapping("/items")
  public PortfolioResponse upsert(@RequestBody PortfolioItemRequest request) {
    return service.upsert(request);
  }

  @PutMapping("/items/{code}")
  public PortfolioResponse updateWeight(@PathVariable String code, @RequestBody PortfolioItemRequest request) {
    return service.updateWeight(code, request);
  }

  @DeleteMapping("/items/{code}")
  public PortfolioResponse delete(@PathVariable String code) {
    return service.delete(code);
  }
}
