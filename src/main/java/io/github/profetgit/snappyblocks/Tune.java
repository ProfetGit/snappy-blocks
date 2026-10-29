package io.github.profetgit.snappyblocks;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Feel numbers, all guesses until the feel checks (PRD §5). They live in the config's "tune" object, which is
 * re-read when the file changes, so a note from a hands-on becomes a number change without a rebuild.
 */
public final class Tune {
    static final Map<String, Double> DEFAULTS = new LinkedHashMap<>();

    public static final String POP_START = def("pop.start", 0.70);
    public static final String POP_PEAK = def("pop.peak", 1.04);
    public static final String POP_TICKS = def("pop.ticks", 5);
    /** Share of pop.ticks spent growing to the peak; the rest settles to 1. */
    public static final String POP_PEAK_AT = def("pop.peakAt", 0.5);

    private Tune() {}

    public static String def(String key, double value) {
        DEFAULTS.put(key, value);
        return key;
    }

    public static double get(String key) {
        String dev = System.getProperty("snappy_blocks.tune." + key);
        if (dev != null && !dev.isEmpty()) return Double.parseDouble(dev);
        Double v = Config.get().tune.get(key);
        return v != null ? v : DEFAULTS.get(key);
    }
}
