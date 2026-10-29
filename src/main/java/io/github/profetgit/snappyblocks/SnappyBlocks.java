package io.github.profetgit.snappyblocks;

import io.github.profetgit.snappyblocks.burst.Bursts;
import io.github.profetgit.snappyblocks.pop.Pops;
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
        Bursts.clear(); // registers its tune keys before the config is read
        Config.load();
        LOG.info("Snappy Blocks loaded (enabled={}, intensity={}%)", Config.get().enabled, Config.get().intensity);
    }

    /** End of every client tick (paused or not). */
    public static void onClientTick(Minecraft mc) {
        Clock.tick(mc);
        Config.pollReload();
    }

    /** The client level is about to change (join, dimension change, respawn into a new level, disconnect). */
    public static void onLevelChange() {
        Clock.reset();
        Probe.reset();
        Pops.clear();
        Bursts.clear();
        Rtt.clear();
    }
}
