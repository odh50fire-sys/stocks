import urllib.request
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')
headers = {'User-Agent': 'Mozilla/5.0'}

def test_api(path):
    url = f"https://hlkr.co.kr{path}"
    print(f"Testing {url} ...")
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            print("Status 200 OK!")
            if isinstance(data, dict):
                print("Keys:", list(data.keys()))
                if "candles" in data:
                    print(f"Candles count: {len(data['candles'])}")
                    if len(data['candles']) > 0:
                        print("Sample candle:", data['candles'][-1])
                else:
                    print("Sample dict preview:", str(data)[:300])
            elif isinstance(data, list):
                print(f"List count: {len(data)}")
                if len(data) > 0:
                    print("Sample item:", str(data[0])[:300])
    except Exception as e:
        print(f"Error {path}: {e}")

test_api("/api/candles/MU?interval=5m")
test_api("/api/candles/SKHX?interval=5m")
test_api("/api/candles/SMSN?interval=5m")
test_api("/api/candles/KR200?interval=5m")
test_api("/api/world")
