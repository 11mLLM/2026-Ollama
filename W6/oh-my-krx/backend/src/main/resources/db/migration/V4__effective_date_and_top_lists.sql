ALTER TABLE daily_summaries
  ADD COLUMN effective_date VARCHAR(8) NULL AFTER anomalies_text,
  ADD COLUMN top_gainers_json TEXT NULL AFTER effective_date,
  ADD COLUMN top_losers_json TEXT NULL AFTER top_gainers_json,
  ADD COLUMN most_mentioned_top_json TEXT NULL AFTER top_losers_json;
