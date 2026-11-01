import sys
import re
import json

sys.stdout.reconfigure(encoding='utf-8')

with open("extracted_data.txt", "r", encoding="utf-8") as f:
    data = f.read()

# Look for JSON objects in extracted_data
print("Looking for JSON objects...")
json_candidates = re.findall(r'\{[^{}]*"ticker"[^{}]*\}', data)
print(f"Ticker matches: {len(json_candidates)}")
for c in json_candidates[:5]:
    print(" ", c)

# Let's search for perp, symbol, coin, asset
symbols = re.findall(r'\"(?:symbol|coin|ticker|asset|name|id)\":\"([^\"]+)\"', data)
print("Symbols found:", set(symbols))

# Let's search for hyperliquid api calls or endpoints
endpoints = re.findall(r'https?://[^\s\"\'\<\>]+', data)
print("Endpoints found:")
for ep in set(endpoints):
    if any(k in ep.lower() for k in ['hyperliquid', 'api', 'hlkr', 'upbit', 'binance', 'pyth']):
        print(" ", ep)
