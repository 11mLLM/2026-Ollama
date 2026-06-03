package com.krbrief.summaries;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.krbrief.marketdata.DailyMarketBrief;
import java.util.List;
import org.junit.jupiter.api.Test;

class SummaryAnomalyCodecTest {

  @Test
  void encodeDecode_roundTrip_preservesReasonAndFlags() {
    List<DailyMarketBrief.AnomalyCandidate> anomalies =
        List.of(
            new DailyMarketBrief.AnomalyCandidate(
                "005930",
                "테스트주",
                128.4,
                List.of("prior_close_zero", "zero_volume_streak"),
                "전일 종가 0원 및 거래량 연속 0 구간 이후 급등으로 비정상 점프 가능성이 큽니다"));

    String encoded = SummaryAnomalyCodec.encode(anomalies);
    List<SummaryDto.AnomalyDto> decoded = SummaryAnomalyCodec.decode(encoded);

    assertEquals(1, decoded.size());
    assertEquals("005930", decoded.get(0).symbol());
    assertEquals("테스트주", decoded.get(0).name());
    assertEquals(128.4, decoded.get(0).rate());
    assertEquals(List.of("prior_close_zero", "zero_volume_streak"), decoded.get(0).flags());
    assertFalse(decoded.get(0).oneLineReason().isBlank());
  }
}
