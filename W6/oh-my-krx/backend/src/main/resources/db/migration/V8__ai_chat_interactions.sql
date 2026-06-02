CREATE TABLE IF NOT EXISTS ai_chat_interactions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  stock_code VARCHAR(6) NULL,
  stock_name VARCHAR(120) NULL,
  question TEXT NULL,
  response_mode VARCHAR(80) NULL,
  provider VARCHAR(80) NULL,
  model VARCHAR(160) NULL,
  confidence VARCHAR(40) NULL,
  basis_date VARCHAR(24) NULL,
  answer_preview VARCHAR(520) NULL,
  answer_text TEXT NULL,
  sources_json TEXT NULL,
  limitations_json TEXT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  INDEX idx_ai_chat_interactions_stock_created (stock_code, created_at),
  INDEX idx_ai_chat_interactions_created (created_at)
);
