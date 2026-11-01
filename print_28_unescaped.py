import sys
sys.stdout.reconfigure(encoding='utf-8')

with open("sample_output.txt", "r", encoding="utf-8") as f:
    text = f.read()

idx = text.find('28:[[')
if idx != -1:
    print(text[idx:idx+4000].replace('\\"', '"').replace('\\n', '\n'))
