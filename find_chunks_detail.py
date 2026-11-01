import re
import sys
sys.stdout.reconfigure(encoding='utf-8')

with open("sample_output.txt", "r", encoding="utf-8") as f:
    text = f.read()

# Let's search for "initialInterval"
idx = text.find("initialInterval")
if idx != -1:
    print("Around initialInterval:")
    print(text[max(0, idx-200):min(len(text), idx+200)])

# And search for "0zh7o13328rps" which was one of the chunks
idx2 = text.find("0zh7o13328rps")
if idx2 != -1:
    print("\nAround 0zh7o13328rps:")
    print(text[max(0, idx2-100):min(len(text), idx2+200)])
