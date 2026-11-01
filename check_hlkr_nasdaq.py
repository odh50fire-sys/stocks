import urllib.request
import sys
sys.stdout.reconfigure(encoding='utf-8')

headers = {'User-Agent': 'Mozilla/5.0'}
paths = [
    "/nasdaq", "/stocks/nasdaq", "/stocks/ndx", "/stocks/qqq",
    "/futures/nasdaq", "/indices/nasdaq", "/market"
]

for p in paths:
    url = f"https://hlkr.co.kr{p}"
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req) as resp:
            print(f"[{p}] -> 200 OK (Length: {len(resp.read())})")
    except urllib.error.HTTPError as e:
        print(f"[{p}] -> {e.code}")
    except Exception as e:
        print(f"[{p}] -> {e}")
