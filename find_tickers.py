import sys
import re
import json

sys.stdout.reconfigure(encoding='utf-8')

with open("home.txt", "r", encoding="utf-8") as f:
    home_html = f.read()

# Find next_f chunks
matches = re.findall(r'self\.__next_f\.push\(\[1,"(.*?)"\]\)', home_html)
full_home = "".join(matches).replace('\\"', '"').replace('\\n', '\n').replace('\\/', '/')

with open("home_extracted.txt", "w", encoding="utf-8") as f:
    f.write(full_home)

# Look for stock tickers or market symbols
tickers = ["kospi", "nasdaq", "skhx", "smsn", "samsung", "mu", "pltr", "nvda", "meta", "tsla"]
for t in tickers:
    matches = [m.start() for m in re.finditer(t, full_home, re.IGNORECASE)]
    print(f"Ticker '{t}' found {len(matches)} times in home")
    for idx in matches[:2]:
        print("  Snippet:", full_home[max(0, idx-50):min(len(full_home), idx+150)].replace('\n', ' '))
