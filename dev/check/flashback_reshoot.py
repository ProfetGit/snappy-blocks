#!/usr/bin/env python3
"""PRD §9 / M4 Flashback row: a re-shoot of a recorded take shows the effects.

Re-shoots the Build Buddy auto-build take (media/Showcase/out/cy_auto_build, recorded without Snappy Blocks: 30 blocks
placed by Build Buddy, ticks 16-58) twice with the same locked-off camera, once with the Snappy Blocks jar of the Fabric
26.3 profile and once without it, as PNG frames (one per tick, no shaders). Then it compares the two:
  - the replay's own placements pop (frames differ around each new block for a few ticks, then match again),
  - outside those moments the two runs match (noise floor of a re-shoot),
and writes a contact sheet of the moments that differ most. Exit 0 when the effects show.

  python3 mods/SnappyBlocks/dev/check/flashback_reshoot.py      (~2 min; frames in dev-out/flashback, git-ignored)
"""
import json
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image

FB = Path.home() / ".claude/skills/pack-showcase/scripts/flashback"
sys.path[:0] = [str(FB), str(FB.parent)]
import cam  # noqa: E402
import showcase as S  # noqa: E402
from recipes import Reel  # noqa: E402

ROOT = Path.home() / "Projects/Minecraft Datapacks/media/Showcase"
TAKE = ROOT / "out/cy_auto_build"
WORK = Path(__file__).resolve().parents[2] / "dev-out/flashback"
# off to the side, so the recorded player (at 0.5 -60 0.5) is not between the camera and the build
EYE, TARGET = (7.5, -55.0, -2.5), (0.5, -59.5, 4.5)
TICKS = (12, 64)
SIZE = (640, 360)


def shoot(name: str, exclude_snappy: bool) -> Path:
    scene = json.loads((TAKE / "cy_auto_build.scene.json").read_text())
    # Chorus Fold 0.0.4 can't join a replay recorded without it (missing damage-type tag: the client disconnects)
    scene["exclude_mods"] = scene.get("exclude_mods", []) + ["chorus_fold-*"]
    if exclude_snappy:
        scene["exclude_mods"] = scene.get("exclude_mods", []) + ["snappy_blocks-*"]
    else:
        scene["exclude_mods"] = [m for m in scene.get("exclude_mods", []) if not m.startswith("snappy_blocks")]
    sp = WORK / f"{name}.scene.json"
    sp.write_text(json.dumps(scene, indent=1))
    reel = Reel(TAKE / "cy_auto_build.replay.zip", WORK / name, scene=sp)
    camera, fov = cam.static(EYE, TARGET, TICKS, fov=60)
    reel._job("frames", container="PNG_SEQUENCE", w=SIZE[0], h=SIZE[1], fps=20, start=TICKS[0], end=TICKS[1], camera=camera, fov=fov,
              look="noshader")
    res = reel.run()
    if not all(v.get("ok") for v in res.values()):
        raise SystemExit(f"{name}: re-shoot failed: {res}")
    return WORK / name / "frames"


def main():
    S.set_root(ROOT)
    WORK.mkdir(parents=True, exist_ok=True)
    on = shoot("with", False)
    off = shoot("without", True)
    a, b = sorted(on.glob("*.png")), sorted(off.glob("*.png"))
    n = min(len(a), len(b))
    if n < 20:
        raise SystemExit(f"too few frames: {len(a)} with, {len(b)} without")
    diffs = []
    for i in range(n):
        x = np.asarray(Image.open(a[i]).convert("RGB"), dtype=np.int16)
        y = np.asarray(Image.open(b[i]).convert("RGB"), dtype=np.int16)
        d = np.abs(x - y).max(axis=2)
        diffs.append(float((d > 24).mean() * 100))
    floor = float(np.median(diffs))
    hot = [i for i, v in enumerate(diffs) if v > max(0.05, floor * 4)]
    print("per-tick share of pixels that differ (%):", " ".join(f"{v:.2f}" for v in diffs))
    print(f"noise floor (median) {floor:.3f} %, frames above it: {len(hot)} of {n} (ticks {[TICKS[0] + i for i in hot]})")
    top = sorted(range(n), key=lambda i: -diffs[i])[:4]
    pairs = []
    for i in sorted(top):
        pairs += [str(a[i]), str(b[i])]
    sheet = WORK / "with_vs_without.png"
    subprocess.run(["wb", "img", "sheet", *pairs, "--cols", "2", "--out", str(sheet)], check=False, capture_output=True)
    print(f"sheet (left with, right without; ticks {[TICKS[0] + i for i in sorted(top)]}): {sheet}")
    ok = len(hot) >= 5 and floor < 0.5
    print("PASS: the re-shoot shows the effects" if ok else "FAIL: no clear difference between the runs")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
