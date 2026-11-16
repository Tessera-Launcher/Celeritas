# Celeritas

This tree exists to build one artifact: the statically patched Minecraft 1.8.9 client
(`patched-client/build/libs/client.jar`) and the patch that produces it from a vanilla client
(`client.tpatch`). There is no mod jar for 1.8.9 and no other Minecraft version is supported.

## Layout

- `common/`: shared rendering core (`org.embeddedt.embeddium.impl.*`). Celeritas-owned API goes in
  `org.taumc.celeritas`. The build script is Groovy (`common/build.gradle`). JMH benches are in
  `common/src/jmh`, tests in `common/src/test`.
- `ornithe/`: the 1.8.9 target (Loom + Ploceus/Feather mappings + Pylon), a plain single-version
  project. It shades `:common` into `celeritas-fabriclike-mc8.9-<version>.jar`, which is the input
  to the patcher. Version and dependency pins live in the root `gradle.properties`.
- `patched-client/`: `OfflinePatcher` applies `mixins.celeritas.client.json` to the decompressed
  vanilla classes and runs Tessera's LWJGL3 rewriter, then writes `client.jar`; `patch-tool/` (Rust)
  turns `client-base.jar` + `client.jar` into `client.tpatch`. See `patched-client/README.md` for
  the full pipeline.
- `patch-tool/`: the `.tpatch` generator/applier (`cargo build --release`).

`ornithe/src/main` was flattened from a Stonecutter tree, so some files still carry `//? if`
comment markers with the inactive branches commented out. They are plain comments now; the active
branch is the one that is uncommented.

## Building and verifying

- Full chain: `./gradlew :ornithe:packageJar`, then `./patched-client/remap-official.sh`,
  then `./gradlew :patched-client:assemble`. The last step downloads the official 1.8.9 client and
  its libraries from Mojang into `patched-client/build/base/` and verifies their SHA-1.
- Builds are slow and memory-hungry, so run them one at a time.
- `:common` must stay compatible with fastutil 7.1.0 (the patched client runs against Tessera's
  fastutil 8.5.x, but `common` is compiled against the older API surface).
- The patched client must be byte-reproducible: two builds of the same inputs must produce the same
  `client.jar`/`client.tpatch` so Tessera's AOT cache stays valid. That is why the patcher pins
  Mixin's random `@MixinMerged.sessionId` and the uid-based names of generated members. Check with
  `./gradlew :patched-client:generatePatch --rerun-tasks` twice and compare `sha256sum`.
- MixinExtras is copied into the output only where a patched class actually references it; the
  patcher fails the build if a reference would be left dangling. Do not go back to copying the whole
  runtime.
- Never run `taugradle_publish`, because it publishes artifacts.

## Licensing

- Never read, port or cherry-pick Sodium 0.6+ code, or commits that came from that lineage. Its
  license is incompatible and would contaminate our codebase. Don't pull upstream Iris changes
  that clearly depend on or derive from newer Sodium either.
- Celeritas has no addon API. Don't add compat shims or feature flags to keep third-party addons
  working.
- The generated `client.jar` embeds Tessera's LWJGL3 shim (GPL-3.0-or-later); Celeritas' own code
  stays LGPL-3.0.
