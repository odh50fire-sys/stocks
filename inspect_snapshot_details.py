import urllib.request
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')
headers = {'User-Agent': 'Mozilla/5.0'}

req = urllib.request.Request("https://hlkr.co.kr/api/snapshot", headers=headers)
with urllib.request.urlopen(req) as resp:
    data = json.loads(resp.read().decode('utf-8'))

assets = data.get("assets", [])
print(f"Total assets in snapshot: {len(assets)}")
for a in assets:
    asset_info = a.get("asset", {})
    sym = asset_info.get("symbol")
    name = asset_info.get("nameKo")
    hl = a.get("hl") or {}
    naver = a.get("naver") or {}
    usQuote = a.get("usQuote") or {}
    premiumPct = a.get("premiumPct")
    refPriceKrw = a.get("refPriceKrw")
    perpKrw = a.get("perpKrw")

    print(f"\n[{sym}] {name}")
    print(f"  HL: markPx={hl.get('markPx')}, prevDayPx={hl.get('prevDayPx')}, premium={hl.get('premium')}")
    print(f"  premiumPct (괴리율/김프): {premiumPct}")
    print(f"  refPriceKrw={refPriceKrw}, perpKrw={perpKrw}")
    if usQuote:
        print(f"  usQuote: last={usQuote.get('last_price')}, prev={usQuote.get('prev_close')}, chgPct={usQuote.get('change_1d_pct')}")
    if naver:
        print(f"  naver: krxClose={naver.get('krxClose')}, prevClose={naver.get('prevClose')}")
