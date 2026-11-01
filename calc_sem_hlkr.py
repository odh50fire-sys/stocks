import urllib.request
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')
headers = {'User-Agent': 'Mozilla/5.0'}

# 1. Binance SAMSUNGEMUSDT
b_url = "https://fapi.binance.com/fapi/v1/ticker/24hr?symbol=SAMSUNGEMUSDT"
req = urllib.request.Request(b_url, headers=headers)
with urllib.request.urlopen(req) as resp:
    b_data = json.loads(resp.read().decode('utf-8'))
    usd_price = float(b_data["lastPrice"])
    binance_chg = float(b_data["priceChangePercent"])

# 2. HLKR Snapshot for rate & refPriceKrw
s_url = "https://hlkr.co.kr/api/snapshot"
req2 = urllib.request.Request(s_url, headers=headers)
with urllib.request.urlopen(req2) as resp2:
    s_data = json.loads(resp2.read().decode('utf-8'))
    rate = s_data.get("fx", {}).get("naverUsdKrw", 1335.8)
    sem_ref = 1196000
    for a in s_data.get("assets", []):
        if a.get("asset", {}).get("symbol") == "SAMSUNGEM":
            sem_ref = a.get("refPriceKrw", 1196000)

perp_krw = usd_price * rate
premium_pct = ((perp_krw - sem_ref) / sem_ref) * 100

print(f"=== SAMSUNGEM HLKR Exact Matching Calculation ===")
print(f"Binance USD: ${usd_price}")
print(f"USD/KRW Rate: ₩{rate}")
print(f"24h Estimated KRW: ₩{int(round(perp_krw)):,}")
print(f"KRX Reference Close: ₩{sem_ref:,}")
print(f"HLKR Premium Pct (괴리율): {premium_pct:.2f}%")
print(f"Binance 24h Pct (선물 변동률): {binance_chg:+.2f}%")
