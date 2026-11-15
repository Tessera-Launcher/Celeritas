package org.taumc.celeritas.agent;

import org.spongepowered.asm.service.IMixinServiceBootstrap;

/** Service-loader entry point for {@link AgentMixinService}. */
public final class AgentMixinServiceBootstrap implements IMixinServiceBootstrap {
    @Override
    public String getName() {
        return "celeritas-agent";
    }

    @Override
    public String getServiceClassName() {
        return AgentMixinService.class.getName();
    }

    @Override
    public void bootstrap() {
    }
}
