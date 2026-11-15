package org.taumc.celeritas.agent;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.transformers.MixinClassWriter;

/** Applies Celeritas' mixins to classes as they are loaded. */
final class CeleritasTransformer implements ClassFileTransformer {
    private static final Logger LOG = Logger.getLogger("celeritas-agent");

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null || className.startsWith("java/") || className.startsWith("javax/")
                || className.startsWith("jdk/") || className.startsWith("sun/") || className.startsWith("com/sun/")
                || className.startsWith("org/w3c/") || className.startsWith("org/xml/")
                || className.startsWith("org/spongepowered/") || className.startsWith("com/llamalad7/")
                || className.startsWith("org/objectweb/asm/") || className.startsWith("org/taumc/celeritas/agent/")
                || className.startsWith("org/taumc/celeritas/mixin/")
                || className.startsWith("org/lwjgl/") || className.startsWith("tessera/")) {
            return null;
        }

        // ASM 9.8 cannot read JDK 28 (major 72) classes; they are never mixin
        // targets anyway.
        int major = ((classfileBuffer[6] & 0xff) << 8) | (classfileBuffer[7] & 0xff);
        if (major > 69) {
            return null;
        }

        try {
            ClassNode node = new ClassNode();
            new ClassReader(classfileBuffer).accept(node, 0);

            if (!MixinRuntime.transform(node)) {
                return null;
            }

            ClassWriter writer = new MixinClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            node.accept(writer);
            byte[] result = writer.toByteArray();
            LOG.fine(() -> "Celeritas patched " + node.name);
            return result;
        } catch (Throwable t) {
            // A failed mixin must be loud, but it must not take the JVM down before
            // the game can print its own diagnostic.
            LOG.log(Level.SEVERE, "Celeritas failed to transform " + className, t);
            return null;
        }
    }
}
