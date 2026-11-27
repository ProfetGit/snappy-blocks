package io.github.profetgit.snappyblocks.selftest;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import io.github.profetgit.profetsettings.ModDef;
import io.github.profetgit.profetsettings.Preview;
import io.github.profetgit.profetsettings.ProfetSettings;
import io.github.profetgit.profetsettings.ProfetSettingsScreen;
import io.github.profetgit.profetsettings.Setting;
import io.github.profetgit.snappyblocks.Config;
import io.github.profetgit.snappyblocks.ConfigScreens;
import io.github.profetgit.snappyblocks.SnappyBlocks;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

// Scene "ui" (only when named; run at 1920x1080): Snappy Blocks in the shared Profet settings screen, every option
// through the real mouse, every preview at fixed moments, and, with Tidy Pockets installed too, both mods in one screen.
final class UiScenes {
    private static final String SWITCHES = "enabled calm pop chips cubes items pickup";
    private static String opening, openingTidy;
    private static List<Object> openingTidyValues;

    private UiScenes() {}

    static void add(Script s) {
        s.run(mc -> {
                reset();
                gui(mc, 2);
            })
            .waitTicks(5)
            .check("ui: fonts, sprites and language load", mc -> {
                for (String path : new String[] {"profet_settings:font/tiny5.ttf", "profet_settings:font/tiny5_bold.ttf", "profet_settings:font/tiny5_title.ttf", "profet_settings:textures/gui/sprites/golem.png", "snappy_blocks:icon.png"}) {
                    if (mc.getResourceManager().getResource(Identifier.tryParse(path)).isEmpty()) return "missing " + path;
                }
                for (String key : new String[] {"profet_settings.ui.done", "snappy_blocks.setting.pop", "snappy_blocks.preview.off.all"}) {
                    if (net.minecraft.client.resources.language.I18n.get(key).equals(key)) return "untranslated " + key;
                }
                return null;
            });
        //? if fabric {
        s.run(mc -> mc.gui.setScreen(new PauseScreen(true))).waitTicks(15)
            .check("ui: the Game Menu has exactly one golem button", mc -> golems(mc).size() == 1 ? null : "golem buttons: " + golems(mc).size());
        capture(s, "ui-0-menu");
        s.run(mc -> {
                Button b = golems(mc).get(0);
                click(mc, b.getX() + 10, b.getY() + 10);
            })
            .waitTicks(30)
            .check("ui: the golem button opens the hub", mc -> ProfetSettings.isScreen(mc.gui.screen()) ? null : "screen " + mc.gui.screen());
        //?}
        s.run(mc -> mc.gui.setScreen(ConfigScreens.create(null))).waitTicks(30)
            .check("ui: the config button opens the hub on Snappy Blocks", mc -> hub(mc).debugSelected().id.equals(SnappyBlocks.ID) ? null : "selected " + hub(mc).debugSelected().id)
            .check("ui: every option is listed with a label and a description", mc -> {
                List<String> keys = new ArrayList<>();
                for (Setting o : hub(mc).debugSelected().all()) {
                    keys.add(o.key);
                    if (o.label.startsWith("snappy_blocks.") || o.desc.startsWith("snappy_blocks.")) return "untranslated " + o.key;
                }
                return String.join(" ", keys).equals("enabled intensity calm pop pulseStyle pulseStrength breakStyle breakStrength chips placeEffect placeStrength cubes items pickup") ? null : "keys " + keys;
            })
            .check("ui: the listed mods are the installed Profet mods", mc -> {
                List<String> ids = hub(mc).debugMods().stream().map(m -> m.id).toList();
                List<String> want = tidy() ? List.of(SnappyBlocks.ID, "tidypockets") : List.of(SnappyBlocks.ID);
                return ids.equals(want) ? null : "mods " + ids;
            });
        for (int gs : new int[] {2, 3, 4}) {
            s.run(mc -> {
                    gui(mc, gs);
                    mc.gui.setScreen(ConfigScreens.create(null));
                })
                .waitTicks(30)
                .run(mc -> SelfTest.record("ui layout gs" + gs, true, hub(mc).debugLayout()));
            capture(s, "ui-gs" + gs + "-a-top");
            s.run(mc -> hub(mc).debugScroll(100000)).waitTicks(30);
            capture(s, "ui-gs" + gs + "-b-end");
        }
        s.run(mc -> {
                gui(mc, 2);
                mc.gui.setScreen(ConfigScreens.create(null));
            })
            .waitTicks(30);

        previews(s);
        presetPreviews(s);
        controls(s);
        lockAndActions(s);
        cancel(s);
        bugReport(s);
        s.run(mc -> {
            if (tidy()) SnappyBlocks.LOG.info("[selftest] Tidy Pockets present: running the two-mod checks");
        });
        two(s);
        s.run(mc -> {
            reset();
            mc.gui.setScreen(null);
        });
    }

    // ---- previews ----

