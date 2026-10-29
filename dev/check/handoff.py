#!/usr/bin/env python3
"""Hand-off check (PRD pillar 2): how much the picture changes on the frame a pop's copy gives way to the chunk mesh.

handoff.py <frames dir>   (reads <dir>.handoffs.txt written by the test driver)

The frame on which the hand-off was seen (the copy no longer drawn, the mesh live) is compared with the frame before.
A seamless swap changes only noise; a shading mismatch (AO, light, material) shows as a tile that jumps. Prints the
worst 16 px tile difference per hand-off and the same measure for ordinary frame pairs as the baseline.
"""
import sys
from pathlib import Path

from PIL import Image, ImageChops

TILE = 16


def worst(a: Image.Image, b: Image.Image) -> float:
    d = ImageChops.difference(a.convert("L"), b.convert("L"))
    w, h = d.size
    small = d.resize((w // TILE, h // TILE), Image.Resampling.BOX)
    return max(small.get_flattened_data())


def main():
    frames = Path(sys.argv[1])
    hand = [int(x) for x in (frames.parent / (frames.name + ".handoffs.txt")).read_text().split()]
    files = sorted(frames.glob("f*.png"))
    img = lambda i: Image.open(files[i])
    rows = []
    for k in hand:
        # the frame recorded is the next capture index: the change happened between k-1 and k
        if 1 <= k < len(files):
            rows.append((k, worst(img(k - 1), img(k))))
    base = []
    for k in range(2, len(files), 9):
        if all(abs(k - h) > 2 for h in hand):
            base.append(worst(img(k - 1), img(k)))
    base.sort()
    for k, v in rows:
        print(f"handoff frame {k}: worst tile {v:.1f}")
    if base:
        print(f"baseline (non-handoff pairs, n={len(base)}): median {base[len(base) // 2]:.1f}, max {base[-1]:.1f}")
    if rows:
        print(f"handoffs: max {max(v for _, v in rows):.1f}")


if __name__ == "__main__":
    main()
