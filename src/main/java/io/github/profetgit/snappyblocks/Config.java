package io.github.profetgit.snappyblocks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/**
 * `config/snappy_blocks.json`, re-read when the file changes (PRD §5.6). Unknown keys are kept and missing keys get
 * their defaults (PRD §8).
 */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve(SnappyBlocks.ID + ".json");

    public boolean enabled = true;
    public boolean pop = true;
    public boolean pulse = true;
    public boolean burst = true;
    public boolean items = true;
    public boolean pickup = true;
    /** 50-150 (%), scales every amplitude. */
    public int intensity = 100;
    /** Reduced motion: amplitudes x0.3, no hit-stop, no hop. */
    public boolean calm = false;
    /** Feel numbers (see {@link Tune}); missing keys take the defaults. */
    public Map<String, Double> tune = new LinkedHashMap<>(Tune.DEFAULTS);

    private static Config current = new Config();
    private static long stamp = -1;
    private static int poll;

    public static Config get() {
        return current;
    }

    static void load() {
        JsonObject raw = new JsonObject();
        try {
            if (Files.exists(FILE)) {
                JsonElement e = JsonParser.parseString(Files.readString(FILE));
                if (e.isJsonObject()) raw = e.getAsJsonObject();
            }
        } catch (IOException | RuntimeException e) {
            SnappyBlocks.LOG.warn("Snappy Blocks: config unreadable, using defaults ({})", e.toString());
        }
        Config c;
        try {
            c = GSON.fromJson(raw, Config.class);
        } catch (RuntimeException e) {
            SnappyBlocks.LOG.warn("Snappy Blocks: config has a bad value, using defaults ({})", e.toString());
            c = new Config();
        }
        c.intensity = Math.clamp(c.intensity, 50, 150);
        if (c.tune == null) c.tune = new LinkedHashMap<>();
        for (var e : Tune.DEFAULTS.entrySet()) c.tune.putIfAbsent(e.getKey(), e.getValue());
        current = c;
        JsonObject merged = GSON.toJsonTree(c).getAsJsonObject();
        for (String k : raw.keySet()) if (!merged.has(k)) merged.add(k, raw.get(k));
        try {
            Files.createDirectories(FILE.getParent());
            String text = GSON.toJson(merged);
            if (!Files.exists(FILE) || !Files.readString(FILE).equals(text)) Files.writeString(FILE, text);
            stamp = Files.getLastModifiedTime(FILE).toMillis();
        } catch (IOException e) {
            SnappyBlocks.LOG.warn("Snappy Blocks: could not write the config ({})", e.toString());
        }
    }

    /** Every 40 client ticks: reload when the file's modification time changed. */
    static void pollReload() {
        if (++poll < 40) return;
        poll = 0;
        try {
            if (Files.exists(FILE) && Files.getLastModifiedTime(FILE).toMillis() != stamp) load();
        } catch (IOException ignored) {
        }
    }

    /** The intensity as a factor, with Calm applied. */
    public double amplitude() {
        return intensity / 100.0 * (calm ? 0.3 : 1.0);
    }
}
