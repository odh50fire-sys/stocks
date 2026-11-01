import re
import sys
sys.stdout.reconfigure(encoding='utf-8')

with open("sample_output.txt", "r", encoding="utf-8") as f:
    text = f.read()

# Search for CandleChart invocation
matches = re.finditer(r'CandleChart', text)
for m in matches:
    start = max(0, m.start() - 200)
    end = min(len(text), m.end() + 1000)
    print("=== Found CandleChart in HTML ===")
    print(text[start:end])
