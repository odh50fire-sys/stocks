import re
import urllib.request
import sys
sys.stdout.reconfigure(encoding='utf-8')

# Search extracted_data.txt for api or ws or fetch
with open("extracted_data.txt", "r", encoding="utf-8") as f:
    text = f.read()

# Let's search for wss:// or api/ or chart endpoint
urls = re.findall(r'wss?://[^\s\"\'\<\>]+', text)
print("WebSockets:", set(urls))

apis = re.findall(r'(/api/[^\s\"\'\<\>]+)', text)
print("API paths:", set(apis))

# Look for candle or chart
charts = re.findall(r'([^\s\"\'\<\>]*chart[^\s\"\'\<\>]*)', text, re.IGNORECASE)
print("Chart occurrences:", set(charts)[:10])
