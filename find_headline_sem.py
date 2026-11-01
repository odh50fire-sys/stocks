import re
import sys

sys.stdout.reconfigure(encoding='utf-8')

with open("hlkr_fresh_home.txt", "r", encoding="utf-8") as f:
    home_html = f.read()

# Let's search for samsungem in home_html
print("=== Search in home_html ===")
matches = [m.start() for m in re.finditer(r'009150|samsungem|삼성전기', home_html, re.IGNORECASE)]
for idx in matches:
    print(home_html[max(0, idx-60):min(len(home_html), idx+150)].replace('\n', ' '))

# And check stocks/samsungem html
with open("samsungem_raw.txt", "w", encoding="utf-8") as f:
    import urllib.request
    req = urllib.request.Request("https://hlkr.co.kr/stocks/samsungem", headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req) as resp:
        f.write(resp.read().decode('utf-8', errors='ignore'))

with open("samsungem_raw.txt", "r", encoding="utf-8") as f:
    sem_html = f.read()

idx = sem_html.find("headline truncate block")
if idx != -1:
    print("\n=== Around headline in stocks/samsungem ===")
    print(sem_html[max(0, idx-100):min(len(sem_html), idx+1500)].replace('\\"', '"').replace('\n', ' '))
