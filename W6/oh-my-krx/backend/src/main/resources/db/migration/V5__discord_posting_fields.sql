ALTER TABLE daily_summaries
  ADD COLUMN discord_posted_at TIMESTAMP NULL AFTER archived_at,
  ADD COLUMN discord_message_id VARCHAR(64) NULL AFTER discord_posted_at,
  ADD COLUMN discord_channel_id VARCHAR(32) NULL AFTER discord_message_id,
  ADD COLUMN discord_thread_id VARCHAR(32) NULL AFTER discord_channel_id;
