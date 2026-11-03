package io.github.profetgit.snappyblocks.item;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.profetgit.snappyblocks.Clock;
import io.github.profetgit.snappyblocks.Compat;
import io.github.profetgit.snappyblocks.Config;
import io.github.profetgit.snappyblocks.RenderPass;
import io.github.profetgit.snappyblocks.Tune;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Items that are gone from the level but still drawn for a few ticks, from their own render state (PRD §5.4, §5.5):
 * <ul>
 *   <li>a pickup arcs to the picker's chest; for the local player in first person it flies into its hotbar slot
 *       instead, shrinking to the slot's size on the way, and the slot bumps when it arrives;</li>
 *   <li>an item removed without a take packet holds its place for a tick: if a stack of the same item close by grew
 *       that tick it slides into it (a merge), otherwise it shrinks away (despawn, lava, cactus, hopper).</li>
 * </ul>
 * Instead of the GUI hand-over at the near plane in the PRD the world item itself flies into the slot (2026-10-29):
 * no 3D-to-2D swap, so nothing can pop, and the hotbar draws over its last frames.
 */
public final class Ghosts {
    static final String PICKUP_TICKS = Tune.def("pickup.ticks", 5);
    static final String MERGE_TICKS = Tune.def("merge.ticks", 3);
    static final String DESPAWN_TICKS = Tune.def("despawn.ticks", 3);
    static final int CAP = 64;
    /**
     * Whole ticks a removed item waits for a stack next to it to grow. The server sends entity data before it ticks
     * entities, so a merge's growth can land one tick after the removal.
     */
    static final int MERGE_WAIT = 2;
    /** How far in front of the eye the slot flight ends: the point on the ray through the slot's centre. */
    static final double SLOT_DEPTH = 0.9;
    static final double ARC = 0.35, SLOT_ARC = 0.12;

    enum Kind { PICKUP, SLOT, PENDING, MERGE, DESPAWN }

    static final class Ghost {
        final GhostItem item;
        final ItemStack stack;
        final int entityId;
        Kind kind;
        double t0, dur;
        /** Start centre (world). */
        double sx, sy, sz;
        /** Last drawn centre and scale, for a retarget without a jump. */
        double lx, ly, lz, ls = 1;
        double s0 = 1;
        Entity target;
        int slot = -1;
        boolean bound, local;
        ItemStack hidden;
        long hiddenTick;
        long removedTick;
        final double born = Clock.now();
        float age0;

        Ghost(GhostItem item, ItemStack stack, int entityId) {
            this.item = item;
            this.stack = stack;
            this.entityId = entityId;
        }

        double u(double now) {
            return dur <= 0 ? 1 : Math.clamp((now - t0) / dur, 0, 1);
        }
    }

    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static final Matrix4f INV = new Matrix4f();
    private static final Vector4f V4 = new Vector4f();
    private static final Vector3f V3 = new Vector3f();
    private static double lastPx, lastPy, lastPz;
    private static long teleportTick = Long.MIN_VALUE;
    private static double partial;

    public static int lastSlot = -1;
    public static int pickups, slotFlights, slotBumps, merges, despawns, dropped, retargets, hiddenSlots, slotReveals, slotStopReveals;
    public static long spentNanos;
    /** A newly filled slot is shown after this many ticks even if its flight hasn't arrived (PRD pillar 4). */
    static final long HIDE_LIMIT_TICKS = 6;

    private Ghosts() {}

    public static void clear() {
        for (Ghost g : GHOSTS) g.hidden = null;
        GHOSTS.clear();
    }

    public static int active() {
        return GHOSTS.size();
    }

    static boolean on() {
        return Config.get().enabled && Config.get().items;
    }

    private static Ghost ghost(ItemEntity e, int count) {
        GhostItem item = GhostItem.capture(e, count);
        if (item == null) return null;
        Ghost g = new Ghost(item, e.getItem().copy(), e.getId());
        g.age0 = item.age0;
        double lift = item.lift();
        g.sx = g.lx = item.x;
        g.sy = g.ly = item.y + lift;
        g.sz = g.lz = item.z;
        return g;
    }

