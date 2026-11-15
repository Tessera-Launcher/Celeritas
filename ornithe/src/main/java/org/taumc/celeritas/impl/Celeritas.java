package org.taumc.celeritas.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.gui.SodiumGameOptions;

/**
 * 1.8.9 entry point data.  This used to implement Fabric's
 * {@code ClientModInitializer} and look the version up through
 * {@code FabricLoader}; the standalone patched client has neither, so the
 * version is baked into the jar instead.
 */
public class Celeritas {
    public static final String MODID = "celeritas";
    public static final String VERSION = "2.5.0-patch3";

    private static final Logger LOGGER = LogManager.getLogger("celeritas");
    private static SodiumGameOptions CONFIG;

    public static Logger logger() {
        return LOGGER;
    }

    public static SodiumGameOptions options() {
        if (CONFIG == null) {
            try {
                CONFIG = SodiumGameOptions.load();
            } catch (Exception e) {
                LOGGER.error("Failed to load Celeritas configuration, using defaults", e);
                CONFIG = new SodiumGameOptions();
                CONFIG.setReadOnly();
            }
        }
        return CONFIG;
    }
}
