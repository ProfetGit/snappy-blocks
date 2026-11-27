package io.github.profetgit.snappyblocks.ui;

import io.github.profetgit.profetsettings.Gfx;
import io.github.profetgit.profetsettings.Skin;
import io.github.profetgit.snappyblocks.burst.Chip;
import io.github.profetgit.snappyblocks.burst.Cubes;
import io.github.profetgit.snappyblocks.burst.Dust;
import io.github.profetgit.snappyblocks.burst.DustArt;
import java.util.Random;
import io.github.profetgit.snappyblocks.pulse.Pulses;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

// The preview stage: the subject in a soft pool of light on the box's own dark, and its tool: the player's own copper
// pickaxe or axe swung along a Blockbench animation (Tool), or a throwing arm modelled, animated and rendered there
// (dev/preview/scene.js, compose.py). Blocks, chips and items are drawn live from the player's own textures; the ground is not drawn, only its shadows. The art is 2 texture pixels
// per GUI pixel. Block space: 1 = a block, the spot's floor centre is the origin, +x runs to the lower right, +z to the
// lower left.
final class Diorama {
    private Diorama() {}

    // the art is made for a W x H GUI pixel box with the spot's floor centre at (AX, AY); a block is 2 * HX wide and TALL high
    static final int W = 198, H = 150, AX = 99, AY = 71;
    static final float HX = 32, HZ = 16, TALL = 40;
    // screen length of one block seen face on, for camera-facing things (chips, items)
    static final float UNIT = 45.25f;

    private static final Identifier GLOW = art("glow"), SHADOW = art("shadow_block"), ITEM_SHADOW = art("shadow_item"),
        SHADE_TOP = art("shade_top"), SHADE_LEFT = art("shade_left"), SHADE_RIGHT = art("shade_right");
    // warm light on top, cool shade on the right, like the tools' render
    private static final int LIT_TOP = 0xFFFFF5E3, LIT_LEFT = 0xFFD2C2BC, LIT_RIGHT = 0xFF918AA3, LIT_CHIP = 0xFFBBB0A8;

    // an animation sheet as printed by compose.py: the crop's place and cell size in texture pixels, 7 cells a row
    record Sheet(Identifier tex, int x, int y, int w, int h, int rows) {
        void draw(Gfx g, int boxX, int boxY, int frame) {
            art(g, tex, boxX + x / 2f, boxY + y / 2f, frame % 7 * w, frame / 7 * h, w, h, 7 * w, rows * h);
        }
    }

    static final Sheet ARM = new Sheet(art("arm"), 0, 46, 120, 102, 5);
    // the tools' clips in the swing's frames (preview/swing.json)
    static final int SWING = 0, SWING_FRAMES = 18, FOLLOW = 18, FOLLOW_FRAMES = 31, RAISE = 49, RAISE_FRAMES = 16, MINE = 65, MINE_FRAMES = 12;
    // the arm's one clip; the fist lets go on frame RELEASE, at (HAND_X, HAND_Y) from the box's corner
    static final int THROW_FRAMES = 31, RELEASE = 15;
    static final float HAND_X = 46, HAND_Y = 52;

    // the textures of a block's three visible faces
    record Look(Identifier top, Identifier left, Identifier right) {
        static Look all(Identifier tex) {
            return new Look(tex, tex, tex);
        }
    }

    private static Identifier art(String name) {
        return Identifier.tryParse("snappy_blocks:" + "textures/gui/preview/" + name + ".png");
    }

    // a piece of art (given in its own pixels) at half size, its top-left corner at (x, y)
    private static void art(Gfx g, Identifier tex, float x, float y, int u, int v, int w, int h, int tw, int th) {
        g.pushTranslate(x, y);
        g.scale(0.5f, 0.5f);
        g.blit(tex, 0, 0, u, v, w, h, tw, th, 0xFFFFFFFF);
        g.pop();
    }

    static void backdrop(Gfx g, int x, int y) {
        art(g, GLOW, x, y, 0, 0, W * 2, H * 2, W * 2, H * 2);
    }

    // the stage fading to empty and back: the box's dark over the subject, then the light again on top of that
    static void curtain(Gfx g, int x, int y, int w, int h, float amount) {
        if (amount <= 0.004f) return;
        amount = Math.min(1, amount);
        g.rect(x, y + 2, w, h - 4, Skin.argb(Math.round(amount * 255), Skin.IN_BG & 0xFFFFFF));
        float a = g.alpha;
        g.alpha *= amount;
        backdrop(g, x + (w - W) / 2, y);
        g.alpha = a;
    }

