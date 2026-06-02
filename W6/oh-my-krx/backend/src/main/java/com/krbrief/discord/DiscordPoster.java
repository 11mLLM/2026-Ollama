package com.krbrief.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krbrief.summaries.DailySummary;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Posts a generated daily summary to Discord via Webhook.
 *
 * <p>Enable by setting DISCORD_WEBHOOK_URL. To post into an existing thread, also set
 * DISCORD_THREAD_ID.
 */
@Component
public class DiscordPoster {
  private static final Logger log = LoggerFactory.getLogger(DiscordPoster.class);
  private static final ObjectMapper JSON = new ObjectMapper();

  private final String webhookUrl;
  private final String threadId;
  private final RestClient http;

  public DiscordPoster(
      @Value("${discord.webhookUrl:}") String webhookUrl,
      @Value("${discord.threadId:}") String threadId) {
    this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
    this.threadId = threadId == null ? "" : threadId.trim();
    this.http = RestClient.builder().build();
  }

  public boolean enabled() {
    return webhookUrl != null && !webhookUrl.isBlank();
  }

  /**
   * @return Map containing postedAt/messageId/channelId/threadId if succeeded; empty if disabled.
   */
  public Result post(DailySummary s) {
    if (!enabled()) {
      return Result.disabled();
    }

    String url = buildExecuteUrl(webhookUrl, threadId);
    String content = renderDiscordContent(s);

    // Avoid accidental mentions.
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("content", content);
    payload.put("allowed_mentions", Map.of("parse", new Object[] {}));

    try {
      String body =
          http.post()
              .uri(URI.create(url))
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON)
              .body(payload)
              .retrieve()
              .body(String.class);

      String messageId = "";
      if (body != null && !body.isBlank()) {
        JsonNode node = JSON.readTree(body);
        messageId = node.path("id").asText("");
      }

      return new Result(true, Instant.now(), messageId, "", threadId);
    } catch (Exception e) {
      // Do not fail generation if Discord post fails.
      log.warn("discord webhook post failed: {}", e.getMessage());
      return Result.failed(e.getClass().getSimpleName() + ":" + (e.getMessage() == null ? "" : e.getMessage()));
    }
  }

  private String buildExecuteUrl(String base, String threadId) {
    if (threadId == null || threadId.isBlank()) return base;
    // Discord supports thread_id query param for webhook execute.
    return base.contains("?") ? (base + "&thread_id=" + threadId) : (base + "?thread_id=" + threadId);
  }

  private String renderDiscordContent(DailySummary s) {
    // Keep it short and scannable.
    StringBuilder sb = new StringBuilder();
    sb.append("[" + s.getDate() + "] 한국 주식 일간 브리프\n");
    sb.append("- 최대 상승: " + dash(s.getTopGainer()) + "\n");
    sb.append("- 최대 하락: " + dash(s.getTopLoser()) + "\n");
    sb.append("- 최다 언급(토론방): " + dash(s.getMostMentioned()) + "\n");
    sb.append("- 코스피 픽: " + dash(s.getKospiPick()) + "\n");
    sb.append("- 코스닥 픽: " + dash(s.getKosdaqPick()) + "\n");
    return sb.toString();
  }

  private static String dash(String s) {
    return s == null || s.isBlank() ? "-" : s;
  }

  public record Result(
      boolean ok,
      Instant postedAt,
      String messageId,
      String channelId,
      String threadId,
      String reason) {
    static Result disabled() {
      return new Result(false, null, "", "", "", "disabled");
    }

    static Result failed(String reason) {
      return new Result(false, null, "", "", "", reason);
    }

    Result(boolean ok, Instant postedAt, String messageId, String channelId, String threadId) {
      this(ok, postedAt, messageId, channelId, threadId, "");
    }
  }
}
