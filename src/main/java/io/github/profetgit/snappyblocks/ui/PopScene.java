package io.github.profetgit.snappyblocks.ui;

//? if >=26.3 {
import io.github.profetgit.profetsettings.Ctx;
import io.github.profetgit.profetsettings.Gfx;
import io.github.profetgit.snappyblocks.Compat;
import io.github.profetgit.snappyblocks.Config;
import io.github.profetgit.snappyblocks.pop.Pops;
import net.minecraft.client.Minecraft;

// Place pop: two presses of the Use key. Bricks grow up from the ground, then a crafting table grows out of the face it
// is placed against, both along the real curve about the real pivot.
final class PopScene extends SnappyScene {
    // ms: the two placements, then the stage clears for the next round
    private static final float PERIOD = 3000, FIRST = 300, SECOND = 1250, CLEAR = 2650, CLEARED = 2900;

    @Override
    public void render(Gfx g, Ctx c, int x, int y, int w, int h) {
        float time = time(c, PERIOD);
        Diorama.Look bricks = Diorama.Look.all(tex("bricks"));
        Diorama.Look table = new Diorama.Look(tex("crafting_table_top"), tex("crafting_table_front"), tex("crafting_table_side"));
        int ox = x + (w - Diorama.W) / 2;
        // the bricks' spot; the table stands one block towards +z (lower left) of it
        float cx = ox + Diorama.AX + Diorama.HX / 2, floor = y + 64;
        float tx = Diorama.sx(cx, 0, 1), tf = Diorama.sy(floor, 0, 0, 1);
        String off = off("pop", Config.get().pop, Compat.placeAside);
        Diorama.backdrop(g, ox, y);
        double[] whole = {1, 1, 1};
        // switched off, a block is there at full size at once, as in vanilla
        boolean pop = off == null;
        double first = time < FIRST || time >= CLEARED ? 0 : pop ? Pops.scale((time - FIRST) / MS) : 1;
        double second = time < SECOND || time >= CLEARED ? 0 : pop ? Pops.scale((time - SECOND) / MS) : 1;
        // the table's pivot is the middle of the face the two share; its shadow stays on the ground
        float px = Diorama.sx(cx, 0, 0.5), py = Diorama.sy(floor, 0, 0.5, 0.5);
        if (first > 0) Diorama.shadow(g, cx, floor, (float) first);
        if (second > 0) Diorama.shadow(g, Diorama.sx(cx, 0, 0.5 + 0.5 * second), Diorama.sy(floor, 0, 0, 0.5 + 0.5 * second), (float) second);
        if (first > 0) {
            g.pushScale(cx, floor, (float) first, (float) first);
            Diorama.body(g, cx, floor, bricks, whole, -1);
            g.pop();
        }
        if (second > 0) {
            g.pushScale(px, py, (float) second, (float) second);
            Diorama.body(g, tx, tf, table, whole, -1);
            g.pop();
        }
        if (time >= CLEAR && time < CLEARED) Diorama.curtain(g, x, y, w, h, (time - CLEAR) / (CLEARED - CLEAR - 30));
        if (off != null) Diorama.note(g, x, y, w, h, off);
        boolean pressed = time >= FIRST - 60 && time < FIRST + 160 || time >= SECOND - 60 && time < SECOND + 160;
        key(g, c, Minecraft.getInstance().options.keyUse, x + w / 2f, y + 120, pressed);
    }

}
//?}
