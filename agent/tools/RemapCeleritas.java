import java.nio.file.*;
import java.util.*;
import net.fabricmc.tinyremapper.*;
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension;

public class RemapCeleritas {
  public static void main(String[] args) throws Exception {
    Path input = Path.of(args[0]);
    Path output = Path.of(args[1]);
    Path mappings = Path.of(args[2]);
    String from = args[3], to = args[4];
    List<Path> cp = new ArrayList<>();
    for (int i = 5; i < args.length; i++) cp.add(Path.of(args[i]));

    TinyRemapper remapper = TinyRemapper.newRemapper()
        .withMappings(TinyUtils.createTinyMappingProvider(mappings, from, to))
        .extension(new MixinExtension())
        .build();
    try (OutputConsumerPath out = new OutputConsumerPath.Builder(output).build()) {
      out.addNonClassFiles(input, NonClassCopyMode.FIX_META_INF, remapper);
      remapper.readInputs(input);
      remapper.readClassPath(cp.toArray(Path[]::new));
      remapper.apply(out);
    } finally {
      remapper.finish();
    }
  }
}
