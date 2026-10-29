package io.github.profetgit.snappyblocks;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

/**
 * The round trip as this client sees it: from a predicted action (placing, breaking) to the server's acknowledgement.
 * The server's own ping figure (PlayerInfo latency) is updated rarely and starts at 0, so a burst's claim window built on
 * it missed the miner's own drop at 200 ms (M1, 2026-10-29).
 */
public final class Rtt {
    private static final Int2LongOpenHashMap SENT = new Int2LongOpenHashMap();
    private static double ms = -1;

    private Rtt() {}

    public static void sent(int sequence) {
        if (SENT.size() > 64) SENT.clear();
        SENT.put(sequence, System.nanoTime());
    }

    public static void acked(int sequence) {
        long t = SENT.remove(sequence);
        if (t == 0) return;
        double sample = (System.nanoTime() - t) / 1e6;
        ms = ms < 0 ? sample : ms * 0.7 + sample * 0.3;
        SENT.keySet().removeIf(s -> s < sequence);
    }

    static void clear() {
        SENT.clear();
        ms = -1;
    }

    /** The round trip in ticks: the larger of the measured one and the server's ping figure. */
    public static double ticks() {
        Minecraft mc = Minecraft.getInstance();
        double server = 0;
        if (mc.getConnection() != null && mc.player != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) server = info.getLatency();
        }
        return Math.max(Math.max(ms, 0), server) / 50.0;
    }
}