    private static void previews(Script s) {
        preview(s, "enabled", "general", "", 150, 450, 900, 1180, 1580, 1780, 1850, 2150, 2600, 3200);
        preview(s, "pop", "pop", "", 150, 320, 420, 600, 1270, 1370, 2000, 2780);
        preview(s, "pulseStyle", "pulse", "", 150, 420, 505, 540, 740, 1130, 1710, 1800, 1900, 2300);
        preview(s, "breakStyle", "burst", "", 150, 500, 700, 900, 1000, 1240, 1325, 1370, 1420, 1500, 1650, 1900, 2000, 2800, 3480, 3660, 3750);
        preview(s, "items", "drops", "", 100, 260, 320, 380, 500, 800, 860, 1400, 1900, 2460, 2800, 3370, 3550);
        preview(s, "pickup", "pickup", "", 100, 300, 560, 700, 1100, 1200, 1260, 1330, 1420, 1520, 2000, 2550);
        for (String style : new String[] {"twirl"}) {
            s.run(mc -> Config.get().breakStyle = style);
            preview(s, "breakStyle", "burst", "-" + style, 1340, 1400, 1450, 1500, 1560, 1620, 1700, 1900);
            preview(s, "enabled", "general", "-" + style, 1800, 1900, 2000);
        }
        for (String style : new String[] {"burst", "twirl"}) {
            for (int strength : new int[] {25, 200}) {
                s.run(mc -> {
                    Config.get().breakStyle = style;
                    Config.get().breakStrength = strength;
                });
                preview(s, "breakStrength", "burst", "-" + style + "-" + strength, 1340, 1450, 1560, 1700);
            }
        }
        s.run(mc -> {
            Config.get().breakStyle = "burst";
            Config.get().breakStrength = 100;
        });
        for (String style : new String[] {"shake", "wobble"}) {
            s.run(mc -> Config.get().pulseStyle = style);
            preview(s, "pulseStyle", "pulse", "-" + style, 520, 560, 600, 640, 680, 1520, 1560, 1600, 1640, 1680);
            preview(s, "enabled", "general", "-" + style, 1180, 1220, 1260);
        }
        for (int strength : new int[] {25, 200}) {
            s.run(mc -> {
                Config.get().pulseStyle = "shake";
                Config.get().pulseStrength = strength;
            });
            preview(s, "pulseStrength", "pulse", "-shake-" + strength, 520, 560, 600, 640, 680);
        }
        s.run(mc -> {
            Config.get().pulseStyle = "pulse";
            Config.get().pulseStrength = 100;
        });
        s.run(mc -> Config.get().chips = false);
        preview(s, "chips", "chips", "-nochips", 1370, 1460, 1650);
        preview(s, "pulseStyle", "pulse", "-nochips", 740);
        preview(s, "enabled", "general", "-nochips", 1850);
        s.run(mc -> Config.get().chips = true);
        preview(s, "chips", "chips", "", 1460);
        s.run(mc -> Config.get().burst = false);
        preview(s, "chips", "chips", "-burstoff", 740, 1710);
        s.run(mc -> Config.get().burst = true);
        // every placing effect, off (the default) first; then 3D cubes off and on
        preview(s, "placeEffect", "place", "-off", 420, 700);
        s.run(mc -> Config.get().placeEffect = "bits");
        preview(s, "placeEffect", "place", "-bits", 150, 520, 650, 900, 1300);
        s.run(mc -> Config.get().placeEffect = "poof");
        preview(s, "placeEffect", "place", "-poof", 500, 580, 660, 760);
        s.run(mc -> Config.get().placeEffect = "shockwave");
        preview(s, "placeEffect", "place", "-shock", 540, 620, 700, 780);
        s.run(mc -> Config.get().placeEffect = "explosion");
        preview(s, "placeEffect", "place", "-explosion", 150, 500, 580, 700, 900, 1200, 1500, 1750);
        for (int strength : new int[] {25, 200}) {
            s.run(mc -> Config.get().placeStrength = strength);
            preview(s, "placeStrength", "place", "-explosion-" + strength, 580, 750);
            s.run(mc -> Config.get().placeEffect = "poof");
            preview(s, "placeStrength", "place", "-poof-" + strength, 660);
            s.run(mc -> Config.get().placeEffect = "explosion");
        }
        s.run(mc -> Config.get().placeStrength = 100);
        preview(s, "cubes", "cubes", "-off", 1100, 1500);
        s.run(mc -> Config.get().cubes = true);
        preview(s, "cubes", "cubes", "", 500, 760, 1000, 1100, 1250, 1500, 2100, 2800);
        preview(s, "placeEffect", "place", "-explosion-cubes", 580, 900);
        preview(s, "breakStyle", "burst", "-cubes", 1420, 1650);
        preview(s, "pulseStyle", "pulse", "-cubes", 740, 1710);
        s.run(mc -> {
            Config.get().cubes = false;
            Config.get().placeEffect = "off";
        });
        // the previews react to the options
        s.run(mc -> Config.get().intensity = 150);
        preview(s, "intensity", "general", "-lively", 450, 1580, 1850);
        preview(s, "pop", "pop", "-lively", 300, 420);
        preview(s, "pulseStyle", "pulse", "-lively", 540);
        preview(s, "breakStyle", "burst", "-lively", 1325, 1420);
        preview(s, "items", "drops", "-lively", 900, 1000);
        s.run(mc -> {
            Config.get().intensity = 100;
            Config.get().calm = true;
        });
        preview(s, "calm", "general", "-calm", 450, 1580, 1850, 2150);
        preview(s, "pop", "pop", "-calm", 300, 420);
        preview(s, "breakStyle", "burst", "-calm", 1325, 1900);
        preview(s, "items", "drops", "-calm", 900, 2000);
        s.run(mc -> {
            Config.get().calm = false;
            Config.get().pop = Config.get().pulse = Config.get().burst = Config.get().items = false;
        });
        preview(s, "enabled", "general", "-vanilla", 450, 1580, 1780);
        s.run(mc -> {
            Config.get().items = true;
            Config.get().pickup = false;
        });
        preview(s, "pop", "pop", "-off", 320, 1270);
        preview(s, "pulseStyle", "pulse", "-off", 540, 740);
        preview(s, "breakStyle", "burst", "-off", 1000, 1325, 1900);
        s.run(mc -> {
            Config.get().burst = true;
            Config.get().items = false;
        });
        preview(s, "breakStyle", "burst", "-itemsoff", 1370, 1650);
        s.run(mc -> {
            Config.get().burst = false;
            Config.get().items = true;
        });
        preview(s, "pickup", "pickup", "-off", 1100, 1180, 1220, 1500);
        s.run(mc -> Config.get().items = false);
        preview(s, "items", "drops", "-off", 320, 900, 1900, 2460, 3370);
        preview(s, "pickup", "pickup", "-itemsoff", 900, 1180, 1500);
        s.run(mc -> {
            reset();
            Config.get().enabled = false;
        });
        preview(s, "enabled", "general", "-off", 450, 1580, 1780, 2600);
        // every 60 fps moment of every loop, for the flicker check (--var pvdense=true)
        if (Boolean.getBoolean("snappy_blocks.selftest.pvdense")) {
            s.run(mc -> reset());
            dense(s, "enabled", "general", 3500);
            dense(s, "pop", "pop", 3000);
            dense(s, "pulseStyle", "pulse", 2600);
            dense(s, "breakStyle", "burst", 4000);
            dense(s, "items", "drops", 3600);
            dense(s, "pickup", "pickup", 2600);
            s.run(mc -> {
                Config.get().cubes = true;
                Config.get().placeEffect = "explosion";
            });
            dense(s, "placeEffect", "place", 2000);
            dense(s, "cubes", "cubes", 3200);
            s.run(mc -> {
                Config.get().cubes = false;
                Config.get().placeEffect = "off";
            });
        }
        s.run(mc -> {
            reset();
            Preview.debugTime = -1;
            mc.gui.setScreen(ConfigScreens.create(null));
        }).waitTicks(30);
    }

