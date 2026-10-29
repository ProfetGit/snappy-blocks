package io.github.profetgit.snappyblocks.selftest;

import io.github.profetgit.snappyblocks.Clock;
import io.github.profetgit.snappyblocks.Probe;
import io.github.profetgit.snappyblocks.SnappyBlocks;
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
        for (String name : List.of("smoke")) {
            if (!only.equals("all") && !Arrays.asList(only.split(",")).contains(name)) continue;
            switch (name) {
                case "smoke" -> smoke(s);
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
        s.run(mc -> SnappyBlocks.LOG.info("SB-MP {} done: {}", role, Probe.summary()));
        return s;
    }
}
