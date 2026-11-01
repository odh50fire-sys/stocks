import sys
sys.stdout.reconfigure(encoding='utf-8')

with open("home.txt", "r", encoding="utf-8") as f:
    text = f.read()

idx = text.find('span\",\"나스닥\"')
if idx != -1:
    print(text[max(0, idx-500):min(len(text), idx+500)].replace('\\"', '"'))
