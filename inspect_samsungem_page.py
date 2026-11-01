import urllib.request
import re
import sys

sys.stdout.reconfigure(encoding='utf-8')
headers = {'User-Agent': 'Mozilla/5.0'}

req = urllib.request.Request("https://hlkr.co.kr/stocks/samsungem", headers=headers)
with urllib.request.urlopen(req) as resp:
    html = resp.read().decode('utf-8', errors='ignore')

# Look for text like "삼성전기 주가는..." or "현재 ₩..." or percentages
clean = re.sub(r'<[^>]+>', ' ', html)
clean = re.sub(r'\s+', ' ', clean)

matches = [m.start() for m in re.finditer(r'삼성전기', clean)]
print(f"Total occurrences of 삼성전기: {len(matches)}")
for idx in matches[:5]:
    print("--- Snippet ---")
    print(clean[max(0, idx-50):min(len(clean), idx+200)])

# Also search for percent values in this page
pcts = re.findall(r'([+\-]?[0-9]+\.[0-9]+)\s*%', clean)
print("Percentages found in page:", pcts[:10])
