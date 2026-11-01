import urllib.request
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')
headers = {'User-Agent': 'Mozilla/5.0'}
url = 'https://api.github.com/repos/odh50fire-sys/stocks/actions/runs/41295565237/jobs'

try:
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read().decode('utf-8'))
        jobs = data.get('jobs', [])
        for j in jobs:
            print(f"Job: {j.get('name')} | Status: {j.get('status')} | Conclusion: {j.get('conclusion')}")
            for step in j.get('steps', []):
                print(f"  - Step: {step.get('name')} | Status: {step.get('status')} | Conclusion: {step.get('conclusion')}")
except Exception as e:
    print('Error:', e)
