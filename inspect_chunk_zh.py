import urllib.request
import re
import sys

sys.stdout.reconfigure(encoding='utf-8')
headers = {'User-Agent': 'Mozilla/5.0'}
url = "https://hlkr.co.kr/_next/static/chunks/0zh7o13328rps.js"

req = urllib.request.Request(url, headers=headers)
with urllib.request.urlopen(req) as resp:
    content = resp.read().decode('utf-8', errors='ignore')

print("Length of 0zh7o13328rps.js:", len(content))

# Look for all URLs, API calls, endpoints, or fetch calls
endpoints = re.findall(r'(\b(?:fetch|get|post)\s*\([^)]+\)|https?://[^\s\"\'\<\>]+|/api/[^\s\"\'\<\>]+)', content)
print("Endpoints / Fetch calls:")
for ep in set(endpoints):
    print(" ", ep)

# Search for candle fetching
for kw in ["candles", "candleSnapshot", "interval", "startTime", "resolution"]:
    idx = 0
    while True:
        idx = content.find(kw, idx)
        if idx == -1:
            break
        print(f"\nKeyword '{kw}':")
        print(content[max(0, idx-100):min(len(content), idx+200)])
        idx += 50
        if idx > 1000:
            break
