#!/usr/bin/env python3
"""Snappy Blocks on a real dedicated server with two clients (PRD §13 M0, and M1's 200 ms run).

mp.py [--mc 26.3] [--port 25691] [--latency-ms 0]

The server is Fabric with no mods: gameplay equals a vanilla server, as for any player of this client-only mod. Both
clients run the mod and its test driver (Scenarios.multiplayer): role "a" (Builder) places planks at MP_SPOT with a
predicted placement, role "b" (Watcher) must see them appear as a remote change. With --latency-ms both clients connect
through a proxy that delays every chunk by half that round trip each way, order kept.
Work dir: dev/server/.work/mp-<mc>. Pattern from mods/HarvestSweep/dev/server/mp.py.
"""
import argparse
import asyncio
import importlib.util
import re
import shutil
import subprocess
import sys
import threading
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
WS = ROOT.parents[1]
sys.path.insert(0, str(WS / "tools/ModTest"))
sys.path.insert(0, str(WS / "tools/ModJar"))
import client  # noqa: E402
import slots  # noqa: E402

_spec = importlib.util.spec_from_file_location("modjar_smoke", WS / "tools/ModJar" / "smoke.py")
ms = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(ms)

PLAYERS = {"a": ("Builder", "5e1dcaa0-0000-4000-8000-000000005b01"), "b": ("Watcher", "5e1dcaa0-0000-4000-8000-000000005b02")}
Y = 100


def jar(mc: str) -> Path:
    version = re.search(r"^mod\.version=(.+)$", (ROOT / "gradle.properties").read_text(), re.M).group(1).strip()
    j = ROOT / "versions" / f"{mc}-fabric" / "build/libs" / f"snappy_blocks-{version}+{mc}-fabric.jar"
    if not j.is_file():
        sys.exit(f"no {j.name}: build it first")
    return j


class LatencyProxy:
    """listen -> server, each chunk delayed by half the round trip in each direction, order kept."""

    def __init__(self, listen: int, target: int, latency_ms: int):
        self.listen, self.target, self.delay = listen, target, latency_ms / 2000
        self.loop = asyncio.new_event_loop()
        self.ready = threading.Event()
        threading.Thread(target=self._run, daemon=True).start()
        self.ready.wait(10)

    def _run(self):
        asyncio.set_event_loop(self.loop)
        self.loop.run_until_complete(asyncio.start_server(self._handle, "127.0.0.1", self.listen))
        self.ready.set()
        self.loop.run_forever()

    async def _pipe(self, r, w):
        q = asyncio.Queue()

        async def reader():
            try:
                while data := await r.read(65536):
                    await q.put((self.loop.time() + self.delay, data))
            except (ConnectionError, asyncio.CancelledError):
                pass
            await q.put(None)

        task = asyncio.ensure_future(reader())
        try:
            while (item := await q.get()) is not None:
                due, data = item
                if (wait := due - self.loop.time()) > 0:
                    await asyncio.sleep(wait)
                w.write(data)
                await w.drain()
        except (ConnectionError, asyncio.CancelledError):
            pass
        finally:
            task.cancel()
            try:
                w.close()
            except Exception:
                pass

    async def _handle(self, cr, cw):
        try:
            sr, sw = await asyncio.open_connection("127.0.0.1", self.target)
        except OSError:
            cw.close()
            return
        await asyncio.gather(self._pipe(cr, sw), self._pipe(sr, cw))

    def stop(self):
        self.loop.call_soon_threadsafe(self.loop.stop)


class Server:
    def __init__(self, work: Path, cmd: list[str], port: int):
        shutil.rmtree(work, ignore_errors=True)
        (work / "mods").mkdir(parents=True)
        (work / "tmp").mkdir()
        (work / "eula.txt").write_text("eula=true\n")
        (work / "server.properties").write_text(
            f"online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\nlevel-type=minecraft\\:flat\ngenerate-structures=false\n"
            "view-distance=6\nsimulation-distance=6\nspawn-protection=0\ndifficulty=peaceful\npause-when-empty-seconds=0\n"
            "allow-flight=false\nwhite-list=false\nenforce-whitelist=false\nsync-chunk-writes=false\n")
        self.log = work / "server.log"
        self.out = open(self.log, "w")
        self.proc = subprocess.Popen(cmd, cwd=work, stdin=subprocess.PIPE, stdout=self.out, stderr=subprocess.STDOUT, text=True)

    def send(self, *lines):
        for l in lines:
            self.proc.stdin.write(l + "\n")
        self.proc.stdin.flush()

    def text(self) -> str:
        return self.log.read_text(errors="replace")

    def wait(self, pattern: str, timeout: float):
        end = time.time() + timeout
        while time.time() < end:
            if m := re.search(pattern, self.text()):
                return m
            if self.proc.poll() is not None:
                return None
            time.sleep(0.25)
        return None

    def stop(self):
        try:
            self.send("stop")
            self.proc.wait(90)
        except Exception:
            self.proc.kill()
        self.out.close()


