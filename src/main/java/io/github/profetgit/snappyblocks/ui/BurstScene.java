package io.github.profetgit.snappyblocks.ui;

import io.github.profetgit.profetsettings.Ctx;
import io.github.profetgit.profetsettings.Gfx;
import io.github.profetgit.snappyblocks.Compat;
import io.github.profetgit.snappyblocks.Config;
import io.github.profetgit.snappyblocks.burst.Bursts;
import io.github.profetgit.snappyblocks.pop.Pops;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

// Break burst on the stage: three pickaxe swings crack a diamond ore, then a hit-stop squash, the block shrinks away in
// chips and its diamond springs out of the middle. The next block returns with the place pop. Switched off, the block is
// simply gone, as in vanilla.
final class BurstScene extends SnappyScene {
    private final Tool tool = Tool.pickaxe();
    // ms: mining starts, the last swing breaks the block, the diamond starts to leave, the next block is placed
    private static final float PERIOD = 4000, MINE = 450, BREAK = 1300, LEAVE = 3350, RETURN = 3600;
    // vanilla swings take 6 ticks
    private static final float HIT1 = BREAK - 2 * 300;

    @Override
    public void render(Gfx g, Ctx c, int x, int y, int w, int h) {
        float time = time(c, PERIOD);
        Identifier ore = tex("diamond_ore"), diamond = itemTex("diamond");
        Diorama.Look look = Diorama.Look.all(ore);
        int ox = x + (w - Diorama.W) / 2;
        float cx = ox + Diorama.AX, floor = y + Diorama.AY;
        Config k = Config.get();
        String off = off("burst", k.burst, false);
        Diorama.backdrop(g, ox, y);
        double[] whole = {1, 1, 1};
        boolean burst = off == null, items = k.enabled && k.items;
        float t = (time - BREAK) / MS;
        boolean back = time >= RETURN;
        boolean chips = burst && !back && t >= 0;
        float gone = (RETURN - BREAK) / MS;
        if (chips) burstChips(g, cx, floor, ore, t, gone, false);
        double[] shape = burst && t >= 0 && t < Bursts.burstTicks() ? Bursts.shape(false, t) : null;
        // the diamond stays behind the shrinking block while the block still covers it
        boolean covered = covers(shape);
        float a = g.alpha;
        float seen = time < LEAVE ? 1 : Math.max(0, 1 - (time - LEAVE) / (RETURN - LEAVE));
        boolean drop = t >= 0 && seen > 0;
        if (drop && covered) drop(g, cx, floor, diamond, 1, t, 1, items, burst);
        if (back) {
            double s = k.enabled && k.pop && !Compat.placeAside ? Pops.scale((time - RETURN) / MS) : 1;
            Diorama.block(g, cx, floor, look, new double[] {s, s, 1}, -1);
        } else if (t < 0) {
            int crack = time < MINE ? -1 : (int) ((time - MINE) / (BREAK - MINE) * 10);
            Diorama.block(g, cx, floor, look, whole, crack);
        } else if (shape != null) {
            Diorama.block(g, cx, floor, look, shape, -1);
        }
        if (drop && !covered) {
            g.alpha = a * seen;
            drop(g, cx, floor, diamond, 1, t, 1, items, burst);
            g.alpha = a;
        }
        tool.draw(g, ox, y, swing(time, HIT1, BREAK, false));
        if (chips) burstChips(g, cx, floor, ore, t, gone, true);
        if (off != null) Diorama.note(g, x, y, w, h, off);
        key(g, c, Minecraft.getInstance().options.keyAttack, x + w / 2f, y + 120, time >= MINE && time < BREAK + 60);
    }
}
