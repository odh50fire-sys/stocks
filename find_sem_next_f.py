import re
import sys

sys.stdout.reconfigure(encoding='utf-8')

with open("samsungem_raw.txt", "r", encoding="utf-8") as f:
    text = f.read()

# Look for next_f pushes
matches = re.findall(r'self\.__next_f\.push\(\[1,"(.*?)"\]\)', text)
full_data = "".join(matches).replace('\\"', '"').replace('\\n', '\n').replace('\\/', '/')

# Search for SAMSUNGEM in full_data
idx = 0
while True:
    idx = full_data.find("SAMSUNGEM", idx)
    if idx == -1:
        break
    print("=== SAMSUNGEM in next_f ===")
    print(full_data[max(0, idx-100):min(len(full_data), idx+1000)])
    idx += 9
