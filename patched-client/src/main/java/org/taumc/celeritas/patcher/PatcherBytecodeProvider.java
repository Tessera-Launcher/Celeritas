package org.taumc.celeritas.patcher;

import java.io.IOException;
import java.io.InputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.service.IClassBytecodeProvider;

/** Reads target classes from the game's system class loader. */
final class PatcherBytecodeProvider implements IClassBytecodeProvider {
    private final ClassLoader loader = ClassLoader.getSystemClassLoader();

    @Override
    public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
        return getClassNode(name, false, 0);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
        return getClassNode(name, runTransformers, 0);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers, int readerFlags)
            throws ClassNotFoundException, IOException {
        String resource = name.replace('.', '/') + ".class";
        try (InputStream stream = loader.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new ClassNotFoundException(name);
            }
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, readerFlags);
            return node;
        }
    }
}
