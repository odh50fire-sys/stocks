import urllib.request
import re
import sys
import json

sys.stdout.reconfigure(encoding='utf-8')
headers = {'User-Agent': 'Mozilla/5.0'}

req = urllib.request.Request("https://hlkr.co.kr/", headers=headers)
with urllib.request.urlopen(req) as resp:
    html = resp.read().decode('utf-8', errors='ignore')

# Save full html
with open("hlkr_fresh_home.txt", "w", encoding="utf-8") as f:
    f.write(html)

# Let's search for SK하이닉스 and 코스피 in html
for kw in ["코스피", "SK하이닉스", "삼성전자", "마이크론", "KR200", "SKHX"]:
    print(f"\n=== Occurrences of {kw} ===")
    pos = 0
    while True:
        idx = html.find(kw, pos)
        if idx == -1:
            break
        print(html[max(0, idx-100):min(len(html), idx+300)].replace('\n', ' '))
        pos = idx + len(kw) + 10
        if pos > idx + 1000:
            break
