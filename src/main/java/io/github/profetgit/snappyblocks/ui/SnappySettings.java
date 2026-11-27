package io.github.profetgit.snappyblocks.ui;

import io.github.profetgit.profetsettings.ModDef;
import io.github.profetgit.profetsettings.ProfetSettings;
import io.github.profetgit.profetsettings.Setting;
import io.github.profetgit.snappyblocks.Config;
import io.github.profetgit.snappyblocks.SnappyBlocks;
import io.github.profetgit.snappyblocks.burst.Break;
import io.github.profetgit.snappyblocks.burst.Puff;
import io.github.profetgit.snappyblocks.pulse.Pulses;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.client.resources.language.I18n;

// Snappy Blocks' options for the shared Profet screen, bound to the same Config fields and file as the old screen. The
// feel numbers in the file's "tune" object are not listed. Config.get() each time: the file is re-read when it changes.
public final class SnappySettings {
    private SnappySettings() {}

    private static String t(String key) {
        return I18n.get("snappy_blocks." + key);
    }

    private static Setting bool(String k, Function<Config, Boolean> get, BiConsumer<Config, Boolean> set) {
        boolean def = get.apply(new Config());
        return Setting.bool(k, t("setting." + k), t("setting." + k + ".desc"), () -> get.apply(Config.get()), v -> set.accept(Config.get(), v), def);
    }

    // one row for a feature's switch and its animations: Off is the switch false, so older config files keep working
    private static String[] choices(String key, String[] styles) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of("off"), java.util.Arrays.stream(styles))
            .map(e -> t(key + e)).toArray(String[]::new);
    }

    public static ModDef create() {
        ModDef m = new ModDef(SnappyBlocks.ID, "Snappy Blocks", "Snappy", ProfetSettings.modVersion(SnappyBlocks.ID), t("tagline"), "snappy_blocks:icon.png")
            .issues("ProfetGit/snappy-blocks")
            .diagnostics(() -> "Shader pack active: " + io.github.profetgit.snappyblocks.pop.ShaderPack.active()
                + (io.github.profetgit.snappyblocks.pop.SodiumHidden.wired ? ", Sodium hide wired" : ""));
        m.onChange = Config::save;
        m.group(t("group.general"),
            bool("enabled", c -> c.enabled, (c, v) -> c.enabled = v).preview("general"),
            Setting.range("intensity", t("setting.intensity"), t("setting.intensity.desc"), 50, 150,
                () -> Config.get().intensity, v -> Config.get().intensity = v, new Config().intensity).format(v -> v + "%").preview("general"),
            bool("calm", c -> c.calm, (c, v) -> c.calm = v).preview("general"));
        m.group(t("group.blocks"),
            bool("pop", c -> c.pop, (c, v) -> c.pop = v).preview("pop"),
            Setting.choice("pulseStyle", t("setting.pulseStyle"), t("setting.pulseStyle.desc"), choices("mine.", Pulses.STYLES),
                () -> Config.get().pulse ? 1 + Pulses.style().ordinal() : 0,
                i -> {
                    Config.get().pulse = i > 0;
                    if (i > 0) Config.get().pulseStyle = Pulses.STYLES[i - 1];
                }, 1).preview("pulse"),
            Setting.range("pulseStrength", t("setting.pulseStrength"), t("setting.pulseStrength.desc"), 25, 200,
                () -> Config.get().pulseStrength, v -> Config.get().pulseStrength = v, new Config().pulseStrength).format(v -> v + "%").preview("pulse"),
            Setting.choice("breakStyle", t("setting.breakStyle"), t("setting.breakStyle.desc"), choices("break.", Break.STYLES),
                () -> Config.get().burst ? 1 + Break.style().ordinal() : 0,
                i -> {
                    Config.get().burst = i > 0;
                    if (i > 0) Config.get().breakStyle = Break.STYLES[i - 1];
                }, 1).preview("burst"),
            Setting.range("breakStrength", t("setting.breakStrength"), t("setting.breakStrength.desc"), 25, 200,
                () -> Config.get().breakStrength, v -> Config.get().breakStrength = v, new Config().breakStrength).format(v -> v + "%").preview("burst"));
        m.group(t("group.particles"),
            bool("chips", c -> c.chips, (c, v) -> c.chips = v).preview("chips"),
            Setting.choice("placeEffect", t("setting.placeEffect"), t("setting.placeEffect.desc"),
                java.util.Arrays.stream(Puff.EFFECTS).map(e -> t("place." + e)).toArray(String[]::new),
                () -> Math.max(0, java.util.Arrays.asList(Puff.EFFECTS).indexOf(Config.get().placeEffect)),
                i -> Config.get().placeEffect = Puff.EFFECTS[i], 0).preview("place"),
            Setting.range("placeStrength", t("setting.placeStrength"), t("setting.placeStrength.desc"), 25, 200,
                () -> Config.get().placeStrength, v -> Config.get().placeStrength = v, new Config().placeStrength).format(v -> v + "%").preview("place"),
            bool("cubes", c -> c.cubes, (c, v) -> c.cubes = v).preview("cubes"));
        m.group(t("group.items"),
            bool("items", c -> c.items, (c, v) -> c.items = v).preview("drops"),
            bool("pickup", c -> c.pickup, (c, v) -> c.pickup = v).preview("pickup"));
        m.scene("general", d -> new GeneralScene()).scene("pop", d -> new PopScene()).scene("pulse", d -> new PulseScene())
            .scene("burst", d -> new BurstScene()).scene("chips", d -> new ChipsScene())
            .scene("place", d -> new PlaceScene()).scene("cubes", d -> new CubesScene()).scene("drops", d -> new DropsScene()).scene("pickup", d -> new PickupScene());
        m.preset(t("preset.off"), "enabled", false);
        m.preset(t("preset.calm"), "calm", true);
        m.preset(t("preset.default"));
        m.preset(t("preset.lively"), "intensity", 130);
        String[] ids = {"off", "calm", "default", "lively"};
        for (int i = 0; i < ids.length; i++) {
            java.util.Map<String, Object> values = m.presets.get(i).values();
            m.presetPreview(t("preset." + ids[i]), "preset." + ids[i], t("preset." + ids[i] + ".note"));
            m.scene("preset." + ids[i], d -> new PresetScene(new GeneralScene(), values));
        }
        return m;
    }
}
