package io.github.profetgit.snappyblocks.selftest;

import static io.github.profetgit.snappyblocks.selftest.Scenarios.Y;
import static io.github.profetgit.snappyblocks.selftest.Scenarios.cmd;
import static io.github.profetgit.snappyblocks.selftest.Scenarios.say;

import io.github.profetgit.snappyblocks.Clock;
import io.github.profetgit.snappyblocks.Compat;
import io.github.profetgit.snappyblocks.Config;
import io.github.profetgit.snappyblocks.SnappyBlocks;
import io.github.profetgit.snappyblocks.burst.Bursts;
import io.github.profetgit.snappyblocks.item.Ghosts;
import io.github.profetgit.snappyblocks.item.ItemFx;
import io.github.profetgit.snappyblocks.pop.Hidden;
import io.github.profetgit.snappyblocks.pop.Pops;
import io.github.profetgit.snappyblocks.pulse.Pulses;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * PRD §13 M4, compatibility and scale: normal play at volume (actions100), the worst case (vein64), a placing mod at
 * full rate (autoplace), the clock standing still (freeze), a dimension change, connected textures (ctm), and the
 * other mods of PRD §9 (timber, blast, sweep, aside). Scenes that need another mod check vanilla behaviour when it is
 * missing, so every run asserts something.
 */
final class ScaleScenes {
    /** The 5x4 floor grid in front of the player, within creative reach. */
    static final int GX0 = -2, GX1 = 2, GZ0 = -1, GZ1 = 2;
    /** Hotbar of the volume scenes: six cubes, a chest, a torch, a stack to throw. */
    static final String[] HOTBAR = {"stone", "oak_planks", "glass", "bricks", "oak_log", "white_wool", "chest", "torch", "cobblestone"};

    private ScaleScenes() {}

    static void add(Script s, String name) {
        switch (name) {
            case "actions100" -> actions100(s);
            case "vein64" -> vein64(s);
            case "autoplace" -> autoplace(s);
            case "freeze" -> freeze(s);
            case "dimension" -> dimension(s);
            case "ctm" -> ctm(s);
            case "timber" -> timber(s);
            case "blast" -> blast(s);
            case "sweep" -> sweep(s);
            case "aside" -> aside(s);
            case "replay" -> replay(s);
            case "config" -> config(s);
            default -> {
            }
        }
    }

    static final List<String> NAMES = List.of("actions100", "vein64", "autoplace", "freeze", "dimension", "ctm", "timber", "blast", "sweep", "aside", "replay", "config");

