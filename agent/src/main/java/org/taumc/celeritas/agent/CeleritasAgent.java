package org.taumc.celeritas.agent;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;

/**
 * One agent that does two things:
 *
 * <ol>
 *   <li>applies Celeritas' 1.8.9 mixins at class-load time;</li>
 *   <li>runs Tessera's LWJGL3 shim/rewriter afterwards, so the calls Celeritas
 *       injects are rewritten as well.</li>
 * </ol>
 *
 * The LWJGL3 agent is embedded in this jar (its shim classes are at the root of
 * this jar). Keeping both halves in one agent makes the transformer order
 * deterministic: Celeritas first, LWJGL3 second.
 */
public final class CeleritasAgent {
    private static final Logger LOG = Logger.getLogger("celeritas-agent");

    private CeleritasAgent() {
    }

    public static void premain(String args, Instrumentation instrumentation) {
        try {
            // Make sure the agent jar (mixins, Celeritas classes, LWJGL3 shim) is
            // visible to the system loader. We also put it on -cp in the test
            // launcher, but this keeps the agent self-contained.
            URL self = CeleritasAgent.class.getProtectionDomain().getCodeSource().getLocation();
            java.io.File selfFile = new java.io.File(self.toURI());
            instrumentation.appendToSystemClassLoaderSearch(new java.util.jar.JarFile(selfFile));

            requireFastutil();
            MixinBootstrap.init();
            MixinBootstrap.getPlatform().inject();
            // MixinExtras needs the active transformer to register its extensions.
            MixinRuntime.install();
            MixinExtrasBootstrap.init();
            Mixins.addConfiguration("mixins.celeritas.agent.json");

            setCeleritasVersion();

            // Register the Mixin transformer first: the LWJGL3 transformer must see
            // the bytecode Celeritas injects.
            instrumentation.addTransformer(new CeleritasTransformer(), false);

            // Now boot Tessera's LWJGL3 agent. It appends the same jar again and
            // registers its own transformer after ours.
            Class<?> lwjgl3Agent = Class.forName("TesseraLwjgl3Agent", true, ClassLoader.getSystemClassLoader());
            Method premain = lwjgl3Agent.getMethod("premain", String.class, Instrumentation.class);
            premain.invoke(null, args, instrumentation);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "Celeritas agent failed to start", t);
            throw new RuntimeException("Celeritas agent failed to start", t);
        }
    }

    private static void requireFastutil() {
        try {
            Class.forName("it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap", false,
                    ClassLoader.getSystemClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Celeritas needs fastutil on the launch classpath; add fastutil-8.5.x.jar to the game libraries",
                    e);
        }
    }

    private static void setCeleritasVersion() {
        try {
            Class<?> celeritas = Class.forName("org.taumc.celeritas.impl.Celeritas");
            Field version = celeritas.getField("VERSION");
            version.set(null, "2.5.0-agent");
        } catch (Throwable ignored) {
            // Version is only used for the F3 overlay; not fatal.
        }
    }
}
