import urllib.request
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')
headers = {'User-Agent': 'Mozilla/5.0'}

apis = ["/api/snapshot", "/api/kospi-futures", "/api/weekend-session", "/api/dash-macro", "/api/adr"]

for a in apis:
    url = f"https://hlkr.co.kr{a}"
    print(f"\n=================== {a} ===================")
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req) as resp:
            text = resp.read().decode('utf-8')
            print(f"Status 200, length {len(text)}")
            try:
                data = json.loads(text)
                if isinstance(data, dict):
                    print("Keys:", list(data.keys())[:10])
                    # Print preview of dict
                    for k in list(data.keys())[:5]:
                        print(f"  {k} -> {str(data[k])[:150]}")
                elif isinstance(data, list):
                    print("List len:", len(data))
                    if len(data) > 0:
                        print("Sample item:", str(data[0])[:200])
            except Exception:
                print("Text preview:", text[:300])
    except Exception as e:
        print(f"Error: {e}")
