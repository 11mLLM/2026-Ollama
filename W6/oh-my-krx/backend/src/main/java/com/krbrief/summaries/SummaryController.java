package com.krbrief.summaries;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import com.krbrief.config.AdminKeyGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@RestController
@RequestMapping("/api/summaries")
@Validated
public class SummaryController {
  private final DailySummaryService service;
  private final AdminKeyGuard admin;

  public SummaryController(DailySummaryService service, AdminKeyGuard admin) {
    this.service = service;
    this.admin = admin;
  }

  @GetMapping
  public List<SummaryDto> list(
      @RequestParam("from") @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam("to") @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    if (from.isAfter(to)) {
      throw new ResponseStatusException(BAD_REQUEST, "from_must_be_on_or_before_to");
    }
    return service.list(from, to).stream().map(SummaryDto::from).toList();
  }

  @GetMapping("/insights")
  public SummaryInsightsDto insights(
      @RequestParam("from") @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam("to") @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    if (from.isAfter(to)) {
      throw new ResponseStatusException(BAD_REQUEST, "from_must_be_on_or_before_to");
    }
    return service.insights(from, to);
  }

  @GetMapping("/latest")
  public ResponseEntity<SummaryDto> latest() {
    return service.latest().map(s -> ResponseEntity.ok(SummaryDto.from(s)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/stats")
  public SummaryStatsDto stats() {
    return service.stats();
  }

  @GetMapping("/{date:\\d{4}-\\d{2}-\\d{2}}")
  public ResponseEntity<SummaryDto> get(
      @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return service.get(date).map(s -> ResponseEntity.ok(SummaryDto.from(s)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/{date:\\d{4}-\\d{2}-\\d{2}}/verification/krx")
  public ResponseEntity<KrxVerificationArtifactDto> getKrxVerificationArtifact(
      @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return service.krxVerificationArtifact(date).map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping("/{date:\\d{4}-\\d{2}-\\d{2}}/generate")
  public SummaryDto generate(
      HttpServletRequest request,
      @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    // Future dates are not allowed
    if (date.isAfter(service.todaySeoul())) {
      throw new ResponseStatusException(BAD_REQUEST, "future_date_not_allowed");
    }
    // If already exists, only admin can regenerate/overwrite.
    if (service.existsAny(date) && !admin.isAdmin(request)) {
      throw new ResponseStatusException(CONFLICT, "summary_already_exists_admin_only_regenerate");
    }
    return SummaryDto.from(service.generate(date));
  }

  @PutMapping("/{date:\\d{4}-\\d{2}-\\d{2}}/archive")
  public ResponseEntity<SummaryDto> archive(
      HttpServletRequest request,
      @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    // Future dates are not allowed
    if (date.isAfter(service.todaySeoul())) {
      throw new ResponseStatusException(BAD_REQUEST, "future_date_not_allowed");
    }
    if (!admin.isAdmin(request)) {
      throw new ResponseStatusException(FORBIDDEN, "admin_only");
    }
    return service.archive(date).map(s -> ResponseEntity.ok(SummaryDto.from(s)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping("/backfill")
  public BackfillResponseDto backfill(
      HttpServletRequest request,
      @RequestParam("from") @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam("to") @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    if (!admin.isAdmin(request)) {
      throw new ResponseStatusException(FORBIDDEN, "admin_only");
    }
    if (from.isAfter(to)) {
      throw new ResponseStatusException(BAD_REQUEST, "from_must_be_on_or_before_to");
    }
    // Future dates are not allowed
    LocalDate today = service.todaySeoul();
    if (from.isAfter(today) || to.isAfter(today)) {
      throw new ResponseStatusException(BAD_REQUEST, "future_date_not_allowed");
    }
    return service.backfill(from, to);
  }

  @PostMapping("/generate/today")
  public SummaryDto generateToday(HttpServletRequest request) {
    LocalDate today = service.todaySeoul();
    if (service.existsAny(today) && !admin.isAdmin(request)) {
      throw new ResponseStatusException(CONFLICT, "summary_already_exists_admin_only_regenerate");
    }
    return SummaryDto.from(service.generate(today));
  }
}
