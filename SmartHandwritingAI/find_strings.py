import os
import re

path = 'app/src/main/java/dat/nguyenvan/smarthandwritingai'
pattern = re.compile(r'"([^"\\]*(?:\\.[^"\\]*)*)"')
vietnamese_chars = set("đáàảãạâêôưíìỉĩịóòỏõọúùủũụýỳỷỹỵĐÁÀẢÃẠÂÊÔƯÍÌỈĨỊÓÒỎÕỌÚÙỦŨỤÝỲỶỸỴăâêôơư")

results = []
for root, dirs, files in os.walk(path):
    for f in files:
        if f.endswith('.java'):
            filepath = os.path.join(root, f)
            try:
                with open(filepath, 'r', encoding='utf-8') as file:
                    for idx, line in enumerate(file, 1):
                        for match in pattern.findall(line):
                            if any(c in vietnamese_chars for c in match):
                                results.append(f"{f}:{idx}: {line.strip()}")
            except Exception as e:
                print(f"Error reading {f}: {e}")

with open('found_strings.txt', 'w', encoding='utf-8') as outfile:
    for r in results:
        outfile.write(r + '\n')
print(f"Done! Found {len(results)} strings. Saved to found_strings.txt")
