import urllib.request
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')
headers = {'User-Agent': 'Mozilla/5.0'}

url = "https://fapi.binance.com/fapi/v1/ticker/24hr?symbol=SAMSUNGEMUSDT"
print(f"Testing Binance: {url}")
try:
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read().decode('utf-8'))
        print("Binance Response:")
        print(json.dumps(data, indent=2))
        last_price = float(data.get("lastPrice", 0))
        price_change_percent = float(data.get("priceChangePercent", 0))
        print(f"Last Price: {last_price}, PriceChangePercent: {price_change_percent}%")
except Exception as e:
    print("Binance Error:", e)
