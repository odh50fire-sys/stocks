import urllib.request
import re
import sys
sys.stdout.reconfigure(encoding='utf-8')

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
    'Accept-Language': 'en-US,en;q=0.5'
}

def check_ig(url):
    print(f"=== Fetching {url} ===")
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req) as resp:
            html = resp.read().decode('utf-8', errors='ignore')
            print(f"Status 200, length: {len(html)}")
            
            # Look for price or epic or instrument ID
            epics = re.findall(r'\"epic\":\"([^\"]+)\"', html)
            print("Epics found:", epics)
            
            # Look for chart data or API
            apis = re.findall(r'https?://[^\s\"\'\<\>]+', html)
            print("Sample APIs:")
            for a in set(apis):
                if any(k in a.lower() for k in ['price', 'chart', 'market', 'quote', 'lightstreamer', 'gateway']):
                    print("  ", a)
            
            with open("ig_sample.txt", "w", encoding="utf-8") as f:
                f.write(html)
    except Exception as e:
        print(f"Error fetching {url}: {e}")

check_ig("https://www.ig.com/en/indices/markets-indices/weekend-us-tech-100-e1")
