import re
import urllib.request
import sys
sys.stdout.reconfigure(encoding='utf-8')

with open("home.txt", "r", encoding="utf-8") as f:
    text = f.read()

# Let's search for "나스닥", "삼성전기", "전기"
for kw in ["나스닥", "삼성전기", "전기", "NASDAQ", "NDX", "EM", "009150"]:
    pos = 0
    print(f"\n--- Searching '{kw}' in home.txt ---")
    while True:
        idx = text.find(kw, pos)
        if idx == -1:
            break
        print(text[max(0, idx-50):min(len(text), idx+100)].replace('\n', ' '))
        pos = idx + len(kw) + 10
        if pos > idx + 500:
            break
