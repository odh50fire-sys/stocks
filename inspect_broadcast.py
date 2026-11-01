import urllib.request
import re
import sys

sys.stdout.reconfigure(encoding='utf-8')
headers = {'User-Agent': 'Mozilla/5.0'}
url = "https://hlkr.co.kr/_next/static/chunks/0cl44xyiugdpg.js"

req = urllib.request.Request(url, headers=headers)
with urllib.request.urlopen(req) as resp:
    content = resp.read().decode('utf-8', errors='ignore')

# Search for /api/broadcast context
idx = 0
while True:
    idx = content.find("/api/broadcast", idx)
    if idx == -1:
        break
    print("=== Context around /api/broadcast ===")
    print(content[max(0, idx-200):min(len(content), idx+400)])
    idx += 20
