package io.github.profetgit.snappyblocks.selftest;

import static io.github.profetgit.snappyblocks.selftest.Scenarios.Y;
import static io.github.profetgit.snappyblocks.selftest.Scenarios.cmd;

import io.github.profetgit.snappyblocks.Config;
import io.github.profetgit.snappyblocks.SnappyBlocks;
import io.github.profetgit.snappyblocks.burst.Bursts;
import io.github.profetgit.snappyblocks.burst.Chip;
import io.github.profetgit.snappyblocks.burst.Cubes;
import io.github.profetgit.snappyblocks.burst.Dust;
import io.github.profetgit.snappyblocks.burst.Puff;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Scene `particles`: the placing effects (off: none; bits once per block and per door or bed), chips bouncing by material,
 * 3D cubes (the mod's chips and vanilla's crumbs, only when on). Frames in out/frames/place, cubes and flat.
 */
final class ParticleScenes {
    private ParticleScenes() {}

    private static void place(Minecraft mc, BlockPos on) {
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(on).add(0, 0.5, 0), Direction.UP, on, false));
    }

    static void add(Script s) {
        int[] base = new int[4];
        s.server(srv -> {
            Scenarios.clearArea(srv);
            cmd(srv, "fill -6 " + (Y - 1) + " -4 6 " + (Y - 1) + " 6 minecraft:smooth_stone");
            cmd(srv, "tp @p 0.5 " + Y + " -2.6 0 36");
            cmd(srv, "gamemode survival @p");
            cmd(srv, "clear @p");
            cmd(srv, "item replace entity @p hotbar.0 with minecraft:oak_planks 16");
            cmd(srv, "item replace entity @p hotbar.1 with minecraft:oak_door 4");
            cmd(srv, "item replace entity @p hotbar.2 with minecraft:red_bed 1");
        }).waitTicks(20).run(mc -> {
            Config c = Config.get();
            c.cubes = false;
            c.placeEffect = "off";
            c.placeStrength = 100;
            c.enabled = c.chips = c.burst = c.pop = true;
            TestCompat.selectSlot(mc.player, 0);
            base[0] = Puff.puffs;
        }).waitTicks(4).run(mc -> place(mc, new BlockPos(-2, Y - 1, 0))).waitTicks(16)
            .check("place_off", mc -> Puff.puffs == base[0] ? null : "a puff with placing particles off")
            .run(mc -> {
                Config.get().placeEffect = "bits";
                base[0] = Puff.puffs;
                base[1] = Puff.chipsMade;
                if (!TestCompat.hudHidden(mc)) TestCompat.toggleHud(mc);
                SelfTest.startCapture("place", 150);
            }).waitTicks(4).run(mc -> place(mc, new BlockPos(0, Y - 1, 0))).waitTicks(20)
            .check("place_on", mc -> Puff.puffs == base[0] + 1 && Puff.chipsMade > base[1] ? null
                : "puffs " + (Puff.puffs - base[0]) + ", chips " + (Puff.chipsMade - base[1]))
            .run(mc -> TestCompat.selectSlot(mc.player, 1)).waitTicks(4).run(mc -> place(mc, new BlockPos(1, Y - 1, 0))).waitTicks(20)
            .check("place_door", mc -> Puff.puffs == base[0] + 2 ? null : "puffs after a door: " + (Puff.puffs - base[0] - 1))
            .run(mc -> TestCompat.selectSlot(mc.player, 2)).waitTicks(4).run(mc -> place(mc, new BlockPos(-1, Y - 1, 0))).waitTicks(20)
            .check("place_bed", mc -> Puff.puffs == base[0] + 3 ? null : "puffs after a bed: " + (Puff.puffs - base[0] - 2))
            .run(mc -> {
                SelfTest.stopCapture();
                Config c = Config.get();
                c.pop = false;
                TestCompat.selectSlot(mc.player, 0);
                base[0] = Puff.puffs;
            }).waitTicks(4).run(mc -> place(mc, new BlockPos(2, Y - 1, 0))).waitTicks(12)
            .check("place_without_pop", mc -> Puff.puffs == base[0] + 1 ? null : "no puff with the pop off")
            .run(mc -> {
                Config c = Config.get();
                c.pop = true;
                c.placeEffect = "off";
            });

        // the dust effects and the explosion, each with only its own pieces, and nothing when off
        for (String effect : new String[] {"off", "poof", "shockwave", "explosion"}) {
            s.server(srv -> {
                Scenarios.clearArea(srv);
                cmd(srv, "item replace entity @p hotbar.3 with minecraft:sandstone 16");
                cmd(srv, "item replace entity @p hotbar.4 with minecraft:deepslate 16");
            }).waitTicks(20).run(mc -> {
                Config.get().placeEffect = effect;
                TestCompat.selectSlot(mc.player, 3);
                base[0] = Dust.made;
                base[1] = Puff.chipsMade;
                base[2] = Dust.rings;
                if (!effect.equals("off")) SelfTest.startCapture(effect, 90);
            }).waitTicks(4).run(mc -> place(mc, new BlockPos(0, Y - 1, 0))).waitTicks(18)
                .run(mc -> TestCompat.selectSlot(mc.player, 4)).waitTicks(4).run(mc -> place(mc, new BlockPos(-2, Y - 1, 0))).waitTicks(24)
                .check("effect_" + effect, mc -> {
                    SelfTest.stopCapture();
                    Config.get().placeEffect = "off";
                    int clouds = Dust.made - base[0], rings = Dust.rings - base[2], chips = Puff.chipsMade - base[1];
                    SnappyBlocks.LOG.info("[selftest] particles: {}: {} chips, {} clouds, {} rings for two blocks", effect, chips, clouds, rings);
                    if (effect.equals("explosion")) return chips >= 24 && clouds == 0 ? null : chips + " chips, " + clouds + " clouds";
                    if (chips != 0) return chips + " chips";
                    if (effect.equals("off")) return clouds == 0 ? null : clouds + " clouds with the effect off";
                    if (rings != (effect.equals("shockwave") ? 2 : 0)) return rings + " rings";
                    return clouds >= 12 ? null : clouds + " clouds for two blocks";
                });
        }

        // the strength slider: more pieces, more clouds
        s.run(mc -> {
            Config c = Config.get();
            c.placeStrength = 25;
            int few = Puff.blastCount(), puffsFew = Dust.count(Dust.Style.POOF);
            c.placeStrength = 200;
            int many = Puff.blastCount(), puffsMany = Dust.count(Dust.Style.POOF);
            c.placeStrength = 100;
            SnappyBlocks.LOG.info("[selftest] particles: strength 25/200: explosion {}/{}, poofs {}/{}", few, many, puffsFew, puffsMany);
            SelfTest.record("effect_strength", few > 0 && many >= few * 6 && puffsMany >= puffsFew * 6, few + "/" + many + " " + puffsFew + "/" + puffsMany);
        });

        for (String[] block : new String[][] {{"glass", "yes"}, {"sand", "no"}}) {
            s.server(srv -> {
                Scenarios.clearArea(srv);
                cmd(srv, "setblock 0 " + Y + " 0 minecraft:" + block[0]);
            }).waitTicks(30).run(mc -> {
                base[0] = Chip.bounced;
                base[1] = Bursts.chipsMade;
            }).server(srv -> cmd(srv, "setblock 0 " + Y + " 0 minecraft:air destroy")).waitTicks(50).check("bounce_" + block[0], mc -> {
                int hops = Chip.bounced - base[0], chips = Bursts.chipsMade - base[1];
                SnappyBlocks.LOG.info("[selftest] particles: {} chips of {} bounced {} times", chips, block[0], hops);
                if (chips <= 0) return "no chips";
                return (hops > 0) == block[1].equals("yes") ? null : hops + " bounces";
            });
        }

        String[] blocks = {"stone", "oak_log", "grass_block", "glass", "diamond_ore"};
        for (String look : new String[] {"flat", "cubes"}) {
            boolean cubes = look.equals("cubes");
            s.server(srv -> {
                Scenarios.clearArea(srv);
                for (int i = 0; i < blocks.length; i++) cmd(srv, "setblock " + (i - 2) + " " + Y + " 0 minecraft:" + blocks[i]);
            }).waitTicks(30).run(mc -> {
                Config.get().cubes = cubes;
                base[0] = Cubes.drawn;
                SelfTest.startCapture(look, 200);
            });
            for (int i = 0; i < blocks.length; i++) {
                int x = i - 2;
                s.server(srv -> cmd(srv, "setblock " + x + " " + Y + " 0 minecraft:air destroy")).waitTicks(7);
            }
            s.waitTicks(30).check("cubes_" + (cubes ? "on" : "off"), mc -> {
                SelfTest.stopCapture();
                int drawn = Cubes.drawn - base[0];
                SnappyBlocks.LOG.info("[selftest] particles: {} cubes drawn ({})", drawn, look);
                return (drawn > 0) == cubes ? null : drawn + " cubes drawn with 3D " + (cubes ? "on" : "off");
            });
        }

        // the worst case: 60 blocks at once, vanilla's 3,800 crumbs and a burst's chips, all cubes
        long[] cost = new long[1];
        s.server(srv -> {
            Scenarios.clearArea(srv);
            cmd(srv, "fill -2 " + Y + " 0 2 " + (Y + 2) + " 3 minecraft:stone");
        }).waitTicks(30).run(mc -> {
            Config.get().cubes = true;
            base[0] = Cubes.drawn;
            cost[0] = Cubes.spentNanos;
        }).server(srv -> cmd(srv, "fill -2 " + Y + " 0 2 " + (Y + 2) + " 3 minecraft:air destroy")).waitTicks(30).check("cubes_mass", mc -> {
            int drawn = Cubes.drawn - base[0];
            long ns = Cubes.spentNanos - cost[0];
            SnappyBlocks.LOG.info("[selftest] particles: 60-block break, {} cube draws, {} ns each", drawn, drawn == 0 ? 0 : ns / drawn);
            return drawn > 0 ? null : "no cubes";
        });

        // vanilla's own crumbs are cubes too: Snappy chips off, so every particle here is vanilla's
        s.server(srv -> {
            Scenarios.clearArea(srv);
            cmd(srv, "setblock 0 " + Y + " 0 minecraft:stone");
        }).waitTicks(30).run(mc -> {
            Config.get().chips = false;
            base[0] = Cubes.drawn;
        }).server(srv -> cmd(srv, "setblock 0 " + Y + " 0 minecraft:air destroy")).waitTicks(10).check("cubes_vanilla", mc -> {
            int drawn = Cubes.drawn - base[0];
            Config c = Config.get();
            c.chips = true;
            c.cubes = false;
            if (TestCompat.hudHidden(mc)) TestCompat.toggleHud(mc);
            return drawn > 0 ? null : "vanilla crumbs stayed flat";
        });
    }
}
