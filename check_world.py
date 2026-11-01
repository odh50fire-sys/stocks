import urllib.request
import json
import sys
sys.stdout.reconfigure(encoding='utf-8')

headers = {'User-Agent': 'Mozilla/5.0'}
req = urllib.request.Request("https://hlkr.co.kr/api/world", headers=headers)
with urllib.request.urlopen(req) as resp:
    data = json.loads(resp.read().decode('utf-8'))

print("World keys:", list(data.keys()))
print("Quotes keys:", list(data.get("quotes", {}).keys()))
print("KR keys:", list(data.get("kr", {}).keys()))
