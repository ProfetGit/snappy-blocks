package io.github.profetgit.snappyblocks.ui;

import io.github.profetgit.profetsettings.Ctx;
import io.github.profetgit.profetsettings.Gfx;
import io.github.profetgit.snappyblocks.Compat;
import io.github.profetgit.snappyblocks.Config;

// Snappy chips fly from two effects: the break burst and each hit of the mining pulse. The preview shows the burst, or the
// mining when only that one is on, so the switch always has chips to show if the game would.
final class ChipsScene extends SnappyScene {
    private final BurstScene burst = new BurstScene();
    private final PulseScene pulse = new PulseScene();

    @Override
    public void render(Gfx g, Ctx c, int x, int y, int w, int h) {
        Config k = Config.get();
        boolean mining = k.enabled && !k.burst && k.pulse && !Compat.pulseAside;
        (mining ? pulse : burst).render(g, c, x, y, w, h);
    }
}
