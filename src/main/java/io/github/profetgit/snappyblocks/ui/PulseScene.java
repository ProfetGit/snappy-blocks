package io.github.profetgit.snappyblocks.ui;

import io.github.profetgit.profetsettings.Ctx;
import io.github.profetgit.profetsettings.Gfx;
import io.github.profetgit.snappyblocks.Compat;
import io.github.profetgit.snappyblocks.Config;
import io.github.profetgit.snappyblocks.pulse.Pulses;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

// The mining animation: the Attack key is held, the pickaxe strikes every 4 ticks (as often as the server sends hits),
// each hit moving the block the chosen way and knocking chips off it. The key is let go before the block breaks, so the cracks clear as in
// vanilla and the loop runs on without a cut.
final class PulseScene extends SnappyScene {
    private final Tool tool = Tool.pickaxe();
    private static final float PERIOD = 2600, HIT1 = 500, EVERY = 200;
    private static final int HITS = 7;
    // ms: the key is let go at the top of the last backswing, and the raise clip runs backwards to rest from the frame
    // that holds nearly the same pose
    private static final float LET_GO = HIT1 + (HITS - 1) * EVERY + 133;
    private static final int TOP = 3;

    @Override
    public void render(Gfx g, Ctx c, int x, int y, int w, int h) {
        float time = time(c, PERIOD);
        Identifier ore = tex("copper_ore");
        Diorama.Look look = Diorama.Look.all(ore);
        int ox = x + (w - Diorama.W) / 2;
        float cx = ox + Diorama.AX, floor = y + Diorama.AY;
        String off = off("pulse", Config.get().pulse, Compat.pulseAside);
        Diorama.backdrop(g, ox, y);
        // switched off, the same mining without the movement and its chips, as in vanilla
        boolean pulse = off == null;
        boolean mining = time >= HIT1 && time < LET_GO;
        int hit = mining ? Math.min(HITS - 1, (int) ((time - HIT1) / EVERY)) : -1;
        double[] p = mining && pulse ? Pulses.shape(Pulses.style(), (time - HIT1 - hit * EVERY) / MS, Direction.EAST, hit) : null;
        if (pulse) chips(g, cx, floor, ore, time, false);
        Diorama.shadow(g, cx, floor, p == null ? 1 : (float) p[0]);
        Diorama.mined(g, cx, floor, look, p, mining ? hit + 1 : -1);
        int pose = time < LET_GO ? swing(time, HIT1, LET_GO, true) : TOP - (int) ((time - LET_GO) * 60 / 1000);
        if (time >= LET_GO) pose = pose >= 0 ? Diorama.RAISE + pose : rest();
        tool.draw(g, ox, y, pose);
        if (pulse) chips(g, cx, floor, ore, time, true);
        if (off != null) Diorama.note(g, x, y, w, h, off);
        key(g, c, Minecraft.getInstance().options.keyAttack, x + w / 2f, y + 120, mining);
    }

    private static void chips(Gfx g, float cx, float floor, Identifier tex, float time, boolean front) {
        for (int i = 0; i < HITS; i++) {
            float at = HIT1 + i * EVERY;
            hitChips(g, cx, floor, tex, i, (time - at) / MS, (PERIOD - at) / MS, front);
        }
    }
}
