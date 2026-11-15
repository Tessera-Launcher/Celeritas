package org.taumc.celeritas.patcher;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.transformers.TreeTransformer;

/**
 * Thin reflective bridge to Mixin's package-private transformer.
 *
 * Mixin's public bootstrap is LaunchWrapper-shaped; the transformer itself is
 * not. The offline applier used by this project uses the same reflection.
 */
final class MixinBridge {
    private static final Object TRANSFORMER;
    private static final Method TRANSFORM_CLASS;

    static {
        try {
            Class<?> type = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer");
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            TRANSFORMER = constructor.newInstance();

            TRANSFORM_CLASS = type.getDeclaredMethod("transformClass", MixinEnvironment.class, String.class, ClassNode.class);
            TRANSFORM_CLASS.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private MixinBridge() {
    }

    static void install() {
        MixinEnvironment.getEnvironment(MixinEnvironment.Phase.DEFAULT)
                .setActiveTransformer((IMixinTransformer) TRANSFORMER);
    }

    static boolean transform(ClassNode node) throws Exception {
        MixinEnvironment environment = MixinEnvironment.getEnvironment(MixinEnvironment.Phase.DEFAULT);
        return (boolean) TRANSFORM_CLASS.invoke(TRANSFORMER, environment, node.name.replace('/', '.'), node);
    }
}
