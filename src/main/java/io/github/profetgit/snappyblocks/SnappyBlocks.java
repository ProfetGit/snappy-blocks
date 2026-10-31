package io.github.profetgit.snappyblocks;

import io.github.profetgit.snappyblocks.burst.Bursts;
import io.github.profetgit.snappyblocks.item.Ghosts;
import io.github.profetgit.snappyblocks.item.ItemFx;
import io.github.profetgit.snappyblocks.pop.Pops;
import io.github.profetgit.snappyblocks.pulse.Pulses;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client entry point. Everything the mod does is visual and client-side (PRD §1, §7). */
public final class SnappyBlocks implements ClientModInitializer {
    public static final String ID = "snappy_blocks";
    public static final Logger LOG = LoggerFactory.getLogger("Snappy Blocks");

    @Override
    public void onInitializeClient() {
        // register their tune keys before the config is read
        Bursts.clear();
        Pulses.clear();
        Pops.clear();
        ItemFx.clear();
        Ghosts.clear();
        Config.load();
        Compat.detect();
        LOG.info("Snappy Blocks loaded (enabled={}, intensity={}%)", Config.get().enabled, Config.get().intensity);
    }

    /** End of every client tick (paused or not). */
    public static void onClientTick(Minecraft mc) {
        Clock.tick(mc);
        if (!mc.isPaused()) {
            ItemFx.tick(mc);
            Ghosts.tick(mc);
        }
        // the watchdogs also run when no frame draws the world
        Pops.update();
        Bursts.update();
        Config.pollReload();
    }

    private static int[] logged = new int[6];

    /** The client level is about to change (join, dimension change, respawn into a new level, disconnect). */
    public static void onLevelChange() {
        int[] now = {Pops.started, Pops.handedOff, Bursts.started, Pulses.started, ItemFx.landings, Ghosts.pickups};
        if (now[0] != logged[0] || now[2] != logged[2] || now[3] != logged[3] || now[4] != logged[4] || now[5] != logged[5]) {
            // one line per level, so a log shows the effects ran (replays, servers, other mods' test runs)
            LOG.info("Snappy Blocks: in that level {} pops ({} handed off), {} bursts, {} pulses, {} landings, {} pickups",
                now[0] - logged[0], now[1] - logged[1], now[2] - logged[2], now[3] - logged[3], now[4] - logged[4], now[5] - logged[5]);
            logged = now;
        }
        Clock.reset();
        Probe.reset();
        Pops.clear();
        Bursts.clear();
        Pulses.clear();
        ItemFx.clear();
        Ghosts.clear();
        Rtt.clear();
    }
}
