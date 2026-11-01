import urllib.request
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

url = "https://api.hyperliquid.xyz/info"

def query_hl(payload):
    data = json.dumps(payload).encode('utf-8')
    req = urllib.request.Request(url, data=data, headers={'Content-Type': 'application/json', 'User-Agent': 'Mozilla/5.0'})
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode('utf-8'))
    except Exception as e:
        print(f"Error querying {payload}: {e}")
        return None

# Check metaAndAssetCtxs
res = query_hl({"type": "metaAndAssetCtxs"})
if res:
    universe = res[0]["universe"]
    print(f"Total perps in Hyperliquid: {len(universe)}")
    # Find perps matching our symbols
    target_coins = ["KR200", "SMSN", "SKHX", "SAMSUNGEM", "MU", "PLTR", "NVDA", "META", "TSLA"]
    found = []
    for idx, u in enumerate(universe):
        name = u.get("name", "")
        for t in target_coins:
            if t in name:
                found.append((name, idx, res[1][idx]))
    print("Found in metaAndAssetCtxs:")
    for name, idx, ctx in found:
        print(f"  {name}: markPx={ctx.get('markPx')}, prevDayPx={ctx.get('prevDayPx')}, oraclePx={ctx.get('oraclePx')}")
