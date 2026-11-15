package org.taumc.celeritas.patcher;

import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Rewrites the LWJGL 2 call sites that LWJGL 3 spells differently.
 *
 * <p>The rename table comes from Tessera's GPL-3.0-or-later
 * {@code lwjgl3-agent}; this copy runs at build time so the patched client does
 * not need an agent at runtime.</p>
 */
final class Lwjgl3Rewriter {
    private static final String BRIDGE = "tessera/lwjgl3/Bridge";

    // Generated from the 1.8.9 class files: every LWJGL 2 name the game uses
        // that LWJGL 3 spells differently, checked against the 3.4.3 jars.
    static final String[][] RENAMES = new String[][] {
        {"org/lwjgl/openal/AL10", "alListener", "(ILjava/nio/FloatBuffer;)V", "alListenerfv"},
        {"org/lwjgl/openal/AL10", "alSource", "(IILjava/nio/FloatBuffer;)V", "alSourcefv"},
        {"org/lwjgl/openal/AL10", "alSourceStop", "(Ljava/nio/IntBuffer;)V", "alSourceStopv"},
        {"org/lwjgl/opengl/ARBShaderObjects", "glUniform1ARB", "(ILjava/nio/FloatBuffer;)V", "glUniform1fvARB"},
        {"org/lwjgl/opengl/ARBShaderObjects", "glUniform1ARB", "(ILjava/nio/IntBuffer;)V", "glUniform1ivARB"},
        {"org/lwjgl/opengl/ARBShaderObjects", "glUniform2ARB", "(ILjava/nio/FloatBuffer;)V", "glUniform2fvARB"},
        {"org/lwjgl/opengl/ARBShaderObjects", "glUniform2ARB", "(ILjava/nio/IntBuffer;)V", "glUniform2ivARB"},
        {"org/lwjgl/opengl/ARBShaderObjects", "glUniform3ARB", "(ILjava/nio/FloatBuffer;)V", "glUniform3fvARB"},
        {"org/lwjgl/opengl/ARBShaderObjects", "glUniform3ARB", "(ILjava/nio/IntBuffer;)V", "glUniform3ivARB"},
        {"org/lwjgl/opengl/ARBShaderObjects", "glUniform4ARB", "(ILjava/nio/FloatBuffer;)V", "glUniform4fvARB"},
        {"org/lwjgl/opengl/ARBShaderObjects", "glUniform4ARB", "(ILjava/nio/IntBuffer;)V", "glUniform4ivARB"},
        {"org/lwjgl/opengl/ARBShaderObjects", "glUniformMatrix2ARB", "(IZLjava/nio/FloatBuffer;)V", "glUniformMatrix2fvARB"},
        {"org/lwjgl/opengl/ARBShaderObjects", "glUniformMatrix3ARB", "(IZLjava/nio/FloatBuffer;)V", "glUniformMatrix3fvARB"},
        {"org/lwjgl/opengl/ARBShaderObjects", "glUniformMatrix4ARB", "(IZLjava/nio/FloatBuffer;)V", "glUniformMatrix4fvARB"},
        {"org/lwjgl/opengl/GL11", "glFog", "(ILjava/nio/FloatBuffer;)V", "glFogfv"},
        {"org/lwjgl/opengl/GL11", "glGetFloat", "(ILjava/nio/FloatBuffer;)V", "glGetFloatv"},
        {"org/lwjgl/opengl/GL11", "glGetInteger", "(ILjava/nio/IntBuffer;)V", "glGetIntegerv"},
        {"org/lwjgl/opengl/GL11", "glLight", "(IILjava/nio/FloatBuffer;)V", "glLightfv"},
        {"org/lwjgl/opengl/GL11", "glLightModel", "(ILjava/nio/FloatBuffer;)V", "glLightModelfv"},
        {"org/lwjgl/opengl/GL11", "glMultMatrix", "(Ljava/nio/FloatBuffer;)V", "glMultMatrixf"},
        {"org/lwjgl/opengl/GL11", "glTexEnv", "(IILjava/nio/FloatBuffer;)V", "glTexEnvfv"},
        {"org/lwjgl/opengl/GL11", "glTexGen", "(IILjava/nio/FloatBuffer;)V", "glTexGenfv"},
        {"org/lwjgl/opengl/GL20", "glUniform1", "(ILjava/nio/FloatBuffer;)V", "glUniform1fv"},
        {"org/lwjgl/opengl/GL20", "glUniform1", "(ILjava/nio/IntBuffer;)V", "glUniform1iv"},
        {"org/lwjgl/opengl/GL20", "glUniform2", "(ILjava/nio/FloatBuffer;)V", "glUniform2fv"},
        {"org/lwjgl/opengl/GL20", "glUniform2", "(ILjava/nio/IntBuffer;)V", "glUniform2iv"},
        {"org/lwjgl/opengl/GL20", "glUniform3", "(ILjava/nio/FloatBuffer;)V", "glUniform3fv"},
        {"org/lwjgl/opengl/GL20", "glUniform3", "(ILjava/nio/IntBuffer;)V", "glUniform3iv"},
        {"org/lwjgl/opengl/GL20", "glUniform4", "(ILjava/nio/FloatBuffer;)V", "glUniform4fv"},
        {"org/lwjgl/opengl/GL20", "glUniform4", "(ILjava/nio/IntBuffer;)V", "glUniform4iv"},
        {"org/lwjgl/opengl/GL20", "glUniformMatrix2", "(IZLjava/nio/FloatBuffer;)V", "glUniformMatrix2fv"},
        {"org/lwjgl/opengl/GL20", "glUniformMatrix3", "(IZLjava/nio/FloatBuffer;)V", "glUniformMatrix3fv"},
        {"org/lwjgl/opengl/GL20", "glUniformMatrix4", "(IZLjava/nio/FloatBuffer;)V", "glUniformMatrix4fv"},
    };