    static float sx(float cx, double x, double z) {
        return (float) (cx + (x - z) * HX);
    }

    static float sy(float floor, double x, double y, double z) {
        return (float) (floor + (x + z) * HZ - y * TALL);
    }

    // ---- the block ----

    // the block under a break animation's transform: {@code shape} is Bursts.shape's {width, height, size} and, for the
    // ones that move, {turn, rise}, applied in three dimensions as the real renderer does (width and height about the
    // floor, size and turn about the centre)
    static void block(Gfx g, float cx, float floor, Look look, double[] shape, int crack) {
        shadow(g, cx, floor, (float) (shape[0] * shape[2]));
        body(g, cx, floor, look, shape, crack);
    }

    // the block under a mining hit: {@code p} is Pulses.shape's size, move and turn about its centre, or null
    static void mined(Gfx g, float cx, float floor, Look look, double[] p, int crack) {
        if (p == null) p = new double[] {1, 1, 1, 0, 0, 0};
        Quaternionf turn = p.length > 6 ? Pulses.turn(p) : null;
        float[] back = corner(cx, floor, p, turn, -1, 1, -1), right = corner(cx, floor, p, turn, 1, 1, -1),
            left = corner(cx, floor, p, turn, -1, 1, 1), front = corner(cx, floor, p, turn, 1, 1, 1),
            leftFoot = corner(cx, floor, p, turn, -1, -1, 1), frontFoot = corner(cx, floor, p, turn, 1, -1, 1);
        Identifier cracks = crack < 0 ? null : SnappyScene.tex("destroy_stage_" + Math.min(9, crack));
        side(g, look.top(), cracks, SHADE_TOP, 64, LIT_TOP, back, right, left);
        side(g, look.left(), cracks, SHADE_LEFT, 80, LIT_LEFT, left, front, leftFoot);
        side(g, look.right(), cracks, SHADE_RIGHT, 80, LIT_RIGHT, front, right, frontFoot);
    }

    // a corner of the mined block on screen
    private static float[] corner(float cx, float floor, double[] p, Quaternionf turn, int x, int y, int z) {
        Vector3f v = new Vector3f((float) (x * p[0] / 2), (float) (y * p[1] / 2), (float) (z * p[2] / 2));
        if (turn != null) turn.transform(v);
        double wx = p[3] + v.x, wz = p[5] + v.z;
        return new float[] {sx(cx, wx, wz), sy(floor, wx, 0.5 + p[4] + v.y, wz)};
    }

    private static void side(Gfx g, Identifier tex, Identifier cracks, Identifier shade, int sh, int tint, float[] o, float[] u, float[] v) {
        face(g, tex, cracks, shade, 64, sh, tint, o[0], o[1], u[0] - o[0], u[1] - o[1], v[0] - o[0], v[1] - o[1]);
    }

    // the same without its ground shadow
    static void body(Gfx g, float cx, float floor, Look look, double[] shape, int crack) {
        double wide = shape[0] * shape[2], size = shape[2];
        if (size <= 0.001) return;
        double h = 0.5 * wide, lo = shape[1] * 0.5 * (1 - size), hi = shape[1] * 0.5 * (1 + size);
        if (shape.length > 3) floor -= (float) (shape[4] * TALL);
        if (shape.length > 3 && shape[3] != 0) {
            turned(g, cx, floor, look, h, lo, hi, shape[3], crack);
            return;
        }
        float backX = sx(cx, -h, -h), backY = sy(floor, -h, hi, -h);
        float rightX = sx(cx, h, -h), rightY = sy(floor, h, hi, -h);
        float leftX = sx(cx, -h, h), leftY = sy(floor, -h, hi, h);
        float frontX = sx(cx, h, h), frontY = sy(floor, h, hi, h);
        float drop = (float) ((hi - lo) * TALL);
        Identifier cracks = crack < 0 ? null : SnappyScene.tex("destroy_stage_" + Math.min(9, crack));
        face(g, look.top(), cracks, SHADE_TOP, 64, 64, LIT_TOP, backX, backY, rightX - backX, rightY - backY, leftX - backX, leftY - backY);
        face(g, look.left(), cracks, SHADE_LEFT, 64, 80, LIT_LEFT, leftX, leftY, frontX - leftX, frontY - leftY, 0, drop);
        face(g, look.right(), cracks, SHADE_RIGHT, 64, 80, LIT_RIGHT, frontX, frontY, rightX - frontX, rightY - frontY, 0, drop);
    }

