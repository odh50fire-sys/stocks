import urllib.request
import re
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')
headers = {'User-Agent': 'Mozilla/5.0'}

# 1. Check https://hlkr.co.kr/market
print("=== 1. Checking https://hlkr.co.kr/market ===")
req = urllib.request.Request("https://hlkr.co.kr/market", headers=headers)
with urllib.request.urlopen(req) as resp:
    html = resp.read().decode('utf-8', errors='ignore')

# Search for SAMSUNGEM or 009150 or 삼성전기
idx = 0
while True:
    idx = html.find("009150", idx)
    if idx == -1:
        break
    print(html[max(0, idx-100):min(len(html), idx+300)].replace('\\"', '"').replace('\n', ' '))
    idx += 7

# 2. Check https://hlkr.co.kr/api/snapshot
print("\n=== 2. Checking https://hlkr.co.kr/api/snapshot ===")
req2 = urllib.request.Request("https://hlkr.co.kr/api/snapshot", headers=headers)
with urllib.request.urlopen(req2) as resp2:
    data = json.loads(resp2.read().decode('utf-8'))
    for it in data.get("assets", []):
        if "SAMSUNGEM" in str(it):
            print(json.dumps(it, ensure_ascii=False, indent=2))
