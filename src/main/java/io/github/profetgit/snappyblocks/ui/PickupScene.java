package io.github.profetgit.snappyblocks.ui;

import io.github.profetgit.profetsettings.Ctx;
import io.github.profetgit.profetsettings.Gfx;
import io.github.profetgit.snappyblocks.Compat;
import io.github.profetgit.snappyblocks.Config;
import io.github.profetgit.snappyblocks.item.Ghosts;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// Pickup flight, as the player sees it: emeralds drop in front of them and fly into the hotbar slot they are going to,
// along the real path (the slot lies 0.9 blocks in front of the eye), passing under the hotbar at the end, and the slot
// gives vanilla's bump when they arrive. The hotbar is the player's own.
final class PickupScene extends SnappyScene {
    // ms: the item is taken; the slot is free again when the loop restarts, as an item leaves a slot in vanilla
    private static final float PERIOD = 2600, TAKE = 1150;
    // focal length of the eye in GUI pixels (a 70 degree view, 540 pixels high), the item's depth on the ground and the
    // slot's (Ghosts.SLOT_DEPTH)
    private static final double F = 385, AWAY = 2.5, NEAR = 0.9;
    // the item's start, from the box's corner: its x, the ground line, the height it drops from in blocks
    private static final float START = 52, FLOOR = 80;
    private static final double HEIGHT = 2.4;
    private static final int SLOT = 5, COUNT = 3;
    // vanilla's pickup particle: its life, and where it heads, below the eye and at the player
    private static final double VANILLA_TICKS = 3, CHEST = 0.81, CHEST_DEPTH = 0.2;
    //? if >=1.20.2 {
    private static final Identifier HOTBAR = Identifier.tryParse("hud/hotbar"),
        SELECTION = Identifier.tryParse("hud/hotbar_selection");
    //?} else {
    /*private static final Identifier WIDGETS = Identifier.tryParse("textures/gui/widgets.png");
    *///?}
    // the copper pickaxe exists from 1.21.9; before that the preview holds the iron one
    //? if >=1.21.9 {
    private static final Item PICKAXE = Items.COPPER_PICKAXE;
    private static final String PICKAXE_TEX = "textures/item/copper_pickaxe.png";
    //?} else {
    /*private static final Item PICKAXE = Items.IRON_PICKAXE;
    private static final String PICKAXE_TEX = "textures/item/iron_pickaxe.png";
    *///?}

