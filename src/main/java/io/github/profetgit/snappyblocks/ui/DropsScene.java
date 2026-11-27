package io.github.profetgit.snappyblocks.ui;

import io.github.profetgit.profetsettings.Ctx;
import io.github.profetgit.profetsettings.Gfx;
import io.github.profetgit.snappyblocks.Compat;
import io.github.profetgit.snappyblocks.Config;
import io.github.profetgit.snappyblocks.burst.Bursts;
import io.github.profetgit.snappyblocks.item.Ghosts;
import io.github.profetgit.snappyblocks.item.ItemFx;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

// Item drops: the Drop key flicks apples out of the hand. A stack springs from the fist and lands with a squash and a
// hop, a second apple follows and slides into it (the stack grows), and the stack finally shrinks away as it despawns.
final class DropsScene extends SnappyScene {
    // ms: the two throws leave the hand, the merge, the despawn
    private static final float PERIOD = 3600, FIRST = 300, SECOND = 1350, MERGE = 2400, DESPAWN = 3300;
    // ms into the arm's clip when the fist lets go
    private static final float RELEASE = Diorama.RELEASE * 1000f / 60;
    // the ground line, in pixels below the box's top; a block is as wide as on the stage
    private static final float FLOOR = 96, BLOCK = 2 * Diorama.HX;
    // the flights, in blocks: height of the start, speed upwards, and forwards for each throw
    private static final double H0 = 0.8, UP = 0.1, FAR = 0.166, NEAR = 0.102;
    private static final int STACK = 16;

    @Override
    public void render(Gfx g, Ctx c, int x, int y, int w, int h) {
        float time = time(c, PERIOD);
        Identifier apple = itemTex("apple");
        int ox = x + (w - Diorama.W) / 2;
        float floor = y + FLOOR, start = ox + Diorama.HAND_X + 6;
        String off = off("items", Config.get().items, false);
        Diorama.backdrop(g, ox, y);
        // switched off: vanilla's item is at full size at once, lands flat, merges and despawns in a blink
        boolean mod = off == null;
        Diorama.ARM.draw(g, ox, y, arm(time));
        double mergeTicks = Ghosts.mergeTicks();
        double merge = mod ? (time - MERGE) / MS / mergeTicks : time < MERGE ? 0 : 1;
        double despawn = (time - DESPAWN) / MS / Ghosts.despawnTicks();
        double size = despawn <= 0 ? 1 : mod ? 1 - Ghosts.smooth(Math.min(1, despawn)) : 0;
        double[] a = time < FIRST ? null : flight(ox, y, start, floor, FAR, (time - FIRST) / MS, mod);
        double[] b = time < SECOND || merge >= 1 ? null : flight(ox, y, start, floor, NEAR, (time - SECOND) / MS, mod);
        if (a != null && size > 0.001) {
            // x, middle, width, height, shadow x, height above the ground
            Diorama.groundShadow(g, (float) a[4], floor, (float) (1.2 * size * a[6]), a[5]);
            draw(g, apple, a, size, merge >= 1 ? STACK + 1 : STACK);
        }
        if (b != null) {
            if (merge > 0 && a != null) {
                // the merge slide: the same apple, drawn on its way into the stack
                double e = Ghosts.smooth(merge), scale = 1 + (Ghosts.MERGE_SCALE - 1) * e;
                b[0] += (a[0] - b[0]) * e;
                b[1] += (a[1] - b[1]) * e;
                b[2] *= scale;
                b[3] *= scale;
                b[4] += (a[4] - b[4]) * e;
            }
            Diorama.groundShadow(g, (float) b[4], floor, (float) b[6], b[5]);
            draw(g, apple, b, 1, 1);
        }
        if (!mod) Diorama.note(g, x, y, w, h, off);
        boolean pressed = time >= FIRST - 60 && time < FIRST + 160 || time >= SECOND - 60 && time < SECOND + 160;
        key(g, c, Minecraft.getInstance().options.keyDrop, x + w / 2f, y + 120, pressed);
    }

    // the arm's frame: at rest, and through its clip around each throw
    private static int arm(float time) {
        for (float at : new float[] {FIRST, SECOND}) {
            float into = time - (at - RELEASE);
            if (into >= 0 && into < Diorama.THROW_FRAMES * 1000f / 60) return frame(into, Diorama.THROW_FRAMES);
        }
        return 0;
    }

    // a thrown stack {@code t} ticks after it left the hand: {x, middle y, width, height, shadow x, height above the
    // ground in blocks, grown 0..1}, along vanilla's flight and the mod's spring out of the hand
    private static double[] flight(int ox, int y, float start, float floor, double speed, float t, boolean mod) {
        double[] e = fall(0, H0, speed, UP, t);
        double[] pose = !mod || Compat.landAside ? new double[] {1, 1, 0} : landing(e[2]);
        double up = e[1] + pose[2] + (e[2] < 0 ? 0 : bob(e[2]));
        double ix = start + e[0] * BLOCK, middle = floor - up * Diorama.TALL - Diorama.ITEM_HALF;
        double k = mod ? Bursts.springBack(t / ItemFx.throwTicks()) : 0;
        double grow = ItemFx.throwGrow(k);
        double shadow = ix + (ox + Diorama.HAND_X - ix) * k;
        ix = shadow;
        middle += (y + Diorama.HAND_Y - middle) * k;
        return new double[] {ix, middle, pose[0] * grow, pose[1] * grow, shadow, up, grow};
    }

    // draws a flight's stack, shrunk about its middle by {@code size}
    private static void draw(Gfx g, Identifier tex, double[] f, double size, int count) {
        double wide = f[2] * size, tall = f[3] * size;
        Diorama.item(g, tex, (float) f[0], (float) (f[1] + Diorama.ITEM_HALF * tall), wide, tall, count);
    }
}
