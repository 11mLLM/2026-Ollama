package com.krbrief.marketdata;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Best-effort KRX download via OTP flow.
 *
 * <p>This is intentionally simple (internal use). If the KRX endpoints change, we can patch it.
 */
@Component
@ConditionalOnProperty(name = "marketdata.provider", havingValue = "krx")
public class KrxMarketDataClient implements MarketDataClient {
  private final RestClient http;

  // These endpoints are commonly used for the KRX data download flow.
  // If they change, adjust here.
  private final String generateOtpPath;
  private final String downloadPath;

  public KrxMarketDataClient(
      @Value("${marketdata.krx.baseUrl:https://data.krx.co.kr}") String baseUrl,
      @Value("${marketdata.krx.generateOtpPath:/comm/fileDn/GenerateOTP/generate.cmd}")
          String generateOtpPath,
      @Value("${marketdata.krx.downloadPath:/comm/fileDn/download_csv/download.cmd}")
          String downloadPath) {
    this.http = RestClient.builder().baseUrl(baseUrl).build();
    this.generateOtpPath = generateOtpPath;
    this.downloadPath = downloadPath;
  }

  @Override
  public Optional<DailyMarketBrief> getDailyBrief(LocalDate date) {
    try {
      // NOTE: For MVP we fetch the "전종목 등락률" style dataset and pick max/min.
      // The exact bld + parameters may need tweaking depending on KRX.
      String ymd = date.toString().replace("-", "");

      MultiValueMap<String, String> otpForm = new LinkedMultiValueMap<>();
      // Common params seen in KRX examples
      otpForm.add("mktId", "ALL");
      otpForm.add("trdDd", ymd);
      otpForm.add("money", "1");
      otpForm.add("csvxls_isNo", "false");
      otpForm.add("name", "fileDown");
      // bld value: dataset builder id (may change)
      otpForm.add("bld", "dbms/MDC/STAT/standard/MDCSTAT01501");

      String otp =
          http
              .post()
              .uri(generateOtpPath)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(otpForm)
              .retrieve()
              .body(String.class);

      if (otp == null || otp.isBlank()) {
        return Optional.of(
            new DailyMarketBrief(
                "-",
                "-",
                "-",
                "-",
                "-",
                "krx",
                "KRX OTP generation returned empty response (endpoint/params may have changed)"));
      }

      MultiValueMap<String, String> dlForm = new LinkedMultiValueMap<>();
      dlForm.add("code", otp.trim());

      String csv =
          http
              .post()
              .uri(downloadPath)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .accept(new MediaType("text", "csv", StandardCharsets.UTF_8))
              .body(dlForm)
              .retrieve()
              .body(String.class);

      if (csv == null || csv.isBlank()) {
        return Optional.of(
            new DailyMarketBrief(
                "-",
                "-",
                "-",
                "-",
                "-",
                "krx",
                "KRX CSV download returned empty response (OTP ok, download failed)"));
      }

      // TODO: parse CSV properly and compute top gainer/loser.
      // For now we return placeholders but keep the real CSV snippet in notes for debugging.
      String firstLine = csv.lines().findFirst().orElse("");
      return Optional.of(
          new DailyMarketBrief(
              "-",
              "-",
              "-",
              "-",
              "-",
              "krx(otp-download)",
              "Fetched CSV (not parsed yet). Header: " + firstLine));
    } catch (Exception e) {
      return Optional.of(
          new DailyMarketBrief(
              "-",
              "-",
              "-",
              "-",
              "-",
              "krx_error",
              "KRX fetch failed: " + e.getClass().getSimpleName() + ": " + e.getMessage()));
    }
  }
}