    @Override
    public void render(Gfx g, Ctx c, int x, int y, int w, int h) {
        float time = time(c, PERIOD);
        Identifier emerald = itemTex("emerald");
        int ox = x + (w - Diorama.W) / 2;
        int bar = x + (w - 182) / 2, by = y + 111, ix = bar + 3 + SLOT * 20, iy = by + 3;
        float floor = y + FLOOR, startX = ox + START;
        String off = !Config.get().items && Config.get().enabled ? t("off.items") : off("pickup", Config.get().pickup, Compat.pickupAside);
        Diorama.backdrop(g, ox, y);
        // switched off: vanilla's slot fills at once, while the item itself is flicked at the player's chest in 3 ticks
        boolean mod = off == null, lands = Config.get().enabled && Config.get().items && !Compat.landAside;
        double t = (time - TAKE) / MS, u = t / Ghosts.pickupTicks();
        double[] now = ground(floor, Math.min(time, TAKE) / MS, lands);
        // view space around the middle of the box: x right, y up, depth forward
        double mx = x + w / 2.0, my = y + h / 2.0;
        double ax = (startX - mx) * AWAY / F, ay = (my - now[0]) * AWAY / F;
        if (t < 0) {
            Diorama.groundShadow(g, startX, floor, 1.2f, now[3]);
            Diorama.item(g, emerald, startX, (float) (now[0] + Diorama.ITEM_HALF * now[2]), now[1], now[2], COUNT);
        } else if (mod && u < 1) {
            double bx = (ix + 8 - mx) * NEAR / F, bz = (my - (iy + 8)) * NEAR / F;
            double e = Ghosts.smooth(u);
            double px = ax + (bx - ax) * e, py = ay + (bz - ay) * e + Ghosts.SLOT_ARC * 4 * u * (1 - u), depth = AWAY + (NEAR - AWAY) * e;
            // the item shrinks in the world so that on arrival it covers exactly the slot's 16 pixels
            double size = 2 * Diorama.ITEM_HALF * AWAY, k = 16 * NEAR / size;
            double on = size * (1 + (k - 1) * e) / depth / (2 * Diorama.ITEM_HALF);
            float sx = (float) (mx + F * px / depth), sy = (float) (my - F * py / depth);
            Diorama.groundShadow(g, sx, floor, (float) (1.2 * (1 - e)), e);
            Diorama.item(g, emerald, sx, (float) (sy + Diorama.ITEM_HALF * on), on, on, COUNT);
        } else if (!mod && t < VANILLA_TICKS) {
            double e = t / VANILLA_TICKS * (t / VANILLA_TICKS);
            double depth = AWAY + (CHEST_DEPTH - AWAY) * e, on = AWAY / depth;
            float sx = (float) (mx + F * ax * (1 - e) / depth), sy = (float) (my - F * (ay + (-CHEST - ay) * e) / depth);
            Diorama.item(g, emerald, sx, (float) (sy + Diorama.ITEM_HALF * on), on, on, COUNT);
        }
        // the hotbar draws over the end of the flight, as in game
        hotbar(g, bar, by);
        if (mod ? u >= 1 : t >= 0) {
            double pop = 5 - (mod ? t - Ghosts.pickupTicks() : t);
            if (pop > 0) {
                float squeeze = (float) (1 + pop / 5);
                g.pushTranslate(ix + 8, iy + 12);
                g.scale(1 / squeeze, (squeeze + 1) / 2);
                g.translate(-(ix + 8), -(iy + 12));
            }
            if (stacksReady()) {
                ItemStack stack = new ItemStack(Items.EMERALD, COUNT);
                g.item(stack, ix, iy);
                if (pop > 0) g.pop();
                g.itemDecorations(stack, ix, iy);
            } else {
                g.blit(emerald, ix, iy, 0, 0, 16, 16, 16, 16, 0xFFFFFFFF);
                if (pop > 0) g.pop();
            }
        }
        if (!mod) Diorama.note(g, x, y, w, h, off);
    }

    // the item {@code t} ticks into the loop, dropping in from above and landing: {middle y, width, height, height above
    // the ground in blocks}
    private static double[] ground(float floor, double t, boolean lands) {
        double[] e = fall(0, HEIGHT, 0, 0, t);
        double[] pose = lands ? landing(e[2]) : new double[] {1, 1, 0};
        double up = e[1] + pose[2] + (e[2] < 0 ? 0 : bob(e[2]));
        return new double[] {floor - 2 - up * Diorama.TALL - Diorama.ITEM_HALF * pose[1], pose[0], pose[1], up};
    }

    // vanilla's hotbar with a few things already in it; the slot the pickup goes to is free
    private static void hotbar(Gfx g, int x, int y) {
        //? if >=1.20.2 {
        g.sprite(HOTBAR, x, y, 182, 22, 0xFFFFFFFF);
        g.sprite(SELECTION, x - 1, y - 1, 24, 23, 0xFFFFFFFF);
        //?} else {
        /*g.blit(WIDGETS, x, y, 0, 0, 182, 22, 256, 256);
        g.blit(WIDGETS, x - 1, y - 1, 0, 22, 24, 24, 256, 256);
        *///?}
        held(g, PICKAXE, PICKAXE_TEX, 1, x + 3, y + 3);
        held(g, Items.BREAD, "textures/item/bread.png", 7, x + 23, y + 3);
        held(g, Items.TORCH, "textures/block/torch.png", 32, x + 43, y + 3);
    }

    private static void held(Gfx g, Item what, String tex, int count, int x, int y) {
        if (stacksReady()) item(g, new ItemStack(what, count), x, y);
        else g.blit(Identifier.tryParse(tex), x, y, 0, 0, 16, 16, 16, 16, 0xFFFFFFFF);
    }

    // 26.x binds item components when a world's data loads: before that (the title screen) no ItemStack can be made,
    // so the slots show the item textures alone
    private static boolean stacksReady() {
        //? if >=26.1 {
        return Items.EMERALD.builtInRegistryHolder().areComponentsBound();
        //?} else {
        /*return true;
        *///?}
    }
}
