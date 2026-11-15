package org.taumc.celeritas.patcher;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import com.llamalad7.mixinextras.utils.ClassGenUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.transformers.MixinClassWriter;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Builds a patched 1.8.9 {@code client.jar} without leaving a runtime agent
 * behind.  Celeritas' mixins are applied to the decompressed client classes,
 * the LWJGL3 call-site rewriter runs over both those classes and the Celeritas
 * classes, and only the pieces needed at runtime are merged back in.
 */
public final class OfflinePatcher {
    private static final String MIXIN_CONFIG = "mixins.celeritas.client.json";
    private static final Set<String> MIXIN_SKIP = Set.of(
            "java/", "javax/", "jdk/", "sun/", "com/sun/", "org/w3c/", "org/xml/",
            "org/spongepowered/", "com/llamalad7/", "org/objectweb/asm/",
            "org/taumc/celeritas/patched-client/", "org/taumc/celeritas/mixin/",
            "org/lwjgl/", "tessera/");
    private static final Pattern GENERATED = Pattern.compile(
            "com/llamalad7/mixinextras/sugar/impl/ref/generated/[A-Za-z0-9_$]+");
    private static final SimpleRemapper CALLBACKS = new SimpleRemapper(Map.of(
            "org/spongepowered/asm/mixin/injection/callback/CallbackInfo",
            "org/taumc/celeritas/patch/CallbackInfo",
            "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable",
            "org/taumc/celeritas/patch/CallbackInfoReturnable"));
    private static final String[] SHIMS = {"CallbackInfo", "CallbackInfoReturnable"};

