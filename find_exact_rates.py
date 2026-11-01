import urllib.request
import re
import sys

sys.stdout.reconfigure(encoding='utf-8')
headers = {'User-Agent': 'Mozilla/5.0'}

req = urllib.request.Request("https://hlkr.co.kr/", headers=headers)
with urllib.request.urlopen(req) as resp:
    html = resp.read().decode('utf-8', errors='ignore')

# Search for -1.76 and -0.01
print("Search -1.76 in hlkr.co.kr:")
matches = [m.start() for m in re.finditer(r'-1\.76', html)]
for idx in matches:
    print(html[max(0, idx-100):min(len(html), idx+150)].replace('\n', ' '))

print("\nSearch -0.01 in hlkr.co.kr:")
matches2 = [m.start() for m in re.finditer(r'-0\.01', html)]
for idx in matches2:
    print(html[max(0, idx-100):min(len(html), idx+150)].replace('\n', ' '))