    private static void presetPreviews(Script s) {
        s.run(mc -> {
                reset();
                Preview.debugTime = 1900;
                mc.gui.setScreen(ConfigScreens.create(null));
            })
            .waitTicks(20)
            .run(mc -> hub(mc).debugScroll(0))
            .waitTicks(6);
        String[] names = {"Off", "Calm", "Default", "Lively"};
        String[] ids = {"off", "calm", "default", "lively"};
        String[] notes = {"Vanilla blocks, nothing animated", "Smaller, softer movement, no hit-stop", "The recommended look", "Bigger, bouncier movement"};
        for (int i = 0; i < names.length; i++) {
            int idx = i;
            s.run(mc -> {
                    int[] p = hub(mc).debugPresetPoint(idx);
                    hub(mc).debugHover(p[0], p[1]);
                })
                .waitTicks(12)
                .check("ui: hovering Quick setup " + names[i] + " shows its preview", mc -> ("preset." + ids[idx]).equals(hub(mc).debugPreviewKind()) ? null : "preview " + hub(mc).debugPreviewKind())
                .check("ui: the preview title is the preset name " + names[i], mc -> names[idx].equals(hub(mc).debugPreviewTitle()) ? null : "title " + hub(mc).debugPreviewTitle())
                .check("ui: the preview note for " + names[i] + " is plain words, no numbers", mc -> {
                    String n = hub(mc).debugPreviewNote();
                    return n != null && !n.isBlank() && n.chars().noneMatch(Character::isDigit) ? null : "note '" + n + "'";
                })
                .check("ui: the note for " + names[i] + " is the mod's own", mc -> notes[idx].equals(hub(mc).debugPreviewNote()) ? null : "note '" + hub(mc).debugPreviewNote() + "'");
            for (int ms : new int[] {360, 410, 1850, 1950}) {
                s.run(mc -> Preview.debugTime = ms).waitTicks(2);
                capture(s, "ui-pv-preset-" + ids[i] + "-" + ms);
            }
        }
        // a hover restarts the scene: moving between presets, or back onto the same one after leaving, makes a new one
        double[] mount = {0};
        for (int i : new int[] {1, 3, 1}) {
            int idx = i;
            s.run(mc -> hub(mc).debugHover(-1, -1))
                .waitTicks(4)
                .run(mc -> {
                    int[] p = hub(mc).debugPresetPoint(idx);
                    hub(mc).debugHover(p[0], p[1]);
                })
                .waitTicks(8)
                .check("ui: hovering " + names[i] + " again starts its scene over", mc -> {
                    double m = hub(mc).debugSceneMount();
                    boolean fresh = m > mount[0];
                    mount[0] = m;
                    return fresh ? null : "scene was not recreated";
                })
                .waitTicks(8)
                .check("ui: " + names[i] + "'s scene keeps running while hovered", mc -> hub(mc).debugSceneMount() == mount[0] ? null : "scene recreated");
        }
        s.run(mc -> hub(mc).debugRevealRow("pop"))
            .waitTicks(4)
            .run(mc -> {
                int[] p = hub(mc).debugPoint("pop", -1f);
                hub(mc).debugHover(p[0], p[1]);
            })
            .waitTicks(12)
            .check("ui: hovering a setting after a preset shows the setting's preview again", mc -> "pop".equals(hub(mc).debugPreviewKind()) ? null : "preview " + hub(mc).debugPreviewKind())
            .run(mc -> {
                reset();
                Preview.debugTime = -1;
                hub(mc).debugHover(-1, -1);
                mc.gui.setScreen(ConfigScreens.create(null));
            })
            .waitTicks(20);
    }

    private static void preview(Script s, String key, String kind, String suffix, int... times) {
        s.run(mc -> {
                mc.gui.setScreen(ConfigScreens.create(null));
                Preview.debugTime = times[0];
            })
            .waitTicks(20)
            .run(mc -> hub(mc).debugRevealRow(key))
            .waitTicks(4)
            .run(mc -> hover(mc, key))
            .waitTicks(6)
            // again: under load several ticks run before one frame, and the first point can predate the scroll
            .run(mc -> hover(mc, key))
            .waitTicks(12)
            .check("ui: " + key + " shows the " + kind + " preview" + suffix, mc -> kind.equals(hub(mc).debugPreviewKind()) ? null : "preview " + hub(mc).debugPreviewKind());
        for (int t : times) {
            s.run(mc -> Preview.debugTime = t).waitTicks(3);
            capture(s, "ui-pv-" + kind + suffix + "-" + t);
        }
    }

    private static void hover(Minecraft mc, String key) {
        // a switch has one centre; a slider's point is a fraction along it
        int[] p = hub(mc).debugPoint(key, key.equals("intensity") || key.endsWith("Strength") ? 0.5f : -1f);
        hub(mc).debugHover(p[0], p[1]);
    }

    private static void dense(Script s, String key, String kind, int period) {
        preview(s, key, kind, "-dense", 0);
        for (int i = 0; i < period * 60 / 1000; i++) {
            int t = i * 1000 / 60;
            s.run(mc -> {
                    Preview.debugTime = t;
                    SelfTest.startCapture(String.format("ui-dense-%s-%04d", kind, t), 1);
                })
                .until("ui: dense frame written", 40, mc -> !SelfTest.capturing());
        }
    }

    // ---- every control through the real mouse ----

