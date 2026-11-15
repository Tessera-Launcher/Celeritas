package org.taumc.celeritas.patcher;

import java.util.Collection;

import org.spongepowered.asm.launch.platform.IMixinPlatformAgent;
import org.spongepowered.asm.launch.platform.IMixinPlatformServiceAgent;
import org.spongepowered.asm.launch.platform.MixinPlatformAgentAbstract;
import org.spongepowered.asm.launch.platform.MixinPlatformManager;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.util.Constants;

/** Tells Mixin it is running on a plain client JVM. */
public final class PatcherPlatformService extends MixinPlatformAgentAbstract implements IMixinPlatformServiceAgent {
    @Override
    public void init() {
    }

    @Override
    public String getSideName() {
        return Constants.SIDE_CLIENT;
    }

    @Override
    public AcceptResult accept(MixinPlatformManager manager, IContainerHandle handle) {
        return AcceptResult.ACCEPTED;
    }

    @Override
    public Collection<IContainerHandle> getMixinContainers() {
        return null;
    }
}
