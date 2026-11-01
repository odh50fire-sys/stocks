import urllib.request
import re
import json
import time
import sys

sys.stdout.reconfigure(encoding='utf-8')

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,application/json,*/*;q=0.8',
    'Accept-Language': 'ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7'
}

def fetch_url(url, timeout=7):
    req = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read().decode('utf-8', errors='ignore')

def get_market_and_world_data():
    """Fetch HLKR market page and world API for quotes"""
    market_items = {}
    try:
        html = fetch_url("https://hlkr.co.kr/market")
        unescaped = html.replace('\\"', '"').replace('\\/', '/')
        # Find asset snaps
        # Format: {"asset":{"symbol":"MU",...},"hl":{...},"refPriceKrw":...,"perpKrw":...,"premiumPct":...}
        matches = re.finditer(r'\{"asset":\{"symbol":"([^"]+)"[^}]*\},"hl":(\{.*?\})?,"refPriceKrw":([0-9\.]+)?,"refSource":"([^"]+)",("naver":(\{.*?\})?,)?"perpKrw":([0-9\.]+)?,"premiumPct":([0-9\.-]+)?', unescaped)
        for m in matches:
            sym = m.group(1)
            market_items[sym] = {
                "symbol": sym,
                "hl": m.group(2),
                "refPriceKrw": m.group(3),
                "refSource": m.group(4),
                "naver": m.group(6),
                "perpKrw": m.group(7),
                "premiumPct": m.group(8)
            }
    except Exception as e:
        print(f"Error fetching market: {e}")

    world_quotes = {}
    try:
        w_text = fetch_url("https://hlkr.co.kr/api/world")
        w_json = json.loads(w_text)
        world_quotes = w_json.get("quotes", {})
    except Exception as e:
        print(f"Error fetching world: {e}")

    return market_items, world_quotes

def get_ig_data(url, default_epic):
    """Scrape price and change from IG.com"""
    try:
        html = fetch_url(url)
        # Find price ticket
        bid_m = re.search(r'data-field="BID"[^>]*>([^<]+)<', html)
        ofr_m = re.search(r'data-field="OFR"[^>]*>([^<]+)<', html)
        cpt_m = re.search(r'data-field="CPT"[^>]*>([^<]+)<', html)
        cpc_m = re.search(r'data-field="CPC"[^>]*>([^<]+)<', html)
        
        bid = bid_m.group(1).strip() if bid_m else None
        ofr = ofr_m.group(1).strip() if ofr_m else None
        cpt = cpt_m.group(1).strip() if cpt_m else None
        cpc = cpc_m.group(1).strip() if cpc_m else None
        
        return {
            "bid": bid,
            "ofr": ofr,
            "changePoint": cpt,
            "changePct": float(cpc) if cpc else 0.0,
            "price": bid if bid else ofr
        }
    except Exception as e:
        print(f"Error fetching IG {url}: {e}")
        return {"price": "-", "changePct": 0.0, "changePoint": "-"}

def collect_all_12_indicators():
    market_items, world_quotes = get_market_and_world_data()
    
    # 1. 위켄드 코스피200 (KR200)
    kr200_info = market_items.get("KR200", {})
    kr200_price = 882.18
    kr200_chg = 0.55
    if kr200_info.get("hl"):
        try:
            hl_dict = json.loads(kr200_info["hl"])
            kr200_price = hl_dict.get("markPx", kr200_price)
            prev = hl_dict.get("prevDayPx")
            if prev and kr200_price:
                kr200_chg = round((kr200_price - prev) / prev * 100, 2)
        except Exception:
            pass

    # 2. 위켄드 나스닥100 (hlkr)
    # Quotes in world quotes: NQ=F (E-mini Nasdaq future) or %5EIXIC
    nq = world_quotes.get("NQ=F", {}) or world_quotes.get("%5EIXIC", {})
    nasdaq_price = nq.get("price", 28504.1)
    nasdaq_chg = nq.get("changePct", -0.02)

    # 3. Weekend US Tech 100 (ig.com)
    ig_tech = get_ig_data("https://www.ig.com/en/indices/markets-indices/weekend-us-tech-100-e1", "IX.D.SUNNAS.BMU.IP")

    # 4. Weekend Oil - US Crude (ig.com)
    ig_oil = get_ig_data("https://www.ig.com/en/indices/markets-indices/weekend-oil---us-crude", "IX.D.SUNCL.BMU.IP")

    def parse_hl_stock(sym, fallback_price, fallback_chg):
        info = market_items.get(sym, {})
        price = fallback_price
        chg = fallback_chg
        if info.get("hl"):
            try:
                hl_dict = json.loads(info["hl"])
                price = hl_dict.get("markPx", price)
                prev = hl_dict.get("prevDayPx")
                if prev and price:
                    chg = round((price - prev) / prev * 100, 2)
            except Exception:
                pass
        return price, chg

    # 5. SK하이닉스 (SKHX)
    skhx_px, skhx_chg = parse_hl_stock("SKHX", 1059.2, 1.65)
    # 6. 삼성전자 (SMSN)
    smsn_px, smsn_chg = parse_hl_stock("SMSN", 164.17, 1.41)

    # 7. 삼성전기 (SAMSUNGEM)
    sem_info = market_items.get("SAMSUNGEM", {})
    sem_price = 1196000
    sem_chg = 0.0
    if sem_info.get("naver"):
        try:
            n_dict = json.loads(sem_info["naver"])
            sem_price = n_dict.get("krxClose", sem_price)
            prev = n_dict.get("prevClose")
            if prev and sem_price:
                sem_chg = round((sem_price - prev) / prev * 100, 2)
        except Exception:
            pass

    # 8. 마이크론 (MU)
    mu_px, mu_chg = parse_hl_stock("MU", 905.19, 0.82)
    # 9. 팔란티어 (PLTR)
    pltr_px, pltr_chg = parse_hl_stock("PLTR", 185.29, 0.38)
    # 10. 엔비디아 (NVDA)
    nvda_px, nvda_chg = parse_hl_stock("NVDA", 204.67, 0.78)
    # 11. 메타 (META)
    meta_px, meta_chg = parse_hl_stock("META", 615.04, 0.16)
    # 12. 테슬라 (TSLA)
    tsla_px, tsla_chg = parse_hl_stock("TSLA", 400.73, 0.47)

    indicators = [
        {
            "id": "kr200",
            "index": 1,
            "name": "위켄드 코스피200",
            "symbol": "KR200",
            "source": "hlkr.co.kr",
            "sourceType": "direct_api",
            "hasChart": True,
            "price": kr200_price,
            "changePct": kr200_chg,
            "currency": "pt"
        },
        {
            "id": "nasdaq",
            "index": 2,
            "name": "위켄드 나스닥100",
            "symbol": "NQ100",
            "source": "hlkr.co.kr",
            "sourceType": "scraped",
            "hasChart": False,
            "price": nasdaq_price,
            "changePct": nasdaq_chg,
            "currency": "pt"
        },
        {
            "id": "ig_tech100",
            "index": 3,
            "name": "Weekend US Tech 100",
            "symbol": "TECH100",
            "source": "ig.com",
            "sourceType": "scraped",
            "hasChart": False,
            "price": ig_tech["price"],
            "changePct": ig_tech["changePct"],
            "currency": "pt"
        },
        {
            "id": "ig_oil",
            "index": 4,
            "name": "Weekend Oil - US Crude",
            "symbol": "OIL (WTI)",
            "source": "ig.com",
            "sourceType": "scraped",
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
            "sourceType": "direct_api",
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
            "sourceType": "direct_api",
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
            "sourceType": "scraped",
            "hasChart": False,
            "price": sem_price,
            "changePct": sem_chg,
            "currency": "KRW"
        },
        {
            "id": "mu",
            "index": 8,
            "name": "마이크론",
            "symbol": "MU",
            "source": "hlkr.co.kr/stocks/mu",
            "sourceType": "direct_api",
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
            "sourceType": "direct_api",
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
            "sourceType": "direct_api",
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
            "sourceType": "direct_api",
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
            "sourceType": "direct_api",
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
        chart_badge = "📈 [Chart ON]" if r["hasChart"] else "📊 [Value ONLY]"
        print(f"[{idx:2d}] {r['name']:<22} | {r['symbol']:<10} | Px: {str(r['price']):<10} | Chg: {r['changePct']:>+6.2f}% | {chart_badge}")
