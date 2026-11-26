#!/usr/bin/env python3
"""Turns the Blockbench arm renders and swing motion (scene.js) into the settings preview's textures. All art here is ours.
Run from anywhere: python3 -I dev/preview/compose.py ."""
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

DIR = Path(__file__).resolve().parent
OUT = DIR / "out"
ASSETS = DIR.parents[1] / "src/main/resources/assets/snappy_blocks/textures/gui/preview"
W, H, AX, AY = 396, 300, 198, 142


def load(name):
    return np.asarray(Image.open(OUT / name).convert("RGBA"), dtype=np.float32) / 255


def save(a, name, blur=False):
    ASSETS.mkdir(parents=True, exist_ok=True)
    Image.fromarray((np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA").save(ASSETS / name, optimize=True)
    meta = ASSETS / (name + ".mcmeta")
    if blur:
        meta.write_text('{"texture":{"blur":true,"clamp":true}}\n')
    elif meta.exists():
        meta.unlink()


def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


# ---- the pool of light behind the block, laid over the preview box's own dark ----
def glow():
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    pool = np.exp(-(((xx - AX) / 130) ** 2 + ((yy - AY + 22) / 95) ** 2))
    edge = smooth(0, 40, np.minimum(np.minimum(xx, W - 1 - xx), np.minimum(yy, H - 1 - yy)))
    bayer = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], np.float32) / 16 - 0.5
    a = np.clip(0.096 * pool * edge + np.tile(bayer, (H // 4, W // 4)) / 255 * (pool * edge > 0.02), 0, 1)
    save(np.dstack([np.full((H, W), 1.0), np.full((H, W), 0.75), np.full((H, W), 0.59), a]), "glow.png")


# ---- shadows and the hero block's light ----
def shadows():
    def soft(w, h, shape, blur, alpha, name):
        yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
        u, v = (xx + 0.5 - w / 2), (yy + 0.5 - h / 2)
        m = (shape(u, v) <= 1).astype(np.uint8) * 255
        m = np.asarray(Image.fromarray(m, "L").filter(ImageFilter.GaussianBlur(blur)), np.float32) / 255
        out = np.zeros((h, w, 4), np.float32)
        out[..., :3] = (0.05, 0.03, 0.10)
        out[..., 3] = m * alpha
        save(out, name, blur=True)
    # the block's footprint on the ground (a 128 x 64 diamond), centred in 192 x 112
    soft(192, 112, lambda u, v: np.abs(u) / 70 + np.abs(v) / 36, 7, 0.62, "shadow_block.png")
    soft(64, 32, lambda u, v: np.hypot(u / 20, v / 8), 2.5, 0.88, "shadow_item.png")


def shade():
    def put(a, mask, col, al):
        a[mask] = (*col, al)

    # top face, in texture space (u towards the right-hand edge, v towards the left-hand one)
    n = 64
    v, u = (np.mgrid[0:n, 0:n].astype(np.float32) + 0.5) / n
    d = (u + v) / 2
    top = np.zeros((n, n, 4), np.float32)
    top[d < 0.5] = 0
    top[..., :3] = np.where((d < 0.5)[..., None], np.array([1.0, 0.95, 0.80]), np.array([0.12, 0.06, 0.22]))
    top[..., 3] = np.where(d < 0.5, (0.5 - d) * 0.30, (d - 0.5) * 0.20)
    put(top, (u > 1 - 2 / n) | (v > 1 - 2 / n), (1.0, 0.96, 0.86), 0.38)
    save(top, "shade_top.png")
    # side faces, 64 x 80: darker towards the ground, a lit top and front edge
    for name, front, edge_a in (("shade_left.png", "right", 0.22), ("shade_right.png", "left", 0.14)):
        h, w = 80, 64
        yy, xx = (np.mgrid[0:h, 0:w].astype(np.float32) + 0.5)
        a = np.zeros((h, w, 4), np.float32)
        a[..., :3] = (0.08, 0.03, 0.18)
        a[..., 3] = 0.34 * smooth(0.25, 1.0, yy / h)
        put(a, yy < 2, (1.0, 0.93, 0.82), edge_a * 0.9)
        put(a, (xx > w - 2) if front == "right" else (xx < 2), (1.0, 0.93, 0.82), edge_a)
        save(a, name)


# ---- the arm: 2x renders filtered down, a dark rim, cropped to a sheet ----
def frame(name):
    a = load(name)
    p = a[..., :3] * a[..., 3:]
    q = np.dstack([p, a[..., 3]]).reshape(H, 2, W, 2, 4).mean((1, 3))
    al = q[..., 3]
    rgb = q[..., :3] / np.maximum(al, 1e-4)[..., None]
    m = Image.fromarray((al * 255).astype(np.uint8), "L")
    ring = np.asarray(m.filter(ImageFilter.MaxFilter(3)), np.float32) / 255
    ra = np.clip(ring - al, 0, 1) * 0.85
    oa = al + ra * (1 - al)
    orgb = (rgb * al[..., None] + np.array([0.13, 0.05, 0.06]) * (ra * (1 - al))[..., None]) / np.maximum(oa, 1e-4)[..., None]
    return np.dstack([orgb, oa])


# one crop for all the named models, so their sheets share a geometry
def sheets(meta, names, clips):
    frames = {n: [frame(f"{n}_{c}_{i:02d}.png") for c in clips for i in range(meta[f"{n}_{c}"])] for n in names}
    mask = np.max([f[..., 3] for fs in frames.values() for f in fs], axis=0) > 0.01
    ys, xs = np.nonzero(mask)
    x0, y0 = max(0, xs.min() - 1) // 2 * 2, max(0, ys.min() - 1) // 2 * 2
    x1, y1 = min(W, -(-(xs.max() + 2) // 2) * 2), min(H, -(-(ys.max() + 2) // 2) * 2)
    cw, ch, cols = x1 - x0, y1 - y0, 7
    for n, fs in frames.items():
        rows = -(-len(fs) // cols)
        sheet = np.zeros((rows * ch, cols * cw, 4), np.float32)
        for i, f in enumerate(fs):
            sheet[i // cols * ch:(i // cols + 1) * ch, i % cols * cw:(i % cols + 1) * cw] = f[y0:y1, x0:x1]
        save(sheet, n + ".png")
        geo = {"x": int(x0), "y": int(y0), "w": int(cw), "h": int(ch), "cols": cols, "rows": rows, **{c: meta[f"{n}_{c}"] for c in clips}}
        print(n, geo, "sheet", sheet.shape[1], "x", sheet.shape[0])


if __name__ == "__main__":
    glow()
    shadows()
    shade()
    meta = json.loads((OUT / "meta.json").read_text())
    order = ("swing", "follow", "raise", "mine")
    clips = {clip: meta["swing_" + clip] for clip in order}
    assert len(meta["motion"]) == sum(clips.values())
    (ASSETS.parents[2] / "preview").mkdir(exist_ok=True)
    (ASSETS.parents[2] / "preview/swing.json").write_text(json.dumps({"frames": meta["motion"]}, separators=(",", ":")) + "\n")
    print("swing", clips, "frames", len(meta["motion"]))
    sheets(meta, ("arm",), ("throw",))
    # the fist at the release frame, in GUI pixels from the box's corner
    print("fist", [round(v / 2, 1) for v in meta["fist"]])
