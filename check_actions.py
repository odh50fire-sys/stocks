import urllib.request
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')
headers = {'User-Agent': 'Mozilla/5.0'}
url = 'https://api.github.com/repos/odh50fire-sys/stocks/actions/runs'

try:
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read().decode('utf-8'))
        runs = data.get('workflow_runs', [])
        print(f"Total workflow runs: {len(runs)}")
        for r in runs[:3]:
            print(f"- Run #{r.get('run_number')}: {r.get('name')} | Status: {r.get('status')} | Conclusion: {r.get('conclusion')}")
            print(f"  URL: {r.get('html_url')}")
except Exception as e:
    print('GitHub API error:', e)
