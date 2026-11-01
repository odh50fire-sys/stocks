import urllib.request
import re
import sys

sys.stdout.reconfigure(encoding='utf-8')
headers = {'User-Agent': 'Mozilla/5.0'}

req = urllib.request.Request("https://hlkr.co.kr/market", headers=headers)
with urllib.request.urlopen(req) as resp:
    html = resp.read().decode('utf-8', errors='ignore')
    print("Market page length:", len(html))
    with open("market.txt", "w", encoding="utf-8") as f:
        f.write(html)

unescaped = html.replace('\\"', '"').replace('\\/', '/')
symbols = re.findall(r'"symbol":"([^"]+)"', unescaped)
print("Symbols in market:", set(symbols))
nameKos = re.findall(r'"nameKo":"([^"]+)"', unescaped)
print("nameKo in market:", set(nameKos))
