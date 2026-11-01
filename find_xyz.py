import re
import urllib.request
import sys
sys.stdout.reconfigure(encoding='utf-8')

# Search extracted_data.txt for how xyz:KR200 or xyz is requested
with open("extracted_data.txt", "r", encoding="utf-8") as f:
    text = f.read()

# Let's search for "xyz:" occurrences
pos = 0
while True:
    idx = text.find("xyz:", pos)
    if idx == -1:
        break
    print(text[max(0, idx-60):min(len(text), idx+120)].replace('\n', ' '))
    pos = idx + 10
