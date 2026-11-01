import re
import sys
sys.stdout.reconfigure(encoding='utf-8')

with open("sample_output.txt", "r", encoding="utf-8") as f:
    text = f.read()

# Let's search for the ID corresponding to CandleChart
# In Next.js: 2f:I["...", [...], "CandleChart"]
matches = re.findall(r'(\w+):I\(\[[^\]]*CandleChart[^\]]*\]\)', text)
print("CandleChart component ids:", matches)

for cid in matches:
    # search for ["$","$L" + cid
    pattern = rf'\["\$","\$L{cid}",[^\]]+\]'
    invocations = re.findall(pattern, text)
    print(f"Invocations of $L{cid}: {len(invocations)}")
    for inv in invocations[:3]:
        print("  -->", inv[:500])
