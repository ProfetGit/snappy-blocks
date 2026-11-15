import sys, glob, os
from PIL import Image
d = sys.argv[1]
fs = sorted(glob.glob(os.path.join(d, 'f*.png')))
counts = []
for f in fs:
    im = Image.open(f).convert('L'); w, h = im.size
    c = im.crop((w//2-100, h//2-100, w//2+100, h//2+100))
    counts.append(sum(1 for p in c.get_flattened_data() if p < 45))
first = next((i for i, n in enumerate(counts) if n > 0), None)
last = max((i for i, n in enumerate(counts) if n > 0), default=None)
zeros = [i for i in range(first, last + 1) if counts[i] == 0] if first is not None else []
print(f"{d}: frames {len(fs)}, crack span {first}-{last}, zero frames in span {len(zeros)}: {zeros}")
print(' '.join(map(str, counts)))
