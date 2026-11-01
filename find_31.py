import re
import sys
sys.stdout.reconfigure(encoding='utf-8')

with open("sample_output.txt", "r", encoding="utf-8") as f:
    text = f.read()

# Look for 31:I or 31:
m = re.search(r'31:I\(\[(.*?)\]\)', text)
if m:
    print("31 definition:", m.group(1))
else:
    print("31 not found directly, searching 31:...")
    m2 = re.findall(r'(\d+):I\(\[[^\]]*0zh7o13328rps[^\]]*\]\)', text)
    print("Chunk matches:", m2)
