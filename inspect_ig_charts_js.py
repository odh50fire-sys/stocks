import re
import urllib.request
import sys
sys.stdout.reconfigure(encoding='utf-8')

headers = {'User-Agent': 'Mozilla/5.0'}
url = "https://www.ig.com/etc.clientlibs/settings/wcm/designs/onedomain/1790053953000/clientlibs_ig_charts.min.js"

req = urllib.request.Request(url, headers=headers)
try:
    with urllib.request.urlopen(req) as resp:
        content = resp.read().decode('utf-8', errors='ignore')
        print("Charts JS length:", len(content))
        
        # look for chart api endpoints
        apis = re.findall(r'(https?://[^\s\"\'\<\>]+|/gateway/[^\s\"\'\<\>]+|/chart/[^\s\"\'\<\>]+|/prices/[^\s\"\'\<\>]+)', content)
        print("Chart JS endpoints:", set(apis)[:10])
except Exception as e:
    print("Error:", e)
