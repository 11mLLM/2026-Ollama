SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'daily_summaries' AND column_name = 'kospi_top_gainer') = 0,
  'ALTER TABLE daily_summaries ADD COLUMN kospi_top_gainer VARCHAR(255) NULL AFTER most_mentioned_top_json',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'daily_summaries' AND column_name = 'kospi_top_loser') = 0,
  'ALTER TABLE daily_summaries ADD COLUMN kospi_top_loser VARCHAR(255) NULL AFTER kospi_top_gainer',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'daily_summaries' AND column_name = 'kosdaq_top_gainer') = 0,
  'ALTER TABLE daily_summaries ADD COLUMN kosdaq_top_gainer VARCHAR(255) NULL AFTER kospi_top_loser',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'daily_summaries' AND column_name = 'kosdaq_top_loser') = 0,
  'ALTER TABLE daily_summaries ADD COLUMN kosdaq_top_loser VARCHAR(255) NULL AFTER kosdaq_top_gainer',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'daily_summaries' AND column_name = 'kospi_top_gainer_code') = 0,
  'ALTER TABLE daily_summaries ADD COLUMN kospi_top_gainer_code VARCHAR(255) NULL AFTER kosdaq_top_loser',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'daily_summaries' AND column_name = 'kospi_top_loser_code') = 0,
  'ALTER TABLE daily_summaries ADD COLUMN kospi_top_loser_code VARCHAR(255) NULL AFTER kospi_top_gainer_code',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'daily_summaries' AND column_name = 'kosdaq_top_gainer_code') = 0,
  'ALTER TABLE daily_summaries ADD COLUMN kosdaq_top_gainer_code VARCHAR(255) NULL AFTER kospi_top_loser_code',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'daily_summaries' AND column_name = 'kosdaq_top_loser_code') = 0,
  'ALTER TABLE daily_summaries ADD COLUMN kosdaq_top_loser_code VARCHAR(255) NULL AFTER kosdaq_top_gainer_code',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'daily_summaries' AND column_name = 'kospi_top_gainer_rate') = 0,
  'ALTER TABLE daily_summaries ADD COLUMN kospi_top_gainer_rate DOUBLE NULL AFTER kosdaq_top_loser_code',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'daily_summaries' AND column_name = 'kospi_top_loser_rate') = 0,
  'ALTER TABLE daily_summaries ADD COLUMN kospi_top_loser_rate DOUBLE NULL AFTER kospi_top_gainer_rate',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'daily_summaries' AND column_name = 'kosdaq_top_gainer_rate') = 0,
  'ALTER TABLE daily_summaries ADD COLUMN kosdaq_top_gainer_rate DOUBLE NULL AFTER kospi_top_loser_rate',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'daily_summaries' AND column_name = 'kosdaq_top_loser_rate') = 0,
  'ALTER TABLE daily_summaries ADD COLUMN kosdaq_top_loser_rate DOUBLE NULL AFTER kosdaq_top_gainer_rate',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'daily_summaries' AND column_name = 'kospi_top_gainers_json') = 0,
  'ALTER TABLE daily_summaries ADD COLUMN kospi_top_gainers_json TEXT NULL AFTER kosdaq_top_loser_rate',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'daily_summaries' AND column_name = 'kospi_top_losers_json') = 0,
  'ALTER TABLE daily_summaries ADD COLUMN kospi_top_losers_json TEXT NULL AFTER kospi_top_gainers_json',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'daily_summaries' AND column_name = 'kosdaq_top_gainers_json') = 0,
  'ALTER TABLE daily_summaries ADD COLUMN kosdaq_top_gainers_json TEXT NULL AFTER kospi_top_losers_json',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'daily_summaries' AND column_name = 'kosdaq_top_losers_json') = 0,
  'ALTER TABLE daily_summaries ADD COLUMN kosdaq_top_losers_json TEXT NULL AFTER kosdaq_top_gainers_json',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
