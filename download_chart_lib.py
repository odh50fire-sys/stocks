import urllib.request
import os

url = "https://unpkg.com/lightweight-charts/dist/lightweight-charts.standalone.production.js"
headers = {'User-Agent': 'Mozilla/5.0'}
req = urllib.request.Request(url, headers=headers)
try:
    with urllib.request.urlopen(req) as resp:
        content = resp.read()
        with open("lightweight-charts.standalone.production.js", "wb") as f:
            f.write(content)
        print(f"Downloaded lightweight-charts! Size: {len(content)} bytes")
except Exception as e:
    print(f"Failed to download lightweight-charts: {e}")