    static void place(Minecraft mc, BlockPos against, Direction face) {
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(against).relative(face, 0.5), face, against, false));
    }

    static BlockPos freeCell(Minecraft mc) {
        for (int z = GZ0; z <= GZ1; z++) for (int x = GX0; x <= GX1; x++) {
            BlockPos p = new BlockPos(x, Y, z);
            if (mc.level.getBlockState(p).isAir()) return p;
        }
        return null;
    }

    /** Creative, the hotbar of {@link #HOTBAR}, standing south of the grid looking at it. */
    static void volumeSetup(net.minecraft.server.MinecraftServer srv) {
        Scenarios.clearArea(srv);
        cmd(srv, "tp @p 0.5 " + Y + " -2.5 0 35");
        cmd(srv, "gamemode creative @p");
        cmd(srv, "clear @p");
        for (int i = 0; i < HOTBAR.length; i++) cmd(srv, "item replace entity @p hotbar." + i + " with minecraft:" + HOTBAR[i] + " 64");
    }

    /** Runs {@code each} every client tick for {@code ticks} ticks. */
    static void sample(Script s, int ticks, Consumer<Minecraft> each) {
        int[] left = {ticks};
        s.steps.add(mc -> {
            each.accept(mc);
            return --left[0] <= 0;
        });
    }

    /** Feel numbers changed for a scene (longer curves so the clock can stop mid-effect), put back after. */
    static void tune(Script s, Map<String, Double> values) {
        s.run(mc -> Config.get().tune.putAll(values));
    }

    static void untune(Script s, Map<String, Double> values) {
        s.run(mc -> {
            for (String k : values.keySet()) Config.get().tune.remove(k);
        });
    }

    // ---------------------------------------------------------------------------------------------------------------

    /**
     * PRD pillars 2 and 3: 100 mixed actions, one every 4 ticks (places of cubes, a chest and a torch, creative breaks,
     * server breaks with drops, a Q-throw, a pickup). Every pop hands off, every burst opens, nothing is left hidden,
     * and our hooks cost ≤ 0.25 ms per frame on average. Frames go to out/frames/actions100 for `wb img flicker`.
     */
    private static void actions100(Script s) {
        int gap = 4;
        int[] base = new int[12];
        int[] seen = new int[4]; // placed, expected pops, breaks done, server breaks with a drop
        ArrayDeque<BlockPos> placed = new ArrayDeque<>();
        BlockPos[] just = new BlockPos[1];
        s.server(srv -> {
            volumeSetup(srv);
            cmd(srv, "gamerule send_command_feedback false");
        }).waitTicks(20).run(mc -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            base[0] = Pops.started;
            base[1] = Pops.handedOff;
            base[2] = Pops.watchdogs;
            base[3] = Pops.entityPops;
            base[4] = Bursts.started;
            base[5] = Bursts.claimed;
            base[6] = Bursts.revealed;
            base[7] = Bursts.watchdogs;
            base[8] = Ghosts.pickups;
            base[9] = ItemFx.thrown;
            base[10] = Pops.overCap;
            base[11] = Bursts.overBudget;
            SelfTest.costReset();
            SelfTest.startCapture("actions100", Integer.getInteger("snappy_blocks.selftest.capture", 100 * gap * 3 + 60));
        });
        for (int i = 0; i < 100; i++) {
            int k = i % 10, n = i;
            s.run(mc -> {
                just[0] = null;
                switch (k) {
                    case 0, 1, 2, 5, 8 -> {
                        BlockPos cell = freeCell(mc);
                        if (cell == null) return;
                        int slot = k == 5 ? 6 : k == 8 ? 7 : (n / 10 * 3 + k) % 6;
                        // the camera stays still (no aim) so `wb img flicker` sees only the effects move
                        mc.player.getInventory().setSelectedSlot(slot);
                        place(mc, cell.below(), Direction.UP);
                        placed.add(cell);
                        just[0] = cell;
                    }
                    case 3 -> {
                        BlockPos p = placed.poll();
                        if (p == null || mc.level.getBlockState(p).isAir()) return;
                        mc.gameMode.startDestroyBlock(p, Direction.UP);
                        seen[2]++;
                    }
                    case 4, 9 -> {
                        BlockPos p = placed.poll();
                        if (p == null || mc.level.getBlockState(p).isAir()) return;
                        say(mc, "setblock " + p.getX() + " " + p.getY() + " " + p.getZ() + " air destroy");
                        seen[2]++;
                        // glass drops nothing
                        if (!mc.level.getBlockState(p).is(Blocks.GLASS)) seen[3]++;
                    }
                    case 6 -> {
                        mc.player.getInventory().setSelectedSlot(8);
                        mc.gameMode.dropItem(mc.player, false);
                    }
                    case 7 -> say(mc, "summon item 0.5 " + Y + " -1.2 {Item:{id:\"minecraft:apple\",count:1},PickupDelay:5}");
                    default -> {
                    }
                }
            }).waitTicks(2).run(mc -> {
                // one tick later: what the placement left (the prediction is in by now)
                if (just[0] != null) {
                    BlockPos cell = just[0];
                    BlockState st = mc.level.getBlockState(cell);
                    if (st.isAir()) return;
                    seen[0]++;
                    if (Pops.wouldPop(cell, st)) {
                        seen[1]++;
                        if (!Pops.popping(cell)) SnappyBlocks.LOG.warn("[selftest] actions100 #{}: {} at {} placed but not popping", n, st, cell);
                    }
                }
            }).waitTicks(gap - 1);
        }
        s.waitTicks(40).run(mc -> {
            SelfTest.stopCapture();
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.FULL);
        }).check("actions100", mc -> {
            int started = Pops.started - base[0], handed = Pops.handedOff - base[1], dogs = Pops.watchdogs - base[2], entity = Pops.entityPops - base[3];
            int bursts = Bursts.started - base[4], claimed = Bursts.claimed - base[5], revealed = Bursts.revealed - base[6], bdogs = Bursts.watchdogs - base[7];
            String cost = SelfTest.cost();
            SnappyBlocks.LOG.info("[selftest] actions100: {} placed, {} should pop, {} popped ({} renderer), {} handed off, {} watchdog; {} breaks ({} server), {} bursts, {} claimed, {} revealed, {} watchdog; {} pickups, {} throws; {}",
                seen[0], seen[1], started, entity, handed, dogs, seen[2], seen[3], bursts, claimed, revealed, bdogs, Ghosts.pickups - base[8], ItemFx.thrown - base[9], cost);
            if (seen[0] < 40) return "only " + seen[0] + " of 50 placements landed";
            if (started != seen[1]) return "popped " + started + " of " + seen[1];
            if (handed != started - entity || dogs > 0) return "handed off " + handed + " of " + (started - entity) + ", " + dogs + " watchdog";
            if (bursts != seen[2]) return bursts + " bursts for " + seen[2] + " breaks";
            if (claimed < seen[3] || revealed != claimed || bdogs > 0) return claimed + " claimed for " + seen[3] + " server breaks with a drop, " + revealed + " revealed, " + bdogs + " watchdog";
            if (Pops.overCap > base[10] || Bursts.overBudget > base[11]) return "over the cap or budget";
            if (Pops.active() > 0 || !Hidden.isEmpty() || Bursts.hiddenDrops() > 0) return "left over: " + Pops.active() + " pops, " + Bursts.hiddenDrops() + " hidden drops";
            if (SelfTest.costSum / Math.max(1, SelfTest.costFrames) > 0.25) return cost + " (mean > 0.25)";
            return null;
        }).server(srv -> cmd(srv, "kill @e[type=item]"));
    }

    // ---------------------------------------------------------------------------------------------------------------

    /**
     * PRD §9 Veinminer and pillar 3's worst case: 64 ore blocks destroyed in one tick (a vein mod that uses destroy, as
     * `/fill destroy` does: 64 level events 2001) stays within the burst budget (8 full + 32 lite) and ≤ 2 ms in the
     * worst frame. With Veinminer installed, a real 64-block chain (its blocks go with plain setblock, no 2001) bursts
     * only the mined block.
     */
    private static void vein64(Script s) {
        int[] base = new int[4];
        String cube = "2 " + Y + " 2 5 " + (Y + 3) + " 5";
        s.server(srv -> {
            Scenarios.clearArea(srv);
            cmd(srv, "fill -6 " + Y + " -2 6 " + (Y + 6) + " 8 minecraft:air");
            cmd(srv, "tp @p 0.5 " + Y + " -2.0 -30 20");
            cmd(srv, "gamemode creative @p");
            cmd(srv, "fill " + cube + " minecraft:coal_ore");
        }).waitTicks(20).run(mc -> {
            base[0] = Bursts.started;
            base[1] = Bursts.lite;
            base[2] = Bursts.overBudget;
            base[3] = Bursts.claimed;
            SelfTest.costReset();
            SelfTest.startCapture("vein64", 120);
        }).server(srv -> cmd(srv, "fill " + cube + " minecraft:air destroy")).waitTicks(40).check("vein64_destroy", mc -> {
            SelfTest.stopCapture();
            int started = Bursts.started - base[0], lite = Bursts.lite - base[1], over = Bursts.overBudget - base[2], claimed = Bursts.claimed - base[3];
            String cost = SelfTest.cost();
            SnappyBlocks.LOG.info("[selftest] vein64 destroy: {} bursts ({} lite), {} over budget, {} claimed; {}", started, lite, over, claimed, cost);
            if (started != 40 || lite != 32 || over != 24) return started + " bursts (" + lite + " lite), " + over + " over budget; want 40 (32 lite), 24";
            if (SelfTest.costMax > 2) return cost + " (worst > 2 ms)";
            if (Bursts.hiddenDrops() > 0 || !Hidden.isEmpty()) return Bursts.hiddenDrops() + " drops still hidden";
            return null;
        }).server(srv -> cmd(srv, "kill @e[type=item]"));

        boolean vein = FabricLoader.getInstance().isModLoaded("vanilla_veinminer");
        BlockPos first = new BlockPos(2, Y, 2);
        s.server(srv -> {
            cmd(srv, "fill " + cube + " minecraft:coal_ore");
            cmd(srv, "gamemode survival @p");
            cmd(srv, "tp @p 0.5 " + Y + " 0.5 -30 20");
            cmd(srv, "clear @p");
            // Enchanted Veinminer (the user's profile has it) gates chains behind its enchantment
            String vm = FabricLoader.getInstance().isModLoaded("enchanted_veinminer") ? ",\"enchanted_veinminer:veinminer\":1" : "";
            cmd(srv, "item replace entity @p hotbar.0 with minecraft:netherite_pickaxe[enchantments={efficiency:5" + vm + "}]");
            cmd(srv, "scoreboard players set #require_sneak veinminer.config 0");
        }).waitTicks(20).run(mc -> {
            mc.player.getInventory().setSelectedSlot(0);
            base[0] = Bursts.started;
            SelfTest.costReset();
            SelfTest.startCapture("vein64_chain", 150);
        });
        Scenarios.mine(s, first, 100);
        s.until("chain to finish", 300, mc -> {
            if (!vein) return true;
            int left = 0;
            for (BlockPos p : BlockPos.betweenClosed(2, Y, 2, 5, Y + 3, 5)) if (!mc.level.getBlockState(p).isAir()) left++;
            return left <= 1;
        }).waitTicks(60).check("vein64_chain", mc -> {
            SelfTest.stopCapture();
            int left = 0;
            for (BlockPos p : BlockPos.betweenClosed(2, Y, 2, 5, Y + 3, 5)) if (!mc.level.getBlockState(p).isAir()) left++;
            int started = Bursts.started - base[0];
            String cost = SelfTest.cost();
            SnappyBlocks.LOG.info("[selftest] vein64 chain (Veinminer {}): {} of 64 left, {} bursts; {}", vein ? "installed" : "absent", left, started, cost);
            if (vein && left > 1) return left + " ores left: Veinminer didn't chain";
            if (!vein && left != 63) return left + " ores left without Veinminer (want 63)";
            if (started != 1) return started + " bursts (only the mined block should burst)";
            if (SelfTest.costMax > 2) return cost + " (worst > 2 ms)";
            return Pops.active() > 0 || !Hidden.isEmpty() || Bursts.hiddenDrops() > 0 ? "something left hidden" : null;
        }).server(srv -> {
            cmd(srv, "fill " + cube + " minecraft:air");
            cmd(srv, "kill @e[type=item]");
        });
    }

    // ---------------------------------------------------------------------------------------------------------------

    /**
     * PRD §9 Build Buddy: its auto-place calls MultiPlayerGameMode.useItemOn once per block at most 20 blocks a second
     * (Rate.MAX). 200 placements at that rate all pop and hand off, within the pop cap, at ≤ 0.25 ms per frame.
     */
    private static void autoplace(Script s) {
        int[] base = new int[5];
        int[] seen = new int[2];
        s.server(srv -> {
            volumeSetup(srv);
            cmd(srv, "gamerule send_command_feedback false");
        }).waitTicks(20).run(mc -> {
            base[0] = Pops.started;
            base[1] = Pops.handedOff;
            base[2] = Pops.watchdogs;
            base[3] = Pops.overCap;
            base[4] = Pops.entityPops;
            SelfTest.costReset();
            SelfTest.startCapture("autoplace", 120);
        });
        String[] blocks = {"stone", "oak_planks", "bricks", "oak_log"};
        for (int round = 0; round < 10; round++) {
            int r = round;
            for (int i = 0; i < 20; i++) {
                int n = i;
                BlockPos cell = new BlockPos(GX0 + n % 5, Y, GZ0 + n / 5);
                s.run(mc -> {
                    mc.player.getInventory().setSelectedSlot(n % 2 == 0 ? (r % 2 == 0 ? 0 : 1) : (r % 2 == 0 ? 3 : 4));
                    place(mc, cell.below(), Direction.UP);
                }).waitTicks(2).run(mc -> {
                    BlockState st = mc.level.getBlockState(cell);
                    if (st.isAir()) return;
                    seen[0]++;
                    if (Pops.wouldPop(cell, st)) seen[1]++;
                });
            }
            s.waitTicks(15).server(srv -> cmd(srv, "fill " + GX0 + " " + Y + " " + GZ0 + " " + GX1 + " " + Y + " " + GZ1 + " minecraft:air")).waitTicks(3);
        }
        s.waitTicks(10).check("autoplace200", mc -> {
            SelfTest.stopCapture();
            int started = Pops.started - base[0], handed = Pops.handedOff - base[1], dogs = Pops.watchdogs - base[2], over = Pops.overCap - base[3];
            String cost = SelfTest.cost();
            SnappyBlocks.LOG.info("[selftest] autoplace: {} placed at 20/s, {} should pop, {} popped, {} handed off, {} watchdog, {} over cap; {} (Build Buddy {})",
                seen[0], seen[1], started, handed, dogs, over, cost, FabricLoader.getInstance().isModLoaded("buildbuddy") ? "installed" : "absent");
            if (seen[0] < 195) return "only " + seen[0] + " of 200 placed";
            if (started != seen[1] || handed != started - (Pops.entityPops - base[4]) || dogs > 0 || over > 0)
                return started + " popped of " + seen[1] + ", " + handed + " handed off, " + dogs + " watchdog, " + over + " over cap";
            if (SelfTest.costSum / Math.max(1, SelfTest.costFrames) > 0.25 || SelfTest.costMax > 2) return cost;
            return Pops.active() > 0 || !Hidden.isEmpty() ? "pops left" : null;
        });
        if (FabricLoader.getInstance().isModLoaded("buildbuddy")) buildBuddy(s);
    }

    /** Build Buddy's own commands and settings, as its demo scenes use them (dev hooks of the installed jar). */
    static void bb(String line) {
        try {
            Class.forName("io.github.profetgit.buildbuddy.command.DevCommands").getMethod("run", String.class).invoke(null, line);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Build Buddy command " + line, e);
        }
    }

    static void bbRate(int perSecond) {
        try {
            Object data = Class.forName("io.github.profetgit.buildbuddy.ui.Settings").getMethod("get").invoke(null);
            data.getClass().getField("autoRate").setInt(data, perSecond);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Build Buddy rate", e);
        }
    }

    /** With Build Buddy installed: its auto-builder sweeps the sample house in at its top rate (20 blocks/s). */
    private static void buildBuddy(Script s) {
        int[] base = new int[5];
        int[] quiet = new int[2];
        s.server(srv -> {
            Scenarios.clearArea(srv);
            cmd(srv, "fill -12 " + Y + " -2 12 " + (Y + 10) + " 12 minecraft:air");
            cmd(srv, "tp @p 0.5 " + Y + " 0.5 0 20");
            cmd(srv, "gamemode creative @p");
            cmd(srv, "clear @p");
        }).waitTicks(20).run(mc -> {
            bbRate(20);
            bb("/buildbuddy sample");
        }).waitTicks(20).run(mc -> bb("/buildbuddy place -5 " + Y + " 3")).waitTicks(20).run(mc -> {
            base[0] = Pops.started;
            base[1] = Pops.handedOff;
            base[2] = Pops.watchdogs;
            base[3] = Pops.overCap;
            base[4] = Pops.entityPops;
            SelfTest.costReset();
            SelfTest.startCapture("autoplace_bb", 240);
            bb("/buildbuddy auto sweep");
        }).until("Build Buddy to finish", 2400, mc -> {
            // done when nothing new popped for 3 s
            if (Pops.started != quiet[0]) {
                quiet[0] = Pops.started;
                quiet[1] = 0;
            }
            return ++quiet[1] > 60;
        }).run(mc -> bb("/buildbuddy auto off")).waitTicks(10).check("autoplace_buildbuddy", mc -> {
            SelfTest.stopCapture();
            int started = Pops.started - base[0], handed = Pops.handedOff - base[1], dogs = Pops.watchdogs - base[2], over = Pops.overCap - base[3];
            int entity = Pops.entityPops - base[4];
            String cost = SelfTest.cost();
            SnappyBlocks.LOG.info("[selftest] Build Buddy auto sweep: {} pops ({} renderer), {} handed off, {} watchdog, {} over cap; {}", started, entity, handed, dogs, over, cost);
            if (started < 20) return "only " + started + " pops: Build Buddy didn't build";
            if (handed != started - entity || dogs > 0 || over > 0) return handed + " handed off of " + (started - entity) + ", " + dogs + " watchdog, " + over + " over cap";
            if (SelfTest.costSum / Math.max(1, SelfTest.costFrames) > 0.25 || SelfTest.costMax > 2) return cost;
            return Pops.active() > 0 || !Hidden.isEmpty() ? "pops left" : null;
        }).server(srv -> {
            cmd(srv, "fill -12 " + Y + " -2 12 " + (Y + 10) + " 12 minecraft:air");
            cmd(srv, "kill @e[type=!player]");
        });
    }

    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Pops hiding their blocks, a burst holding its drop, a pickup hiding its new slot: all at once, with long curves
     * (LONG). The stop has to come within the drop's 6-tick watchdog, so the caller stops the clock right after.
     */
    private static void hideAll(Script s) {
        s.server(srv -> {
            volumeSetup(srv);
            cmd(srv, "gamemode survival @p");
            cmd(srv, "clear @p");
            cmd(srv, "item replace entity @p hotbar.0 with minecraft:stone 16");
            cmd(srv, "setblock 2 " + Y + " 3 minecraft:dirt");
        }).waitTicks(20).run(mc -> {
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            mc.player.getInventory().setSelectedSlot(0);
            for (int x = -1; x <= 1; x++) place(mc, new BlockPos(x, Y - 1, 0), Direction.UP);
        }).server(srv -> {
            cmd(srv, "setblock 2 " + Y + " 3 minecraft:air destroy");
            cmd(srv, "summon item 0.5 " + Y + " -2.5 {Item:{id:\"minecraft:apple\",count:1},PickupDelay:0}");
        }).waitTicks(2).run(mc -> SnappyBlocks.LOG.info("[selftest] before the stop: hidden blocks {}, hidden drops {}, hidden slots {}",
            !Hidden.isEmpty(), Bursts.hiddenDrops(), Ghosts.hiddenNow()));
    }

    static final Map<String, Double> LONG = Map.of("pop.ticks", 40.0, "pickup.ticks", 40.0, "burst.hold", 20.0);

    /**
     * PRD §13 M4: /tick freeze and pause leave nothing hidden. Hidden blocks, drops and hotbar slots show as soon as the
     * clock stops, released by the stop itself (not by the curve's end or a watchdog).
     */
    private static void freeze(Script s) {
        int[] base = new int[3];
        Runnable snap = () -> {
            base[0] = Pops.stopReleases;
            base[1] = Bursts.stopReveals;
            base[2] = Ghosts.slotStopReveals;
        };
        tune(s, LONG);
        s.run(mc -> snap.run());
        hideAll(s);
        s.server(srv -> cmd(srv, "tick freeze")).until("clock to freeze", 40, mc -> Clock.frozen()).waitTicks(3)
            .check("freeze_nothing_hidden", mc -> stopped("frozen", base))
            .server(srv -> cmd(srv, "tick unfreeze")).waitTicks(60)
            .check("freeze_after", mc -> Pops.active() == 0 && Ghosts.active() == 0 ? null : Pops.active() + " pops, " + Ghosts.active() + " ghosts left after the unfreeze");
        s.run(mc -> snap.run());
        hideAll(s);
        s.run(mc -> mc.pauseGame(false)).until("game to pause", 40, Minecraft::isPaused).waitTicks(3)
            .check("pause_nothing_hidden", mc -> stopped("paused", base))
            .run(mc -> mc.gui.setScreen(null)).waitTicks(60)
            .check("pause_after", mc -> Pops.active() == 0 && Ghosts.active() == 0 ? null : Pops.active() + " pops, " + Ghosts.active() + " ghosts left after the pause");
        untune(s, LONG);
        s.server(srv -> cmd(srv, "kill @e[type=item]"));
    }

    private static String stopped(String how, int[] base) {
        int pops = Pops.stopReleases - base[0], drops = Bursts.stopReveals - base[1];
        String left = left();
        int slots = Ghosts.slotStopReveals - base[2];
        SnappyBlocks.LOG.info("[selftest] {}: released by the stop: {} pops, {} drops, {} slots", how, pops, drops, slots);
        if (left != null) return how + ": " + left;
        return pops > 0 && drops > 0 && slots > 0 ? null : how + ": released by the stop: " + pops + " pops, " + drops + " drops, " + slots + " slots (each should be > 0)";
    }

    /** What is still hidden, or null. The HUD's own slot draw shows a hidden slot, so it is asked here as the HUD would. */
    private static String left() {
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < 9; i++) Ghosts.hiddenSlot(mc.player.getInventory().getItem(i));
        if (!Hidden.isEmpty()) return "blocks still hidden";
        if (Bursts.hiddenDrops() > 0) return Bursts.hiddenDrops() + " drops still hidden";
        if (Ghosts.hiddenNow() > 0) return Ghosts.hiddenNow() + " hotbar slots still hidden";
        return null;
    }

    /** PRD §5 and M4: a dimension change clears every effect's state. */
    private static void dimension(Script s) {
        int[] pre = new int[1];
        tune(s, LONG);
        hideAll(s);
        s.run(mc -> pre[0] = Pops.active() + Bursts.active() + Ghosts.active()).server(srv -> {
            cmd(srv, "gamemode creative @p");
            cmd(srv, "execute in minecraft:the_nether run tp @p 0.5 100 0.5");
        }).until("the nether", 600, mc -> mc.level != null && mc.level.dimension() == Level.NETHER && mc.gui.screen() == null)
            .check("dimension_clears", mc -> {
                if (pre[0] == 0) return "setup: nothing was running before the change";
                if (Pops.active() + Bursts.active() + Pulses.active() + Ghosts.active() > 0)
                    return Pops.active() + " pops, " + Bursts.active() + " bursts, " + Pulses.active() + " pulses, " + Ghosts.active() + " ghosts after the change";
                return !Hidden.isEmpty() || Bursts.hiddenDrops() > 0 || Ghosts.hiddenNow() > 0 ? "something still hidden" : null;
            }).server(srv -> cmd(srv, "execute in minecraft:overworld run tp @p 0.5 " + Y + " -2.5 0 30"))
            .until("the overworld", 600, mc -> mc.level != null && mc.level.dimension() == Level.OVERWORLD && mc.gui.screen() == null
                && mc.level.hasChunkAt(new BlockPos(0, Y, 0)))
            .waitTicks(40);
        untune(s, LONG);
        s.server(srv -> cmd(srv, "kill @e[type=item]"));
    }

    /**
     * PRD §9 Flashback: a replay delivers the recorded player's placement as a block update followed by its place sound
     * in the same tick (decoded from cy_auto_build: 59 of 59). Client-only blocks stand in for the packets: the update
     * then the sound pops; a sound one frame late, or none, leaves the block alone (it was already drawn whole).
     */
    private static void replay(Script s) {
        BlockPos a = new BlockPos(-2, Y, 1), b = new BlockPos(0, Y, 1), c = new BlockPos(2, Y, 1);
        BlockState stone = Blocks.STONE.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        int[] base = new int[3];
        int[] frame = new int[1];
        s.server(ScaleScenes::volumeSetup).waitTicks(20).run(mc -> {
            base[0] = Pops.started;
            base[1] = Pops.remote;
            base[2] = Pops.lateSounds;
            mc.level.setServerVerifiedBlockState(a, stone, 19);
            placeSound(mc, a, stone);
            mc.level.setServerVerifiedBlockState(b, stone, 19);
            mc.level.setServerVerifiedBlockState(c, stone, 19);
            frame[0] = SelfTest.frames;
        }).until("a frame", 40, mc -> SelfTest.frames > frame[0]).run(mc -> placeSound(mc, b, stone)).waitTicks(30)
            .check("replay_late_sound", mc -> {
                int started = Pops.started - base[0], remote = Pops.remote - base[1], late = Pops.lateSounds - base[2];
                SnappyBlocks.LOG.info("[selftest] replay: {} popped ({} remote, {} on a late sound)", started, remote, late);
                if (started != 1 || remote != 1 || late != 1) return started + " popped, " + remote + " remote, " + late + " late (want 1, 1, 1)";
                return Pops.active() == 0 && Hidden.isEmpty() ? null : "still popping or hidden after 30 ticks";
            }).run(mc -> {
                for (BlockPos p : List.of(a, b, c)) mc.level.setServerVerifiedBlockState(p, air, 19);
            });
    }

    private static void placeSound(Minecraft mc, BlockPos pos, BlockState state) {
        mc.level.playSeededSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, net.minecraft.core.Holder.direct(state.getSoundType().getPlaceSound()),
            net.minecraft.sounds.SoundSource.BLOCKS, 1f, 0.8f, 0L);
    }

    // ---------------------------------------------------------------------------------------------------------------

    /**
     * PRD §9 Continuity: a placed block is drawn by its copy, which may not take connected textures, for no longer than
     * the pop and its hand-off; a burst never hides anything. Run with the profile addon (Continuity + Connected Rocks).
     */
    private static void ctm(Script s) {
        int[] base = new int[4];
        int[] hiddenTicks = new int[1];
        s.server(srv -> {
            volumeSetup(srv);
            cmd(srv, "fill -2 " + Y + " 2 2 " + (Y + 2) + " 2 minecraft:stone");
            cmd(srv, "tp @p 0.5 " + Y + " -2.0 0 10");
        }).waitTicks(20).run(mc -> {
            mc.player.getInventory().setSelectedSlot(0);
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            base[0] = Pops.started;
            base[1] = Pops.handedOff;
            base[2] = Pops.watchdogs;
            Pops.maxLife = 0;
            SelfTest.startCapture("ctm", 150);
        });
        for (int x = -2; x <= 2; x++) {
            BlockPos against = new BlockPos(x, Y + 1, 2);
            s.run(mc -> place(mc, against, Direction.NORTH)).waitTicks(7);
        }
        s.waitTicks(15).check("ctm_pop", mc -> {
            int started = Pops.started - base[0], handed = Pops.handedOff - base[1], dogs = Pops.watchdogs - base[2];
            // the pop's curve plus its hand-off to the remeshed section (a few frames; more under load)
            double limit = io.github.profetgit.snappyblocks.Tune.get(io.github.profetgit.snappyblocks.Tune.POP_TICKS) + 1.5;
            SnappyBlocks.LOG.info("[selftest] ctm: {} popped, {} handed off, longest copy {} ticks (Continuity {})", started, handed,
                String.format("%.2f", Pops.maxLife), FabricLoader.getInstance().isModLoaded("continuity") ? "installed" : "absent");
            if (started != 5 || handed != 5 || dogs > 0) return started + " popped, " + handed + " handed off, " + dogs + " watchdog";
            return Pops.maxLife <= limit ? null : String.format("a copy stood in for %.2f ticks (> %.1f)", Pops.maxLife, limit);
        }).run(mc -> base[3] = Bursts.started);
        for (int x = -2; x <= 2; x++) {
            int bx = x;
            s.server(srv -> cmd(srv, "setblock " + bx + " " + (Y + 1) + " 1 minecraft:air destroy"));
            sample(s, 6, mc -> {
                if (!Hidden.isEmpty()) hiddenTicks[0]++;
            });
        }
        s.waitTicks(20).check("ctm_burst", mc -> {
            SelfTest.stopCapture();
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            int bursts = Bursts.started - base[3];
            if (bursts != 5) return bursts + " bursts of 5";
            return hiddenTicks[0] == 0 ? null : "a block was hidden for " + hiddenTicks[0] + " ticks while bursting";
        }).server(srv -> cmd(srv, "kill @e[type=item]"));
    }

    // ---------------------------------------------------------------------------------------------------------------

    /**
     * PRD §9 Timber + Seamless Displays: the chopped log bursts; the rest of the tree goes with plain setblock (no 2001),
     * so it doesn't. Without Timber only the chopped log goes.
     */
    private static void timber(Script s) {
        boolean timber = FabricLoader.getInstance().isModLoaded("vanilla_timber");
        BlockPos base = new BlockPos(0, Y, 3);
        int[] b = new int[2];
        s.server(srv -> {
            Scenarios.clearArea(srv);
            cmd(srv, "fill -4 " + Y + " -2 4 " + (Y + 8) + " 8 minecraft:air");
            cmd(srv, "setblock 0 " + (Y - 1) + " 3 minecraft:grass_block");
            cmd(srv, "fill -2 " + (Y + 3) + " 1 2 " + (Y + 4) + " 5 minecraft:oak_leaves[persistent=false,distance=1]");
            cmd(srv, "fill -1 " + (Y + 5) + " 2 1 " + (Y + 5) + " 4 minecraft:oak_leaves[persistent=false,distance=1]");
            cmd(srv, "fill 0 " + Y + " 3 0 " + (Y + 4) + " 3 minecraft:oak_log");
            cmd(srv, "tp @p 0.5 " + Y + " 0.5 0 20");
            cmd(srv, "gamemode survival @p");
            cmd(srv, "clear @p");
            // Enchanted Timber gates felling behind its enchantment
            cmd(srv, "item replace entity @p hotbar.0 with minecraft:diamond_axe"
                + (FabricLoader.getInstance().isModLoaded("enchanted_timber") ? "[enchantments={\"enchanted_timber:timber\":1}]" : ""));
        }).waitTicks(20).run(mc -> {
            mc.player.getInventory().setSelectedSlot(0);
            b[0] = Bursts.started;
            b[1] = Pops.started;
            SelfTest.startCapture("timber", 200);
        });
        Scenarios.mine(s, base, 200);
        s.until("the tree to fall", 400, mc -> {
            if (!timber) return true;
            for (int y = 1; y <= 4; y++) if (mc.level.getBlockState(base.above(y)).is(Blocks.OAK_LOG)) return false;
            return true;
        }).waitTicks(100).check("timber_no_burst", mc -> {
            SelfTest.stopCapture();
            int bursts = Bursts.started - b[0], pops = Pops.started - b[1];
            int logs = 0;
            for (int y = 1; y <= 4; y++) if (mc.level.getBlockState(base.above(y)).is(Blocks.OAK_LOG)) logs++;
            SnappyBlocks.LOG.info("[selftest] timber (Timber {}, Seamless Displays {}): {} bursts, {} pops, {} logs left", timber ? "installed" : "absent",
                FabricLoader.getInstance().isModLoaded("seamless_displays") ? "installed" : "absent", bursts, pops, logs);
            if (timber && logs > 0) return logs + " logs left: Timber didn't fell the tree";
            if (!timber && logs != 4) return logs + " logs left without Timber";
            if (bursts != 1 || pops != 0) return bursts + " bursts, " + pops + " pops (only the chopped log bursts)";
            return !Hidden.isEmpty() || Bursts.hiddenDrops() > 0 ? "something left hidden" : null;
        }).server(srv -> {
            cmd(srv, "fill -4 " + Y + " -2 4 " + (Y + 8) + " 8 minecraft:air");
            cmd(srv, "setblock 0 " + (Y - 1) + " 3 minecraft:stone");
            cmd(srv, "kill @e[type=!player]");
        });
    }

    /**
     * PRD §9 Having a Blast: an explosion isn't ours (no 2001, so no bursts, no claims); the drops Having a Blast hides
     * are left alone (no ghosts for them when they go); its repair places blocks without place sounds, so no pops.
     */
    private static void blast(Script s) {
        boolean hab = FabricLoader.getInstance().isModLoaded("havingablast");
        int[] b = new int[4];
        String house = "-2 " + Y + " 8 2 " + (Y + 2) + " 10";
        // first without Having a Blast's repair (with it on, the blast leaves no drops to hide)
        s.server(srv -> {
            Scenarios.clearArea(srv);
            cmd(srv, "fill " + house + " minecraft:cobblestone");
            cmd(srv, "fill -1 " + Y + " 9 1 " + (Y + 1) + " 9 minecraft:dirt");
            cmd(srv, "tp @p 0.5 " + Y + " -2.0 0 15");
            cmd(srv, "gamemode creative @p");
            if (hab) cmd(srv, "havingablast repair tnt false");
        }).waitTicks(20).run(mc -> {
            b[0] = Bursts.started;
            b[1] = Bursts.claimed;
            b[3] = Compat.blastSkips;
            SelfTest.startCapture("blast", 120);
        }).server(srv -> cmd(srv, "summon tnt 0.5 " + (Y + 3) + " 9.5 {fuse:1}")).waitTicks(6)
            .server(srv -> cmd(srv, "kill @e[type=item]")).waitTicks(2).check("blast_not_ours", mc -> {
                SelfTest.stopCapture();
                int bursts = Bursts.started - b[0], claims = Bursts.claimed - b[1], skips = Compat.blastSkips - b[3];
                SnappyBlocks.LOG.info("[selftest] blast (Having a Blast {}): {} bursts, {} claims, {} ghosts, {} hidden drops left alone", hab ? "installed" : "absent",
                    bursts, claims, Ghosts.active(), skips);
                if (bursts != 0 || claims != 0) return bursts + " bursts, " + claims + " claims from an explosion";
                return hab && skips == 0 ? "no drop Having a Blast hid was left alone (none hidden, or we didn't ask)" : null;
            });
        // then with its repair: the rebuilt blocks arrive without place sounds, so nothing pops
        s.server(srv -> {
            Scenarios.clearArea(srv);
            cmd(srv, "fill " + house + " minecraft:cobblestone");
            if (hab) {
                cmd(srv, "havingablast repair tnt true");
                cmd(srv, "havingablast repair delay 3");
            }
        }).waitTicks(20).run(mc -> b[2] = Pops.started)
            .server(srv -> cmd(srv, "summon tnt 0.5 " + (Y + 3) + " 9.5 {fuse:1}")).waitTicks(hab ? 160 : 10).check("blast_repair_not_ours", mc -> {
                int pops = Pops.started - b[2];
                int back = 0;
                for (BlockPos p : BlockPos.betweenClosed(-2, Y, 8, 2, Y + 2, 10)) if (!mc.level.getBlockState(p).isAir()) back++;
                SnappyBlocks.LOG.info("[selftest] blast repair: {} of 45 blocks standing, {} pops", back, pops);
                if (hab && back < 45) return "Having a Blast repaired only " + back + " of 45";
                return pops == 0 ? null : pops + " pops during the repair";
            }).server(srv -> {
                if (hab) cmd(srv, "havingablast repair tnt false");
                Scenarios.clearArea(srv);
            });
    }

    /**
     * PRD §9 Harvest Sweep: crop breaks (#crops) are left to it; its harvests use setblock (no 2001), so they never
     * burst. Without it a crop broken by hand bursts like any block.
     */
    private static void sweep(Script s) {
        boolean hs = Compat.harvestSweep;
        int[] b = new int[1];
        s.server(srv -> {
            Scenarios.clearArea(srv);
            cmd(srv, "fill -2 " + (Y - 1) + " 2 2 " + (Y - 1) + " 2 minecraft:farmland");
            cmd(srv, "fill -2 " + Y + " 2 2 " + Y + " 2 minecraft:wheat[age=7]");
            cmd(srv, "tp @p 0.5 " + Y + " 0.0 0 40");
            cmd(srv, "gamemode survival @p");
            cmd(srv, "clear @p");
            cmd(srv, "item replace entity @p hotbar.0 with minecraft:iron_hoe");
        }).waitTicks(20).run(mc -> {
            mc.player.getInventory().setSelectedSlot(0);
            b[0] = Bursts.started;
        });
        Scenarios.mine(s, new BlockPos(-2, Y, 2), 40);
        s.waitTicks(10).check("sweep_hand_break", mc -> {
            int bursts = Bursts.started - b[0];
            SnappyBlocks.LOG.info("[selftest] sweep (Harvest Sweep {}): crop broken by hand, {} bursts", hs ? "installed" : "absent", bursts);
            return bursts == (hs ? 0 : 1) ? null : bursts + " bursts for a crop broken by hand";
        }).run(mc -> {
            b[0] = Bursts.started;
            Scenarios.aim(mc, new BlockPos(0, Y, 2));
            place(mc, new BlockPos(0, Y, 2), Direction.UP);
        }).waitTicks(40).check("sweep_harvest", mc -> {
            int bursts = Bursts.started - b[0];
            BlockState mid = mc.level.getBlockState(new BlockPos(0, Y, 2));
            boolean harvested = !mid.is(Blocks.WHEAT) || mid.getValue(net.minecraft.world.level.block.CropBlock.AGE) < 7;
            SnappyBlocks.LOG.info("[selftest] sweep harvest: harvested {}, {} bursts", harvested, bursts);
            if (hs && !harvested) return "Harvest Sweep didn't harvest";
            return bursts == 0 ? null : bursts + " bursts from a hoe harvest";
        }).server(srv -> {
            Scenarios.clearArea(srv);
            cmd(srv, "fill -2 " + (Y - 1) + " 2 2 " + (Y - 1) + " 2 minecraft:stone");
        });
    }

    /**
     * PRD §9 stand-asides: with a mod that does our job installed (the `aside` addon loads stand-ins with their ids, and
     * one known only by its name), that feature stays vanilla. Without them nothing stands aside, which also checks the
     * user's whole mod list (addon `all`) for false matches.
     */
    private static void aside(Script s) {
        List<String> stubs = List.of("a_good_place", "itemphysiclite", "tiny_pickup_animation", "snappy_stub_block_motion");
        boolean stubbed = Compat.found.containsAll(stubs);
        int[] b = new int[4];
        s.check("aside_detect", mc -> {
            SnappyBlocks.LOG.info("[selftest] aside: found {}, place {}, landing {}, pickup {}", Compat.found, Compat.placeAside, Compat.landAside, Compat.pickupAside);
            if (stubbed) return Compat.placeAside && Compat.landAside && Compat.pickupAside ? null : "stand-ins loaded but a feature didn't stand aside";
            for (String id : stubs) if (Compat.found.contains(id)) return "only some stand-ins found: " + Compat.found;
            return !Compat.placeAside && !Compat.landAside && !Compat.pickupAside ? null : "something stands aside with no such mod: " + Compat.found;
        });
        s.server(srv -> {
            Scenarios.clearArea(srv);
            cmd(srv, "tp @p 0.5 " + Y + " -2.0 0 30");
            cmd(srv, "gamemode survival @p");
            cmd(srv, "clear @p");
            cmd(srv, "item replace entity @p hotbar.0 with minecraft:stone 8");
            cmd(srv, "setblock 1 " + Y + " 1 minecraft:stone");
        }).waitTicks(20).run(mc -> {
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            mc.player.getInventory().setSelectedSlot(0);
            b[0] = Pops.started;
            b[1] = Pulses.started;
            b[2] = ItemFx.landings;
            b[3] = Ghosts.slotFlights + Ghosts.pickups;
            place(mc, new BlockPos(-1, Y - 1, 0), Direction.UP);
        }).server(srv -> {
            cmd(srv, Scenarios.item(2.5, Y + 3.5, 2.5, "bone", 1, ""));
            cmd(srv, "summon item 0.5 " + Y + " -1.2 {Item:{id:\"minecraft:apple\",count:1},PickupDelay:10}");
        }).waitTicks(4);
        Scenarios.mine(s, new BlockPos(1, Y, 1), 200);
        s.waitTicks(40).check("aside_behaviour", mc -> {
            int pops = Pops.started - b[0], pulses = Pulses.started - b[1], landings = ItemFx.landings - b[2], pickups = Ghosts.slotFlights + Ghosts.pickups - b[3];
            SnappyBlocks.LOG.info("[selftest] aside behaviour: {} pops, {} pulses, {} landings, {} pickup flights", pops, pulses, landings, pickups);
            boolean on = !stubbed;
            if ((pops > 0) != on || (pulses > 0) != on || (landings > 0) != on || (pickups > 0) != on)
                return pops + " pops, " + pulses + " pulses, " + landings + " landings, " + pickups + " pickup flights (stand-ins " + (stubbed ? "loaded" : "absent") + ")";
            return null;
        }).server(srv -> cmd(srv, "kill @e[type=item]"));
    }

    /**
     * PRD §5.6: every toggle turns off exactly its own effect, `enabled` turns off all of them, Calm drops the hit-stop
     * and scales the amplitude, and the file is re-read when it changes (intensity clamped to 50-150).
     */
    private static void config(Script s) {
        BlockPos cell = new BlockPos(0, Y, 0);
        int[] base = new int[4];
        String[] saved = new String[1];
        java.nio.file.Path file = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve(SnappyBlocks.ID + ".json");
        s.server(srv -> {
            volumeSetup(srv);
            cmd(srv, "gamerule send_command_feedback false");
        }).waitTicks(20).run(mc -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            say(mc, "kill @e[type=item]");
        });
        record Trial(String name, java.util.function.Consumer<Config> set, boolean pop, boolean burst, boolean thrown, boolean pickup) {}
        List<Trial> trials = List.of(
            new Trial("all_on", c -> {}, true, true, true, true),
            new Trial("enabled_off", c -> c.enabled = false, false, false, false, false),
            new Trial("pop_off", c -> c.pop = false, false, true, true, true),
            new Trial("burst_off", c -> c.burst = false, true, false, true, true),
            new Trial("items_off", c -> c.items = false, true, true, false, false),
            new Trial("pickup_off", c -> c.pickup = false, true, true, true, false),
            new Trial("calm", c -> c.calm = true, true, true, true, true));
        for (Trial t : trials) {
            s.run(mc -> {
                Config c = Config.get();
                c.enabled = c.pop = c.pulse = c.burst = c.items = c.pickup = true;
                c.calm = false;
                c.intensity = 100;
                t.set().accept(c);
                base[0] = Pops.started;
                base[1] = Bursts.started;
                base[2] = ItemFx.thrown;
                base[3] = Ghosts.pickups;
                mc.player.getInventory().setSelectedSlot(0);
                place(mc, cell.below(), Direction.UP);
            }).waitTicks(8).run(mc -> {
                if (!mc.level.getBlockState(cell).isAir()) mc.gameMode.startDestroyBlock(cell, Direction.UP);
            }).waitTicks(10).run(mc -> {
                mc.player.getInventory().setSelectedSlot(8);
                mc.gameMode.dropItem(mc.player, false);
                say(mc, "summon item 0.5 " + Y + " -1.2 {Item:{id:\"minecraft:apple\",count:1},PickupDelay:5}");
            }).waitTicks(40).check("config_" + t.name(), mc -> {
                boolean[] got = {Pops.started > base[0], Bursts.started > base[1], ItemFx.thrown > base[2], Ghosts.pickups > base[3]};
                boolean[] want = {t.pop(), t.burst(), t.thrown(), t.pickup()};
                String[] what = {"pops", "bursts", "throws", "pickups"};
                for (int i = 0; i < 4; i++) {
                    if (got[i] != want[i]) return what[i] + (got[i] ? " ran" : " did not run") + " (expected " + (want[i] ? "yes" : "no") + ")";
                }
                if (t.name().equals("calm")) {
                    double amp = Config.get().amplitude();
                    if (Math.abs(amp - 0.3) > 1e-9) return "calm amplitude " + amp;
                    if (Bursts.hold(false) != 0) return "calm hit-stop " + Bursts.hold(false);
                } else if (Bursts.hold(false) <= 0) {
                    return "hit-stop missing without calm";
                }
                return null;
            }).run(mc -> say(mc, "kill @e[type=item]")).waitTicks(4);
        }
        s.run(mc -> {
            Config c = Config.get();
            c.enabled = c.pop = c.pulse = c.burst = c.items = c.pickup = true;
            c.calm = false;
            try {
                saved[0] = java.nio.file.Files.readString(file);
                java.nio.file.Files.writeString(file, "{\"calm\": true, \"intensity\": 400, \"pop\": false, \"mine\": 1}");
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }).waitTicks(100).check("config_reload", mc -> {
            Config c = Config.get();
            if (!c.calm || c.pop) return "file change not read: calm=" + c.calm + " pop=" + c.pop;
            if (c.intensity != 150) return "intensity " + c.intensity + " (expected 150, clamped)";
            if (!c.burst || c.tune.get("pop.ticks") == null) return "missing keys did not get defaults";
            try {
                if (!java.nio.file.Files.readString(file).contains("\"mine\"")) return "unknown key dropped from the file";
                java.nio.file.Files.writeString(file, saved[0]);
            } catch (java.io.IOException e) {
                return e.toString();
            }
            return null;
        }).waitTicks(100).check("config_restored", mc -> Config.get().calm || !Config.get().pop || Config.get().intensity != 100
            ? "restored file not read" : null);
    }
}