    // the block turned {@code yaw} radians about its upright axis: the top and the sides that face the camera, each side
    // lit between the left and the right face's light by where it points
    private static void turned(Gfx g, float cx, float floor, Look look, double h, double lo, double hi, double yaw, int crack) {
        double cos = Math.cos(yaw), sin = Math.sin(yaw);
        Identifier cracks = crack < 0 ? null : SnappyScene.tex("destroy_stage_" + Math.min(9, crack));
        // the corners, going round: back, right, front, left before the turn; screen x and y at the top and bottom
        double[][] at = {{-h, -h}, {h, -h}, {h, h}, {-h, h}};
        float[][] p = new float[4][];
        for (int i = 0; i < 4; i++) {
            double x = at[i][0] * cos + at[i][1] * sin, z = at[i][1] * cos - at[i][0] * sin;
            p[i] = new float[] {sx(cx, x, z), sy(floor, x, hi, z)};
        }
        float drop = (float) ((hi - lo) * TALL);
        face(g, look.top(), cracks, SHADE_TOP, 64, 64, LIT_TOP, p[0][0], p[0][1], p[1][0] - p[0][0], p[1][1] - p[0][1],
            p[3][0] - p[0][0], p[3][1] - p[0][1]);
        // each side from its first corner to its second as seen from outside, and its outward direction
        int[][] sides = {{3, 2}, {2, 1}, {1, 0}, {0, 3}};
        double[][] out = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
        for (int i = 0; i < 4; i++) {
            double x = out[i][0] * cos + out[i][1] * sin, z = out[i][1] * cos - out[i][0] * sin;
            if (x + z <= 0.01) continue;
            float k = (float) Math.max(0, Math.min(1, (x - z + 1) / 2));
            float[] a = p[sides[i][0]], b = p[sides[i][1]];
            Identifier tex = i % 2 == 0 ? look.left() : look.right();
            face(g, tex, cracks, k < 0.5f ? SHADE_LEFT : SHADE_RIGHT, 64, 80, mix(LIT_LEFT, LIT_RIGHT, k), a[0], a[1], b[0] - a[0], b[1] - a[1], 0, drop);
        }
    }

    private static int mix(int a, int b, float k) {
        int out = 0xFF000000;
        for (int shift = 0; shift < 24; shift += 8) out |= Math.round((a >> shift & 255) * (1 - k) + (b >> shift & 255) * k) << shift;
        return out;
    }

    // one textured parallelogram from (ox, oy): the texture's u axis (ux, uy) and v axis (vx, vy), then its light
    private static void face(Gfx g, Identifier tex, Identifier cracks, Identifier shade, int sw, int sh, int tint,
        float ox, float oy, float ux, float uy, float vx, float vy) {
        g.push();
        g.mul(new Matrix3x2f(ux / 16, uy / 16, vx / 16, vy / 16, ox, oy));
        g.blit(tex, 0, 0, 0, 0, 16, 16, 16, 16, tint);
        if (cracks != null) g.blit(cracks, 0, 0, 0, 0, 16, 16, 16, 16, 0x70000000);
        g.pop();
        g.push();
        g.mul(new Matrix3x2f(ux / sw, uy / sw, vx / sh, vy / sh, ox, oy));
        g.blit(shade, 0, 0, 0, 0, sw, sh, sw, sh, 0xFFFFFFFF);
        g.pop();
    }

    static void shadow(Gfx g, float cx, float floor, float size) {
        if (size <= 0.01f) return;
        g.pushTranslate(cx, floor);
        g.scale(size / 2, size / 2);
        float a = g.alpha;
        g.alpha *= Math.min(1, size);
        g.blit(SHADOW, -96, -56, 0, 0, 192, 112, 192, 112, 0xFFFFFFFF);
        g.alpha = a;
        g.pop();
    }

    // ---- chips ----

