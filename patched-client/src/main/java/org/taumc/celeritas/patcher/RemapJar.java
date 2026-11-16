package org.taumc.celeritas.patcher;

import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Remaps a jar between the namespaces of the 1.8.9 mappings.
 *
 * <p>Celeritas' sources are written against Feather's named Minecraft, while
 * the vanilla client the patcher works on is in official names. The build
 * remaps the client to named once (official -> intermediary -> named) to
 * compile against it, then remaps the compiled classes back the other way.
 *
 * <p>Mixin's annotation strings (targets, method and field references) are
 * handled by tiny-remapper's mixin extension, which is why the mixin classes are
 * remapped instead of relying on a refmap.
 */
public final class RemapJar {
    private RemapJar() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("usage: RemapJar <input.jar> <output.jar> <mappings> <from> <to> [classpath.jar...]");
            System.exit(2);
        }
        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        Path mappings = mappingFile(Path.of(args[2]));
        String from = args[3];
        String to = args[4];
        List<Path> classpath = new ArrayList<>();
        for (int i = 5; i < args.length; i++) {
            classpath.add(Path.of(args[i]));
        }
        classpath.sort(Comparator.comparing(Path::toString));

        Path parent = output.toAbsolutePath().getParent();
        Files.createDirectories(parent);

        TinyRemapper remapper = TinyRemapper.newRemapper()
                .withMappings(TinyUtils.createTinyMappingProvider(mappings, from, to))
                .extension(new MixinExtension())
                .build();
        try (OutputConsumerPath consumer = new OutputConsumerPath.Builder(output).build()) {
            consumer.addNonClassFiles(input, net.fabricmc.tinyremapper.NonClassCopyMode.FIX_META_INF, remapper);
            remapper.readInputs(input);
            remapper.readClassPath(classpath.toArray(Path[]::new));
            remapper.apply(consumer);
        } finally {
            remapper.finish();
        }
        System.out.println("remapped " + input.getFileName() + " (" + from + " -> " + to + ") -> " + output);
    }

    /**
     * The mappings are published as jars with {@code mappings/mappings.tiny}
     * inside; a loose {@code .tiny} file is accepted as well.
     */
    private static Path mappingFile(Path path) throws IOException {
        if (path.getFileName().toString().endsWith(".tiny")) {
            return path;
        }
        try (ZipFile zip = new ZipFile(path.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".tiny")) {
                    continue;
                }
                Path extracted = Files.createTempFile("celeritas-mappings", ".tiny");
                extracted.toFile().deleteOnExit();
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, extracted, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                return extracted;
            }
        }
        throw new IOException("no .tiny mappings found in " + path);
    }
}
