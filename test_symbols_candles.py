import urllib.request
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')
headers = {'User-Agent': 'Mozilla/5.0'}

symbols_to_test = [
    "KR200", "NDX", "QQQ", "NASDAQ", "US100", "NQ", "IXIC",
    "SKHX", "SMSN", "SAMSUNGEM", "MU", "PLTR", "NVDA", "META", "TSLA"
]

for s in symbols_to_test:
    url = f"https://hlkr.co.kr/api/candles/{s}?interval=5m"
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            candles = data.get("candles", [])
            print(f"[{s}] -> Success! Candles: {len(candles)}")
    except urllib.error.HTTPError as e:
        print(f"[{s}] -> HTTPError {e.code}")
    except Exception as e:
        print(f"[{s}] -> Error: {e}")
