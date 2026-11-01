import re
import json

with open("sample_output.txt", "r", encoding="utf-8") as f:
    text = f.read()

# Find next_f pushes
matches = re.findall(r'self\.__next_f\.push\(\[1,"(.*?)"\]\)', text)
full_data = "".join(matches).replace('\\"', '"').replace('\\n', '\n').replace('\\/', '/')

with open("extracted_data.txt", "w", encoding="utf-8") as f:
    f.write(full_data)

print(f"Extracted {len(full_data)} characters")

# Search for source, provider, 출처, 데이터, IG, API, etc.
keywords = ["출처", "데이터", "제공", "source", "provider", "ig", "broker", "api", "chart", "차트", "실시간", "단위", "지표"]
for kw in keywords:
    matches = [m.start() for m in re.finditer(kw, full_data, re.IGNORECASE)]
    print(f"Keyword '{kw}': {len(matches)} occurrences")
    for idx in matches[:3]:
        snippet = full_data[max(0, idx-100):min(len(full_data), idx+150)]
        print(f"   Snippet: {snippet.strip()}")
