import re
import sys
sys.stdout.reconfigure(encoding='utf-8')

with open("market.txt", "r", encoding="utf-8") as f:
    text = f.read()

unescaped = text.replace('\\"', '"').replace('\\/', '/')

# Search for SAMSUNGEM
idx = unescaped.find('"symbol":"SAMSUNGEM"')
if idx != -1:
    print("=== SAMSUNGEM Object ===")
    print(unescaped[max(0, idx-100):min(len(unescaped), idx+1000)])

# Search for NASDAQ or NDX or 나스닥
for kw in ["나스닥", "NASDAQ", "NDX", "US100", "QQQ"]:
    idx2 = unescaped.find(kw)
    if idx2 != -1:
        print(f"=== Found {kw} in Market ===")
        print(unescaped[max(0, idx2-100):min(len(unescaped), idx2+500)])
