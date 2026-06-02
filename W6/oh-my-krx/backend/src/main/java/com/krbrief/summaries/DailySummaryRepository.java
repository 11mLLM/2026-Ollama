package com.krbrief.summaries;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DailySummaryRepository extends JpaRepository<DailySummary, LocalDate> {
  List<DailySummary> findAllByDateBetweenAndArchivedAtIsNullOrderByDateAsc(LocalDate from, LocalDate to);

  java.util.Optional<DailySummary> findTopByArchivedAtIsNullOrderByDateDesc();

  java.util.Optional<DailySummary> findByDateAndArchivedAtIsNull(LocalDate date);

  long countByArchivedAtIsNull();
}

