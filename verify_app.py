import urllib.request
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

try:
    res = urllib.request.urlopen('http://localhost:8080/api/indicators', timeout=7)
    print('Indicators status:', res.status)
    data = json.loads(res.read().decode('utf-8'))
    items = data.get('items', [])
    print(f'Total items: {len(items)}')
    for it in items:
        badge = "📈 차트 제공" if it["hasChart"] else "📊 수치 전용"
        print(f"[{it['index']:2d}] {it['name']:<20} ({it['symbol']:<10}) | {it['price']} | {it['changePct']:>+6.2f}% | {badge}")

    res2 = urllib.request.urlopen('http://localhost:8080/api/candles?symbol=MU&interval=5m', timeout=7)
    print('Candles status:', res2.status)
    cdata = json.loads(res2.read().decode('utf-8'))
    print('Candles count for MU:', len(cdata.get('candles', [])))
    
    res3 = urllib.request.urlopen('http://localhost:8080/index.html', timeout=5)
    print('index.html status:', res3.status, f'(Length: {len(res3.read())} bytes)')
    
    print("\n✅ ALL ENDPOINTS AND FRONTEND ASSETS VERIFIED SUCCESSFULLY!")
except Exception as e:
    print('Error:', e)
