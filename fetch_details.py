import urllib.request
import re
import json

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
}

def get_page_text(url):
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req) as resp:
        return resp.read().decode('utf-8', errors='ignore')

methodology_html = get_page_text("https://hlkr.co.kr/methodology")
with open("methodology.txt", "w", encoding="utf-8") as f:
    f.write(methodology_html)

home_html = get_page_text("https://hlkr.co.kr/")
with open("home.txt", "w", encoding="utf-8") as f:
    f.write(home_html)

print("Saved methodology.txt and home.txt")
