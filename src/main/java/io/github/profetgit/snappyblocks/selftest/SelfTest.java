package io.github.profetgit.snappyblocks.selftest;

import io.github.profetgit.snappyblocks.SnappyBlocks;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * In-game test runner, inert unless -Dsnappy_blocks.selftest=&lt;out dir&gt; is set (ModTest sets it). Mode "boot" only
 * reaches the title screen; "full" creates a world and runs {@link Scenarios}; "mp-&lt;role&gt;" joins the server it was
 * started for (dev/server/mp.py). Results go to results.json, then the game quits.
 */
public final class SelfTest {
    private static final String OUT = System.getProperty("snappy_blocks.selftest");
    private static final String MODE = System.getProperty("snappy_blocks.selftest.mode", "full");
    private static final List<String> RESULTS = new ArrayList<>();

    private static boolean finished;
    private static int titleTicks, ticks;
    private static List<Script.Step> steps;
    private static int index;
    static boolean attack;

    private SelfTest() {}

    public static boolean active() {
        return OUT != null;
    }

    public static void onClientTick(Minecraft mc) {
        if (finished) return;
        if (++ticks % 400 == 0) {
            SnappyBlocks.LOG.info("[selftest] step {}/{} screen={}", index, steps == null ? 0 : steps.size(),
                mc.gui.screen() == null ? null : mc.gui.screen().getClass().getSimpleName());
        }
        if (steps == null && MODE.startsWith("mp-")) {
            if (!mc.isGameLoadFinished()) return;
            ModTestHook.audit();
            steps = Scenarios.multiplayer(MODE.substring(3)).steps;
        }
        if (steps == null) {
            if (!mc.isGameLoadFinished() || !(mc.gui.screen() instanceof TitleScreen) || ++titleTicks < 20) return;
            ModTestHook.audit();
            record("boot", true, "title screen reached");
            if (MODE.equals("boot")) {
                finish(mc);
                return;
            }
            steps = Scenarios.build().steps;
        }
        try {
            while (index < steps.size() && steps.get(index).tick(mc)) index++;
        } catch (Throwable t) {
            SnappyBlocks.LOG.error("[selftest] step {} failed", index, t);
            record("step " + index, false, t.toString());
            index = steps.size();
        }
        if (index >= steps.size()) finish(mc);
    }

    public static void onFrame(Minecraft mc) {
    }

    /** The test holds the attack button (MinecraftMixin). */
    public static boolean attackHeld() {
        return attack && !finished;
    }

    public static void record(String name, boolean pass, String detail) {
        RESULTS.add(String.format("{\"name\":\"%s\",\"pass\":%s,\"detail\":\"%s\"}",
            name, pass, detail.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")));
        SnappyBlocks.LOG.info("[selftest] {} {} {}", pass ? "PASS" : "FAIL", name, detail);
    }

    private static void finish(Minecraft mc) {
        finished = true;
        String json = String.format("{\"loader\":\"%s\",\"mc\":\"%s\",\"results\":[%s]}%n",
            "fabric", net.minecraft.SharedConstants.getCurrentVersion().name(), String.join(",", RESULTS));
        try {
            Files.createDirectories(Path.of(OUT));
            Files.writeString(Path.of(OUT, "results.json"), json);
        } catch (IOException e) {
            SnappyBlocks.LOG.error("[selftest] could not write results", e);
        }
        mc.stop();
    }
}
