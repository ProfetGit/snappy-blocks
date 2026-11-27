package io.github.profetgit.snappyblocks.ui;

import io.github.profetgit.profetsettings.Ctx;
import io.github.profetgit.profetsettings.Gfx;
import io.github.profetgit.profetsettings.Preview;
import io.github.profetgit.snappyblocks.Config;
import java.util.Map;

// A Quick setup's preview: the inner scene plays with that preset's values (every other option at its default) instead
// of the live config, which is put back right after the frame.
final class PresetScene extends Preview {
    private final Preview inner;
    private final Config values = new Config();

    PresetScene(Preview inner, Map<String, Object> preset) {
        this.inner = inner;
        preset.forEach((k, v) -> {
            switch (k) {
                case "enabled" -> values.enabled = (Boolean) v;
                case "calm" -> values.calm = (Boolean) v;
                case "intensity" -> values.intensity = ((Number) v).intValue();
                case "pop" -> values.pop = (Boolean) v;
                case "pulse" -> values.pulse = (Boolean) v;
                case "burst" -> values.burst = (Boolean) v;
                case "chips" -> values.chips = (Boolean) v;
                case "cubes" -> values.cubes = (Boolean) v;
                case "items" -> values.items = (Boolean) v;
                case "pickup" -> values.pickup = (Boolean) v;
                case "popSpeed" -> values.popSpeed = ((Number) v).intValue();
                case "breakSpeed" -> values.breakSpeed = ((Number) v).intValue();
                case "pulseSpeed" -> values.pulseSpeed = ((Number) v).intValue();
                case "pulseStrength" -> values.pulseStrength = ((Number) v).intValue();
                case "breakStrength" -> values.breakStrength = ((Number) v).intValue();
                case "placeStrength" -> values.placeStrength = ((Number) v).intValue();
                default -> {}
            }
        });
    }

    @Override
    public void render(Gfx g, Ctx c, int x, int y, int w, int h) {
        Config live = Config.get();
        Config saved = new Config();
        copy(live, saved);
        copy(values, live);
        try {
            inner.render(g, c, x, y, w, h);
        } finally {
            copy(saved, live);
        }
    }

    private static void copy(Config from, Config to) {
        to.enabled = from.enabled;
        to.pop = from.pop;
        to.pulse = from.pulse;
        to.pulseStyle = from.pulseStyle;
        to.pulseSpeed = from.pulseSpeed;
        to.popSpeed = from.popSpeed;
        to.breakSpeed = from.breakSpeed;
        to.pulseStrength = from.pulseStrength;
        to.burst = from.burst;
        to.breakStyle = from.breakStyle;
        to.breakStrength = from.breakStrength;
        to.chips = from.chips;
        to.placeEffect = from.placeEffect;
        to.placeStrength = from.placeStrength;
        to.cubes = from.cubes;
        to.items = from.items;
        to.pickup = from.pickup;
        to.intensity = from.intensity;
        to.calm = from.calm;
    }
}
