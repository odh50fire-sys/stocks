import urllib.request
import re
import json
import time
import sys

sys.stdout.reconfigure(encoding='utf-8')

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml,application/json;q=0.9,*/*;q=0.8',
    'Accept-Language': 'ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7'
}

def fetch_url(url, timeout=7):
    req = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read().decode('utf-8', errors='ignore')

def get_ig_data(url):
    """Scrape price and change from IG.com"""
    try:
        html = fetch_url(url)
        bid_m = re.search(r'data-field="BID"[^>]*>([^<]+)<', html)
        ofr_m = re.search(r'data-field="OFR"[^>]*>([^<]+)<', html)
        cpc_m = re.search(r'data-field="CPC"[^>]*>([^<]+)<', html)
        
        bid = bid_m.group(1).strip() if bid_m else None
        ofr = ofr_m.group(1).strip() if ofr_m else None
        cpc = cpc_m.group(1).strip() if cpc_m else None
        
        return {
            "price": bid if bid else ofr,
            "changePct": float(cpc) if cpc else 0.0
        }
    except Exception as e:
        print(f"Error fetching IG {url}: {e}")
        return {"price": "-", "changePct": 0.0}

def collect_all_12_indicators():
    # 1. Fetch HLKR weekend-session (Exact rate for Weekend Kospi200 & Weekend Nasdaq100)
    weekend_kospi_chg = -1.76
    weekend_kospi_px = 883.78
    weekend_nasdaq_chg = 0.56
    weekend_nasdaq_px = 28556
    try:
        w_json = json.loads(fetch_url("https://hlkr.co.kr/api/weekend-session"))
        if w_json.get("kospi"):
            weekend_kospi_chg = round(w_json["kospi"].get("changePct", weekend_kospi_chg), 2)
            weekend_kospi_px = w_json["kospi"].get("price", weekend_kospi_px)
        if w_json.get("nasdaq"):
            weekend_nasdaq_chg = round(w_json["nasdaq"].get("changePct", weekend_nasdaq_chg), 2)
            weekend_nasdaq_px = w_json["nasdaq"].get("price", weekend_nasdaq_px)
    except Exception as e:
        print(f"Error fetching weekend-session: {e}")

    # 2. Fetch HLKR snapshot (Exact premiumPct / rate for stocks as displayed on hlkr.co.kr)
    snapshot_map = {}
    try:
        snap_json = json.loads(fetch_url("https://hlkr.co.kr/api/snapshot"))
        for item in snap_json.get("assets", []):
            sym = item.get("asset", {}).get("symbol")
            if sym:
                snapshot_map[sym] = item
    except Exception as e:
        print(f"Error fetching snapshot: {e}")

    # 3. Fetch IG.com real-time tickets
    ig_tech = get_ig_data("https://www.ig.com/en/indices/markets-indices/weekend-us-tech-100-e1")
    ig_oil = get_ig_data("https://www.ig.com/en/indices/markets-indices/weekend-oil---us-crude")

    def parse_stock(sym, fallback_px, fallback_chg):
        item = snapshot_map.get(sym, {})
        hl = item.get("hl") or {}
        px = hl.get("markPx") or fallback_px
        chg = fallback_chg
        # hlkr displays premiumPct as the primary indicator for stocks
        if item.get("premiumPct") is not None:
            chg = round(item["premiumPct"], 2)
        elif hl.get("prevDayPx") and px:
            chg = round((px - hl["prevDayPx"]) / hl["prevDayPx"] * 100, 2)
        return px, chg

    # 5. SK하이닉스
    skhx_px, skhx_chg = parse_stock("SKHX", 1062.2, -0.01)
    # 6. 삼성전자
    smsn_px, smsn_chg = parse_stock("SMSN", 164.59, -2.28)

    # 7. 삼성전기 (HLKR uses Binance SAMSUNGEMUSDT * USD/KRW vs KRX Close)
    sem_px = 1147425
    sem_chg = -4.06
    try:
        b_res = json.loads(fetch_url("https://fapi.binance.com/fapi/v1/ticker/24hr?symbol=SAMSUNGEMUSDT", timeout=5))
        b_usd = float(b_res.get("lastPrice", 858.98))
        fx_rate = 1335.8
        snap_item = snapshot_map.get("SAMSUNGEM", {})
        ref_krw = snap_item.get("refPriceKrw", 1196000)
        # Check fx rate from snapshot
        snap_json_fx = snap_item.get("naverUsdKrw") or 1335.8
        perp_krw = b_usd * snap_json_fx
        sem_px = int(round(perp_krw))
        if ref_krw and ref_krw > 0:
            sem_chg = round(((perp_krw - ref_krw) / ref_krw) * 100, 2)
    except Exception as e:
        print(f"Error calculating Binance SAMSUNGEM: {e}")

    # 8. 마이크론
    mu_px, mu_chg = parse_stock("MU", 906.65, 2.48)
    # 9. 팔란티어
    pltr_px, pltr_chg = parse_stock("PLTR", 185.26, 0.62)
    # 10. 엔비디아
    nvda_px, nvda_chg = parse_stock("NVDA", 204.47, -0.08)
    # 11. 메타
    meta_px, meta_chg = parse_stock("META", 614.73, -0.04)
    # 12. 테슬라
    tsla_px, tsla_chg = parse_stock("TSLA", 400.17, 0.09)

    indicators = [
        {
            "id": "kr200",
            "index": 1,
            "name": "W 코스피200",
            "symbol": "KR200",
            "source": "hlkr.co.kr",
            "hasChart": True,
            "price": weekend_kospi_px,
            "changePct": weekend_kospi_chg,
            "currency": "pt"
        },
        {
            "id": "nasdaq",
            "index": 2,
            "name": "W 나스닥100",
            "symbol": "NQ100",
            "source": "hlkr.co.kr",
            "hasChart": False,
            "price": weekend_nasdaq_px,
            "changePct": weekend_nasdaq_chg,
            "currency": "pt"
        },
        {
            "id": "ig_tech100",
            "index": 3,
            "name": "W US Tech 100",
            "symbol": "TECH100",
            "source": "ig.com",
            "hasChart": False,
            "price": ig_tech["price"],
            "changePct": ig_tech["changePct"],
            "currency": "pt"
        },
        {
            "id": "ig_oil",
            "index": 4,
            "name": "W Oil (Crude)",
            "symbol": "OIL (WTI)",
            "source": "ig.com",
            "hasChart": False,
            "price": ig_oil["price"],
            "changePct": ig_oil["changePct"],
            "currency": "pt"
        },
        {
            "id": "skhx",
            "index": 5,
            "name": "SK하이닉스",
            "symbol": "SKHX",
            "source": "hlkr.co.kr",
            "hasChart": True,
            "price": skhx_px,
            "changePct": skhx_chg,
            "currency": "$"
        },
        {
            "id": "smsn",
            "index": 6,
            "name": "삼성전자",
            "symbol": "SMSN",
            "source": "hlkr.co.kr",
            "hasChart": True,
            "price": smsn_px,
            "changePct": smsn_chg,
            "currency": "$"
        },
        {
            "id": "samsungem",
            "index": 7,
            "name": "삼성전기",
            "symbol": "SAMSUNGEM",
            "source": "hlkr.co.kr",
            "hasChart": False,
            "price": sem_px,
            "changePct": sem_chg,
            "currency": "KRW"
        },
        {
            "id": "mu",
            "index": 8,
            "name": "마이크론",
            "symbol": "MU",
            "source": "hlkr.co.kr/stocks/mu",
            "hasChart": True,
            "price": mu_px,
            "changePct": mu_chg,
            "currency": "$"
        },
        {
            "id": "pltr",
            "index": 9,
            "name": "팔란티어",
            "symbol": "PLTR",
            "source": "hlkr.co.kr/stocks/pltr",
            "hasChart": True,
            "price": pltr_px,
            "changePct": pltr_chg,
            "currency": "$"
        },
        {
            "id": "nvda",
            "index": 10,
            "name": "엔비디아",
            "symbol": "NVDA",
            "source": "hlkr.co.kr/stocks/nvda",
            "hasChart": True,
            "price": nvda_px,
            "changePct": nvda_chg,
            "currency": "$"
        },
        {
            "id": "meta",
            "index": 11,
            "name": "메타",
            "symbol": "META",
            "source": "hlkr.co.kr/stocks/meta",
            "hasChart": True,
            "price": meta_px,
            "changePct": meta_chg,
            "currency": "$"
        },
        {
            "id": "tsla",
            "index": 12,
            "name": "테슬라",
            "symbol": "TSLA",
            "source": "hlkr.co.kr/stocks/tsla",
            "hasChart": True,
            "price": tsla_px,
            "changePct": tsla_chg,
            "currency": "$"
        }
    ]
    return indicators

if __name__ == "__main__":
    t0 = time.time()
    results = collect_all_12_indicators()
    print(f"Collected in {time.time()-t0:.2f}s:")
    for idx, r in enumerate(results, 1):
        print(f"[{idx:2d}] {r['name']:<22} | {r['symbol']:<10} | Px: {str(r['price']):<10} | Chg: {r['changePct']:>+6.2f}%")