    /**
     * ClientPacketListener.handleTakeItemEntity, in place of vanilla's straight 3-tick pickup particle. Returns true when
     * the pickup is ours (vanilla's particle is then skipped).
     */
    public static boolean take(Entity from, LivingEntity to, int amount) {
        if (!on() || !Config.get().pickup || Compat.pickupAside || !(from instanceof ItemEntity item) || to == null) return false;
        if (Compat.blastHides(item)) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || to.isSpectator() || mc.player.isSpectator() && to == mc.player) return false;
        if (GHOSTS.size() >= CAP || item.getItem().isEmpty()) return false;
        Ghost g = ghost(item, amount);
        if (g == null) return false;
        g.target = to;
        g.t0 = Clock.now();
        g.dur = Math.max(1, Tune.get(PICKUP_TICKS));
        g.kind = Kind.PICKUP;
        if (to == mc.player) {
            g.local = true;
            int slot = predictSlot(mc.player.getInventory(), item.getItem());
            if (slotFlight(mc) && hudSlot(slot)) {
                g.kind = Kind.SLOT;
                g.slot = slot;
                slotFlights++;
            }
        }
        GHOSTS.add(g);
        pickups++;
        return true;
    }

    /** Where Inventory.add will put the stack on the server: the client's copy of the inventory is still the old one. */
    private static int predictSlot(Inventory inv, ItemStack stack) {
        int slot = inv.getSlotWithRemainingSpace(stack);
        return slot >= 0 ? slot : inv.getFreeSlot();
    }

    private static boolean hudSlot(int slot) {
        return slot >= 0 && slot < 9 || slot == Inventory.SLOT_OFFHAND;
    }

    /** First person, HUD shown, no screen open (PRD §5.5); otherwise the arc ends at the chest. */
    private static boolean slotFlight(Minecraft mc) {
        return mc.options.getCameraType().isFirstPerson() && !mc.gui.hud.isHidden() && mc.gui.screen() == null;
    }

    /**
     * ClientPacketListener.handleContainerSetSlot for the player's inventory: the server says where the pickup went.
     * Binds the oldest unbound local pickup of the same item to that slot, moves the slot's bump to its arrival and,
     * for a slot that was empty, hides the new stack until then.
     */
    public static void slotSet(int menuSlot, ItemStack now, boolean wasEmpty) {
        if (GHOSTS.isEmpty() || now.isEmpty()) return;
        int slot = menuSlot == 45 ? Inventory.SLOT_OFFHAND : menuSlot >= 36 && menuSlot < 45 ? menuSlot - 36 : menuSlot >= 9 && menuSlot < 36 ? menuSlot : -1;
        if (slot < 0) return;
        Minecraft mc = Minecraft.getInstance();
        for (Ghost g : GHOSTS) {
            if (!g.local || g.bound || g.kind != Kind.SLOT && g.kind != Kind.PICKUP) continue;
            if (!ItemStack.isSameItemSameComponents(g.stack, now)) continue;
            g.bound = true;
            double t = Clock.now();
            boolean flying = g.u(t) < 1;
            if (g.kind == Kind.SLOT && g.slot != slot) {
                retarget(g, t, hudSlot(slot) && slotFlight(mc) ? Kind.SLOT : Kind.PICKUP);
            }
            g.slot = slot;
            lastSlot = slot;
            if (g.kind == Kind.SLOT && flying) {
                now.setPopTime(0);
                if (wasEmpty) {
                    g.hidden = now;
                    g.hiddenTick = Clock.ticks();
                    hiddenSlots++;
                }
            }
            return;
        }
    }

    /** A new target mid-flight: start again from where the ghost was drawn last, so it never jumps. */
    private static void retarget(Ghost g, double now, Kind kind) {
        g.sx = g.lx;
        g.sy = g.ly;
        g.sz = g.lz;
        g.s0 = g.ls;
        g.t0 = now;
        g.dur = Math.max(2, g.dur * (1 - g.u(now)));
        g.kind = kind;
        retargets++;
    }

    /** Slots hidden right now (test hook, no side effects). */
    public static int hiddenNow() {
        int n = 0;
        for (Ghost g : GHOSTS) if (g.hidden != null) n++;
        return n;
    }

    /** Hud.extractSlot: a slot that was empty shows its new stack only when the flight arrives. */
    public static boolean hiddenSlot(ItemStack stack) {
        if (GHOSTS.isEmpty()) return false;
        for (Ghost g : GHOSTS) {
            if (g.hidden != stack) continue;
            if (!Clock.stopped() && Clock.ticks() - g.hiddenTick < HIDE_LIMIT_TICKS) return true;
            // paused, frozen or late: the stack shows now, the flight goes on over it
            g.hidden = null;
            slotReveals++;
            if (Clock.stopped()) slotStopReveals++;
        }
        return false;
    }

    /** ClientPacketListener.handleRemoveEntities, before the item is gone: draw it on until we know why it went. */
    public static void removed(ItemEntity e) {
        if (!on() || GHOSTS.size() >= CAP || !ItemFx.near(e.getId()) || Compat.blastHides(e)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !inRange(mc, e.position()) || Clock.ticks() - teleportTick <= 1) return;
        Ghost g = ghost(e, 0);
        if (g == null) return;
        g.kind = Kind.PENDING;
        g.t0 = Clock.now();
        g.removedTick = Clock.ticks();
        g.dur = 1;
        GHOSTS.add(g);
    }

    /** Only well inside the tracking range: at its edge, items leave because we walked away (PRD §5.4). */
    private static boolean inRange(Minecraft mc, Vec3 at) {
        double range = Math.min(96, mc.options.getEffectiveRenderDistance() * 16) - 8;
        return mc.player.position().distanceToSqr(at) < range * range;
    }

    /** ClientPacketListener.handleSetEntityData: a stack grew; a ghost of the same item right next to it merged into it. */
    public static void grew(ItemEntity target) {
        if (GHOSTS.isEmpty()) return;
        for (Ghost g : GHOSTS) {
            if (g.kind != Kind.PENDING || g.entityId == target.getId()) continue;
            if (!ItemStack.isSameItemSameComponents(g.stack, target.getItem())) continue;
            double dx = target.getX() - g.sx, dz = target.getZ() - g.sz, dy = target.getY() - (g.sy - g.item.lift());
            if (dx * dx + dy * dy + dz * dz > 1.5 * 1.5) continue;
            g.kind = Kind.MERGE;
            g.target = target;
            g.t0 = Clock.now();
            g.dur = Math.max(1, Tune.get(MERGE_TICKS));
            merges++;
            return;
        }
    }

    /** End of a client tick: settle pending removals, land arrivals, drop finished ghosts. */
    public static void tick(Minecraft mc) {
        if (mc.player != null) {
            double dx = mc.player.getX() - lastPx, dy = mc.player.getY() - lastPy, dz = mc.player.getZ() - lastPz;
            if (dx * dx + dy * dy + dz * dz > 8 * 8) {
                teleportTick = Clock.ticks();
                for (Ghost g : GHOSTS) if (g.kind == Kind.PENDING) g.kind = null;
            }
            lastPx = mc.player.getX();
            lastPy = mc.player.getY();
            lastPz = mc.player.getZ();
        }
        if (GHOSTS.isEmpty()) return;
        double now = Clock.now();
        for (int i = GHOSTS.size() - 1; i >= 0; i--) {
            Ghost g = GHOSTS.get(i);
            if (g.kind == Kind.PENDING && Clock.ticks() - g.removedTick >= MERGE_WAIT) {
                // nothing grew: it despawned, burned, or a hopper took it
                g.kind = Kind.DESPAWN;
                g.t0 = now;
                g.dur = Math.max(1, Tune.get(DESPAWN_TICKS));
                g.sx = g.lx;
                g.sy = g.ly;
                g.sz = g.lz;
                despawns++;
                continue;
            }
            boolean gone = g.kind == null || g.target != null && g.target.isRemoved() && g.kind == Kind.MERGE;
            if (gone || g.kind != Kind.PENDING && g.u(now) >= 1) {
                if (g.kind == Kind.SLOT) arrive(mc, g);
                if (gone) dropped++;
                g.hidden = null;
                GHOSTS.remove(i);
            }
        }
    }

    private static void arrive(Minecraft mc, Ghost g) {
        if (!g.bound || mc.player == null) return;
        ItemStack s = mc.player.getInventory().getItem(g.slot);
        if (!s.isEmpty()) {
            s.setPopTime(5);
            slotBumps++;
        }
    }

    /** With the pops, in the transient-block pass. */
    public static void submit(RenderPass pass) {
        if (GHOSTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        PoseStack ps = pass.ps;
        Vec3 cam = pass.cam;
        double now = Clock.now();
        partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (Ghost g : GHOSTS) {
            if (g.kind == null) continue;
            double u = g.u(now);
            if (g.kind != Kind.PENDING && u >= 1) continue;
            GhostItem s = g.item;
            s.age((float) (g.age0 + now - g.born)); // spin and bob go on
            double cx = g.sx, cy = g.sy, cz = g.sz, scale = g.s0;
            switch (g.kind) {
                case PICKUP -> {
                    Entity t = g.target;
                    double tx = Mth.lerp(partial, t.xo, t.getX()), ty = Mth.lerp(partial, t.yo, t.getY()), tz = Mth.lerp(partial, t.zo, t.getZ());
                    ty += t.getEyeHeight() / 2;
                    double e = u * u;
                    cx += (tx - cx) * e;
                    cy += (ty - cy) * e + ARC * 4 * u * (1 - u);
                    cz += (tz - cz) * e;
                    scale += (0.45 - scale) * e;
                }
                case SLOT -> {
                    if (!slotPoint(mc, pass, g.slot)) continue;
                    double tx = cam.x + V3.x * SLOT_DEPTH, ty = cam.y + V3.y * SLOT_DEPTH, tz = cam.z + V3.z * SLOT_DEPTH;
                    double size = s.size();
                    double k = 32 * SLOT_DEPTH / (Math.max(0.1, size) * pass.projection.m11() * mc.getWindow().getGuiScaledHeight());
                    double e = u * u * (3 - 2 * u);
                    cx += (tx - cx) * e;
                    cy += (ty - cy) * e + SLOT_ARC * 4 * u * (1 - u);
                    cz += (tz - cz) * e;
                    scale += (k - scale) * e;
                }
                case MERGE -> {
                    Entity t = g.target;
                    double tx = Mth.lerp(partial, t.xo, t.getX()), ty = Mth.lerp(partial, t.yo, t.getY()) + s.lift(), tz = Mth.lerp(partial, t.zo, t.getZ());
                    double e = u * u * (3 - 2 * u);
                    cx += (tx - cx) * e;
                    cy += (ty - cy) * e;
                    cz += (tz - cz) * e;
                    scale += (0.8 - scale) * e;
                }
                case DESPAWN -> scale *= 1 - u * u * (3 - 2 * u);
                case PENDING -> {
                }
            }
            g.lx = cx;
            g.ly = cy;
            g.lz = cz;
            g.ls = scale;
            if (scale <= 0.001) continue;
            double lift = s.lift();
            ps.pushPose();
            ps.translate(cx - cam.x, cy - cam.y, cz - cam.z);
            ps.scale((float) scale, (float) scale, (float) scale);
            s.draw(pass, lift, cx, cy, cz);
            ps.popPose();
        }
    }

    /**
     * The direction from the eye through the centre of a hotbar slot, into {@link #V3}. Unprojects the slot's screen
     * point with this frame's projection and view rotation, so the flight end follows the camera.
     */
    private static boolean slotPoint(Minecraft mc, RenderPass pass, int slot) {
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        double gx, gy = h - 19 + 8;
        if (slot == Inventory.SLOT_OFFHAND) {
            Player p = mc.player;
            if (p == null) return false;
            gx = p.getMainArm().getOpposite() == HumanoidArm.LEFT ? w / 2 - 91 - 26 + 8 : w / 2 + 91 + 10 + 8;
        } else {
            gx = w / 2 - 90 + slot * 20 + 2 + 8;
        }
        float nx = (float) (gx / w * 2 - 1), ny = (float) (1 - gy / h * 2);
        INV.set(pass.projection).invert();
        V4.set(nx, ny, 0.5f, 1).mul(INV);
        if (V4.w == 0) return false;
        V3.set(V4.x / V4.w, V4.y / V4.w, V4.z / V4.w);
        INV.set(pass.viewRotation).invert();
        INV.transformDirection(V3);
        V3.normalize();
        return Float.isFinite(V3.x);
    }
}