    private OfflinePatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("usage: OfflinePatcher <client.jar> <out.jar> <celeritas.jar> "
                    + "<lwjgl3-shim.jar> <mixinextras.jar> [library.jar...]");
            System.exit(2);
        }
        Path client = Path.of(args[0]);
        Path output = Path.of(args[1]);
        Path celeritas = Path.of(args[2]);
        Path lwjgl3Shim = Path.of(args[3]);
        Path mixinextras = Path.of(args[4]);
        List<Path> libraries = new ArrayList<>();
        for (int i = 5; i < args.length; i++) {
            libraries.add(Path.of(args[i]));
        }
        for (Path required : List.of(client, celeritas, lwjgl3Shim, mixinextras)) {
            if (!Files.isRegularFile(required)) {
                throw new IOException("missing input " + required);
            }
        }

        MixinBootstrap.init();
        MixinBootstrap.getPlatform().inject();
        MixinBridge.install();
        MixinExtrasBootstrap.init();
        Mixins.addConfiguration(MIXIN_CONFIG);

        Path parent = output.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path tmp = parent.resolve(output.getFileName() + ".tmp");
        Files.deleteIfExists(tmp);
        Set<String> written = new HashSet<>();
        Set<String> generated = new TreeSet<>();
        int patched = 0;
        int merged = 0;
        int rewrittenLibraries = 0;

        try (ZipFile base = new ZipFile(client.toFile());
             ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
            Enumeration<? extends ZipEntry> entries = base.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || signature(entry.getName())) {
                    continue;
                }
                String name = safe(entry.getName());
                byte[] bytes = base.getInputStream(entry).readAllBytes();
                if (name.equalsIgnoreCase("META-INF/MANIFEST.MF")) {
                    bytes = unsealed(new String(bytes, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
                } else if (name.endsWith(".class")) {
                    byte[] transformed = transform(name, bytes);
                    if (transformed != bytes) {
                        patched++;
                    }
                    bytes = transformed;
                    collectGenerated(bytes, generated);
                }
                writeBase(out, written, name, bytes);
            }

            merged += addCeleritas(out, written, celeritas);
            merged += addLwjgl3(out, written, lwjgl3Shim);
            merged += addMixinExtras(out, written, mixinextras);
            for (Path library : libraries) {
                rewrittenLibraries += addRewrittenLibrary(out, written, library);
            }
            merged += addShims(out, written);
            merged += addGenerated(out, written, generated);

            if (!written.contains("META-INF/MANIFEST.MF")) {
                writeAdded(out, written, "META-INF/MANIFEST.MF",
                        "Manifest-Version: 1.0\r\nMain-Class: net.minecraft.client.Main\r\n\r\n"
                                .getBytes(StandardCharsets.UTF_8));
            }
            for (String name : written) {
                if (name.startsWith("org/spongepowered/") || name.startsWith("org/objectweb/asm/")) {
                    throw new IllegalStateException("forbidden runtime dependency in output: " + name);
                }
            }
        }
        Files.move(tmp, output, StandardCopyOption.REPLACE_EXISTING);

        System.out.println("client.jar: patched classes=" + patched
                + ", merged entries=" + merged
                + ", rewritten library classes=" + rewrittenLibraries
                + " -> " + output);
    }

    private static byte[] transform(String name, byte[] bytes) throws Exception {
        if (MIXIN_SKIP.stream().anyMatch(name::startsWith)) {
            return bytes;
        }
        int major = ((bytes[6] & 0xff) << 8) | (bytes[7] & 0xff);
        // ASM 9.x cannot read a JDK newer than the one this build runs on, and
        // vanilla client classes are never that new.
        if (major > 69) {
            return bytes;
        }
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        byte[] result = bytes;
        if (MixinBridge.transform(node)) {
            ClassWriter writer = new MixinClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            node.accept(writer);
            result = remapCallbacks(writer.toByteArray());
        }
        byte[] rewritten = Lwjgl3Rewriter.rewrite(name, result);
        return rewritten == null ? result : rewritten;
    }

    /**
     * Mixin's generated callbacks are the only part of the Mixin runtime a
     * transformed class still touches.  Move those two value objects to a tiny
     * local shim so the output does not need Mixin (or ASM) at runtime.
     */
    private static byte[] remapCallbacks(byte[] bytes) {
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(bytes).accept(new ClassRemapper(writer, CALLBACKS), 0);
        return writer.toByteArray();
    }

    private static int addCeleritas(ZipOutputStream out, Set<String> written, Path jar)
            throws Exception {
        int added = 0;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || signature(name) || skipMetadata(name)
                        || name.equals("fabric.mod.json") || name.equals("mixins.celeritas.json")
                        || name.equals(MIXIN_CONFIG) || skipCeleritasMixin(name)) {
                    continue;
                }
                byte[] bytes = zip.getInputStream(entry).readAllBytes();
                if (name.endsWith(".class")) {
                    byte[] rewritten = Lwjgl3Rewriter.rewrite(name, bytes);
                    if (rewritten != null) {
                        bytes = rewritten;
                    }
                }
                if (writeAdded(out, written, safe(name), bytes)) {
                    added++;
                }
            }
        }
        return added;
    }

    private static int addLwjgl3(ZipOutputStream out, Set<String> written, Path jar) throws Exception {
        int added = 0;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || signature(name) || skipMetadata(name)
                        || name.startsWith("org/objectweb/asm/") || name.startsWith("TesseraLwjgl3Agent")) {
                    continue;
                }
                if (!name.startsWith("org/lwjgl/") && !name.equals("tessera/lwjgl3/Bridge.class")) {
                    continue;
                }
                if (writeAdded(out, written, safe(name), zip.getInputStream(entry).readAllBytes())) {
                    added++;
                }
            }
        }
        return added;
    }

    private static int addMixinExtras(ZipOutputStream out, Set<String> written, Path jar) throws Exception {
        int added = 0;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || signature(name) || skipMetadata(name)
                        || !name.startsWith("com/llamalad7/")) {
                    continue;
                }
                if (writeAdded(out, written, safe(name), zip.getInputStream(entry).readAllBytes())) {
                    added++;
                }
            }
        }
        return added;
    }

    /**
     * The static jar cannot rewrite third-party classes at class-load time, so
     * rewritten copies (for example paulscode's OpenAL wrapper) are placed in
     * the client jar.  The client jar must precede the ordinary libraries.
     */
    private static int addRewrittenLibrary(ZipOutputStream out, Set<String> written, Path jar)
            throws Exception {
        if (!Files.isRegularFile(jar)) {
            return 0;
        }
        int added = 0;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || signature(name) || !name.endsWith(".class")) {
                    continue;
                }
                byte[] bytes = zip.getInputStream(entry).readAllBytes();
                byte[] rewritten = Lwjgl3Rewriter.rewrite(name, bytes);
                if (rewritten == null) {
                    continue;
                }
                if (writeAdded(out, written, safe(name), rewritten)) {
                    added++;
                    System.out.println("rewrote library class " + jar.getFileName() + "!" + name);
                }
            }
        }
        return added;
    }

    private static int addShims(ZipOutputStream out, Set<String> written) throws Exception {
        int added = 0;
        for (String shim : SHIMS) {
            String name = "org/taumc/celeritas/patch/" + shim + ".class";
            try (InputStream in = OfflinePatcher.class.getClassLoader().getResourceAsStream(name)) {
                if (in == null) {
                    throw new IllegalStateException("missing " + name + " on the patcher classpath");
                }
                if (writeAdded(out, written, name, in.readAllBytes())) {
                    added++;
                }
            }
        }
        return added;
    }

    private static int addGenerated(ZipOutputStream out, Set<String> written, Set<String> referenced)
            throws IOException {
        Map<String, byte[]> definitions = new LinkedHashMap<>(ClassGenUtils.getDefinitions());
        int added = 0;
        for (Map.Entry<String, byte[]> entry : definitions.entrySet()) {
            String name = entry.getKey().replace('.', '/') + ".class";
            if (writeAdded(out, written, name, entry.getValue())) {
                added++;
            }
        }
        for (String name : referenced) {
            if (!written.contains(name + ".class")) {
                throw new IllegalStateException("MixinExtras did not generate " + name);
            }
        }
        return added;
    }

    private static void collectGenerated(byte[] bytes, Set<String> out) {
        Matcher matcher = GENERATED.matcher(new String(bytes, StandardCharsets.ISO_8859_1));
        while (matcher.find()) {
            out.add(matcher.group());
        }
    }

    private static void writeBase(ZipOutputStream out, Set<String> written, String name, byte[] bytes)
            throws IOException {
        if (!written.add(name)) {
            throw new IllegalStateException("duplicate base entry " + name);
        }
        write(out, name, bytes);
    }

    private static boolean writeAdded(ZipOutputStream out, Set<String> written, String name, byte[] bytes)
            throws IOException {
        if (!written.add(name)) {
            return false;
        }
        write(out, name, bytes);
        return true;
    }

    private static void write(ZipOutputStream out, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        // Reproducible output: the AOT cache keys the class path by size and
        // timestamp, so a rebuild must not change an otherwise identical jar.
        entry.setTime(0);
        out.putNextEntry(entry);
        out.write(bytes);
        out.closeEntry();
    }

    private static String safe(String name) {
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("../") || normalized.equals("..")
                || normalized.contains(":") || normalized.isEmpty()) {
            throw new IllegalArgumentException("unsafe jar entry " + name);
        }
        return normalized;
    }

    private static boolean skipCeleritasMixin(String name) {
        return name.startsWith("org/taumc/celeritas/mixin/") && !name.endsWith("Accessor.class");
    }

    private static boolean skipMetadata(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        return name.equalsIgnoreCase("META-INF/MANIFEST.MF")
                || name.startsWith("META-INF/maven/")
                || name.startsWith("META-INF/services/")
                || name.equals("module-info.class")
                || (upper.startsWith("META-INF/") && upper.endsWith(".SF"));
    }

    private static boolean signature(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.startsWith("META-INF/") && (upper.endsWith(".SF") || upper.endsWith(".RSA")
                || upper.endsWith(".DSA") || upper.endsWith(".EC"));
    }

    private static String unsealed(String manifest) {
        StringBuilder out = new StringBuilder(manifest.length());
        for (String line : manifest.split("\n", -1)) {
            String bare = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
            if (bare.trim().toLowerCase(Locale.ROOT).startsWith("sealed:")) {
                continue;
            }
            out.append(bare).append('\n');
        }
        return out.toString();
    }
}
