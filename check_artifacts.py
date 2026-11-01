import urllib.request
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')
headers = {'User-Agent': 'Mozilla/5.0'}

# Check Artifacts
try:
    url = 'https://api.github.com/repos/odh50fire-sys/stocks/actions/runs/41295715613/artifacts'
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read().decode('utf-8'))
        arts = data.get('artifacts', [])
        print(f"Artifacts count: {len(arts)}")
        for a in arts:
            print(f"- Artifact: {a.get('name')} | Size: {a.get('size_in_bytes') / (1024*1024):.2f} MB")
except Exception as e:
    print('Artifacts error:', e)

# Check Releases
try:
    url2 = 'https://api.github.com/repos/odh50fire-sys/stocks/releases'
    req2 = urllib.request.Request(url2, headers=headers)
    with urllib.request.urlopen(req2) as resp2:
        releases = json.loads(resp2.read().decode('utf-8'))
        print(f"Releases count: {len(releases)}")
        if releases:
            rel = releases[0]
            print(f"Release: {rel.get('name')} ({rel.get('tag_name')})")
            print(f"Release URL: {rel.get('html_url')}")
            for asset in rel.get('assets', []):
                print(f"  - Download Asset: {asset.get('name')} | Download URL: {asset.get('browser_download_url')}")
except Exception as e:
    print('Releases error:', e)
