import urllib.request
import re
import sys

sys.stdout.reconfigure(encoding='utf-8')
headers = {'User-Agent': 'Mozilla/5.0'}

paths = ["/stocks/samsungem", "/stocks/009150", "/stocks/samsung-em", "/market"]

for p in paths:
    url = f"https://hlkr.co.kr{p}"
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req) as resp:
            text = resp.read().decode('utf-8', errors='ignore')
            print(f"[{p}] -> 200 OK, length {len(text)}")
            # look for samsungem or 009150 text
            idx = text.find("009150")
            if idx != -1:
                print("  Snippet:", text[max(0, idx-100):min(len(text), idx+300)].replace('\n', ' '))
    except Exception as e:
        print(f"[{p}] -> {e}")
