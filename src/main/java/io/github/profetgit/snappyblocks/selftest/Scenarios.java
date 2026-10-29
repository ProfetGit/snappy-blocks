package io.github.profetgit.snappyblocks.selftest;

import io.github.profetgit.snappyblocks.Clock;
import io.github.profetgit.snappyblocks.Probe;
import io.github.profetgit.snappyblocks.SnappyBlocks;
import io.github.profetgit.snappyblocks.burst.Bursts;
import io.github.profetgit.snappyblocks.pop.Hidden;
import io.github.profetgit.snappyblocks.burst.Chip;
import io.github.profetgit.snappyblocks.item.Ghosts;
import io.github.profetgit.snappyblocks.item.ItemFx;
import io.github.profetgit.snappyblocks.pop.Pops;
import io.github.profetgit.snappyblocks.pulse.Pulses;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** The test scenes. M0: the hooks see predicted placements, breaks, level events 2001/2020, and remote changes. */
final class Scenarios {
    static final int Y = 100;
    /** Where mp role "a" places a block that role "b" must see appear. */
    static final BlockPos MP_SPOT = new BlockPos(2, Y, 0);

    private Scenarios() {}

    static void cmd(MinecraftServer s, String c) {
        s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), c);
    }

    static void say(Minecraft mc, String command) {
        mc.player.connection.sendCommand(command);
    }

    static Script build() {
        Script s = new Script();
        s.run(mc -> mc.createWorldOpenFlows().createFreshLevel("sbtest-" + System.currentTimeMillis(),
                new LevelSettings("sbtest", GameType.SURVIVAL,
                    new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT),
                new WorldOptions(42L, false, false), WorldPresets::createTestWorldDimensions, mc.gui.screen()))
            .until("world to load", 2400, mc -> mc.player != null && mc.level != null && mc.gui.screen() == null)
            .waitTicks(40)
            .check("world loaded", mc -> mc.player.isAlive() ? null : "player not alive");
        s.server(srv -> {
            cmd(srv, "gamerule random_tick_speed 0");
            cmd(srv, "gamerule advance_time false");
            cmd(srv, "time set 6000");
            cmd(srv, "forceload add -16 -16 16 16");
        }).until("platform chunks", 1200, mc -> {
            ServerLevel l = mc.getSingleplayerServer().overworld();
            for (int[] c : new int[][] {{-14, -14}, {14, 14}, {0, 0}}) if (!l.hasChunkAt(new BlockPos(c[0], Y, c[1]))) return false;
            return true;
        }).server(srv -> platform(srv)).waitTicks(40);
        String only = System.getProperty("snappy_blocks.selftest.scenes", "all");
        for (String name : List.of("smoke", "pop", "burst", "pulse", "drops", "deny", "events", "bepop", "landing", "merge", "despawn", "farm1000", "pickup")) {
            if (!only.equals("all") && !Arrays.asList(only.split(",")).contains(name)) continue;
            switch (name) {
                case "smoke" -> smoke(s);
                case "pop" -> pop(s);
                case "burst" -> burst(s);
                case "pulse" -> pulse(s);
                case "drops" -> drops(s);
                case "deny" -> deny(s);
                case "events" -> events(s);
                case "bepop" -> bepop(s);
                case "landing" -> landing(s);
                case "merge" -> merge(s);
                case "despawn" -> despawn(s);
                case "farm1000" -> farm(s);
                case "pickup" -> pickup(s);
                default -> {
                }
            }
        }
        return s;
    }

    private static void platform(MinecraftServer srv) {
        cmd(srv, "fill -12 " + (Y - 1) + " -12 12 " + (Y - 1) + " 12 minecraft:stone");
        cmd(srv, "fill -12 " + Y + " -12 12 " + (Y + 6) + " 12 minecraft:air");
        cmd(srv, "tp @p 0.5 " + Y + " -2.5 0 30");
        cmd(srv, "gamemode survival @p");
        cmd(srv, "clear @p");
    }

    /** Predicted placement, a survival break with its 2020 hits and 2001, and the clock running. */
    private static void smoke(Script s) {
        BlockPos under = new BlockPos(0, Y - 1, 0), placeAt = under.above();
        // stone by hand: ~150 ticks of mining, so the server sends many 2019/2020 hits
        BlockPos dirt = new BlockPos(1, Y, 0);
        long[] clock = new long[1];
        s.server(srv -> {
            cmd(srv, "item replace entity @p weapon.mainhand with minecraft:stone 8");
            cmd(srv, "setblock 1 " + Y + " 0 minecraft:stone");
        }).waitTicks(10).run(mc -> {
            Probe.placed = Probe.placedPredicted = 0;
            clock[0] = Clock.ticks();
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(under).add(0, 0.5, 0), Direction.UP, under, false));
        }).waitTicks(10).check("place_predicted", mc -> {
            if (!mc.level.getBlockState(placeAt).is(Blocks.STONE)) return "no stone at " + placeAt + ": " + mc.level.getBlockState(placeAt);
            if (Probe.placedPredicted < 1 || !placeAt.equals(Probe.lastPlaced)) return "probe: " + Probe.summary() + " last " + Probe.lastPlaced;
            return null;
        }).check("clock_runs", mc -> Clock.ticks() > clock[0] ? null : "clock stuck at " + Clock.ticks());

        int[] t = {0};
        // the slot change goes to the server before mining starts; the button is held through vanilla's continueAttack
        s.run(mc -> mc.player.getInventory().setSelectedSlot(8)).waitTicks(4).run(mc -> {
            Probe.removed = Probe.removedPredicted = Probe.destroyEvents = Probe.hitEvents = 0;
            aim(mc, dirt);
            SelfTest.attack = true;
        }).until("stone to break", 400, mc -> {
            aim(mc, dirt);
            if (mc.level.getBlockState(dirt).isAir()) {
                SelfTest.attack = false;
                return true;
            }
            t[0]++;
            return false;
        }).waitTicks(10).check("break_predicted", mc -> Probe.removedPredicted >= 1 && dirt.equals(Probe.lastRemoved) ? null
            : "probe: " + Probe.summary() + " last " + Probe.lastRemoved)
            .check("event_2001", mc -> Probe.destroyEvents >= 1 && dirt.equals(Probe.lastDestroyEvent) ? null : "probe: " + Probe.summary())
            .check("event_2020", mc -> Probe.hitEvents >= 10 && dirt.equals(Probe.lastHitEvent) ? null
                : "probe: " + Probe.summary() + " after " + t[0] + " mining ticks")
            .run(mc -> SnappyBlocks.LOG.info("[selftest] smoke probe: {}, mined {} ticks", Probe.summary(), t[0]));
    }

    static void aim(Minecraft mc, BlockPos at) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 c = Vec3.atCenterOf(at);
        double dx = c.x - eye.x, dy = c.y - eye.y, dz = c.z - eye.z;
        mc.player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        mc.player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
    }

    record Place(BlockPos against, Direction face, String item) {
        BlockPos at() {
            return against.relative(face);
        }
    }

    static final String[] ITEMS = {"stone", "oak_planks", "glass", "dirt", "cobblestone", "oak_log", "bricks", "oak_leaves", "glowstone", "white_wool"};

    /** One round: a 5x3 floor layer, a layer on top, a side column on the east, torches on the west wall. */
    static List<Place> round(int seed) {
        List<Place> out = new ArrayList<>();
        int i = seed;
        for (int layer = 0; layer < 2; layer++) {
            for (int z = -1; z <= 1; z++) {
                for (int x = -2; x <= 2; x++) {
                    out.add(new Place(new BlockPos(x, Y - 1 + layer, z), Direction.UP, ITEMS[i++ % ITEMS.length]));
                }
            }
        }
        for (int y = 0; y < 2; y++) {
            for (int z = -1; z <= 1; z++) out.add(new Place(new BlockPos(2, Y + y, z), Direction.EAST, ITEMS[i++ % ITEMS.length]));
        }
        for (int z = -1; z <= 1; z++) out.add(new Place(new BlockPos(-2, Y + 1, z), Direction.WEST, "torch"));
        return out;
    }

    /**
     * PRD §13 M1: every placement pops, is hidden from the mesh while it grows, and hands back to the chunk mesh with no
     * watchdog. Frames of the first placements go to out/frames/pop for `wb img flicker` and a contact sheet.
     */
    private static void pop(Script s) {
        int rounds = Integer.getInteger("snappy_blocks.selftest.rounds", 2);
        int gap = Integer.getInteger("snappy_blocks.selftest.gap", 8);
        int[] base = new int[4];
        long[] ns = new long[1];
        int[] frames0 = new int[1];
        for (int r = 0; r < rounds; r++) {
            int round = r;
            List<Place> places = round(r * 3);
            s.server(srv -> {
                cmd(srv, "fill -4 " + Y + " -2 4 " + (Y + 3) + " 2 minecraft:air");
                cmd(srv, "tp @p 0.5 " + Y + " -3.5 0 42");
                cmd(srv, "gamemode creative @p");
                cmd(srv, "gamerule send_command_feedback false");
            }).waitTicks(20).run(mc -> {
                base[0] = Pops.started;
                base[1] = Pops.handedOff;
                base[2] = Pops.watchdogs;
                ns[0] = Pops.spentNanos;
                frames0[0] = SelfTest.frames;
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                mc.gui.hud.getChat().clearMessages(false);
                if (round == 0) SelfTest.startCapture("pop", Integer.getInteger("snappy_blocks.selftest.capture", 12 * gap * 3 + 30));
            });
            for (Place p : places) {
                s.server(srv -> cmd(srv, "item replace entity @p weapon.mainhand with minecraft:" + p.item() + " 64"))
                    .waitTicks(1)
                    .run(mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(p.against()).relative(p.face(), 0.5), p.face(), p.against(), false)))
                    .waitTicks(gap - 1);
            }
            s.waitTicks(30).run(mc -> {
                SelfTest.stopCapture();
                if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            }).check("pop_round" + r, mc -> {
                int started = Pops.started - base[0], handed = Pops.handedOff - base[1], dogs = Pops.watchdogs - base[2];
                int missing = 0, expected = 0;
                for (Place p : places) {
                    if (mc.level.getBlockState(p.at()).isAir()) missing++;
                    else if (Pops.eligible(mc.level.getBlockState(p.at()))) expected++;
                }
                double ms = (Pops.spentNanos - ns[0]) / 1e6 / Math.max(1, SelfTest.frames - frames0[0]);
                SnappyBlocks.LOG.info("[selftest] pop round {}: {} placed, {} eligible, {} popped, {} handed off, {} watchdog, {} missing, {} ms/frame",
                    round, places.size(), expected, started, handed, dogs, missing, String.format("%.4f", ms));
                if (missing > 0) return missing + " of " + places.size() + " blocks not placed";
                if (started != expected) return "popped " + started + " of " + expected + " eligible";
                if (handed != started || dogs > 0) return "handed off " + handed + " of " + started + ", " + dogs + " watchdog";
                if (Pops.active() > 0 || !Hidden.isEmpty()) return Pops.active() + " pops left, hidden set not empty";
                if (ms > 0.25) return String.format("%.3f ms/frame > 0.25", ms);
                return null;
            });
        }
    }

    /**
     * PRD §13 M1 break core: server-side breaks (2001 then the block update, as another player's break arrives) and a
     * predicted survival break each burst; every drop that spawns at a burst's centre is claimed, hidden, then revealed
     * by the burst (not by the watchdog). Frames go to out/frames/burst.
     */
    private static void burst(Script s) {
        String[] blocks = {"dirt", "stone", "oak_log", "glass", "oak_planks"};
        int drops = 4; // glass drops nothing
        int[] base = new int[4];
        s.server(srv -> {
            cmd(srv, "fill -4 " + Y + " -2 4 " + (Y + 3) + " 2 minecraft:air");
            cmd(srv, "kill @e[type=item]");
            cmd(srv, "tp @p 0.5 " + Y + " -3.0 0 45");
            cmd(srv, "gamemode survival @p");
            cmd(srv, "gamerule send_command_feedback false");
            for (int i = 0; i < blocks.length; i++) cmd(srv, "setblock " + (i - 2) + " " + Y + " 0 minecraft:" + blocks[i]);
        }).waitTicks(20).run(mc -> {
            base[0] = Bursts.started;
            base[1] = Bursts.claimed;
            base[2] = Bursts.revealed;
            base[3] = Bursts.watchdogs;
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            SelfTest.startCapture("burst", 240);
        });
        for (int i = 0; i < blocks.length; i++) {
            int x = i - 2;
            s.server(srv -> cmd(srv, "setblock " + x + " " + Y + " 0 minecraft:air destroy")).waitTicks(12);
        }
        s.waitTicks(30).check("burst_server", mc -> {
            int started = Bursts.started - base[0], claimed = Bursts.claimed - base[1], revealed = Bursts.revealed - base[2], dogs = Bursts.watchdogs - base[3];
            SnappyBlocks.LOG.info("[selftest] burst server: {} bursts, {} claimed, {} revealed, {} watchdog, {} live, {} hidden", started, claimed, revealed, dogs,
                Bursts.active(), Bursts.hiddenDrops());
            if (started != blocks.length) return "bursts " + started + " of " + blocks.length;
            if (claimed != drops) return "claimed " + claimed + " of " + drops + " drops";
            if (revealed != claimed || dogs > 0) return "revealed " + revealed + " of " + claimed + ", " + dogs + " by watchdog";
            if (Bursts.hiddenDrops() > 0) return Bursts.hiddenDrops() + " drops still hidden";
            if (Bursts.meshWaits > 0) return Bursts.meshWaits + " bursts started by the watchdog (no remesh within 150 ms)";
            return null;
        });
        BlockPos dirt = new BlockPos(0, Y, -1);
        s.server(srv -> {
            cmd(srv, "kill @e[type=item]");
            cmd(srv, "setblock 0 " + Y + " -1 minecraft:dirt");
        }).waitTicks(10).run(mc -> {
            mc.player.getInventory().setSelectedSlot(8);
            base[0] = Bursts.started;
            base[1] = Bursts.claimed;
            base[3] = Bursts.watchdogs;
        }).waitTicks(4).run(mc -> {
            aim(mc, dirt);
            SelfTest.attack = true;
        }).until("dirt to break", 200, mc -> {
            aim(mc, dirt);
            if (!mc.level.getBlockState(dirt).isAir()) return false;
            SelfTest.attack = false;
            return true;
        }).waitTicks(30).check("burst_predicted", mc -> {
            SelfTest.stopCapture();
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            int started = Bursts.started - base[0], claimed = Bursts.claimed - base[1], dogs = Bursts.watchdogs - base[3];
            if (started != 1) return "bursts " + started + " of 1";
            if (claimed != 1 || dogs > 0) return "claimed " + claimed + " of 1, " + dogs + " by watchdog";
            return null;
        });
    }

    /** Holds the attack button on {@code at} until it is gone (or {@code timeout} ticks pass). */
    static void mine(Script s, BlockPos at, int timeout) {
        s.run(mc -> {
            aim(mc, at);
            SelfTest.attack = true;
        }).until("mining " + at, timeout, mc -> {
            aim(mc, at);
            if (!mc.level.getBlockState(at).isAir()) return false;
            SelfTest.attack = false;
            return true;
        });
    }

    static void clearArea(MinecraftServer srv) {
        cmd(srv, "fill -6 " + Y + " -2 6 " + (Y + 3) + " 6 minecraft:air");
        cmd(srv, "kill @e[type=!player]");
        cmd(srv, "gamerule send_command_feedback false");
    }

    static String item(double x, double y, double z, String id, int count, String extra) {
        return "summon item " + x + " " + y + " " + z + " {Item:{id:\"minecraft:" + id + "\",count:" + count + "},PickupDelay:32767" + extra + "}";
    }

    /**
     * PRD §5.4 landing and Q-throw: single items dropped onto stone, wool and grass squash, hop and play the block's step
     * sound; one dropped into water and a crowd of 20 in one cell don't. A Q press throws from the hand.
     */
    private static void landing(Script s) {
        int[] base = new int[3];
        s.server(srv -> {
            clearArea(srv);
            cmd(srv, "tp @p 0.5 " + Y + " -2.0 0 30");
            cmd(srv, "gamemode survival @p");
            cmd(srv, "clear @p");
            cmd(srv, "setblock 2 " + (Y - 1) + " 2 minecraft:white_wool");
            cmd(srv, "setblock -2 " + (Y - 1) + " 2 minecraft:grass_block");
            cmd(srv, "setblock 4 " + (Y - 1) + " 4 minecraft:water");
        }).waitTicks(20).run(mc -> {
            base[0] = ItemFx.landings;
            base[1] = ItemFx.sounds;
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            SelfTest.startCapture("landing", 60);
        }).server(srv -> {
            cmd(srv, item(0.5, Y + 3.5, 2.5, "stone", 1, ""));
            cmd(srv, item(2.5, Y + 3.5, 2.5, "apple", 1, ""));
            cmd(srv, item(-1.5, Y + 3.5, 2.5, "bone", 1, ""));
            cmd(srv, item(4.5, Y + 3.5, 4.5, "stick", 1, ""));
            for (int i = 0; i < 20; i++) cmd(srv, item(-3.5 + i * 0.01, Y + 3.5, 5.5, "cobblestone", 64, ""));
        }).waitTicks(50).check("landing_hop", mc -> {
            SelfTest.stopCapture();
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            int landed = ItemFx.landings - base[0], sounds = ItemFx.sounds - base[1];
            SnappyBlocks.LOG.info("[selftest] landing: {} landings, {} sounds, {} tracked", landed, sounds, ItemFx.tracked);
            if (landed != 3) return landed + " landings (3 expected: stone, wool, grass; none in water or the crowd)";
            return sounds == 3 ? null : sounds + " landing sounds";
        }).server(srv -> {
            cmd(srv, "kill @e[type=item]");
            cmd(srv, "item replace entity @p hotbar.0 with minecraft:stone 8");
        }).waitTicks(10).run(mc -> {
            mc.player.getInventory().setSelectedSlot(0);
            base[2] = ItemFx.thrown;
            mc.gameMode.dropItem(mc.player, false);
        }).waitTicks(10).check("landing_throw", mc -> ItemFx.thrown - base[2] == 1 ? null : (ItemFx.thrown - base[2]) + " throws from the hand")
            .server(srv -> cmd(srv, "kill @e[type=item]"));
    }

    /** PRD §5.4 merge: two stacks of the same item side by side merge; the vanished one slides into the other. */
    private static void merge(Script s) {
        int[] base = new int[2];
        s.server(srv -> {
            clearArea(srv);
            cmd(srv, "tp @p 0.5 " + Y + " -2.0 0 30");
        }).waitTicks(20).run(mc -> {
            base[0] = Ghosts.merges;
            base[1] = Ghosts.despawns;
        }).server(srv -> {
            // an infinite pickup delay (32767) stops vanilla merging; 200 ticks keeps them out of our hands
            cmd(srv, "summon item 0.3 " + Y + " 1.5 {Item:{id:\"minecraft:stone\",count:3},PickupDelay:200}");
            cmd(srv, "summon item 0.7 " + Y + " 1.5 {Item:{id:\"minecraft:stone\",count:5},PickupDelay:200}");
        }).waitTicks(60).check("merge_slide", mc -> {
            int merged = Ghosts.merges - base[0], gone = Ghosts.despawns - base[1];
            long stacks = mc.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, mc.player.getBoundingBox().inflate(6)).size();
            if (stacks != 1) return stacks + " stacks left (the server didn't merge)";
            return merged == 1 && gone == 0 ? null : merged + " merge slides, " + gone + " despawn shrinks";
        }).server(srv -> cmd(srv, "kill @e[type=item]"));
    }

    /** PRD §5.4 removal without a take packet: an item at the end of its life shrinks out where it lay. */
    private static void despawn(Script s) {
        int[] base = new int[2];
        s.server(srv -> {
            clearArea(srv);
            cmd(srv, "tp @p 0.5 " + Y + " -2.0 0 30");
        }).waitTicks(20).run(mc -> {
            base[0] = Ghosts.merges;
            base[1] = Ghosts.despawns;
        }).server(srv -> cmd(srv, item(0.5, Y, 1.5, "apple", 1, ",Age:5980"))).waitTicks(50).check("despawn_shrink", mc -> {
            int merged = Ghosts.merges - base[0], gone = Ghosts.despawns - base[1];
            long left = mc.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, mc.player.getBoundingBox().inflate(6)).size();
            if (left != 0) return "the apple didn't despawn";
            if (Ghosts.active() != 0) return Ghosts.active() + " ghosts left";
            return gone == 1 && merged == 0 ? null : gone + " despawn shrinks, " + merged + " merge slides";
        });
    }

    /** PRD §5.4 scale: 1000 full stacks; effects run on the 64 nearest only, and /kill makes at most 64 ghosts. */
    private static void farm(Script s) {
        int[] base = new int[1];
        s.server(srv -> {
            clearArea(srv);
            cmd(srv, "tp @p 0.5 " + Y + " -2.0 0 30");
            for (int x = 0; x < 10; x++) for (int z = 0; z < 10; z++) for (int i = 0; i < 10; i++)
                cmd(srv, item(-4.5 + x + i * 0.05, Y + 0.5, 1.5 + z, "cobblestone", 64, ""));
        }).waitTicks(40).check("farm1000_cap", mc -> {
            long n = mc.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, mc.player.getBoundingBox().inflate(16)).size();
            SnappyBlocks.LOG.info("[selftest] farm: {} items, {} tracked", n, ItemFx.tracked);
            if (n < 1000) return "only " + n + " items";
            return ItemFx.tracked <= 64 ? null : ItemFx.tracked + " items tracked";
        }).run(mc -> base[0] = Ghosts.despawns).server(srv -> cmd(srv, "kill @e[type=item]")).waitTicks(1).check("farm1000_kill", mc -> {
            int ghosts = Ghosts.active();
            return ghosts > 0 && ghosts <= 64 ? null : ghosts + " ghosts after /kill";
        }).waitTicks(10).check("farm1000_clear", mc -> Ghosts.active() == 0 ? null : Ghosts.active() + " ghosts left");
    }

    /**
     * PRD §5.5 pickup: in first person with the HUD on, a pickup flies into the slot the server put it in (a partial
     * stack in hotbar slot 3 here), and the slot bumps when it arrives; into an empty slot the new stack shows only then.
     * In third person it arcs to the chest. Frames go to out/frames/pickup (HUD shown).
     */
    private static void pickup(Script s) {
        int[] base = new int[6];
        Runnable snap = () -> {
            base[0] = Ghosts.pickups;
            base[1] = Ghosts.slotFlights;
            base[2] = Ghosts.slotBumps;
            base[3] = Ghosts.hiddenSlots;
            base[4] = Ghosts.retargets;
        };
        s.server(srv -> {
            clearArea(srv);
            cmd(srv, "tp @p 0.5 " + Y + " -2.0 0 30");
            cmd(srv, "gamemode survival @p");
            cmd(srv, "clear @p");
            cmd(srv, "item replace entity @p hotbar.3 with minecraft:stone 10");
            cmd(srv, "item replace entity @p hotbar.0 with minecraft:stick");
        }).waitTicks(20).run(mc -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            mc.player.getInventory().setSelectedSlot(0);
            snap.run();
        }).server(srv -> {
            // in view ahead of the player, out of reach until the step forward
            cmd(srv, "tp @p 0.5 " + Y + " -2.0 0 55");
            cmd(srv, "summon item 0.5 " + Y + " 0.6 {Item:{id:\"minecraft:stone\",count:4},PickupDelay:0}");
        }).waitTicks(20).run(mc -> {
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            SelfTest.startCapture("pickup", 60);
        }).waitTicks(3)
            .server(srv -> cmd(srv, "tp @p 0.5 " + Y + " -0.5 0 55"))
            .waitTicks(40).check("pickup_slot", mc -> {
                SelfTest.stopCapture();
                mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.FULL);
                int picks = Ghosts.pickups - base[0], flights = Ghosts.slotFlights - base[1], bumps = Ghosts.slotBumps - base[2];
                SnappyBlocks.LOG.info("[selftest] pickup: {} pickups, {} slot flights to {}, {} bumps, {} retargets", picks, flights, Ghosts.lastSlot,
                    bumps, Ghosts.retargets - base[4]);
                if (mc.player.getInventory().getItem(3).getCount() != 14) return "slot 3 holds " + mc.player.getInventory().getItem(3);
                if (picks != 1 || flights != 1) return picks + " pickups, " + flights + " slot flights";
                if (Ghosts.lastSlot != 3) return "flew to slot " + Ghosts.lastSlot;
                return bumps == 1 ? null : bumps + " slot bumps";
            }).run(mc -> snap.run()).server(srv -> cmd(srv, "summon item 0.5 " + Y + " -1.4 {Item:{id:\"minecraft:apple\",count:1},PickupDelay:10}"))
            .waitTicks(40).check("pickup_empty_slot", mc -> {
                int flights = Ghosts.slotFlights - base[1], hidden = Ghosts.hiddenSlots - base[3], bumps = Ghosts.slotBumps - base[2];
                if (!mc.player.getInventory().getItem(1).is(net.minecraft.world.item.Items.APPLE)) return "apple went to " + mc.player.getInventory().findSlotMatchingItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE));
                if (Ghosts.hiddenSlot(mc.player.getInventory().getItem(1))) return "slot 1 still hidden";
                return flights == 1 && hidden == 1 && bumps == 1 && Ghosts.lastSlot == 1 ? null
                    : flights + " flights, " + hidden + " hidden slots, " + bumps + " bumps, slot " + Ghosts.lastSlot;
            }).run(mc -> {
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                snap.run();
            }).server(srv -> cmd(srv, "summon item 0.5 " + Y + " -1.4 {Item:{id:\"minecraft:stone\",count:1},PickupDelay:10}"))
            .waitTicks(40).check("pickup_third_person", mc -> {
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                int picks = Ghosts.pickups - base[0], flights = Ghosts.slotFlights - base[1];
                return picks == 1 && flights == 0 && Ghosts.active() == 0 ? null : picks + " pickups, " + flights + " slot flights, " + Ghosts.active() + " live";
            });
    }

    /**
     * PRD §5.2: mining stone by hand pulses on every hit (the first one locally, then the server's 2020s), and the pulse
     * ends when the block breaks. Frames go to out/frames/pulse.
     */
    private static void pulse(Script s) {
        BlockPos at = new BlockPos(0, Y, 0);
        int[] base = new int[2];
        s.server(srv -> {
            clearArea(srv);
            cmd(srv, "setblock 0 " + Y + " 0 minecraft:stone");
            cmd(srv, "tp @p 0.5 " + Y + " -2.0 0 30");
            cmd(srv, "gamemode survival @p");
            cmd(srv, "clear @p");
        }).waitTicks(20).run(mc -> {
            mc.player.getInventory().setSelectedSlot(8);
            base[0] = Pulses.started;
            base[1] = Probe.hitEvents;
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            SelfTest.startCapture("pulse", 120);
        }).waitTicks(4);
        mine(s, at, 400);
        s.waitTicks(10).check("pulse_hits", mc -> {
            SelfTest.stopCapture();
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            int pulses = Pulses.started - base[0], hits = Probe.hitEvents - base[1];
            SnappyBlocks.LOG.info("[selftest] pulse: {} pulses for {} server hits, {} live", pulses, hits, Pulses.active());
            if (hits < 10) return "only " + hits + " server hits";
            if (pulses < hits) return pulses + " pulses for " + hits + " hits";
            if (Pulses.active() > 0) return Pulses.active() + " pulses left after the break";
            return null;
        });
    }

    /**
     * PRD §5.3 drops and budget: silk touch (the block's own item), fortune (a different item), a chest (its contents
     * fan out, 8 at most), glass (no drop, glass chips), a creative break (no drop), and a 54-block /fill destroy that the
     * per-tick budget caps.
     */
    private static void drops(Script s) {
        int[] base = new int[6];
        Runnable snap = () -> {
            base[0] = Bursts.started;
            base[1] = Bursts.claimed;
            base[2] = Bursts.watchdogs;
            base[3] = Bursts.lite;
            base[4] = Bursts.overBudget;
        };
        s.server(srv -> {
            clearArea(srv);
            cmd(srv, "tp @p 0.5 " + Y + " -2.0 0 30");
            cmd(srv, "gamemode survival @p");
            cmd(srv, "clear @p");
            cmd(srv, "item replace entity @p hotbar.0 with minecraft:diamond_pickaxe[enchantments={silk_touch:1}]");
            cmd(srv, "item replace entity @p hotbar.1 with minecraft:diamond_pickaxe[enchantments={fortune:3}]");
            cmd(srv, "setblock -1 " + Y + " 0 minecraft:stone");
            cmd(srv, "setblock 1 " + Y + " 0 minecraft:diamond_ore");
        }).waitTicks(20).run(mc -> {
            mc.player.getInventory().setSelectedSlot(0);
            snap.run();
        }).waitTicks(4);
        mine(s, new BlockPos(-1, Y, 0), 200);
        s.waitTicks(20).check("drops_silk", mc -> {
            int bursts = Bursts.started - base[0], claimed = Bursts.claimed - base[1];
            long stone = mc.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, mc.player.getBoundingBox().inflate(6),
                e -> e.getItem().is(net.minecraft.world.item.Items.STONE)).size();
            if (bursts != 1 || claimed != 1) return bursts + " bursts, " + claimed + " claimed";
            return stone == 1 ? null : "silk touch dropped " + stone + " stone items";
        }).run(mc -> {
            mc.player.getInventory().setSelectedSlot(1);
            snap.run();
        }).waitTicks(4);
        mine(s, new BlockPos(1, Y, 0), 200);
        s.waitTicks(20).check("drops_fortune", mc -> {
            int bursts = Bursts.started - base[0], claimed = Bursts.claimed - base[1];
            return bursts == 1 && claimed == 1 ? null : bursts + " bursts, " + claimed + " claimed";
        });
        StringBuilder items = new StringBuilder();
        String[] kinds = {"apple", "stick", "dirt", "stone", "bone", "string", "feather", "coal", "flint", "clay_ball", "wheat", "bread"};
        for (int i = 0; i < kinds.length; i++) items.append(i == 0 ? "" : ",").append("{Slot:").append(i).append("b,id:\"minecraft:").append(kinds[i]).append("\",count:1}");
        s.server(srv -> {
            cmd(srv, "kill @e[type=item]");
            cmd(srv, "setblock 0 " + Y + " 1 minecraft:chest{Items:[" + items + "]}");
            cmd(srv, "setblock 2 " + Y + " 1 minecraft:glass");
        }).waitTicks(20).run(mc -> {
            snap.run();
            Bursts.lastFanSpread = 0;
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            SelfTest.startCapture("drops", 80);
        }).server(srv -> cmd(srv, "setblock 0 " + Y + " 1 minecraft:air destroy")).waitTicks(30).check("drops_chest", mc -> {
            int bursts = Bursts.started - base[0], claimed = Bursts.claimed - base[1], dogs = Bursts.watchdogs - base[2];
            SnappyBlocks.LOG.info("[selftest] chest: {} bursts, {} claimed, fan {} ticks, {} watchdog", bursts, claimed, Bursts.lastFanSpread, dogs);
            if (bursts != 1) return bursts + " bursts";
            if (claimed != 8) return "claimed " + claimed + " of 13 drops (8 expected)";
            if (Bursts.lastFanSpread < 3) return "fan spread " + Bursts.lastFanSpread + " ticks";
            return dogs == 0 ? null : dogs + " by watchdog";
        }).run(mc -> snap.run()).server(srv -> cmd(srv, "setblock 2 " + Y + " 1 minecraft:air destroy")).waitTicks(20).check("drops_glass", mc -> {
            SelfTest.stopCapture();
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            int bursts = Bursts.started - base[0], claimed = Bursts.claimed - base[1];
            if (bursts != 1 || claimed != 0) return bursts + " bursts, " + claimed + " claimed";
            return Bursts.lastMaterial == Chip.Material.GLASS ? null : "chips flew as " + Bursts.lastMaterial;
        });
        BlockPos dirt = new BlockPos(0, Y, -1);
        s.server(srv -> {
            cmd(srv, "kill @e[type=item]");
            cmd(srv, "gamemode creative @p");
            cmd(srv, "setblock 0 " + Y + " -1 minecraft:dirt");
        }).waitTicks(20).run(mc -> {
            snap.run();
            mc.gameMode.startDestroyBlock(dirt, Direction.UP);
        }).waitTicks(20).check("drops_creative", mc -> {
            int bursts = Bursts.started - base[0], claimed = Bursts.claimed - base[1];
            if (!mc.level.getBlockState(dirt).isAir()) return "dirt not broken";
            return bursts == 1 && claimed == 0 ? null : bursts + " bursts, " + claimed + " claimed";
        });
        s.server(srv -> {
            cmd(srv, "kill @e[type=item]");
            cmd(srv, "gamemode survival @p");
            cmd(srv, "fill -4 " + Y + " 3 4 " + (Y + 1) + " 5 minecraft:dirt");
        }).waitTicks(20).run(mc -> snap.run()).server(srv -> cmd(srv, "fill -4 " + Y + " 3 4 " + (Y + 1) + " 5 minecraft:air destroy")).waitTicks(30)
            .check("drops_budget", mc -> {
                int bursts = Bursts.started - base[0], lite = Bursts.lite - base[3], over = Bursts.overBudget - base[4];
                SnappyBlocks.LOG.info("[selftest] budget: 54 breaks -> {} full, {} lite, {} vanilla only", bursts - lite, lite, over);
                if (bursts + over != 54) return (bursts + over) + " of 54 breaks seen";
                if (bursts - lite > 16) return (bursts - lite) + " full bursts (budget 8 per tick)";
                return lite > 0 ? null : "no lite bursts";
            }).server(srv -> cmd(srv, "kill @e[type=item]"));
    }

    /**
     * PRD §5.1, §5.3 denial: a break the server refuses grows back, a placement the server refuses (outside its world
     * border) shrinks out. The break: the client is shown dirt where the server has bedrock, so the predicted break is
     * corrected at its ack (syncBlockState), the same path as a claim plugin's refusal.
     */
    private static void deny(Script s) {
        BlockPos fake = new BlockPos(0, Y, 0);
        int[] base = new int[4];
        s.server(srv -> {
            clearArea(srv);
            cmd(srv, "setblock 0 " + Y + " 0 minecraft:bedrock");
            cmd(srv, "tp @p 0.5 " + Y + " -2.0 0 30");
            cmd(srv, "gamemode survival @p");
            cmd(srv, "clear @p");
        }).waitTicks(20).run(mc -> {
            mc.player.getInventory().setSelectedSlot(8);
            mc.level.setBlock(fake, Blocks.DIRT.defaultBlockState(), 3);
            base[0] = Bursts.denied;
            base[1] = Pops.regrows;
        }).waitTicks(10);
        s.run(mc -> {
            aim(mc, fake);
            SelfTest.attack = true;
        }).until("fake dirt to break", 200, mc -> {
            aim(mc, fake);
            if (!mc.level.getBlockState(fake).isAir()) return false;
            SelfTest.attack = false;
            return true;
        }).until("the server to put it back", 100, mc -> !mc.level.getBlockState(fake).isAir()).waitTicks(20).check("deny_break", mc -> {
            int denied = Bursts.denied - base[0], regrows = Pops.regrows - base[1];
            SnappyBlocks.LOG.info("[selftest] deny break: {} denied, {} regrown, now {}", denied, regrows, mc.level.getBlockState(fake));
            if (denied != 1 || regrows != 1) return denied + " denied, " + regrows + " regrown";
            return Pops.active() == 0 && Hidden.isEmpty() ? null : "pop left hidden";
        });
        BlockPos against = new BlockPos(5, Y - 1, 0), at = against.above();
        s.server(srv -> {
            cmd(srv, "setblock 0 " + Y + " 0 minecraft:air");
            cmd(srv, "worldborder center 0 0");
            cmd(srv, "worldborder set 8");
            cmd(srv, "tp @p 3.5 " + Y + " 0.5 90 40");
            cmd(srv, "item replace entity @p weapon.mainhand with minecraft:oak_planks 8");
        }).waitTicks(20).run(mc -> {
            // the client's border stays wide, so it predicts the placement the server refuses
            mc.level.getWorldBorder().setSize(5.9999968E7);
            base[2] = Pops.denied;
            base[3] = Bursts.fades;
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(against).add(0, 0.5, 0), Direction.UP, against, false));
        }).waitTicks(20).check("deny_place", mc -> {
            int denied = Pops.denied - base[2], fades = Bursts.fades - base[3];
            if (!mc.level.getBlockState(at).isAir()) return "the planks stayed: " + mc.level.getBlockState(at);
            return denied == 1 && fades == 1 ? null : denied + " denied, " + fades + " faded";
        }).server(srv -> cmd(srv, "worldborder set 59999968"));
    }

    /**
     * PRD §5.3 what counts as a break: a sheep's grass-to-dirt (2001, then a change to dirt) never bursts; a golem built
     * from blocks bursts each block it takes, with nothing claimed; a torch a piston destroys bursts and its drop comes
     * out of it (the client clears the torch's spot before the piston head moves in).
     */
    private static void events(Script s) {
        int[] base = new int[2];
        BlockPos grass = new BlockPos(-3, Y - 1, 0);
        s.server(srv -> {
            clearArea(srv);
            cmd(srv, "tp @p 0.5 " + Y + " -3.0 0 30");
            cmd(srv, "gamemode survival @p");
            cmd(srv, "setblock -3 " + (Y - 1) + " 0 minecraft:grass_block");
        }).waitTicks(20).run(mc -> {
            base[0] = Bursts.started;
            base[1] = Bursts.claimed;
        }).server(srv -> {
            ServerLevel l = srv.overworld();
            l.levelEvent(2001, grass, net.minecraft.world.level.block.Block.getId(Blocks.GRASS_BLOCK.defaultBlockState()));
            l.setBlockAndUpdate(grass, Blocks.DIRT.defaultBlockState());
        }).waitTicks(10).check("events_sheep", mc -> Bursts.started == base[0] ? null : (Bursts.started - base[0]) + " bursts for grass to dirt");
        s.server(srv -> {
            cmd(srv, "setblock 0 " + Y + " 2 minecraft:iron_block");
            cmd(srv, "setblock 0 " + (Y + 1) + " 2 minecraft:iron_block");
            cmd(srv, "setblock -1 " + (Y + 1) + " 2 minecraft:iron_block");
            cmd(srv, "setblock 1 " + (Y + 1) + " 2 minecraft:iron_block");
        }).waitTicks(20).run(mc -> {
            base[0] = Bursts.started;
            base[1] = Bursts.claimed;
        }).server(srv -> cmd(srv, "setblock 0 " + (Y + 2) + " 2 minecraft:carved_pumpkin[facing=north]")).waitTicks(20).check("events_golem", mc -> {
            int bursts = Bursts.started - base[0], claimed = Bursts.claimed - base[1];
            if (!mc.level.getBlockState(new BlockPos(0, Y, 2)).isAir()) return "no golem built";
            return bursts == 4 && claimed == 0 ? null : bursts + " bursts, " + claimed + " claimed";
        }).server(srv -> {
            cmd(srv, "kill @e[type=iron_golem]");
            cmd(srv, "setblock 2 " + Y + " -1 minecraft:piston[facing=east]");
            cmd(srv, "setblock 3 " + Y + " -1 minecraft:torch");
        }).waitTicks(20).run(mc -> {
            base[0] = Bursts.started;
            base[1] = Bursts.claimed;
        }).server(srv -> cmd(srv, "setblock 1 " + Y + " -1 minecraft:redstone_block")).waitTicks(20).check("events_piston", mc -> {
            int bursts = Bursts.started - base[0], claimed = Bursts.claimed - base[1];
            long torches = mc.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, mc.player.getBoundingBox().inflate(8),
                e -> e.getItem().is(net.minecraft.world.item.Items.TORCH)).size();
            if (torches != 1) return torches + " torch drops (piston didn't break it?)";
            return bursts == 1 && claimed == 1 ? null : bursts + " bursts, " + claimed + " claimed";
        }).server(srv -> clearArea(srv));
    }

    /**
     * PRD §5.1 render paths: a chest and a banner (renderer only) pop through the renderer's pose; a furnace and a
     * barrel (mesh only) take the hide path; an enchanting table (mesh and renderer) places without a pop.
     */
    private static void bepop(Script s) {
        String[] items = {"chest", "white_banner", "furnace", "barrel", "enchanting_table"};
        int[] base = new int[3];
        s.server(srv -> {
            clearArea(srv);
            cmd(srv, "tp @p 0.5 " + Y + " -3.0 0 40");
            cmd(srv, "gamemode creative @p");
        }).waitTicks(20).run(mc -> {
            base[0] = Pops.started;
            base[1] = Pops.entityPops;
            base[2] = Pops.handedOff;
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            SelfTest.startCapture("bepop", 5 * 12 + 20);
        });
        for (int i = 0; i < items.length; i++) {
            String item = items[i];
            BlockPos against = new BlockPos(i * 2 - 4, Y - 1, 0);
            s.server(srv -> cmd(srv, "item replace entity @p weapon.mainhand with minecraft:" + item))
                .waitTicks(1)
                .run(mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(against).add(0, 0.5, 0), Direction.UP, against, false)))
                .waitTicks(11);
        }
        s.waitTicks(20).check("bepop_paths", mc -> {
            SelfTest.stopCapture();
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            int started = Pops.started - base[0], entity = Pops.entityPops - base[1], handed = Pops.handedOff - base[2];
            SnappyBlocks.LOG.info("[selftest] bepop: {} pops, {} through the renderer, {} handed off", started, entity, handed);
            for (int i = 0; i < items.length; i++) {
                if (mc.level.getBlockState(new BlockPos(i * 2 - 4, Y, 0)).isAir()) return items[i] + " not placed";
            }
            if (entity != 2) return entity + " renderer pops (chest, banner expected)";
            if (started != 4) return started + " pops (4 expected: no pop for the enchanting table)";
            if (handed != 2) return handed + " hand-offs (furnace, barrel expected)";
            return Pops.active() == 0 && Hidden.isEmpty() ? null : "pops left";
        });
    }

    /** dev/server/mp.py: role "a" places a block at {@link #MP_SPOT}; role "b" must see it appear as a remote change. */
    static Script multiplayer(String role) {
        Script s = new Script();
        s.until("join", 2400, mc -> mc.player != null && mc.level != null && mc.gui.screen() == null).waitTicks(40)
            .check("joined_" + role, mc -> null)
            .run(mc -> SnappyBlocks.LOG.info("SB-MP {} ready", role))
            .until("platform", 2400, mc -> mc.level.getBlockState(MP_SPOT.below()).is(Blocks.STONE))
            .waitTicks(40);
        if (role.equals("a")) {
            s.run(mc -> say(mc, "item replace entity @s weapon.mainhand with minecraft:oak_planks 8"))
                .until("planks in hand", 200, mc -> mc.player.getMainHandItem().is(net.minecraft.world.item.Items.OAK_PLANKS))
                .run(mc -> {
                    Probe.placed = Probe.placedPredicted = 0;
                    BlockPos under = MP_SPOT.below();
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(under).add(0, 0.5, 0), Direction.UP, under, false));
                })
                .until("server to keep the planks", 200, mc -> mc.level.getBlockState(MP_SPOT).is(Blocks.OAK_PLANKS))
                .check("mp_place_predicted", mc -> Probe.placedPredicted >= 1 ? null : "probe: " + Probe.summary())
                .waitTicks(200);
        } else {
            s.until("remote planks", 1200, mc -> mc.level.getBlockState(MP_SPOT).is(Blocks.OAK_PLANKS))
                .check("mp_remote_seen", mc -> Probe.placed >= 1 && Probe.placedPredicted == 0 && MP_SPOT.equals(Probe.lastPlaced) ? null
                    : "probe: " + Probe.summary() + " last " + Probe.lastPlaced)
                .check("mp_remote_pop", mc -> Pops.remote >= 1 ? null : "the remote placement didn't pop (" + Pops.started + " pops)");
        }
        // M1 stop/go: a break on a server bursts on both clients and its drop is claimed by the burst, also at 200 ms
        BlockPos dirt = new BlockPos(3, Y, -1);
        int[] base = new int[4];
        s.run(mc -> {
            base[0] = Bursts.started;
            base[1] = Bursts.claimed;
            base[2] = Bursts.watchdogs;
            base[3] = Pulses.started;
            Bursts.maxClaimDelay = 0;
        });
        if (role.equals("a")) {
            s.run(mc -> say(mc, "setblock 3 " + Y + " -1 minecraft:dirt")).until("dirt", 200, mc -> mc.level.getBlockState(dirt).is(Blocks.DIRT))
                .run(mc -> say(mc, "item replace entity @s weapon.mainhand with minecraft:air")).waitTicks(20)
                .run(mc -> {
                    aim(mc, dirt);
                    SelfTest.attack = true;
                }).until("dirt to break", 400, mc -> {
                    aim(mc, dirt);
                    if (!mc.level.getBlockState(dirt).isAir()) return false;
                    SelfTest.attack = false;
                    return true;
                });
        } else {
            s.until("remote dirt", 1200, mc -> mc.level.getBlockState(dirt).is(Blocks.DIRT))
                .until("remote dirt to break", 1200, mc -> mc.level.getBlockState(dirt).isAir());
        }
        s.waitTicks(40).check("mp_burst_" + role, mc -> {
            int started = Bursts.started - base[0], claimed = Bursts.claimed - base[1], dogs = Bursts.watchdogs - base[2];
            SnappyBlocks.LOG.info("SB-MP {} burst: {} bursts, {} claimed, {} watchdog, drop arrived {} ticks after the burst", role, started, claimed, dogs,
                String.format("%.1f", Bursts.maxClaimDelay));
            if (started != 1) return "bursts " + started + " of 1";
            if (claimed != 1) return "claimed " + claimed + " of 1 (drop arrived after the claim window?)";
            if (dogs > 0) return dogs + " drops shown by the watchdog";
            return null;
        }).check("mp_pulse_" + role, mc -> Pulses.started - base[3] >= 2 ? null : (Pulses.started - base[3]) + " pulses while the dirt was mined")
            .waitTicks(role.equals("a") ? 100 : 20);
        s.run(mc -> SnappyBlocks.LOG.info("SB-MP {} done: {}", role, Probe.summary()));
        return s;
    }
}
