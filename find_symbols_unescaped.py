import sys
import re

sys.stdout.reconfigure(encoding='utf-8')

with open("home.txt", "r", encoding="utf-8") as f:
    text = f.read()

unescaped = text.replace('\\"', '"').replace('\\/', '/')

assets = re.findall(r'"symbol":"([^"]+)"', unescaped)
print("Unique symbols in home.txt:", set(assets))

nameKos = re.findall(r'"nameKo":"([^"]+)"', unescaped)
print("Unique nameKos in home.txt:", set(nameKos))
