import re
import sys
sys.stdout.reconfigure(encoding='utf-8')

with open("sample_output.txt", "r", encoding="utf-8") as f:
    text = f.read()

# find all :I([
all_i = re.findall(r'([0-9a-f]+):I\(\[([^\]]+)\]\)', text)
for k, v in all_i:
    if "Chart" in v or "chart" in v:
        print(f"{k} -> {v}")
