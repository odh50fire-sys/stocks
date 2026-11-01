import urllib.request
import re
import sys

sys.stdout.reconfigure(encoding='utf-8')
headers = {'User-Agent': 'Mozilla/5.0'}
url = "https://hlkr.co.kr/_next/static/chunks/05kg_69die7xy.js"

req = urllib.request.Request(url, headers=headers)
with urllib.request.urlopen(req) as resp:
    content = resp.read().decode('utf-8', errors='ignore')

print("CandleChart script length:", len(content))

# Look for fetch or api or url inside CandleChart script
matches = re.findall(r'(https?://[^\s\"\'\<\>]+|/api/[^\s\"\'\<\>]+|/candles[^\s\"\'\<\>]*)', content)
print("URLs / APIs in CandleChart script:", set(matches))

# Look for candle or resolution or timeframe or history
keywords = ["candle", "resolution", "interval", "ohlc", "tradingview", "lightweight", "history", "series"]
for kw in keywords:
    matches = [m.start() for m in re.finditer(kw, content, re.IGNORECASE)]
    print(f"Keyword {kw}: {len(matches)}")
    for idx in matches[:2]:
        print(f"  Snippet: {content[max(0, idx-80):min(len(content), idx+120)].replace('\n', ' ')}")
