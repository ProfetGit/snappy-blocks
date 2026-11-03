package io.github.profetgit.snappyblocks.platform;

import java.nio.file.Path;
import java.util.List;

/** What the mod needs from the mod loader. Each loader's entry class implements it. */
public interface Platform {
    record ModInfo(String id, String name) {}

    String loader();

    Path configDir();

    boolean isModLoaded(String modId);

    List<ModInfo> mods();
}
