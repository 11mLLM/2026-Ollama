package com.krbrief.summaries;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record KrxVerificationArtifactDto(
    LocalDate tradeDate,
    Instant generatedAt,
    String status,
    String unverifiedReason,
    SourceIdentity rawSourceIdentity,
    ComputationBasis computedTopGainerTopLoserBasis,
    List<VerificationEvidenceRecord> evidenceRecords) {

  public record SourceIdentity(String datasetName, String datasetCode, String officialPortalUrl) {}

  public record ComputationBasis(String metric, String topGainerRule, String topLoserRule, String notes) {}

  public record VerificationEvidenceRecord(
      String field, String summaryValue, String krxReferenceValue, boolean matched) {}
}
