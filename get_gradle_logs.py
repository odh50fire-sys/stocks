import urllib.request
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')
headers = {'User-Agent': 'Mozilla/5.0'}
url = 'https://api.github.com/repos/odh50fire-sys/stocks/actions/runs/41295565237/jobs'

req = urllib.request.Request(url, headers=headers)
with urllib.request.urlopen(req) as resp:
    data = json.loads(resp.read().decode('utf-8'))
    jobs = data.get('jobs', [])
    if jobs:
        job_id = jobs[0].get('id')
        print(f"Job ID: {job_id}")
        log_url = f"https://api.github.com/repos/odh50fire-sys/stocks/actions/jobs/{job_id}/logs"
        try:
            req2 = urllib.request.Request(log_url, headers=headers)
            with urllib.request.urlopen(req2) as resp2:
                log_text = resp2.read().decode('utf-8', errors='ignore')
                print(f"Log length: {len(log_text)}")
                # Print last 50 lines
                lines = log_text.splitlines()
                for line in lines[-60:]:
                    print(line)
        except Exception as e:
            print("Error fetching logs:", e)
