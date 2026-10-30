package io.github.profetgit.snappyblocks.item;

import io.github.profetgit.snappyblocks.Clock;
import io.github.profetgit.snappyblocks.Config;
import io.github.profetgit.snappyblocks.Rtt;
import io.github.profetgit.snappyblocks.Tune;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Item life (PRD §5.4): the landing squash and hop, the Q-throw from the hand, and the bookkeeping that tells a merge
 * from a despawn. Everything here is an offset on the render state; the entity is never moved. Effects run on the
 * {@link #NEAREST} nearest items only.
 */
public final class ItemFx {
    static final String LAND_HOP = Tune.def("land.hop", 0.04);
    static final String LAND_VOLUME = Tune.def("land.volume", 0.15);
    static final String LAND_AIR = Tune.def("land.air", 6);
    static final String THROW_SPRING = Tune.def("throw.spring", 4);
    static final int NEAREST = 64;
    static final int SQUASH_TICKS = 2, HOP_TICKS = 4, LAND_GAP = 20, CROWD = 16, SOUNDS_PER_TICK = 4;

    static final class Tracked {
        int air;
        double landedAt = Double.NaN;
        long lastLand = Long.MIN_VALUE / 2;
        /** A Q-throw: drawn from the hand, springing to the true position. */
        double throwAt = Double.NaN;
        double hx, hy, hz;
        long seenTick;
    }

    private static final Int2ObjectOpenHashMap<Tracked> TRACKED = new Int2ObjectOpenHashMap<>();
    private static final ArrayDeque<Tracked> POOL = new ArrayDeque<>();
    /** Stack count of every item entity, to tell that a stack grew (the far side of a merge). */
    private static final Int2IntOpenHashMap COUNTS = new Int2IntOpenHashMap();
    private static final Long2IntOpenHashMap CELLS = new Long2IntOpenHashMap();
    private static final IntOpenHashSet NEAR = new IntOpenHashSet();
    private static ItemEntity[] scratch = new ItemEntity[256];
    private static double[] dist = new double[256];

    /** A local Q press waiting for its item: clock time and the hand point in the world. */
    private static double throwPressed = Double.NaN;
    private static double thx, thy, thz;

    public static int landings, sounds, thrown, tracked;

    private ItemFx() {}

    public static void clear() {
        TRACKED.clear();
        COUNTS.clear();
        NEAR.clear();
        throwPressed = Double.NaN;
    }

    static boolean on() {
        return Config.get().enabled && Config.get().items;
    }

    /** True for one of the nearest items, the only ones that get effects (pillar 3, PRD §5.4 scale). */
    public static boolean near(int id) {
        return NEAR.contains(id);
    }

    /** End of a client tick: refresh the nearest set, then landings. */
    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || !on()) {
            if (!TRACKED.isEmpty()) TRACKED.clear();
            NEAR.clear();
            return;
        }
        long now = Clock.ticks();
        Vec3 eye = mc.player.getEyePosition();
        int n = 0;
        CELLS.clear();
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof ItemEntity item) || item.isRemoved()) continue;
            if (n == scratch.length) {
                scratch = Arrays.copyOf(scratch, n * 2);
                dist = Arrays.copyOf(dist, n * 2);
            }
            scratch[n] = item;
            dist[n] = item.distanceToSqr(eye);
            n++;
            CELLS.addTo(item.blockPosition().asLong(), 1);
        }
        double cut = Double.MAX_VALUE;
        if (n > NEAREST) {
            double[] d = Arrays.copyOf(dist, n);
            Arrays.sort(d);
            cut = d[NEAREST - 1];
        }
        NEAR.clear();
        int sound = 0;
        for (int i = 0; i < n; i++) {
            if (dist[i] > cut || NEAR.size() >= NEAREST) continue;
            ItemEntity e = scratch[i];
            scratch[i] = null;
            NEAR.add(e.getId());
            Tracked t = TRACKED.get(e.getId());
            if (t == null) {
                t = POOL.isEmpty() ? new Tracked() : POOL.pop();
                t.air = 0;
                t.landedAt = Double.NaN;
                t.lastLand = Long.MIN_VALUE / 2;
                t.throwAt = Double.NaN;
                TRACKED.put(e.getId(), t);
            }
            t.seenTick = now;
            if (!e.onGround()) {
                t.air++;
                continue;
            }
            if (t.air >= Tune.get(LAND_AIR) && now - t.lastLand >= LAND_GAP && landable(level, e)) {
                t.landedAt = Clock.now();
                t.lastLand = now;
                landings++;
                if (sound < SOUNDS_PER_TICK && sound(level, e)) sound++;
            }
            t.air = 0;
        }
        for (int i = 0; i < n; i++) scratch[i] = null;
        for (Iterator<Int2ObjectOpenHashMap.Entry<Tracked>> it = TRACKED.int2ObjectEntrySet().fastIterator(); it.hasNext(); ) {
            Tracked t = it.next().getValue();
            if (t.seenTick != now) {
                it.remove();
                POOL.push(t);
            }
        }
        tracked = TRACKED.size();
        if (!Double.isNaN(throwPressed) && Clock.now() - throwPressed > Math.max(Rtt.ticks(), 1) + 2) throwPressed = Double.NaN;
    }

    /** Never in water, on a hopper or in a crowded cell (item streams and farms stay vanilla). */
    private static boolean landable(ClientLevel level, ItemEntity e) {
        if (e.isInWater() || e.isInLava()) return false;
        if (CELLS.get(e.blockPosition().asLong()) > CROWD) return false;
        BlockState below = level.getBlockState(e.getOnPos());
        return !below.is(Blocks.HOPPER);
    }

    private static boolean sound(ClientLevel level, ItemEntity e) {
        if (e.isSilent()) return false;
        BlockPos on = e.getOnPos();
        BlockState below = level.getBlockState(on);
        if (below.isAir()) return false;
        float volume = (float) (Tune.get(LAND_VOLUME) * Math.min(1, Config.get().amplitude()));
        if (volume <= 0) return false;
        level.playLocalSound(e.getX(), e.getY(), e.getZ(), below.getSoundType().getStepSound(), SoundSource.NEUTRAL, volume, 1.6f, false);
        sounds++;
        return true;
    }

    /** ClientPacketListener.handleSetEntityData: an item's stack arrived or changed. Returns the growth (0 if none). */
    public static int stackChanged(ItemEntity e) {
        int count = e.getItem().getCount();
        int old = COUNTS.put(e.getId(), count);
        return old > 0 ? count - old : 0;
    }

    public static void forget(int id) {
        COUNTS.remove(id);
    }

    /** MultiPlayerGameMode.dropItem with something in the hand and no screen open: the next new item near us is it. */
    public static void dropPressed(Minecraft mc) {
        if (!on() || mc.player == null || mc.gui.screen() != null) return;
        if (Rtt.ticks() > 2) return; // the entity arrives more than a tick old: vanilla (PRD §5.4)
        Entity p = mc.player;
        float yaw = p.getYRot() * Mth.DEG_TO_RAD, pitch = p.getXRot() * Mth.DEG_TO_RAD;
        Vec3 look = p.getLookAngle();
        double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
        if (mc.options.getCameraType().isFirstPerson()) {
            Vec3 eye = p.getEyePosition();
            // where the held item sits in first person: ahead, right and below the view
            double ux = -Mth.sin(yaw) * Mth.sin(pitch), uy = Mth.cos(pitch), uz = Mth.cos(yaw) * Mth.sin(pitch);
            thx = eye.x + look.x * 0.55 + rx * 0.32 - ux * 0.32;
            thy = eye.y + look.y * 0.55 - uy * 0.32;
            thz = eye.z + look.z * 0.55 + rz * 0.32 - uz * 0.32;
        } else {
            thx = p.getX() + rx * 0.35 + look.x * 0.3;
            thy = p.getY() + 0.75;
            thz = p.getZ() + rz * 0.35 + look.z * 0.3;
        }
        if (mc.player.getMainArm() == HumanoidArm.LEFT) {
            thx -= rx * 0.64;
            thz -= rz * 0.64;
        }
        throwPressed = Clock.now();
    }

    /** ClientPacketListener.handleAddEntity of an item: the one our Q press threw starts at the hand. */
    public static void added(ItemEntity e) {
        if (Double.isNaN(throwPressed)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || e.distanceToSqr(mc.player.getEyePosition()) > 2.5 * 2.5) return;
        Tracked t = TRACKED.get(e.getId());
        if (t == null) {
            t = POOL.isEmpty() ? new Tracked() : POOL.pop();
            t.air = 0;
            t.landedAt = Double.NaN;
            t.lastLand = Long.MIN_VALUE / 2;
            t.seenTick = Clock.ticks();
            TRACKED.put(e.getId(), t);
        }
        t.throwAt = Clock.now();
        t.hx = thx;
        t.hy = thy;
        t.hz = thz;
        throwPressed = Double.NaN;
        thrown++;
    }

    /** ItemEntityRenderer.extractRenderState: the throw offset, the hop and the squash. */
    public static void extract(ItemEntity e, ItemEntityRenderState state) {
        ((ItemFxState) state).snappy_blocks$squash(1, 1);
        if (TRACKED.isEmpty()) return;
        Tracked t = TRACKED.get(e.getId());
        if (t == null) return;
        double now = Clock.now();
        if (!Double.isNaN(t.throwAt)) {
            double u = (now - t.throwAt) / Math.max(1, Tune.get(THROW_SPRING));
            if (u >= 1) {
                t.throwAt = Double.NaN;
            } else {
                double k = (1 - u) * (1 - u) * (1 - u);
                // the hand point is the item's centre; the state is at its feet
                state.x += (t.hx - state.x) * k;
                state.y += (t.hy - 0.2 - state.y) * k;
                state.z += (t.hz - state.z) * k;
            }
        }
        if (Double.isNaN(t.landedAt)) return;
        double dt = now - t.landedAt;
        double amp = Config.get().calm ? 0 : Config.get().amplitude();
        if (dt >= SQUASH_TICKS + HOP_TICKS || amp <= 0) {
            if (dt >= SQUASH_TICKS + HOP_TICKS) t.landedAt = Double.NaN;
            return;
        }
        if (dt < SQUASH_TICKS) {
            double s = Math.sin(Math.PI * dt / SQUASH_TICKS) * amp;
            ((ItemFxState) state).snappy_blocks$squash((float) (1 + 0.06 * s), (float) (1 - 0.12 * s));
        } else {
            double v = (dt - SQUASH_TICKS) / HOP_TICKS;
            state.y += Tune.get(LAND_HOP) * amp * 4 * v * (1 - v);
            double s = Math.sin(Math.PI * v) * amp;
            ((ItemFxState) state).snappy_blocks$squash((float) (1 - 0.02 * s), (float) (1 + 0.04 * s));
        }
    }
}
