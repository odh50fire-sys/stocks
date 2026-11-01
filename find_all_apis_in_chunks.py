import re
import urllib.request
import sys

sys.stdout.reconfigure(encoding='utf-8')
headers = {'User-Agent': 'Mozilla/5.0'}

with open("hlkr_fresh_home.txt", "r", encoding="utf-8") as f:
    html = f.read()

scripts = re.findall(r'src="(/_next/static/chunks/[^"]+)"', html)
print(f"Total scripts: {len(scripts)}")

found_apis = set()
for s in scripts:
    url = f"https://hlkr.co.kr{s}"
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req) as resp:
            content = resp.read().decode('utf-8', errors='ignore')
            apis = re.findall(r'(/api/[a-zA-Z0-9_\-]+)', content)
            found_apis.update(apis)
    except Exception as e:
        pass

print("All /api/... endpoints found in scripts:")
for a in sorted(found_apis):
    print(" ", a)
