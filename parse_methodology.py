import re

with open("methodology.txt", "r", encoding="utf-8") as f:
    text = f.read()

# Strip tags and inspect text
clean_text = re.sub(r'<[^>]+>', ' ', text)
clean_text = re.sub(r'\s+', ' ', clean_text)

with open("methodology_clean.txt", "w", encoding="utf-8") as f:
    f.write(clean_text)

print("Methodology preview:", clean_text[:2000])