    // one chip, flown like the game's (vanilla's particle physics with Chip's bounce and spin) for {@code age} ticks;
    // drawn only on the asked side of the block's centre, as seen by the camera. {@code size} is its size factor, y is
    // its centre; {@code gone} is the age by which it has to have faded, so a loop can start clean.
    static void chip(Gfx g, Random r, float cx, float floor, Identifier tex, float age, double x, double y, double z, double[] v,
        Chip.Material m, float size, int lifetime, float gone, boolean front) {
        float life = Math.min(lifetime, gone);
        float u0 = r.nextFloat() * 12, v0 = r.nextFloat() * 12, spin = Chip.spin(r.nextDouble() - 0.5);
        int seed = r.nextInt() | 1;
        if (age < 0 || age >= life) return;
        double half = Chip.half(size);
        double xd = v[0], yd = v[1], zd = v[2], px = x, py = y, pz = z;
        float roll = 0, oldRoll = 0, landed = -1;
        boolean ground = false;
        int bounces = 0;
        for (int i = 0; i <= (int) age; i++) {
            px = x;
            py = y;
            pz = z;
            double fall = yd - 0.04 * m.gravity();
            boolean was = ground;
            x += xd;
            y += fall;
            z += zd;
            ground = y <= half && fall < 0;
            y = Math.max(half, y);
            xd *= m.friction();
            yd = fall * m.friction();
            zd *= m.friction();
            if (ground) {
                xd *= 0.7;
                zd *= 0.7;
                double up = was ? 0 : Chip.bounce(m, fall, bounces);
                if (up > 0) {
                    bounces++;
                    yd = up;
                    xd *= m.skid() / 0.7;
                    zd *= m.skid() / 0.7;
                    ground = false;
                    spin *= 0.6f;
                }
            }
            oldRoll = roll;
            roll = ground ? Chip.rest(roll) : roll + spin;
            if (ground && landed < 0) landed = i + 1;
        }
        double a = age - (int) age;
        double rx = px + (x - px) * a, ry = py + (y - py) * a, rz = pz + (z - pz) * a;
        float fade = Math.max(0, Math.min(1, (life - age) / Chip.FADE_TICKS));
        // a chip leaving the box shrinks away before the edge or the caption would cut it
        float dx = Math.abs(sx(0, rx, rz)), up = sy(0, rx, ry, rz);
        fade *= Math.max(0, Math.min(1, Math.min((78 - dx) / 16, Math.min((34 - up) / 12, (up + 66) / 12))));
        float s = Chip.BASE * size * 2 * UNIT * fade;
        if (s < 1) return;
        // with no floor drawn, the shadows are what shows where the ground is (the back pass runs first, under everything)
        if (!front) groundShadow(g, sx(cx, rx, rz), sy(floor, rx, 0, rz), s / 20, ry - half);
        if ((0.61 * (rx + rz) + 0.5 * (ry - 0.5) >= 0) != front) return;
        if (Cubes.on()) {
            cube(g, cx, floor, tex, u0, v0, rx, ry, rz, (float) half * fade, Cubes.rotation(seed, age, landed, new Quaternionf()));
            return;
        }
        g.pushTranslate(sx(cx, rx, rz), sy(floor, rx, ry, rz));
        g.rotate(oldRoll + (roll - oldRoll) * (float) a);
        g.translate(-s / 2, -s / 2);
        g.scale(s / 8, s / 8);
        g.blit(tex, 0, 0, u0, v0, 8, 8, 4, 4, 16, 16, LIT_CHIP);
        g.pop();
    }

    // a cube chip of half edge {@code h} blocks: the sides that face the stage's camera, along (1, 0.8, 1)
    private static void cube(Gfx g, float cx, float floor, Identifier tex, float u0, float v0, double x, double y, double z, float h, Quaternionf turn) {
        Quaternionf quad = new Quaternionf();
        Vector3f n = new Vector3f(), u = new Vector3f(), w = new Vector3f();
        for (int i = 0; i < Cubes.SIDES; i++) {
            Cubes.side(i, turn, quad, n);
            if (n.x + 0.8f * n.y + n.z <= 0.02f) continue;
            quad.transform(u.set(1, 0, 0));
            quad.transform(w.set(0, 1, 0));
            double ox = x + (n.x - u.x + w.x) * h, oy = y + (n.y - u.y + w.y) * h, oz = z + (n.z - u.z + w.z) * h;
            float ux = (u.x - u.z) * HX * 2 * h, uy = ((u.x + u.z) * HZ - u.y * TALL) * 2 * h;
            float vx = -(w.x - w.z) * HX * 2 * h, vy = -((w.x + w.z) * HZ - w.y * TALL) * 2 * h;
            float lit = Math.min(1, 0.66f * Cubes.shade(n));
            int tint = 0xFF000000 | Math.round(255 * lit) << 16 | Math.round(246 * lit) << 8 | Math.round(238 * lit);
            g.push();
            g.mul(new Matrix3x2f(ux / 8, uy / 8, vx / 8, vy / 8, sx(cx, ox, oz), sy(floor, ox, oy, oz)));
            g.blit(tex, 0, 0, u0, v0, 8, 8, 4, 4, 16, 16, tint);
            g.pop();
        }
    }

