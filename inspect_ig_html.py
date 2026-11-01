import re
import sys
sys.stdout.reconfigure(encoding='utf-8')

with open("ig_sample.txt", "r", encoding="utf-8") as f:
    text = f.read()

# Look for data attributes, epic, instrument, chart config
matches = re.findall(r'data-[a-zA-Z0-9_-]+="[^"]*"', text)
print("Data attributes count:", len(matches))
for m in matches:
    if any(k in m.lower() for k in ['epic', 'instrument', 'market', 'chart', 'price', 'symbol', 'feed', 'id']):
        print(" ", m)

# Search for price / change in text
price_matches = re.findall(r'<div[^>]*class="[^"]*price[^"]*"[^>]*>.*?</div>', text, re.DOTALL)
print("Price divs:", len(price_matches))
for p in price_matches[:5]:
    print(" ", re.sub(r'\s+', ' ', p))
