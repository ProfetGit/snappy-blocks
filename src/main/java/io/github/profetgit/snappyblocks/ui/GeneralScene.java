package io.github.profetgit.snappyblocks.ui;

import io.github.profetgit.profetsettings.Ctx;
import io.github.profetgit.profetsettings.Gfx;
import io.github.profetgit.snappyblocks.Compat;
import io.github.profetgit.snappyblocks.Config;
import io.github.profetgit.snappyblocks.burst.Bursts;
import io.github.profetgit.snappyblocks.item.Ghosts;
import io.github.profetgit.snappyblocks.pop.Pops;
import io.github.profetgit.snappyblocks.pulse.Pulses;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

// The whole mod (on/off, intensity, reduced motion): a bookshelf is placed, chopped with the axe and broken, and its
// books spring out, land and despawn, all at the current movement size. An effect that is switched off on its own behaves
// like vanilla here.
final class GeneralScene extends SnappyScene {
    private final Tool tool = Tool.axe();
    // ms: placed, the third swing after the first hit breaks it, the books despawn
    private static final float PERIOD = 3500, PLACE = 350, EVERY = 200, BREAK = 1750, HIT1 = BREAK - 3 * EVERY, DESPAWN = 3150;
    private static final int HITS = 3;

    @Override
    public void render(Gfx g, Ctx c, int x, int y, int w, int h) {
        float time = time(c, PERIOD);
        Identifier side = tex("bookshelf"), books = itemTex("book");
        Diorama.Look look = new Diorama.Look(tex("oak_planks"), side, side);
        int ox = x + (w - Diorama.W) / 2;
        float cx = ox + Diorama.AX, floor = y + Diorama.AY;
        Config k = Config.get();
        String off = off("all", true, false);
        Diorama.backdrop(g, ox, y);
        double[] whole = {1, 1, 1};
        // with the mod off, every effect behaves as vanilla
        boolean mod = off == null;
        boolean pop = mod && k.pop && !Compat.placeAside, pulse = mod && k.pulse && !Compat.pulseAside;
        boolean burst = mod && k.burst, items = mod && k.items;
        float t = (time - BREAK) / MS;
        float gone = (PERIOD - BREAK) / MS;
        chips(g, cx, floor, side, time, pulse, burst, gone, false);
        double[] shape = burst && t >= 0 && t < Bursts.burstTicks() ? Bursts.shape(false, t) : null;
        boolean covered = covers(shape);
        // the books: vanilla's despawn is a blink, the mod's a short shrink
        double size = time < DESPAWN ? 1 : items ? 1 - Ghosts.smooth(Math.min(1, (time - DESPAWN) / MS / Ghosts.despawnTicks())) : 0;
        boolean drop = t >= 0 && size > 0.001;
        if (drop && covered) drop(g, cx, floor, books, 3, t, size, items, burst);
        if (time >= PLACE && t < 0) {
            int hit = time < HIT1 ? -1 : Math.min(HITS - 1, (int) ((time - HIT1) / EVERY));
            double s = pop ? Pops.scale((time - PLACE) / MS) : 1;
            double[] moved = hit >= 0 && pulse ? Pulses.mined(Pulses.style(), Direction.EAST, (time - HIT1) / MS,
                (time - HIT1 - hit * EVERY) / MS, hit) : null;
            Diorama.shadow(g, cx, floor, (float) s);
            g.pushScale(cx, floor, (float) s, (float) s);
            Diorama.mined(g, cx, floor, look, moved, hit < 0 ? -1 : 2 + hit * 3);
            g.pop();
        } else if (shape != null) {
            Diorama.block(g, cx, floor, look, shape, -1);
        }
        if (drop && !covered) drop(g, cx, floor, books, 3, t, size, items, burst);
        tool.draw(g, ox, y, swing(time, HIT1, BREAK, true));
        chips(g, cx, floor, side, time, pulse, burst, gone, true);
        if (!mod) Diorama.note(g, x, y, w, h, off);
        boolean attack = time >= HIT1 - RAISE;
        key(g, c, attack ? Minecraft.getInstance().options.keyAttack : Minecraft.getInstance().options.keyUse, x + w / 2f, y + 120,
            attack ? time >= HIT1 && time < BREAK + 60 : time >= PLACE - 60 && time < PLACE + 160);
    }

    private static void chips(Gfx g, float cx, float floor, Identifier tex, float time, boolean pulse, boolean burst, float gone, boolean front) {
        if (pulse) {
            for (int i = 0; i < HITS; i++) {
                float at = HIT1 + i * EVERY;
                hitChips(g, cx, floor, tex, i, (time - at) / MS, (PERIOD - at) / MS, front);
            }
        }
        if (burst) burstChips(g, cx, floor, tex, (time - BREAK) / MS, gone, front);
    }
}