    // one cloud of the mod's dust, facing the camera, at {@code u} = age over life; drawn only on the asked side of the
    // block's centre
    static void cloud(Gfx g, float cx, float floor, DustArt.Sheet sheet, double u, double x, double y, double z, double half, int color,
        boolean mirrored, boolean front) {
        if ((0.61 * (x + z) + 0.5 * (y - 0.5) >= 0) != front) return;
        float s = (float) (half * 2 * UNIT);
        g.pushTranslate(sx(cx, x, z), sy(floor, x, y, z));
        g.scale(s / 16, s / 16);
        // a mirrored quad is culled in GUI drawing, so a mirrored cloud reads its frame backwards instead
        int px = sheet == DustArt.Sheet.POOF ? 12 : 6, frame = sheet.frame(u);
        g.blit(art(sheet), -8, -8, mirrored ? (frame + 1) * px : frame * px, 0, 16, 16, mirrored ? -px : px, px,
            sheet.frames * px, px, Math.round(Dust.alpha(sheet, u) * 255) << 24 | color);
        g.pop();
    }

    // the shockwave's ring, lying on the floor: a square of {@code half} blocks each way round the spot
    static void ring(Gfx g, float cx, float floor, double u, double half, int color) {
        float h = (float) half, side = 2 * h;
        int frame = DustArt.Sheet.RING.frame(u);
        g.push();
        g.mul(new Matrix3x2f(side * HX / 48, side * HZ / 48, -side * HX / 48, side * HZ / 48, sx(cx, -h, -h), sy(floor, -h, 0, -h)));
        g.blit(art(DustArt.Sheet.RING), 0, 0, frame * 48, 0, 48, 48, 8 * 48, 48,
            Math.round(Dust.alpha(DustArt.Sheet.RING, u) * 255) << 24 | color);
        g.pop();
    }

    private static Identifier art(DustArt.Sheet sheet) {
        return Identifier.tryParse("snappy_blocks:" + sheet.file);
    }

    // ---- items ----

    // half an item as drawn: 1.5 icon pixels per GUI pixel (half a block wide, as in the world)
    static final float ITEM_HALF = 12;
    private static final int[][] COPIES = {{-4, -3}, {4, -4}, {-2, -7}, {5, -8}};

    // a dropped stack standing on the screen point (x, bottom) with its squash; like vanilla, a bigger stack is drawn as
    // more copies (ItemClusterRenderState.getRenderedAmount)
    static void item(Gfx g, Identifier tex, float x, float bottom, double wide, double tall, int count) {
        if (wide <= 0.001 || tall <= 0.001) return;
        int copies = count <= 1 ? 1 : count <= 16 ? 2 : count <= 32 ? 3 : count <= 48 ? 4 : 5;
        g.pushTranslate(x, bottom);
        g.scale((float) (wide * 1.5), (float) (tall * 1.5));
        for (int i = copies - 2; i >= 0; i--) {
            g.pushTranslate(COPIES[i][0] / 1.5f, COPIES[i][1] / 1.5f);
            g.blit(tex, -8, -16, 0, 0, 16, 16, 16, 16, 0xFFB4ACB4);
            g.pop();
        }
        g.blit(tex, -8, -16, 0, 0, 16, 16, 16, 16, 0xFFFFFFFF);
        g.pop();
    }

    // a soft contact shadow, fading as its owner rises {@code height} blocks
    static void groundShadow(Gfx g, float x, float y, float size, double height) {
        float a = g.alpha;
        g.alpha *= (float) Math.max(0, Math.min(1, 1 - height * 0.7));
        g.pushTranslate(x, y);
        g.scale(size / 2, size / 2);
        g.blit(ITEM_SHADOW, -32, -16, 0, 0, 64, 32, 64, 32, 0xFFFFFFFF);
        g.pop();
        g.alpha = a;
    }

    // ---- the note ----

    // why the scene shows vanilla, in the accent colour along the bottom edge (the only text a preview has: the director,
    // 2026-11-25, timing numbers tell a player nothing they can use)
    static void note(Gfx g, int x, int y, int w, int h, String line) {
        java.util.List<String> lines = io.github.profetgit.profetsettings.Rows.wrap(g, line, w - 8, 2);
        int top = y + h - 3 - lines.size() * 10;
        for (int i = 0; i < lines.size(); i++) g.vanilla(lines.get(i), x + (w - g.vanillaWidth(lines.get(i))) / 2, top + i * 10 + 1, 0xFFF0A566, true);
    }

}
