import sys
sys.stdout.reconfigure(encoding='utf-8')

with open("sample_output.txt", "r", encoding="utf-8") as f:
    text = f.read()

idx = 0
while True:
    idx = text.find("CandleChart", idx)
    if idx == -1:
        break
    print("=== CandleChart occurrence ===")
    print(text[max(0, idx-100):min(len(text), idx+300)])
    idx += 11
