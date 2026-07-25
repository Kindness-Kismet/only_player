# -*- coding: utf-8 -*-
from collections import defaultdict
from pathlib import Path
import zipfile

apk = Path(r"E:/Downloads/Only-Player-ui-fix-v15-arm64-1.0.148.apk")
cats = defaultdict(lambda: [0, 0, 0])  # raw, zip, count
with zipfile.ZipFile(apk) as z:
    for i in z.infolist():
        n = i.filename.replace("\\", "/")
        if n.startswith("META-INF/"):
            cat = "META-INF"
        elif n.endswith(".dex"):
            cat = "dex"
        elif n.endswith(".so"):
            cat = "native_so"
        elif n == "resources.arsc" or n.endswith(".arsc"):
            cat = "arsc"
        elif n.startswith("res/"):
            cat = "res"
        elif n.startswith("assets/"):
            cat = "assets"
        else:
            cat = "other"
        cats[cat][0] += i.file_size
        cats[cat][1] += i.compress_size
        cats[cat][2] += 1

print(f'{"cat":12} {"raw_MB":>8} {"zip_MB":>8} {"n":>5}')
for k, v in sorted(cats.items(), key=lambda x: -x[1][0]):
    print(f"{k:12} {v[0]/1e6:8.1f} {v[1]/1e6:8.1f} {v[2]:5d}")
raw = sum(v[0] for v in cats.values())
print("total raw MB", raw / 1e6)
print("total zip MB", sum(v[1] for v in cats.values()) / 1e6)
dex = cats["dex"][0]
so = cats["native_so"][0]
rest = raw - dex - so - cats["META-INF"][0]
print("dex raw", dex / 1e6)
print("so raw", so / 1e6)
print("rest raw", rest / 1e6)
print("floor if dex 35% so stored rest 40%:", (dex * 0.35 + so + rest * 0.4) / 1e6)
print("floor if dex 30% so stored rest 35%:", (dex * 0.30 + so + rest * 0.35) / 1e6)
print("floor if dex 30% so 70% rest 35% (so also compressed):", (dex * 0.30 + so * 0.70 + rest * 0.35) / 1e6)
