package io.github.profetgit.snappyblocks.ui;

import io.github.profetgit.profetsettings.Ctx;
import io.github.profetgit.profetsettings.Gfx;
import io.github.profetgit.snappyblocks.Compat;
import io.github.profetgit.snappyblocks.Config;
import io.github.profetgit.snappyblocks.burst.Dust;
import io.github.profetgit.snappyblocks.burst.Puff;
import io.github.profetgit.snappyblocks.pop.Pops;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;

// The placing effect: one press of the Use key, a block of sandstone lands on the stage and at the peak of its pop the
// chosen effect leaves it: bits round its foot, dust poofs, the dust shockwave, or an explosion of its bits. Switched
// off, the block is placed without, as in vanilla.
final class PlaceScene extends SnappyScene {
    // ms: placed, then the stage clears for the next round
    private static final float PERIOD = 2000, PLACE = 350, CLEAR = 1650, CLEARED = 1900;

    @Override
    public void render(Gfx g, Ctx c, int x, int y, int w, int h) {
        float time = time(c, PERIOD);
        Identifier side = tex("sandstone");
        Diorama.Look look = new Diorama.Look(tex("sandstone_top"), side, side);
        int ox = x + (w - Diorama.W) / 2;
        float cx = ox + Diorama.AX, floor = y + Diorama.AY;
        Config k = Config.get();
        Puff.Effect effect = Puff.effect();
        String off = off("placeEffect", effect != Puff.Effect.OFF, false);
        Diorama.backdrop(g, ox, y);
        double[] whole = {1, 1, 1};
        boolean pop = k.enabled && k.pop && !Compat.placeAside, shown = time >= PLACE && time < CLEARED;
        Puff.Effect now = off == null && shown ? effect : Puff.Effect.OFF;
        float age = (time - PLACE) / MS - (float) Puff.delay(), gone = (CLEAR - PLACE) / MS - (float) Puff.delay();
        int color = Dust.color(Blocks.SANDSTONE.defaultMapColor().col);
        if (now == Puff.Effect.SHOCKWAVE) shockRing(g, cx, floor, color, age);
        effect(g, now, cx, floor, side, color, age, gone, false);
        if (shown) {
            double s = pop ? Pops.scale((time - PLACE) / MS) : 1;
            Diorama.shadow(g, cx, floor, (float) s);
            g.pushScale(cx, floor, (float) s, (float) s);
            Diorama.body(g, cx, floor, look, whole, -1);
            g.pop();
        }
        effect(g, now, cx, floor, side, color, age, gone, true);
        if (time >= CLEAR && time < CLEARED) Diorama.curtain(g, x, y, w, h, (time - CLEAR) / (CLEARED - CLEAR - 30));
        if (off != null) Diorama.note(g, x, y, w, h, off);
        key(g, c, Minecraft.getInstance().options.keyUse, x + w / 2f, y + 120, time >= PLACE - 60 && time < PLACE + 160);
    }

    // the effect's pieces on one side of the block, behind it or in front
    private static void effect(Gfx g, Puff.Effect e, float cx, float floor, Identifier tex, int color, float age, float gone, boolean front) {
        switch (e) {
            case BITS -> puffChips(g, cx, floor, tex, age, gone, front);
            case POOF -> poofs(g, cx, floor, color, age, front);
            case SHOCKWAVE -> wisps(g, cx, floor, color, age, front);
            case EXPLOSION -> blastChips(g, cx, floor, tex, age, gone, front);
            default -> {
            }
        }
    }
}
