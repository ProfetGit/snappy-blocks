#!/usr/bin/env python3
"""Copy Snappy Blocks' render engine into Snappy Doors (Snappy Blocks is canonical), with the renames applied.
sync_engine.py            write the copies and mods/SnappyDoors/dev/engine.lock
sync_engine.py --check    exit 1 on drift, a leftover Snappy Blocks name, or a synced mixin missing from the mixin config
sync_engine.py --list     print the manifest"""
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SRC_MOD = HERE.parent
WS_MODS = SRC_MOD.parent
DST_MOD = WS_MODS / "SnappyDoors"
SRC_JAVA = SRC_MOD / "src/main/java"
DST_JAVA = DST_MOD / "src/main/java"
BASE = "io/github/profetgit/snappyblocks/"
HEADER = "// SYNCED from mods/SnappyBlocks by dev/sync_engine.py: do not edit, change the source and re-run.\n"
RENAMES = [
    ("snappyblocks", "snappydoors"),
    ("snappy_blocks", "snappy_doors"),
    ("SnappyBlocks", "SnappyDoors"),
    ("Snappy Blocks", "Snappy Doors"),
]
LEFTOVER = re.compile(r"snappy.?blocks", re.I)
LOCK = DST_MOD / "dev/engine.lock"
MIXIN_JSON = DST_MOD / "src/main/resources/snappy_doors.mixins.json"
MIXIN_PLUGIN = "io.github.profetgit.snappydoors.mixin.MixinPlugin"

ENGINE = [
    "pop/Hidden.java",
    "pop/HiddenRegion.java",
    "pop/HiddenHolder.java",
    "pop/SodiumHidden.java",
    "pop/ShaderPack.java",
    "pop/AoConsumer.java",
    "RenderPass.java",
    "Clock.java",
    "platform/Platform.java",
    "mixin/MixinPlugin.java",
    "mixin/RenderRegionCacheMixin.java",
    "mixin/RenderSectionRegionMixin.java",
    "mixin/SectionCompilerMixin.java",
    "mixin/ModelBlockRendererMixin.java",
    "mixin/compat/SodiumMeshingTaskMixin.java",
    "mixin/compat/SodiumMeshingTaskInit6Mixin.java",
    "mixin/compat/SodiumMeshingTaskInit7Mixin.java",
    "mixin/compat/SodiumMeshingTaskInit8Mixin.java",
    "mixin/compat/SodiumBlockRenderContextMixin.java",
    "mixin/compat/SodiumOcclusionCacheMixin.java",
    "mixin/compat/SodiumNonTerrainAoMixin.java",
]


def manifest():
    files = [BASE + f for f in ENGINE]
    files += sorted(p.relative_to(SRC_JAVA).as_posix() for p in (SRC_JAVA / "net/caffeinemc").rglob("*.java"))
    return files


def rename(text):
    for a, b in RENAMES:
        text = text.replace(a, b)
    return text


def generate():
    """{destination relative path: (generated text, text without the header)}"""
    out = {}
    for rel in manifest():
        body = rename((SRC_JAVA / rel).read_text(encoding="utf-8"))
        out[rename(rel)] = (HEADER + body, body)
    return out


def sha(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def git(*args):
    return subprocess.run(["git", "-C", str(SRC_MOD), *args], capture_output=True, text=True).stdout.strip()


def mod_version():
    for line in (SRC_MOD / "gradle.properties").read_text().splitlines():
        if line.startswith("mod.version="):
            return line.split("=", 1)[1].strip()
    return "?"


def lock_text(gen):
    lock = {
        "source": "mods/SnappyBlocks",
        "version": mod_version(),
        "head": git("rev-parse", "HEAD"),
        "dirty": bool(git("status", "--porcelain", "--", "src/main/java")),
        "files": {rel: sha(text) for rel, (text, _) in sorted(gen.items())},
    }
    return json.dumps(lock, indent=2) + "\n"


def mixin_problems(gen):
    problems = []
    cfg = json.loads(MIXIN_JSON.read_text(encoding="utf-8")) if MIXIN_JSON.exists() else {}
    listed = set(cfg.get("client", [])) | set(cfg.get("mixins", [])) | set(cfg.get("server", []))
    for rel in gen:
        if "/mixin/" not in rel or rel.endswith("/MixinPlugin.java"):
            continue
        name = rel.split("/mixin/", 1)[1][:-5].replace("/", ".")
        if name not in listed:
            problems.append(f"mixin {name} is not in {MIXIN_JSON.name}")
    if cfg.get("plugin") != MIXIN_PLUGIN:
        problems.append(f"{MIXIN_JSON.name} has no \"plugin\": \"{MIXIN_PLUGIN}\"")
    return problems


def main():
    args = sys.argv[1:]
    if args == ["--list"]:
        print("\n".join(manifest()))
        return 0
    if args not in ([], ["--check"]):
        print(__doc__)
        return 2
    gen = generate()
    if args == ["--check"]:
        problems = []
        for rel, (text, body) in gen.items():
            f = DST_JAVA / rel
            if not f.exists():
                problems.append(f"missing {rel}")
            elif f.read_text(encoding="utf-8") != text:
                problems.append(f"drift {rel}")
            m = LEFTOVER.search(body)
            if m:
                line = body[:m.start()].count("\n") + 1
                problems.append(f"leftover name '{m.group(0)}' in {rel}:{line}")
        if not LOCK.exists() or LOCK.read_text(encoding="utf-8") and json.loads(LOCK.read_text())["files"] != {
                rel: sha(text) for rel, (text, _) in sorted(gen.items())}:
            problems.append("engine.lock hashes differ from the generated files")
        problems += mixin_problems(gen)
        for p in problems:
            print("FAIL " + p)
        print(f"{len(gen)} files, {len(problems)} problems")
        return 1 if problems else 0
    old = set(json.loads(LOCK.read_text())["files"]) if LOCK.exists() else set()
    for rel in sorted(old - set(gen)):
        (DST_JAVA / rel).unlink(missing_ok=True)
        print(f"removed {rel}")
    changed = 0
    for rel, (text, _) in gen.items():
        f = DST_JAVA / rel
        if f.exists() and f.read_text(encoding="utf-8") == text:
            continue
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(text, encoding="utf-8")
        changed += 1
        print(f"wrote {rel}")
    LOCK.parent.mkdir(parents=True, exist_ok=True)
    LOCK.write_text(lock_text(gen), encoding="utf-8")
    print(f"{len(gen)} files, {changed} written, lock updated")
    for p in mixin_problems(gen):
        print("WARN " + p)
    return 0


sys.exit(main())
