//? if >=1.8 {
package org.taumc.celeritas.impl.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.options.GameOptions;
import org.taumc.celeritas.api.options.structure.OptionFlag;
import org.taumc.celeritas.api.options.structure.OptionStorage;
import org.taumc.celeritas.impl.Celeritas;

import java.util.Set;

public class MinecraftOptionsStorage implements OptionStorage<GameOptions> {
    private final Minecraft client = Minecraft.getInstance();

    @Override
    public GameOptions getData() {
        return this.client.options;
    }

    @Override
    public void save(Set<OptionFlag> flags) {
        this.getData().save();
        Celeritas.logger().info("Flushed changes to Minecraft configuration");
    }
}
//?}
