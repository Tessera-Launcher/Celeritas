package org.taumc.celeritas.patcher;

import org.spongepowered.asm.service.IMixinServiceBootstrap;

/** Service-loader entry point for {@link PatcherMixinService}. */
public final class PatcherMixinServiceBootstrap implements IMixinServiceBootstrap {
    @Override
    public String getName() {
        return "celeritas-patcher";
    }

    @Override
    public String getServiceClassName() {
        return PatcherMixinService.class.getName();
    }

    @Override
    public void bootstrap() {
    }
}
