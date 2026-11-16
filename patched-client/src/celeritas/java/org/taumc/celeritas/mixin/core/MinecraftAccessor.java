package org.taumc.celeritas.mixin.core;

import net.minecraft.client.Minecraft;

/**
 * 1.8.9 has a plain static {@code Minecraft.getInstance()}; no Mixin accessor is
 * needed.  The previous static {@code @Accessor("INSTANCE")} generated a
 * mangled static method on {@code Minecraft} but left this interface's default
 * method throwing {@link AssertionError}, which crashed as soon as a world
 * loaded.
 */
public interface MinecraftAccessor {
    static Minecraft celeritas$getInstance() {
        return Minecraft.getInstance();
    }
}
