package io.github.profetgit.snappyblocks.selftest;

import io.github.profetgit.snappyblocks.Clock;
import io.github.profetgit.snappyblocks.Probe;
import io.github.profetgit.snappyblocks.SnappyBlocks;
import io.github.profetgit.snappyblocks.burst.Bursts;
import io.github.profetgit.snappyblocks.pop.Hidden;
import io.github.profetgit.snappyblocks.pop.Pops;
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
        for (String name : List.of("smoke", "pop", "burst")) {
            if (!only.equals("all") && !Arrays.asList(only.split(",")).contains(name)) continue;
            switch (name) {
                case "smoke" -> smoke(s);
                case "pop" -> pop(s);
                case "burst" -> burst(s);
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
                    : "probe: " + Probe.summary() + " last " + Probe.lastPlaced);
        }
        // M1 stop/go: a break on a server bursts on both clients and its drop is claimed by the burst, also at 200 ms
        BlockPos dirt = new BlockPos(3, Y, -1);
        int[] base = new int[3];
        s.run(mc -> {
            base[0] = Bursts.started;
            base[1] = Bursts.claimed;
            base[2] = Bursts.watchdogs;
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
        }).waitTicks(role.equals("a") ? 100 : 20);
        s.run(mc -> SnappyBlocks.LOG.info("SB-MP {} done: {}", role, Probe.summary()));
        return s;
    }
}