    private static void controls(Script s) {
        for (String key : SWITCHES.split(" ")) {
            boolean[] was = new boolean[1];
            s.run(mc -> was[0] = setting(mc, SnappyBlocks.ID, key).getBool());
            click(s, key, -1f);
            s.check("ui: the " + key + " switch changes the option and the file", mc -> setting(mc, SnappyBlocks.ID, key).getBool() != was[0]
                && file().contains("\"" + key + "\":" + !was[0]) ? null : "value " + setting(mc, SnappyBlocks.ID, key).getBool() + " file " + file());
            click(s, key, -1f);
            s.check("ui: the " + key + " switch changes it back", mc -> setting(mc, SnappyBlocks.ID, key).getBool() == was[0] ? null : "stuck");
        }
        for (String want : new String[] {"shake", "wobble", "off", "pulse"}) {
            click(s, "pulseStyle", 1f);
            s.check("ui: the mining-animation arrow picks " + want + " and saves it", mc -> {
                Config k = Config.get();
                boolean picked = want.equals("off") ? !k.pulse : k.pulse && k.pulseStyle.equals(want);
                return picked && file().contains(want.equals("off") ? "\"pulse\":false" : "\"pulseStyle\":\"" + want + "\"") ? null
                    : "pulse " + k.pulse + " style " + k.pulseStyle;
            });
        }
        for (String want : new String[] {"twirl", "off", "burst"}) {
            click(s, "breakStyle", 1f);
            s.check("ui: the break-animation arrow picks " + want + " and saves it", mc -> {
                Config k = Config.get();
                boolean picked = want.equals("off") ? !k.burst : k.burst && k.breakStyle.equals(want);
                return picked && file().contains(want.equals("off") ? "\"burst\":false" : "\"breakStyle\":\"" + want + "\"") ? null
                    : "burst " + k.burst + " style " + k.breakStyle;
            });
        }
        click(s, "pulseStrength", 1f);
        s.check("ui: the mining-strength slider reaches 200% and is saved", mc -> Config.get().pulseStrength == 200 && file().contains("\"pulseStrength\":200")
            ? null : "strength " + Config.get().pulseStrength);
        click(s, "pulseStrength", 0f);
        s.check("ui: and mining 25%", mc -> Config.get().pulseStrength == 25 ? null : "strength " + Config.get().pulseStrength);
        s.run(mc -> Config.get().pulseStrength = 100);
        click(s, "breakStrength", 1f);
        s.check("ui: the break-strength slider reaches 200% and is saved", mc -> Config.get().breakStrength == 200 && file().contains("\"breakStrength\":200")
            ? null : "strength " + Config.get().breakStrength);
        click(s, "breakStrength", 0f);
        s.check("ui: and 25%", mc -> Config.get().breakStrength == 25 ? null : "strength " + Config.get().breakStrength);
        s.run(mc -> Config.get().breakStrength = 100);
        for (String want : new String[] {"bits", "poof", "shockwave", "explosion", "off"}) {
            click(s, "placeEffect", 1f);
            s.check("ui: the placing-effect arrow picks " + want + " and saves it", mc -> Config.get().placeEffect.equals(want)
                && file().contains("\"placeEffect\":\"" + want + "\"") ? null : "effect " + Config.get().placeEffect);
        }
        int[][] stale = new int[1][];
        String[] effectWas = new String[1];
        s.run(mc -> {
                hub(mc).debugRevealRow("placeEffect");
            }).waitTicks(4)
            .run(mc -> {
                effectWas[0] = Config.get().placeEffect;
                stale[0] = hub(mc).debugPoint("placeEffect", 1f);
                hub(mc).debugParkRow("placeEffect");
                click(mc, stale[0][0], stale[0][1]);
            })
            .waitTicks(10)
            .check("ui: a row scrolled out of view takes no clicks where its arrows were", mc -> Config.get().placeEffect.equals(effectWas[0]) ? null : "effect " + effectWas[0] + " -> " + Config.get().placeEffect);
        boolean[] cubesWas = new boolean[1];
        s.run(mc -> cubesWas[0] = setting(mc, SnappyBlocks.ID, "cubes").getBool());
        click(s, "placeEffect", 1f);
        click(s, "cubes", -1f);
        s.check("ui: a switch clicked after a choice row still toggles", mc -> setting(mc, SnappyBlocks.ID, "cubes").getBool() != cubesWas[0] ? null : "cubes stuck");
        click(s, "cubes", -1f);
        click(s, "placeEffect", 0f);
        s.check("ui: the choice is back to off", mc -> Config.get().placeEffect.equals("off") && setting(mc, SnappyBlocks.ID, "cubes").getBool() == cubesWas[0] ? null : "effect " + Config.get().placeEffect);
        click(s, "placeStrength", 1f);
        s.check("ui: the strength slider reaches 200% and is saved", mc -> Config.get().placeStrength == 200 && file().contains("\"placeStrength\":200")
            ? null : "strength " + Config.get().placeStrength);
        click(s, "placeStrength", 0f);
        s.check("ui: and 25%", mc -> Config.get().placeStrength == 25 ? null : "strength " + Config.get().placeStrength);
        s.run(mc -> Config.get().placeStrength = 100);
        click(s, "intensity", 0f);
        s.check("ui: a slider click sets the intensity", mc -> Config.get().intensity == 50 ? null : "intensity " + Config.get().intensity)
            .run(mc -> {
                int[] a = hub(mc).debugPoint("intensity", 0f), b = hub(mc).debugPoint("intensity", 1f);
                hub(mc).debugPress(a[0], a[1]);
                hub(mc).debugDrag(b[0] + 30, b[1]);
                hub(mc).debugRelease(b[0] + 30, b[1]);
            })
            .waitTicks(20)
            .check("ui: a slider drag reaches the maximum and is saved", mc -> Config.get().intensity == 150 && file().contains("\"intensity\":150") ? null : "intensity " + Config.get().intensity);
        capture(s, "ui-c-changed-row");
        s.run(mc -> press(mc, hub(mc).debugReset("intensity"))).waitTicks(10)
            .check("ui: the reset arrow restores the intensity", mc -> Config.get().intensity == 100 ? null : "intensity " + Config.get().intensity)
            .run(mc -> hub(mc).debugScroll(0)).waitTicks(10);
        preset(s, 0, "Off", c -> !c.enabled);
        preset(s, 1, "Calm", c -> c.enabled && c.calm && c.intensity == 100);
        preset(s, 3, "Lively", c -> c.enabled && !c.calm && c.intensity == 130);
        capture(s, "ui-d-preset-lively");
        preset(s, 2, "Default", c -> c.enabled && !c.calm && c.intensity == 100 && c.pop && c.pulse && c.pulseStyle.equals("pulse") && c.pulseStrength == 100 && c.burst && c.breakStyle.equals("burst") && c.breakStrength == 100 && c.chips && c.placeEffect.equals("off") && c.placeStrength == 100 && !c.cubes && c.items && c.pickup);
        click(s, "pop", -1f);
        s.run(mc -> press(mc, hub(mc).debugChangedButton())).waitTicks(30)
            .check("ui: the Changed filter keeps only the changed row", mc -> hub(mc).debugItemCount() == 2 ? null : "items " + hub(mc).debugItemCount());
        capture(s, "ui-e-changed-filter");
        s.run(mc -> press(mc, hub(mc).debugChangedButton())).waitTicks(20)
            .run(mc -> press(mc, hub(mc).debugMenu())).waitTicks(20);
        capture(s, "ui-f-menu");
        s.run(mc -> press(mc, hub(mc).debugMenuItem(0))).waitTicks(10)
            .check("ui: Reset this mod restores the defaults", mc -> Config.get().pop ? null : "pop still off")
            .run(mc -> press(mc, hub(mc).debugDone())).waitTicks(10)
            .check("ui: Done closes the screen", mc -> mc.gui.screen() == null ? null : "screen " + mc.gui.screen())
            .check("ui: the feel numbers stay in the file", mc -> file().contains("\"tune\":{") && file().contains("\"pop.start\":") ? null : file());
    }

    // ---- read-only (locked) mode and action rows, through test hooks of the library ----

    private static final Screen[] seen = new Screen[1];
    private static final int[] pressed = new int[1];

    private static Setting act1() {
        return Setting.action("act1", "Test action", "A row with a button that opens a child screen.", "Open", scr -> {
            pressed[0]++;
            seen[0] = scr;
            ProfetSettings.debugExtra(SnappyBlocks.ID, act1(), act2());
            Minecraft.getInstance().gui.setScreen(new Screen(Component.literal("child")) {});
        });
    }

    private static Setting act2() {
        return Setting.action("act2", "Second action", "Appears after the child screen returned.", "Go", scr -> {});
    }