def run(mc: str, port: int, latency_ms: int) -> tuple[bool, list[str]]:
    work = HERE / ".work" / f"mp-{mc}"
    shutil.rmtree(work, ignore_errors=True)
    notes, ok, results = [], True, {}
    mod = jar(mc)
    with slots.slot("server", f"SnappyBlocks mp {mc}"):
        srv = Server(work / "server", ms.command(mc, "fabric", work / "server"), port + 1)
        proxy = None
        try:
            if not srv.wait(r"Done \(\d", 240):
                return False, ["FAIL server did not start"] + ms.tail(srv.log)
            target = port + 1
            if latency_ms:
                proxy = LatencyProxy(port, port + 1, latency_ms)
                target = port

            def run_client(role: str):
                name, uuid = PLAYERS[role]
                out = work / f"out-{role}"
                props = {"snappy_blocks.selftest": str(out), "snappy_blocks.selftest.mode": f"mp-{role}", "modtest.audit": "1"}
                results[role] = client.run(mc=mc, loader="fabric", out=out, game=work / f"game-{role}", mod_jars=[mod],
                                           props=props, join=f"127.0.0.1:{target}",
                                           options={"fps": 60, "volume": 0.0, "render_distance": 6}, timeout=420,
                                           user=name, uuid=uuid, label=f"SnappyBlocks mp {role}")

            threads = [threading.Thread(target=run_client, args=(r,)) for r in ("b", "a")]
            for t in threads:
                t.start()
                time.sleep(3)
            joined = all(srv.wait(rf"{PLAYERS[r][0]} joined the game", 300) for r in PLAYERS)
            if not joined:
                notes.append("FAIL a client never joined")
                ok = False
            else:
                srv.send("gamerule spawn_mobs false", "gamerule random_tick_speed 0", "gamerule advance_time false", "time set 6000",
                         *(f"op {n}" for n, _ in PLAYERS.values()), *(f"gamemode survival {n}" for n, _ in PLAYERS.values()),
                         "forceload add -16 -16 16 16")
                srv.wait(r"force loaded|Marked", 60)
                time.sleep(4)
                srv.send(f"fill -12 {Y-1} -12 12 {Y-1} 12 minecraft:stone", f"fill -12 {Y} -12 12 {Y+6} 12 minecraft:air",
                         f"tp Builder 0.5 {Y} -2.5 0 30", f"tp Watcher 4.5 {Y} -2.5 0 30")
            for t in threads:
                t.join(600)
        finally:
            if proxy:
                proxy.stop()
            srv.stop()
    for role in ("a", "b"):
        r = results.get(role)
        for x in (r["results"] if r else []):
            notes.append(("PASS " if x["pass"] else "FAIL ") + f"{role}/{x['name']}  {x.get('detail', '')}")
            ok &= bool(x["pass"])
        if not r or not r["results"]:
            ok = False
            notes.append(f"FAIL {role}: no results" + (f" ({'; '.join(r['notes'])})" if r else ""))
    stext = srv.text()
    for who, why in re.findall(r"(\w+) lost connection: (.*)", stext):
        if "Disconnected" not in why and "Server closed" not in why:
            ok = False
            notes.append(f"FAIL server: {who} dropped: {why[:200]}")
    for line in stext.splitlines():
        if ms.PROBLEM.search(line) and not any(re.search(n, line) for n in ms.NOISE) and "Can't keep up" not in line:
            ok = False
            notes.append("FAIL SERVER LOG " + line.strip()[:220])
    notes.append(f"INFO round trip +{latency_ms} ms")
    return ok, notes


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--mc", default="26.3")
    ap.add_argument("--port", type=int, default=25691)
    ap.add_argument("--latency-ms", type=int, default=0, help="fixed round-trip delay added by the proxy (M1: 200)")
    a = ap.parse_args()
    ok, notes = run(a.mc, a.port, a.latency_ms)
    for n in notes:
        print(n)
    print(f"mp {a.mc}: {'PASS' if ok else 'FAIL'}")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
