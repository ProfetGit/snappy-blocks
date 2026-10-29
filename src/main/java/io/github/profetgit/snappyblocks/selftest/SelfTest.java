package io.github.profetgit.snappyblocks.selftest;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.profetgit.snappyblocks.SnappyBlocks;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
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
    private static final ExecutorService WRITER = Executors.newFixedThreadPool(2);
    private static String captureName;
    private static int captureLeft, captureFrame;
    static int frames;
    private static int handoffsSeen;
    private static final StringBuilder HANDOFFS = new StringBuilder();

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
        frames++;
        int h = io.github.profetgit.snappyblocks.pop.Pops.handedOff;
        if (h != handoffsSeen && captureLeft > 0) HANDOFFS.append(captureFrame).append('\n');
        handoffsSeen = h;
        if (captureLeft <= 0) return;
        Path out = Path.of(OUT, "frames", captureName, String.format("f%04d.png", captureFrame++));
        captureLeft--;
        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), (NativeImage img) -> WRITER.execute(() -> {
            try (img) {
                Files.createDirectories(out.getParent());
                img.writeToFile(out);
            } catch (IOException e) {
                SnappyBlocks.LOG.warn("[selftest] frame write failed: {}", e.toString());
            }
        }));
    }

    /** Saves the next {@code n} frames to out/frames/&lt;name&gt;/. */
    static void startCapture(String name, int n) {
        captureName = name;
        captureLeft = n;
        captureFrame = 0;
    }

    static void stopCapture() {
        captureLeft = 0;
        if (HANDOFFS.isEmpty()) return;
        try {
            Files.createDirectories(Path.of(OUT, "frames", captureName));
            Files.writeString(Path.of(OUT, "frames", captureName + ".handoffs.txt"), HANDOFFS.toString());
        } catch (IOException e) {
            SnappyBlocks.LOG.warn("[selftest] handoff list write failed: {}", e.toString());
        }
        HANDOFFS.setLength(0);
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
        WRITER.shutdown();
        try {
            WRITER.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
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
