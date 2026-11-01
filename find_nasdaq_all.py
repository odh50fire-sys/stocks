import sys
sys.stdout.reconfigure(encoding='utf-8')

with open("home.txt", "r", encoding="utf-8") as f:
    text = f.read()

idx = text.find('나스닥')
while idx != -1:
    print("=== Found ===")
    print(text[max(0, idx-300):min(len(text), idx+300)])
    idx = text.find('나스닥', idx+1)
