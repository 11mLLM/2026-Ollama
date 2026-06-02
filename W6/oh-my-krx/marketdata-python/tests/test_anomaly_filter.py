from __future__ import annotations

import pandas as pd

from app import main


def test_leaders_excludes_anomaly_from_filtered_ranking(monkeypatch):
    main._zero_volume_streak.cache_clear()
    df = pd.DataFrame(
        {
            "등락률": [120.0, 9.5, -7.0],
            "거래량": [1_000, 5_000_000, 4_000_000],
            "종가": [2200.0, 10500.0, 9300.0],
            "변동폭": [2200.0, 910.0, -700.0],
        },
        index=["AAA", "BBB", "CCC"],
    )

    ohlcv = pd.DataFrame({"거래량": [0, 0, 0, 1500]})

    monkeypatch.setattr(main, "_parse_date", lambda _: "20260213")
    monkeypatch.setattr(main, "_previous_business_day", lambda _: "20260212")
    monkeypatch.setattr(main, "_name", lambda ticker: f"NAME_{ticker}")
    monkeypatch.setattr(main.stock, "get_market_price_change_by_ticker", lambda *args, **kwargs: df)
    monkeypatch.setattr(main.stock, "get_market_ticker_list", lambda *args, **kwargs: ["AAA", "BBB", "CCC"])
    monkeypatch.setattr(main.stock, "get_market_ohlcv_by_date", lambda *args, **kwargs: ohlcv)

    res = main.leaders("2026-02-13")

    assert res["rawTopGainer"] == "NAME_AAA"
    assert res["filteredTopGainer"] == "NAME_BBB"
    assert len(res["anomalies"]) >= 1
    assert res["anomalies"][0]["oneLineReason"]


def test_reason_mentions_zero_volume_and_huge_gap():
    reason = main._build_one_line_reason(132.2, ["zero_volume_streak", "huge_gap"], 5)
    assert "거래량 0" in reason
    assert "132.20%" in reason


def test_huge_gap_only_does_not_exclude_top_gainer(monkeypatch):
    main._zero_volume_streak.cache_clear()
    df = pd.DataFrame(
        {
            "등락률": [95.0, 9.5, -7.0],
            "거래량": [50_000, 5_000_000, 4_000_000],
            "종가": [2200.0, 10500.0, 9300.0],
            "변동폭": [1100.0, 910.0, -700.0],
        },
        index=["AAA", "BBB", "CCC"],
    )

    ohlcv = pd.DataFrame({"거래량": [1000, 1200, 1300, 1500]})

    monkeypatch.setattr(main, "_parse_date", lambda _: "20260213")
    monkeypatch.setattr(main, "_previous_business_day", lambda _: "20260212")
    monkeypatch.setattr(main, "_name", lambda ticker: f"NAME_{ticker}")
    monkeypatch.setattr(main.stock, "get_market_price_change_by_ticker", lambda *args, **kwargs: df)
    monkeypatch.setattr(main.stock, "get_market_ticker_list", lambda *args, **kwargs: ["AAA", "BBB", "CCC"])
    monkeypatch.setattr(main.stock, "get_market_ohlcv_by_date", lambda *args, **kwargs: ohlcv)

    res = main.leaders("2026-02-13")

    assert res["topGainer"] == "NAME_AAA"
    assert res["filteredTopGainer"] == "NAME_AAA"
    assert any(a["name"] == "NAME_AAA" for a in res["anomalies"])