    private static final Map<String, String> RENAME = new HashMap<>();
    private static final Map<String, String> BRIDGE_CALLS = new HashMap<>();

    static {
        for (String[] rule : RENAMES) {
            RENAME.put(rule[0] + "|" + rule[1] + "|" + rule[2], rule[3]);
        }
        // LWJGL 2 API with no same-name, same-descriptor equivalent at all.
        BRIDGE_CALLS.put("org/lwjgl/opengl/GL20|glShaderSource|(ILjava/nio/ByteBuffer;)V",
                "glShaderSourceBuffer");
        BRIDGE_CALLS.put("org/lwjgl/opengl/ARBShaderObjects|glShaderSourceARB|(ILjava/nio/ByteBuffer;)V",
                "glShaderSourceBuffer");
        BRIDGE_CALLS.put("org/lwjgl/openal/AL|create|()V", "alCreate");
        BRIDGE_CALLS.put("org/lwjgl/openal/AL|destroy|()V", "alDestroy");
        BRIDGE_CALLS.put("org/lwjgl/openal/AL|isCreated|()Z", "alIsCreated");
    }

    private Lwjgl3Rewriter() {
    }

    /** Returns the rewritten class, or {@code null} when nothing matched. */
    static byte[] rewrite(String name, byte[] bytes) {
        // Never rewrite LWJGL itself: its internals call the very methods we
        // redirect (AL.destroy -> AL.destroy), and the launcher's shim/agent
        // namespaces must stay out of the transformation.
        if (name.startsWith("java/") || name.startsWith("jdk/") || name.startsWith("sun/")
                || name.startsWith("org/lwjgl/") || name.startsWith("tessera/")
                || name.startsWith("org/spongepowered/") || name.startsWith("com/llamalad7/")
                || name.startsWith("org/objectweb/asm/")) {
            return null;
        }
        try {
            ClassReader reader = new ClassReader(bytes);
            ClassWriter writer = new ClassWriter(0);
            boolean[] changed = {false};
            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc,
                                                 String signature, String[] exceptions) {
                    MethodVisitor next = super.visitMethod(access, name, desc, signature, exceptions);
                    return new MethodVisitor(Opcodes.ASM9, next) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String iName,
                                                    String iDesc, boolean isInterface) {
                            String key = owner + "|" + iName + "|" + iDesc;
                            String bridge = BRIDGE_CALLS.get(key);
                            if (bridge != null) {
                                changed[0] = true;
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, bridge, iDesc, false);
                                return;
                            }
                            String renamed = RENAME.get(key);
                            if (renamed != null) {
                                changed[0] = true;
                                super.visitMethodInsn(opcode, owner, renamed, iDesc, isInterface);
                                return;
                            }
                            super.visitMethodInsn(opcode, owner, iName, iDesc, isInterface);
                        }
                    };
                }
            }, 0);
            return changed[0] ? writer.toByteArray() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
