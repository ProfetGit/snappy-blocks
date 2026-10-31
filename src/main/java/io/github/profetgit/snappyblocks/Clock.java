package io.github.profetgit.snappyblocks;

import net.minecraft.client.Minecraft;

/**
 * The animation clock (PRD §5): our own tick counter plus the partial tick. It stops while the game is paused and
 * while the tick rate is frozen. Never the level's game time (time packets make it jump) or the wall clock.
 */
public final class Clock {
    private static long ticks;
    private static long frozenSince = -1;

    private Clock() {}

    static void tick(Minecraft mc) {
        if (mc.level == null || mc.isPaused()) return;
        if (!mc.level.tickRateManager().runsNormally()) {
            if (frozenSince < 0) frozenSince = System.nanoTime();
            return;
        }
        frozenSince = -1;
        ticks++;
    }

    static void reset() {
        ticks = 0;
        frozenSince = -1;
    }

    /** Whole ticks since the level was joined. */
    public static long ticks() {
        return ticks;
    }

    /** Ticks plus the partial tick of the frame being drawn. */
    public static double now() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused() || frozenSince >= 0) return ticks;
        return ticks + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    /** True while the tick rate is frozen. */
    public static boolean frozen() {
        return frozenSince >= 0;
    }

    /** The clock stands still (paused, frozen, no level): nothing may stay hidden then (PRD pillar 4). */
    public static boolean stopped() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null || mc.isPaused() || frozenSince >= 0;
    }
}
