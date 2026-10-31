package io.github.profetgit.snappyblocks;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.world.entity.Entity;

/**
 * Other mods that do one of our jobs (PRD §9): when one is installed, that feature of ours stands aside. Checked once
 * at start by mod id or, for mods whose id isn't published (fresh block animations, Block Motion), by name; each one
 * found is logged once.
 */
public final class Compat {
    /** Placement and mining animations: our pop and pulse stand aside. */
    private static final Set<String> PLACE_IDS = Set.of("a_good_place", "stupid-block-animations", "blockmotion", "block_motion",
        "fresh-blocks-animation", "fresh_block_animations", "freshblockanimations");
    private static final Set<String> PLACE_NAMES = Set.of("a good place", "stupid block animations", "block motion", "fresh block animations");
    /** Item landing: ItemPhysic Lite lays items flat, so our hop and squash stand aside. */
    private static final Set<String> LAND_IDS = Set.of("itemphysiclite", "itemphysic");
    /** Pickup animations: our pickup flight stands aside (merge and despawn stay). */
    private static final Set<String> PICKUP_IDS = Set.of("pickupanimations", "tiny_pickup_animation");

    public static boolean placeAside, landAside, pickupAside;
    /** Harvest Sweep animates crop harvests: crop breaks (#crops) are left to it. */
    public static boolean harvestSweep;
    /** Having a Blast hides explosion drops until its debris lands: those are left alone. */
    private static MethodHandle blastHidden;
    /** Ids of the mods found (test hook), and how often a drop Having a Blast hides was left alone. */
    public static final List<String> found = new ArrayList<>();
    public static int blastSkips;

    private Compat() {}

    static void detect() {
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String id = mod.getMetadata().getId(), name = mod.getMetadata().getName().toLowerCase(Locale.ROOT).trim();
            if (PLACE_IDS.contains(id) || PLACE_NAMES.contains(name)) {
                placeAside = true;
                found(mod, "our place pop and mining pulse stand aside");
            }
            if (LAND_IDS.contains(id)) {
                landAside = true;
                found(mod, "our item landing stands aside");
            }
            if (PICKUP_IDS.contains(id)) {
                pickupAside = true;
                found(mod, "our pickup flight stands aside");
            }
            if (id.equals("harvestsweep")) {
                harvestSweep = true;
                found(mod, "crop breaks are left to it");
            }
            if (id.equals("havingablast")) {
                try {
                    Class<?> drops = Class.forName("io.github.profetgit.havingablast.client.Drops");
                    blastHidden = MethodHandles.publicLookup().findStatic(drops, "hidden", MethodType.methodType(boolean.class, Entity.class));
                    found(mod, "the drops it hides are left alone");
                } catch (ReflectiveOperationException | LinkageError e) {
                    SnappyBlocks.LOG.warn("Snappy Blocks: Having a Blast found, but not its Drops.hidden ({})", e.toString());
                }
            }
        }
    }

    private static void found(ModContainer mod, String what) {
        found.add(mod.getMetadata().getId());
        SnappyBlocks.LOG.info("Snappy Blocks: {} ({}) is installed: {}", mod.getMetadata().getName(), mod.getMetadata().getId(), what);
    }

    /** True while Having a Blast keeps this item hidden. */
    public static boolean blastHides(Entity e) {
        if (blastHidden == null) return false;
        try {
            boolean hidden = (boolean) blastHidden.invokeExact(e);
            if (hidden) blastSkips++;
            return hidden;
        } catch (Throwable t) {
            blastHidden = null;
            SnappyBlocks.LOG.warn("Snappy Blocks: Having a Blast's Drops.hidden failed, no longer asked ({})", t.toString());
            return false;
        }
    }
}
