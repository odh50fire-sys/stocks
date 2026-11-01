import urllib.request
import re
import sys
sys.stdout.reconfigure(encoding='utf-8')

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'
}

url = "https://www.ig.com/en/indices/markets-indices/weekend-oil---us-crude"
req = urllib.request.Request(url, headers=headers)
with urllib.request.urlopen(req) as resp:
    html = resp.read().decode('utf-8', errors='ignore')

# Find epics and price
epics = re.findall(r'data-epic="([^"]+)"', html)
print("Epics in weekend oil:", set(epics))

# Find price ticket
matches = re.findall(r'data-field="([^"]+)"[^>]*>([^<]+)<', html)
print("Fields in weekend oil:", matches[:10])
