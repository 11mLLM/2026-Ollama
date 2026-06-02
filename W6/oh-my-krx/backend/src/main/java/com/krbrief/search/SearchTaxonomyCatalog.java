package com.krbrief.search;

import java.util.List;

final class SearchTaxonomyCatalog {
  private SearchTaxonomyCatalog() {}

  static List<SearchResultDto> baseline() {
    return List.of(
        theme("semiconductor", "반도체", "+2.4%", List.of("AI 반도체", "HBM", "메모리", "장비", "소부장"),
            "AI 서버 투자, 메모리 가격, 장비 수주, 환율을 함께 보는 대표 성장 테마입니다."),
        theme("battery", "2차전지", "-1.1%", List.of("배터리", "양극재", "전기차", "리튬", "소재"),
            "전기차 수요, 원재료 가격, 고객사 재고, 정책 뉴스가 주가 변동과 자주 연결됩니다."),
        theme("ai", "AI", "+1.8%", List.of("반도체", "클라우드", "로봇", "데이터센터"),
            "AI 인프라 투자와 소프트웨어 기대가 반도체, 인터넷, 로봇 종목으로 확산되는지 확인합니다."),
        theme("bio", "바이오", "+0.6%", List.of("CDMO", "신약", "임상", "바이오시밀러"),
            "임상, 허가, 기술수출, 실적 가시성이 동시에 투자심리를 흔드는 고변동 테마입니다."),
        theme("defense", "방산", "+1.4%", List.of("수출", "항공우주", "국방", "정책"),
            "수출 계약, 지정학 이슈, 정부 예산, 환율이 실적 기대와 함께 움직입니다."),
        theme("robot", "로봇", "+1.2%", List.of("자동화", "협동로봇", "AI", "스마트팩토리"),
            "제조 자동화와 AI 적용 기대가 커질 때 관심이 모이는 성장 테마입니다."),
        theme("nuclear", "원전", "+0.9%", List.of("에너지", "정책", "인프라", "수주"),
            "정책, 해외 수주, 전력 수요, 원전 생태계 투자 기대를 함께 확인합니다."),
        theme("entertainment", "엔터", "+0.5%", List.of("K팝", "공연", "콘텐츠", "음반"),
            "앨범, 공연, 팬덤 지표, 아티스트 활동 일정이 실적 기대와 연결됩니다."),
        theme("shipbuilding", "조선", "+0.7%", List.of("LNG선", "수주", "해양플랜트", "환율"),
            "선가, 수주잔고, 원가, 환율이 함께 움직이는 경기민감 산업 테마입니다."),
        theme("cosmetics", "화장품", "+0.4%", List.of("중국", "미국", "수출", "ODM"),
            "수출 데이터, 채널 회복, 브랜드/ODM 실적이 투자심리와 연결됩니다."),
        industry("auto", "자동차", "+0.8%", List.of("완성차", "전기차", "부품", "환율", "수출"),
            "판매량, 인센티브, 환율, 전기차 전환 속도를 함께 보는 대표 수출 산업입니다."),
        industry("finance", "증권/금융", "+0.8%", List.of("은행", "증권", "금리", "배당", "거래대금"),
            "금리, 충당금, 배당, 증시 거래대금이 이익과 밸류에이션에 직접 연결됩니다."),
        industry("internet", "인터넷/플랫폼", "+0.3%", List.of("AI", "광고", "커머스", "콘텐츠"),
            "광고 경기, 커머스 성장, AI 서비스, 규제 이슈를 함께 확인합니다."),
        industry("steel-materials", "철강/소재", "-0.2%", List.of("철강", "리튬", "화학", "원자재"),
            "중국 수요, 원재료 가격, 소재 신사업 기대가 동시에 반영되는 산업군입니다."),
        industry("healthcare", "헬스케어", "+0.5%", List.of("바이오", "의약품", "CDMO", "임상"),
            "실적 안정성과 임상 이벤트가 함께 나타나는지 구분해서 봐야 하는 산업군입니다."),
        industry("game-content", "게임/콘텐츠", "+0.2%", List.of("신작", "IP", "웹툰", "엔터"),
            "신작 흥행, IP 확장, 해외 매출, 플랫폼 수수료 변화를 함께 확인합니다."),
        market("kospi", "KOSPI", List.of("대형주", "유가증권", "지수", "코스피"),
            "대형주 중심 한국 주식시장 흐름을 확인하는 대표 시장 구분입니다."),
        market("kosdaq", "KOSDAQ", List.of("성장주", "중소형주", "지수", "코스닥"),
            "기술주와 중소형 성장주 변동성을 확인하는 대표 시장 구분입니다."));
  }

  private static SearchResultDto theme(String id, String title, String rate, List<String> tags, String summary) {
    return item("theme-" + id, "theme", title, "THEME", "테마", rate, tags, summary);
  }

  private static SearchResultDto industry(String id, String title, String rate, List<String> tags, String summary) {
    return item("industry-" + id, "industry", title, "IND", "산업", rate, tags, summary);
  }

  private static SearchResultDto market(String id, String title, List<String> tags, String summary) {
    return item("market-" + id, "market", title, title, "시장", "시장", tags, summary);
  }

  private static SearchResultDto item(
      String id, String type, String title, String code, String market, String rate, List<String> tags, String summary) {
    return new SearchResultDto(id, type, title, code, market, rate, tags, summary, "search_taxonomy_baseline", null, null, null);
  }
}
