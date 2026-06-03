ALTER TABLE daily_summaries
  ADD COLUMN archived_at TIMESTAMP(6) NULL AFTER updated_at;
