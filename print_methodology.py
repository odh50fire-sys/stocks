import sys
sys.stdout.reconfigure(encoding='utf-8')

with open("methodology_clean.txt", "r", encoding="utf-8") as f:
    text = f.read()

# Find key sections
print("=== First 2000 chars ===")
print(text[:2000])

print("\n=== Data Sources / Calculation ===")
for match in ["데이터", "수집", "계산", "가격", "Hyperliquid", "Binance", "Upbit", "환율", "프리미엄", "HIP", "위켄드", "주말"]:
    pos = 0
    print(f"\n--- Section with '{match}' ---")
    while True:
        idx = text.find(match, pos)
        if idx == -1:
            break
        print(text[max(0, idx-50):min(len(text), idx+150)].strip())
        pos = idx + 200
        if pos > idx + 1000:
            break