    private static void lockAndActions(Script s) {
        String[] before = new String[1];
        s.run(mc -> {
                Config.get().intensity = 150;
                pressed[0] = 0;
                ProfetSettings.debugExtra(SnappyBlocks.ID, act1());
                mc.gui.setScreen(ConfigScreens.create(null));
            })
            .waitTicks(30)
            .check("lock: an action row is listed with its button and is never a changed or listed value", mc -> {
                Setting a = hub(mc).debugSelected().find("act1");
                boolean listed = false;
                for (Setting o : hub(mc).debugSelected().all()) listed |= o.key.equals("act1");
                return hub(mc).debugPoint("act1", -1f) != null && !listed && a == null ? null : "point " + hub(mc).debugPoint("act1", -1f) + " listed " + listed;
            })
            .check("lock: an unlocked mod has no lock note", mc -> hub(mc).debugHasLockNote() ? "note shown" : null)
            .run(mc -> {
                before[0] = new Gson().toJson(Config.get());
                ProfetSettings.debugLockAll("Set by the server: you can look, not change.");
            })
            .waitTicks(15)
            .check("lock: the note shows once at the top of the list", mc -> hub(mc).debugHasLockNote() ? null : "no note");
        capture(s, "ui-lock-a-top");
        s.run(mc -> hub(mc).debugRevealRow("intensity")).waitTicks(6);
        capture(s, "ui-lock-b-rows");
        s.check("lock: a changed locked row has no reset arrow", mc -> hub(mc).debugReset("intensity") == null ? null : "reset arrow shown");
        click(s, "pop", -1f);
        click(s, "intensity", 0f);
        click(s, "placeEffect", 1f);
        s.check("lock: clicks on locked controls change nothing", mc -> before[0].equals(new Gson().toJson(Config.get())) ? null : "config " + new Gson().toJson(Config.get()))
            .run(mc -> hub(mc).debugScroll(0)).waitTicks(6)
            .run(mc -> hub(mc).debugHover(hub(mc).debugPresetPoint(1)[0], hub(mc).debugPresetPoint(1)[1])).waitTicks(12)
            .check("lock: hovering a locked Quick setup button still shows its preview", mc -> "preset.calm".equals(hub(mc).debugPreviewKind()) && "Calm".equals(hub(mc).debugPreviewTitle()) ? null : "preview " + hub(mc).debugPreviewKind() + " / " + hub(mc).debugPreviewTitle())
            .run(mc -> press(mc, hub(mc).debugPreset(0))).waitTicks(15)
            .check("lock: the Quick setup buttons do nothing", mc -> before[0].equals(new Gson().toJson(Config.get())) ? null : "config " + new Gson().toJson(Config.get()))
            .run(mc -> press(mc, hub(mc).debugMenu())).waitTicks(10)
            .run(mc -> press(mc, hub(mc).debugMenuItem(0))).waitTicks(10)
            .check("lock: Reset this mod does nothing", mc -> before[0].equals(new Gson().toJson(Config.get())) ? null : "config " + new Gson().toJson(Config.get()))
            .run(mc -> hub(mc).debugRevealRow("act1")).waitTicks(6)
            .run(mc -> press(mc, hub(mc).debugPoint("act1", -1f))).waitTicks(10)
            .check("lock: a locked action button does nothing", mc -> pressed[0] == 0 && ProfetSettings.isScreen(mc.gui.screen()) ? null : "pressed " + pressed[0]);
        s.run(mc -> ProfetSettings.debugLockAll(null)).waitTicks(15)
            .check("lock: unlocking removes the note", mc -> hub(mc).debugHasLockNote() ? "note still shown" : null)
            .check("lock: unlocking brings the reset arrow back", mc -> {
                hub(mc).debugRevealRow("intensity");
                return null;
            })
            .waitTicks(4)
            .check("lock: reset arrow is back", mc -> hub(mc).debugReset("intensity") != null ? null : "no reset arrow");
        click(s, "pop", -1f);
        s.check("lock: a click works again after unlocking", mc -> Config.get().pop != Boolean.parseBoolean(before[0].replaceAll(".*\"pop\":(true|false).*", "$1")) ? null : "pop did not change");
        click(s, "pop", -1f);
        s.run(mc -> hub(mc).debugRevealRow("act1")).waitTicks(6)
            .run(mc -> press(mc, hub(mc).debugPoint("act1", -1f))).waitTicks(15)
            .check("action: the button calls onPress with the hub screen and the child opens", mc -> pressed[0] == 1 && seen[0] != null && ProfetSettings.isScreen(seen[0]) && mc.gui.screen() != seen[0] ? null : "pressed " + pressed[0] + " screen " + mc.gui.screen())
            .run(mc -> mc.gui.setScreen(seen[0]))
            .waitTicks(20)
            .run(mc -> hub(mc).debugRevealRow("act2")).waitTicks(6)
            .check("action: returning from the child rebuilds the mod's rows (a row added meanwhile appears)", mc -> hub(mc).debugPoint("act2", -1f) != null ? null : "no act2 row")
            .check("action: the same mod stays selected after the return", mc -> hub(mc).debugSelected().id.equals(SnappyBlocks.ID) ? null : "selected " + hub(mc).debugSelected().id)
            .run(mc -> {
                ProfetSettings.debugExtra(SnappyBlocks.ID);
                reset();
                mc.gui.setScreen(null);
            });
    }

    private static void preset(Script s, int index, String name, Function<Config, Boolean> want) {
        s.run(mc -> hub(mc).debugScroll(0)).waitTicks(6)
            .run(mc -> press(mc, hub(mc).debugPreset(index))).waitTicks(20)
            .check("ui: Quick setup " + name, mc -> want.apply(Config.get()) ? null : "config " + new Gson().toJson(Config.get()));
    }

    private static void cancel(Script s) {
        s.run(mc -> {
                Config.get().intensity = 80;
                Config.get().pulse = false;
                Config.save();
                opening = new Gson().toJson(Config.get());
                mc.gui.setScreen(ConfigScreens.create(null));
            })
            .waitTicks(30);
        for (String key : SWITCHES.split(" ")) click(s, key, -1f);
        click(s, "pulseStyle", 1f);
        click(s, "breakStyle", 1f);
        click(s, "breakStrength", 1f);
        click(s, "pulseStrength", 1f);
        click(s, "intensity", 1f);
        s.check("ui: options changed before Cancel", mc -> !opening.equals(new Gson().toJson(Config.get())) ? null : "nothing changed")
            .run(mc -> press(mc, hub(mc).debugCancel())).waitTicks(10)
            .check("ui: Cancel puts every option back", mc -> opening.equals(new Gson().toJson(Config.get())) ? null : "config " + new Gson().toJson(Config.get()))
            .check("ui: Cancel writes the old values to the file", mc -> file().contains("\"intensity\":80") && file().contains("\"pulse\":false") && file().contains("\"enabled\":true") ? null : file())
            .check("ui: Cancel closes the screen", mc -> mc.gui.screen() == null ? null : "screen " + mc.gui.screen())
            .run(mc -> reset());
    }

