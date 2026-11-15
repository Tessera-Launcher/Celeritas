package org.taumc.celeritas.agent;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Collections;

import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IClassProvider;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.IFeatureValidator;
import org.spongepowered.asm.service.IAdviceProvider;
import org.spongepowered.asm.service.IMixinAuditTrail;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.ITransformerProvider;
import org.spongepowered.asm.service.MixinServiceAbstract;

/** Minimal Mixin service for a plain {@code -javaagent} launch. */
public final class AgentMixinService extends MixinServiceAbstract {
    private final AgentClassProvider classProvider = new AgentClassProvider();
    private final AgentBytecodeProvider bytecodeProvider = new AgentBytecodeProvider();
    private final AgentAuditTrail auditTrail = new AgentAuditTrail();

    @Override
    public String getName() {
        return "celeritas-agent";
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public MixinEnvironment.Phase getInitialPhase() {
        return MixinEnvironment.Phase.DEFAULT;
    }

    @Override
    public IClassProvider getClassProvider() {
        return classProvider;
    }

    @Override
    public IClassBytecodeProvider getBytecodeProvider() {
        return bytecodeProvider;
    }

    @Override
    public ITransformerProvider getTransformerProvider() {
        return null;
    }

    @Override
    public IClassTracker getClassTracker() {
        return null;
    }

    @Override
    public IMixinAuditTrail getAuditTrail() {
        return auditTrail;
    }

    @Override
    public IFeatureValidator getFeatureValidator() {
        return null;
    }

    @Override
    public IAdviceProvider getAdviceProvider() {
        return null;
    }

    @Override
    public Collection<String> getPlatformAgents() {
        return Collections.singletonList("org.taumc.celeritas.agent.AgentPlatformService");
    }

    @Override
    public IContainerHandle getPrimaryContainer() {
        return new ContainerHandleVirtual("celeritas");
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        ClassLoader loader = AgentMixinService.class.getClassLoader();
        InputStream stream = loader.getResourceAsStream(name);
        if (stream == null) {
            stream = ClassLoader.getSystemResourceAsStream(name);
        }
        return stream;
    }

    @Override
    public Collection<IContainerHandle> getMixinContainers() {
        return Collections.emptyList();
    }

    static final class AgentClassProvider implements IClassProvider {
        private final ClassLoader loader = ClassLoader.getSystemClassLoader();

        @Override
        public URL[] getClassPath() {
            if (loader instanceof URLClassLoader urlLoader) {
                return urlLoader.getURLs();
            }
            return new URL[0];
        }

        @Override
        public Class<?> findClass(String name) throws ClassNotFoundException {
            return Class.forName(name, false, loader);
        }

        @Override
        public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
            return Class.forName(name, initialize, loader);
        }

        @Override
        public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
            return Class.forName(name, initialize, loader);
        }
    }
}
