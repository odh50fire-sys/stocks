import re
import sys
sys.stdout.reconfigure(encoding='utf-8')

with open("sample_output.txt", "r", encoding="utf-8") as f:
    text = f.read()

# Search for TradingView or chart component or canvas or svg
matches = re.finditer(r'(TradingView|lightweight-charts|Chart|Candle|kline|history|candles|series)', text, re.IGNORECASE)
found = set()
for m in matches:
    start = max(0, m.start() - 50)
    end = min(len(text), m.end() + 100)
    found.add(text[start:end].replace('\n', ' '))

print(f"Total occurrences: {len(found)}")
for sample in list(found)[:15]:
    print("Sample:", sample)