    private static void bugReport(Script s) {
        java.util.List<java.net.URI> opened = new ArrayList<>();
        java.util.function.Consumer<java.net.URI>[] prevOpener = new java.util.function.Consumer[1];
        s.run(mc -> {
                gui(mc, 2);
                ProfetSettings.debugResetBugState();
                mc.gui.setScreen(ConfigScreens.create(null));
                prevOpener[0] = ProfetSettings.uriOpener;
                ProfetSettings.reportEndpoint = ""; // never file real issues from tests
                ProfetSettings.uriOpener = opened::add;
                opened.clear();
            })
            .waitTicks(20)
            .check("bug: Report bug button sits in footer left of Cancel", mc -> {
                int[] b = hub(mc).debugBugButton();
                int[] c = hub(mc).debugCancel();
                return b != null && b[0] < c[0] - 20 ? null : "bug=" + java.util.Arrays.toString(b) + " cancel=" + java.util.Arrays.toString(c);
            })
            .run(mc -> press(mc, hub(mc).debugBugButton()))
            .waitTicks(20)
            .check("bug: clicking Report bug opens the modal", mc -> hub(mc).debugBugModalOpen() ? null : "modal did not open");
        capture(s, "ui-bug-modal");
        s.run(mc -> {
                int[] tp = hub(mc).debugBugTitle();
                click(mc, tp[0], tp[1]);
                for (char ch : "Pop glitch".toCharArray()) {
                    UiInput.type(mc, ch);
                }
                int[] dp = hub(mc).debugBugDesc();
                click(mc, dp[0], dp[1]);
                for (char ch : "Block blinks once.".toCharArray()) {
                    UiInput.type(mc, ch);
                }
            })
            .waitTicks(10)
            .check("bug: focused text box turns on the game's text input (SDL)", mc -> {
                if (!hub(mc).debugTextInputOn()) return "screen did not claim text input";
                return UiInput.textInput(mc, "textInputEnabled", "TextInputManager.textInputEnabled=false");
            })
            .check("bug: diagnostic body has mod, MC version, loader, and mods", mc -> {
                String body = hub(mc).debugBugBody();
                if (!body.contains("Snappy Blocks")) return "missing mod name";
                if (!body.contains(UiInput.versionName())) return "missing MC version";
                if (!body.contains("Installed mods")) return "missing mods section";
                if (!body.contains("Block blinks once.")) return "missing typed description";
                if (!body.contains("## Settings")) return "missing settings section";
                String home = System.getProperty("user.home", "");
                if (!home.isEmpty() && body.contains(home)) return "home path leaked";
                return null;
            })
            .check("bug: redaction hides player name, uuid, ip, token; keeps 4-part versions", mc -> {
                var u = mc.getUser();
                String in = "Setting user: " + u.getName() + " " + u.getProfileId() + " /203.0.113.7:25565 to 198.51.100.2 --accessToken abc.def mod 1.2.3.4 ok";
                String out = io.github.profetgit.profetsettings.BugDiagnostics.redact(in);
                if (u.getName().length() > 2 && out.contains(u.getName())) return out;
                if (out.contains(u.getProfileId().toString()) || out.contains("203.0.113.7") || out.contains("198.51.100.2") || out.contains("abc.def")) return out;
                return out.contains("1.2.3.4") ? null : "version redacted: " + out;
            })
            .run(mc -> press(mc, hub(mc).debugBugSend()))
            .waitTicks(10)
            .check("bug: Open on GitHub emits pre-filled GitHub issue URL", mc -> {
                ProfetSettings.uriOpener = prevOpener[0];
                if (opened.size() != 1) return "opened=" + opened.size();
                String u = opened.get(0).toString();
                if (!u.startsWith("https://github.com/ProfetGit/snappy-blocks/issues/new?")) return "url=" + u;
                if (!u.contains("title=%5B") || !u.contains("%5D%20Pop%20glitch")) return "title not [version] Pop glitch: " + u;
                if (!u.contains("labels=bug")) return "missing bug label in url: " + u;
                return null;
            })
            .run(mc -> UiInput.escape(hub(mc)))
            .waitTicks(10)
            .check("bug: Escape closes modal and returns to settings", mc -> !hub(mc).debugBugModalOpen() && ProfetSettings.isScreen(mc.gui.screen()) ? null : "modal=" + hub(mc).debugBugModalOpen());
        bugDirect(s);
        bugV2(s);
    }

