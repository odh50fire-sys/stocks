import sys
sys.stdout.reconfigure(encoding='utf-8')

with open("home.txt", "r", encoding="utf-8") as f:
    text = f.read()

# Let's see what is inside /api/world or similar endpoints on hlkr
# Or check all API endpoints in 0zh7o13328rps.js or other chunks
# Let's inspect the entire list of assets in home.txt
import re
assets = re.findall(r'\"symbol\":\"([^\"]+)\"', text)
print("Unique symbols in home.txt:", set(assets))

names = re.findall(r'\"nameKo\":\"([^\"]+)\"', text)
print("Unique nameKo in home.txt:", set(names))
