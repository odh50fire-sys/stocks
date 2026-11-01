import urllib.request
import re
import json

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
}

def analyze_page(url):
    print(f"=== Analyzing {url} ===")
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req) as response:
            html = response.read().decode('utf-8', errors='ignore')
            print(f"Status 200, length: {len(html)}")
            
            # Look for JSON-like data or next_f data
            next_data = re.findall(r'self\.__next_f\.push\(\[(.*?)\]\)', html, re.DOTALL)
            print(f"Found {len(next_data)} next_f chunks")
            
            # Look for keywords: ig.com, IG, source, chart, api, websocket, etc.
            keywords = ['api', 'socket', 'ig.com', 'weekend', 'kospi', 'nasdaq', 'provider', 'source', 'chart', 'korean', 'smsn', 'skhx', 'samsung', 'krx', 'candles']
            for line in html.split('\n'):
                for kw in keywords:
                    if kw in line.lower() and len(line) < 300:
                        print(f"  [{kw}]: {line.strip()[:150]}")
                        break
            
            # Save raw HTML sample for inspection
            with open("sample_output.txt", "w", encoding="utf-8") as f:
                f.write(html)
                
    except Exception as e:
        print(f"Error fetching {url}: {e}")

analyze_page("https://hlkr.co.kr/stocks/mu")
