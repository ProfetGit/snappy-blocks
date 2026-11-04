package io.github.profetgit.snappyblocks;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import io.github.profetgit.snappyblocks.platform.Platform.ModInfo;
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

    public static boolean placeAside, pulseAside, landAside, pickupAside;
    /** Sodium is installed but our hide path isn't wired into its mesher (no hook applied for this build): the pop stands aside. */
    private static boolean sodiumPresent;
    private static boolean unwiredLogged;
    /** A mod that meshes chunks itself (Sodium, Embeddium): vanilla's "section mesh is live" signal may never come. */
    public static boolean meshReplaced;
    /** Harvest Sweep animates crop harvests: crop breaks (#crops) are left to it. */
    public static boolean harvestSweep;
    /** Having a Blast hides explosion drops until its debris lands: those are left alone. */
    private static MethodHandle blastHidden;
    /** Ids of the mods found (test hook), and how often a drop Having a Blast hides was left alone. */
    public static final List<String> found = new ArrayList<>();
    public static int blastSkips;

    private Compat() {}

    static void detect() {
        for (String id : new String[] {"sodium", "embeddium", "rubidium"}) {
            meshReplaced |= SnappyBlocks.platform().isModLoaded(id);
        }
        sodiumPresent = SnappyBlocks.platform().isModLoaded("sodium");
        for (String id : new String[] {"embeddium", "rubidium"}) {
            if (SnappyBlocks.platform().isModLoaded(id)) {
                // their meshers have no hide path from us (untested): the pop stands aside, the pulse draws over the mesh and needs none
                placeAside = true;
                SnappyBlocks.LOG.info("Snappy Blocks: {} is installed, not supported by the place pop: it stands aside, the mining pulse stays", id);
            }
        }
        for (ModInfo mod : SnappyBlocks.platform().mods()) {
            String id = mod.id(), name = mod.name().toLowerCase(Locale.ROOT).trim();
            if (PLACE_IDS.contains(id) || PLACE_NAMES.contains(name)) {
                placeAside = true;
                pulseAside = true;
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

    private static void found(ModInfo mod, String what) {
        found.add(mod.id());
        SnappyBlocks.LOG.info("Snappy Blocks: {} ({}) is installed: {}", mod.name(), mod.id(), what);
    }

    /**
     * Whether the place pop stands aside. With Sodium it also does while no Sodium hook has run: the hide path needs the
     * mesher hooks (compat mixins apply only to the builds whose methods match), and a pop with the block still in the
     * mesh would show the block twice. The first Sodium mesh task has run long before a player can place anything.
     */
    public static boolean popAside() {
        if (placeAside) return true;
        if (!sodiumPresent || io.github.profetgit.snappyblocks.pop.SodiumHidden.wired) return false;
        if (!unwiredLogged) {
            unwiredLogged = true;
            SnappyBlocks.LOG.warn("Snappy Blocks: Sodium is installed but this build of it has no hook for the place pop: the pop stands aside, the other effects stay");
        }
        return true;
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
