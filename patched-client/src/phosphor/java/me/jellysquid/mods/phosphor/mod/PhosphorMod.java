package me.jellysquid.mods.phosphor.mod;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 1.8.9 entry point data. This used to be a Fabric {@code ModInitializer}; the
 * standalone patched client has no mod loader, so the state is initialized
 * statically instead.
 */
public class PhosphorMod {
    public static final Logger LOGGER = LogManager.getLogger("phosphor-legacy");
    public static final PhosphorConfig CONFIG = PhosphorConfig.loadConfig();
}
