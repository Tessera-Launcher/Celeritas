package org.taumc.celeritas.impl;

/**
 * 1.8.9 entry point data.  This used to implement Fabric's
 * {@code ClientModInitializer} and look the version up through
 * {@code FabricLoader}; the standalone agent has neither, and loading the class
 * from the F3 overlay then failed with {@code NoClassDefFoundError}.
 */
public class Celeritas {
    public static final String MODID = "celeritas";
    public static final String VERSION = "2.5.0-agent";
}
