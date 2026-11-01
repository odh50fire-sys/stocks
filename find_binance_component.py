import re
import sys

sys.stdout.reconfigure(encoding='utf-8')

# Find chunk defining component L30 or binanceSymbol
with open("samsungem_raw.txt", "r", encoding="utf-8") as f:
    html = f.read()

scripts = re.findall(r'src="(/_next/static/chunks/[^"]+)"', html)
headers = {'User-Agent': 'Mozilla/5.0'}
import urllib.request

for s in scripts:
    url = f"https://hlkr.co.kr{s}"
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req) as resp:
            content = resp.read().decode('utf-8', errors='ignore')
            if "binanceSymbol" in content:
                print(f"Found binanceSymbol in {s}!")
                idx = 0
                while True:
                    idx = content.find("binanceSymbol", idx)
                    if idx == -1: break
                    print("--- Context ---")
                    print(content[max(0, idx-100):min(len(content), idx+600)])
                    idx += 15
    except Exception as e:
        pass