    // Guided fields, greyed Send, version in the title, View report, saved draft, IME area, Ko-fi, crash prompt.
    private static void bugV2(Script s) {
        List<java.net.URI> opened = new ArrayList<>();
        java.util.function.Consumer<java.net.URI>[] prevOpener = new java.util.function.Consumer[1];
        Path[] crash = new Path[1];
        s.run(mc -> {
                prevOpener[0] = ProfetSettings.uriOpener;
                ProfetSettings.uriOpener = opened::add;
                ProfetSettings.reportEndpoint = "";
            })
            .run(mc -> press(mc, hub(mc).debugBugButton()))
            .waitTicks(10)
            .check("bug v2: Send is greyed out while every field is empty", mc -> {
                String[] f = hub(mc).debugBugFields();
                if (!String.join("", f).isEmpty()) return "fields not empty: " + String.join("|", f);
                return hub(mc).debugBugSendEnabled() ? "send enabled" : null;
            })
            .run(mc -> press(mc, hub(mc).debugBugSend()))
            .waitTicks(5)
            .check("bug v2: pressing greyed Send does nothing (no error)", mc -> opened.isEmpty() && hub(mc).debugBugStatus().isEmpty() ? null : "opened=" + opened + " status=" + hub(mc).debugBugStatus())
            .run(mc -> hub(mc).debugSetBugDraft("Guided", "It blinks.", "1. Place stone\n2. Look at it", "No blink"))
            .waitTicks(5)
            .check("bug v2: Send turns on with text", mc -> hub(mc).debugBugSendEnabled() ? null : "still greyed")
            .check("bug v2: guided fields become report sections", mc -> {
                String b = hub(mc).debugBugBody();
                for (String want : new String[] {"### What happened\nIt blinks.", "### Steps to reproduce\n1. Place stone\n2. Look at it", "### Expected\nNo blink"}) {
                    if (!b.contains(want)) return "missing " + want.replace("\n", "/");
                }
                return null;
            })
            .check("bug v2: the issue title starts with the mod version", mc -> {
                String u = hub(mc).debugBugUrl();
                String v = ProfetSettings.modVersion(SnappyBlocks.ID);
                return u.contains("title=%5B" + v) ? null : "version " + v + " not in " + u.substring(0, Math.min(160, u.length()));
            })
            .run(mc -> {
                int[] tp = hub(mc).debugBugTitle();
                click(mc, tp[0], tp[1]);
            })
            .waitTicks(5)
            .check("bug v2: the IME popup area follows the caret", mc -> {
                return UiInput.textInput(mc, "hasTextInputArea", "no text input area set");
            })
            .run(mc -> hub(mc).debugSetBugDraft("Guided", "It blinks. " + "x".repeat(1250), "1. Place stone\n2. Look at it", "No blink"));
        capture(s, "ui-bug-fields");
        s.run(mc -> press(mc, hub(mc).debugBugView()))
            .waitTicks(10)
            .check("bug v2: View report shows the exact report, read-only", mc -> {
                if (!hub(mc).debugBugViewing()) return "not viewing";
                String t = hub(mc).debugBugViewText();
                if (!t.contains("Guided") || !t.contains("### Steps to reproduce") || !t.contains("## Environment")) return "view text: " + t.substring(0, Math.min(200, t.length()));
                String home = System.getProperty("user.home", "");
                if (!home.isEmpty() && t.contains(home)) return "home path visible";
                return null;
            });
        capture(s, "ui-bug-view");
        s.run(mc -> UiInput.escape(hub(mc)))
            .waitTicks(5)
            .check("bug v2: Escape in the view goes back to editing", mc -> !hub(mc).debugBugViewing() && hub(mc).debugBugModalOpen() ? null : "viewing=" + hub(mc).debugBugViewing())
            .run(mc -> {
                hub(mc).debugSetBugDraft("Kept", "Draft text", "Step one", "Expected one");
                hub(mc).debugSaveBugDraft();
                UiInput.escape(hub(mc));
                mc.gui.setScreen(null);
                ProfetSettings.debugReloadBugState();
            })
            .waitTicks(5)
            .run(mc -> mc.gui.setScreen(ConfigScreens.create(null)))
            .waitTicks(10)
            .run(mc -> press(mc, hub(mc).debugBugButton()))
            .waitTicks(10)
            .check("bug v2: the draft survives a restart (read back from disk)", mc -> {
                String[] f = hub(mc).debugBugFields();
                return f[0].equals("Kept") && f[1].equals("Draft text") && f[2].equals("Step one") && f[3].equals("Expected one") ? null : String.join("|", f);
            })
            .run(mc -> {
                hub(mc).debugSetBugDraft("", "", "", "");
                UiInput.escape(hub(mc));
                opened.clear();
            })
            .waitTicks(5)
            .check("bug v2: Ko-fi button sits in the footer", mc -> hub(mc).debugKofi() != null ? null : "no Ko-fi button")
            .run(mc -> press(mc, hub(mc).debugKofi()))
            .waitTicks(5)
            .check("bug v2: Ko-fi opens ko-fi.com/profetgit", mc -> opened.size() == 1 && opened.get(0).toString().equals("https://ko-fi.com/profetgit") ? null : "opened=" + opened)
            .run(mc -> {
                mc.gui.setScreen(null);
                ProfetSettings.debugResetBugState();
                try {
                    Path dir = mc.gameDirectory.toPath().resolve("crash-reports");
                    Files.createDirectories(dir);
                    crash[0] = dir.resolve("crash-2000-01-01_00.00.00-client.txt");
                    Files.writeString(crash[0], "---- Minecraft Crash Report ----\n// Test fixture\n\nTime: 2000-01-01\nDescription: Rendering overlay\n\n"
                        + "java.lang.NullPointerException: test\n\tat io.github.profetgit.snappyblocks.pop.Pops.tick(Pops.java:1)\n\n"
                        + "A detailed walkthrough of the error, its code path and all known details is as follows:\n-- System Details --\nFabric Mods: tidypockets\n");
                    long start = java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
                    Files.setLastModifiedTime(crash[0], java.nio.file.attribute.FileTime.fromMillis(start - 60_000));
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
                mc.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
            })
            .waitTicks(40)
            .check("bug v2: the title screen offers to report the crash", mc -> toast(mc) != null ? null : "no crash toast on " + mc.gui.screen());
        capture(s, "ui-crash-toast");
        s.run(mc -> {
                var t = toast(mc);
                click(mc, t.getX() + t.getWidth() / 2, t.getY() + t.getHeight() / 2);
            })
            .waitTicks(20)
            .check("bug v2: the crash toast opens the report with the crash attached", mc -> {
                if (!ProfetSettings.isScreen(mc.gui.screen()) || !hub(mc).debugBugModalOpen()) return "screen " + mc.gui.screen();
                String[] f = hub(mc).debugBugFields();
                if (!f[0].startsWith("Crash: Rendering overlay")) return "title " + f[0];
                String b = hub(mc).debugBugBody();
                return b.contains("Latest crash report") && b.contains("snappyblocks.pop.Pops.tick") ? null : "crash not attached";
            });
        capture(s, "ui-crash-report");
        s.run(mc -> {
                UiInput.escape(hub(mc));
                mc.gui.setScreen(null);
                io.github.profetgit.profetsettings.CrashPrompt.rescanForTest();
            })
            .check("bug v2: a crash report is offered only once", mc -> io.github.profetgit.profetsettings.CrashPrompt.pending() ? "offered again" : null)
            .run(mc -> {
                ProfetSettings.uriOpener = prevOpener[0];
                ProfetSettings.debugResetBugState();
                try {
                    Files.deleteIfExists(crash[0]);
                } catch (IOException ignored) {}
            });
    }

    private static io.github.profetgit.profetsettings.CrashToast toast(Minecraft mc) {
        if (mc.gui.screen() == null) return null;
        for (var c : mc.gui.screen().children()) if (c instanceof io.github.profetgit.profetsettings.CrashToast t) return t;
        return null;
    }

    // In-game send through the report Worker: a local stand-in answers like the Worker; then an unreachable one.
    private static void bugDirect(Script s) {
        List<String> got = new ArrayList<>();
        com.sun.net.httpserver.HttpServer[] srv = new com.sun.net.httpserver.HttpServer[1];
        List<java.net.URI> opened = new ArrayList<>();
        java.util.function.Consumer<java.net.URI>[] prevOpener = new java.util.function.Consumer[1];
        s.run(mc -> {
                try {
                    srv[0] = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
                srv[0].createContext("/v1/report", ex -> {
                    got.add(ex.getRequestHeaders().getFirst("user-agent") + "\n" + new String(ex.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                    byte[] out = "{\"ok\":true,\"number\":42,\"url\":\"https://github.com/x/y/issues/42\"}".getBytes();
                    ex.getResponseHeaders().add("content-type", "application/json");
                    ex.sendResponseHeaders(200, out.length);
                    ex.getResponseBody().write(out);
                    ex.close();
                });
                srv[0].start();
                ProfetSettings.reportEndpoint = "http://127.0.0.1:" + srv[0].getAddress().getPort() + "/v1/report";
                prevOpener[0] = ProfetSettings.uriOpener;
                ProfetSettings.uriOpener = opened::add;
            })
            .run(mc -> press(mc, hub(mc).debugBugButton()))
            .waitTicks(10)
            .run(mc -> hub(mc).debugSetBugDraft("Direct send", "Sent from inside the game."))
            .run(mc -> press(mc, hub(mc).debugBugSend()))
            .waitTicks(40)
            .check("bug: Send report posts to the report server in game", mc -> {
                if (got.size() != 1) return "requests=" + got.size() + " status=" + hub(mc).debugBugStatus();
                String r = got.get(0);
                if (!r.startsWith("ProfetSettings/")) return "user-agent: " + r.lines().findFirst().orElse("");
                var o = JsonParser.parseString(r.substring(r.indexOf('\n') + 1)).getAsJsonObject();
                if (!o.get("mod").getAsString().equals("snappy_blocks")) return "mod " + o.get("mod");
                if (!o.get("title").getAsString().matches("\\[[^\\]]+\\] Direct send")) return "title " + o.get("title");
                if (!o.get("body").getAsString().contains("Sent from inside the game.")) return "body";
                return opened.isEmpty() ? null : "browser opened";
            })
            .check("bug: the modal shows the report number and locks Send", mc -> hub(mc).debugBugSentNumber() == 42 && hub(mc).debugBugStatus().contains("42") ? null : "status=" + hub(mc).debugBugStatus());
        capture(s, "ui-bug-sent");
        s.run(mc -> press(mc, hub(mc).debugBugSend()))
            .waitTicks(20)
            .check("bug: a second press does not send again", mc -> got.size() == 1 ? null : "requests=" + got.size())
            .run(mc -> {
                UiInput.escape(hub(mc));
                srv[0].stop(0);
                ProfetSettings.reportEndpoint = "http://127.0.0.1:1/v1/report";
                mc.keyboardHandler.setClipboard("");
            })
            .waitTicks(5)
            .run(mc -> press(mc, hub(mc).debugBugButton()))
            .waitTicks(10)
            .run(mc -> hub(mc).debugSetBugDraft("Offline", "No server here."))
            .run(mc -> press(mc, hub(mc).debugBugSend()))
            .waitTicks(60)
            .check("bug: unreachable server copies the report and offers GitHub", mc ->
                !hub(mc).debugBugSending() && hub(mc).debugBugSentNumber() == 0 && mc.keyboardHandler.getClipboard().contains("No server here.")
                    ? null : "status=" + hub(mc).debugBugStatus() + " sending=" + hub(mc).debugBugSending())
            .run(mc -> press(mc, hub(mc).debugBugSend()))
            .waitTicks(10)
            .check("bug: after a failed send, the button opens GitHub", mc -> {
                ProfetSettings.uriOpener = prevOpener[0];
                ProfetSettings.reportEndpoint = "";
                return opened.size() == 1 && opened.get(0).toString().contains("/issues/new?") ? null : "opened=" + opened;
            })
            .run(mc -> UiInput.escape(hub(mc)));
    }

    // ---- both mods in one screen (only with Tidy Pockets installed) ----

    private static void two(Script s) {
        if (!tidy()) return;
        s.run(mc -> {
                reset();
                opening = new Gson().toJson(Config.get());
                openingTidy = tidyFile();
                mc.gui.setScreen(new PauseScreen(true));
            })
            .waitTicks(15);
        //? if fabric {
        s.check("two mods: the Game Menu still has exactly one golem button", mc -> golems(mc).size() == 1 ? null : "golem buttons: " + golems(mc).size());
        capture(s, "ui-two-0-menu");
        s.run(mc -> {
            Button b = golems(mc).get(0);
            click(mc, b.getX() + 10, b.getY() + 10);
        });
        //?} else {
        /*s.run(mc -> mc.gui.setScreen(ConfigScreens.create(mc.gui.screen())));
        *///?}
        s.waitTicks(30)
            .check("two mods: both are listed", mc -> hub(mc).debugMods().stream().map(m -> m.id).toList().equals(List.of(SnappyBlocks.ID, "tidypockets"))
                ? null : "mods " + hub(mc).debugMods().stream().map(m -> m.id).toList())
            .run(mc -> openingTidyValues = values(hub(mc), "tidypockets"));
        capture(s, "ui-two-a-snappy");
        click(s, "calm", -1f);
        selectMod(s, "tidypockets");
        capture(s, "ui-two-b-tidy");
        click(s, "sortSound", -1f);
        click(s, "wheelInvert", -1f);
        s.check("two mods: an option of each changed", mc -> Config.get().calm && !values(hub(mc), "tidypockets").equals(openingTidyValues)
                && !tidyFile().equals(openingTidy) ? null : "calm " + Config.get().calm);
        selectMod(s, SnappyBlocks.ID);
        capture(s, "ui-two-c-badges");
        List<ModDef> kept = new ArrayList<>();
        s.run(mc -> {
                kept.clear();
                kept.addAll(hub(mc).debugMods());
                press(mc, hub(mc).debugCancel());
            })
            .waitTicks(10)
            .check("two mods: Cancel puts Snappy Blocks back", mc -> opening.equals(new Gson().toJson(Config.get())) ? null : "config " + new Gson().toJson(Config.get()))
            .check("two mods: Cancel puts Tidy Pockets back", mc -> {
                List<Object> now = new ArrayList<>();
                for (ModDef m : kept) if (m.id.equals("tidypockets")) for (Setting o : m.all()) now.add(o.get.get());
                return now.equals(openingTidyValues) && json(tidyFile()).equals(json(openingTidy)) ? null : "tidy file " + tidyFile();
            })
            .check("two mods: Cancel returns to the Game Menu", mc -> mc.gui.screen() instanceof PauseScreen ? null : "screen " + mc.gui.screen())
            .run(mc -> mc.gui.setScreen(ConfigScreens.create(null))).waitTicks(30);
        click(s, "pickup", -1f);
        selectMod(s, "tidypockets");
        click(s, "sortSound", -1f);
        s.run(mc -> press(mc, hub(mc).debugMenu())).waitTicks(20)
            .run(mc -> press(mc, hub(mc).debugMenuItem(1))).waitTicks(10)
            .check("two mods: Reset all mods resets both", mc -> {
                for (ModDef m : hub(mc).debugMods()) if (m.changedCount() != 0) return m.id + " still changed";
                return null;
            })
            .run(mc -> press(mc, hub(mc).debugCancel())).waitTicks(10);
    }

    private static void selectMod(Script s, String id) {
        s.run(mc -> {
                int[] p = hub(mc).debugModPoint(id);
                click(mc, p[0], p[1]);
            })
            .waitTicks(30)
            .check("two mods: the list selects " + id, mc -> hub(mc).debugSelected().id.equals(id) ? null : "selected " + hub(mc).debugSelected().id);
    }

    // ---- helpers ----

    private static boolean tidy() {
        return SnappyBlocks.platform().isModLoaded("tidypockets");
    }

    private static void reset() {
        Config c = Config.get();
        Config d = new Config();
        c.enabled = d.enabled;
        c.pop = d.pop;
        c.pulse = d.pulse;
        c.pulseStyle = d.pulseStyle;
        c.pulseStrength = d.pulseStrength;
        c.burst = d.burst;
        c.breakStyle = d.breakStyle;
        c.breakStrength = d.breakStrength;
        c.chips = d.chips;
        c.placeEffect = d.placeEffect;
        c.placeStrength = d.placeStrength;
        c.cubes = d.cubes;
        c.items = d.items;
        c.pickup = d.pickup;
        c.intensity = d.intensity;
        c.calm = d.calm;
        Config.save();
    }

    private static String read(String name) {
        Path f = SnappyBlocks.platform().configDir().resolve(name);
        try {
            return Files.readString(f);
        } catch (IOException e) {
            return "";
        }
    }

    private static String file() {
        return read("snappy_blocks.json").replaceAll("\\s", "");
    }

    private static String tidyFile() {
        return read("tidypockets.json");
    }

    private static Object json(String text) {
        return JsonParser.parseString(text.isEmpty() ? "{}" : text);
    }

    private static void gui(Minecraft mc, int scale) {
        mc.options.guiScale().set(scale);
        UiInput.resize(mc);
    }

    private static void capture(Script s, String name) {
        s.run(mc -> SelfTest.startCapture(name, 1)).waitTicks(3);
    }

    private static ProfetSettingsScreen hub(Minecraft mc) {
        return (ProfetSettingsScreen) mc.gui.screen();
    }

    private static List<Object> values(ProfetSettingsScreen hub, String id) {
        List<Object> out = new ArrayList<>();
        for (ModDef m : hub.debugMods()) if (m.id.equals(id)) for (Setting o : m.all()) out.add(o.get.get());
        return out;
    }

    private static Setting setting(Minecraft mc, String id, String key) {
        for (ModDef m : hub(mc).debugMods()) if (m.id.equals(id)) return m.find(key);
        throw new IllegalStateException("no mod " + id);
    }

    private static List<Button> golems(Minecraft mc) {
        List<Button> out = new ArrayList<>();
        for (var w : mc.gui.screen().children()) if (w instanceof Button b && b.getClass().getSimpleName().equals("GolemButton")) out.add(b);
        return out;
    }

    private static void click(Minecraft mc, int guiX, int guiY) {
        UiInput.click(mc, guiX, guiY);
    }

    private static void click(Script s, String key, float frac) {
        s.run(mc -> hub(mc).debugRevealRow(key))
            .waitTicks(4)
            .run(mc -> {
                int[] p = hub(mc).debugPoint(key, frac);
                if (p == null) throw new IllegalStateException("no control for " + key);
                click(mc, p[0], p[1]);
            })
            .waitTicks(15);
    }

    private static void press(Minecraft mc, int[] p) {
        if (p == null) throw new IllegalStateException("nothing to press");
        hub(mc).debugPress(p[0], p[1]);
        hub(mc).debugRelease(p[0], p[1]);
    }
}
