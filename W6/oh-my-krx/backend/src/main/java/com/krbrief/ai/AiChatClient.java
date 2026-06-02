package com.krbrief.ai;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AiChatClient {
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient http;

  public AiChatClient(
      @Value("${ai.baseUrl:http://ai-service:8100}") String baseUrl,
      @Value("${ai.connectTimeoutSeconds:3}") int connectTimeoutSeconds,
      @Value("${ai.readTimeoutSeconds:28}") int readTimeoutSeconds) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Math.max(1, connectTimeoutSeconds) * 1000);
    requestFactory.setReadTimeout(Math.max(1, readTimeoutSeconds) * 1000);
    this.http = RestClient.builder().requestFactory(requestFactory).baseUrl(baseUrl).build();
  }

  public Map<String, Object> chat(Map<String, Object> request) {
    return postMap("/chat", request, "ai_service_chat_error");
  }

  public Map<String, Object> ollamaInsights(Map<String, Object> request) {
    return postMap("/ollama/insights", request, "ai_service_ollama_insights_error");
  }

  public Map<String, Object> ollamaAfterMarketReport(Map<String, Object> request) {
    return postMap("/ollama/after-market-report", request, "ai_service_ollama_after_market_report_error");
  }

  private Map<String, Object> postMap(String uri, Map<String, Object> request, String errorCode) {
    try {
      Map<String, Object> res =
          http
              .post()
              .uri(uri)
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON)
              .body(request == null ? Map.of() : request)
              .retrieve()
              .body(MAP_TYPE);
      if (res == null) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ai_service_empty_response");
      }
      return res;
    } catch (ResponseStatusException e) {
      throw e;
    } catch (RestClientException e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, errorCode, e);
    }
  }

  public Map<String, Object> status() {
    try {
      Map<String, Object> res =
          http
              .get()
              .uri("/llm/status")
              .accept(MediaType.APPLICATION_JSON)
              .retrieve()
              .body(MAP_TYPE);
      if (res == null) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ai_service_empty_status_response");
      }
      return res;
    } catch (ResponseStatusException e) {
      throw e;
    } catch (RestClientException e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ai_service_status_error", e);
    }
  }
}
