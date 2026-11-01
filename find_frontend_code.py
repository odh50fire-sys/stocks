import re
import urllib.request
import sys
sys.stdout.reconfigure(encoding='utf-8')

with open("sample_output.txt", "r", encoding="utf-8") as f:
    html = f.read()

# Find all script src
scripts = re.findall(r'src="(/_next/static/chunks/[^"]+)"', html)
print(f"Found {len(scripts)} scripts")

# Let's inspect these scripts for api, wss, hyperliquid
headers = {'User-Agent': 'Mozilla/5.0'}
for s in scripts[:8]:
    url = f"https://hlkr.co.kr{s}"
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req) as resp:
            content = resp.read().decode('utf-8', errors='ignore')
            # search for wss or hyperliquid or api
            if "hyperliquid" in content.lower() or "wss:" in content or "candles" in content or "api.hyperliquid" in content:
                print(f"MATCH in {s}!")
                for m in re.finditer(r'(wss?://[^\s\"\'\<\>]+|https?://[^\s\"\'\<\>]+|/api/[^\s\"\'\<\>]+)', content):
                    val = m.group(1)
                    if any(k in val.lower() for k in ['hyperliquid', 'api', 'stock', 'quote', 'chart', 'price', 'ws']):
                        print("  Found:", val)
    except Exception as e:
        print(f"Error {url}: {e}")
