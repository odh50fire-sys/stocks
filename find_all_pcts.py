import re
import sys

sys.stdout.reconfigure(encoding='utf-8')

with open("hlkr_fresh_home.txt", "r", encoding="utf-8") as f:
    html = f.read()

# Look for percentage values like -X.XX% or +X.XX%
pct_matches = re.findall(r'([+\-]?[0-9]+\.[0-9]+)\s*<!-- -->\s*%', html)
print("HTML comment percentages:", pct_matches)

pct_matches2 = re.findall(r'\"children\":\[\"\",\"([+\-]?[0-9\.]+)\",\"%\"\]', html)
print("React next_f percentages:", pct_matches2)

# Find cards with card-like layout
cards = re.findall(r'<div class="card[^"]*"[^>]*>(.*?)</div>\s*</div>', html, re.DOTALL)
print(f"Cards found: {len(cards)}")
for i, c in enumerate(cards[:10]):
    clean = re.sub(r'<[^>]+>', ' ', c)
    clean = re.sub(r'\s+', ' ', clean).strip()
    print(f"Card {i}: {clean[:120]}")
