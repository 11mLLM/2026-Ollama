ALTER TABLE daily_summaries
  ADD COLUMN filtered_top_gainer VARCHAR(255) NULL AFTER top_loser,
  ADD COLUMN filtered_top_loser VARCHAR(255) NULL AFTER filtered_top_gainer,
  ADD COLUMN ranking_warning TEXT NULL AFTER raw_notes,
  ADD COLUMN anomalies_text TEXT NULL AFTER ranking_warning;
