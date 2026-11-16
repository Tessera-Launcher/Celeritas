# Celeritas

This tree exists to build one artifact: the statically patched Minecraft 1.8.9 client
(`patched-client/build/libs/client.jar`) and the patch that produces it from a vanilla client
(`client.tpatch`). There is no mod jar for 1.8.9 and no other Minecraft version is supported.

## Layout

- `common/`: shared rendering core (`org.embeddedt.embeddium.impl.*`). Celeritas-owned API goes in
  `org.taumc.celeritas`. The build script is Groovy (`common/build.gradle`). JMH benches are in
  `common/src/jmh`, tests in `common/src/test`.
- `patched-client/`: everything else.
  - `src/celeritas`: the 1.8.9 Celeritas sources (mixins plus renderer wiring), written in Ornithe
    Feather names, compiled against a Feather-named client jar.
  - `src/phosphor`: a vendored copy of Phosphor-Legacy's 1.8.9 lighting engine, written in Legacy
    Fabric Yarn names, compiled against a Yarn-named client jar. Read
    `patched-client/src/phosphor/README.md` before touching or updating it: the local edits (no
    Fabric API, no static accessor mixins) are required by the static patch.
  - Both are remapped back to official names and handed to `OfflinePatcher`, which applies
    `mixins.celeritas.client.json` + `phosphor.mixins.json` and Tessera's LWJGL3 rewriter and
    writes `client.jar`. `patch-tool/` (Rust) turns `client-base.jar` + `client.jar` into
    `client.tpatch`. See `patched-client/README.md` for the full pipeline. Version and dependency
    pins live in the root `gradle.properties`.

The 1.8.9 sources were flattened from a Stonecutter tree, so some files still carry `//? if`
comment markers with the inactive branches commented out. They are plain comments now; the active
branch is the one that is uncommented. There is no mod jar, no mod loader and no Loom: the mappings
are plain `calamus-intermediary`/`feather` and Legacy Fabric `intermediary`/`yarn` artifacts consumed
by tiny-remapper.

Two things bite when splicing another mod into the patch, both already handled for Phosphor:

- mixin annotation strings (`@Mixin`, `@Shadow`, `@Inject(method = ...)`, `@Accessor(...)`) are
  rewritten from the source namespace to official by tiny-remapper's mixin extension, which needs
  the *source-namespace* client jar on the remap classpath;
- *static* `@Accessor` calls cannot work, because the interface stub is never rewritten for classes
  Mixin does not transform. Use the plain API in the vendored source instead.

## Building and verifying

- Full chain: `./gradlew :patched-client:assemble`. It downloads the official 1.8.9 client and its
  libraries from Mojang into `patched-client/build/base/` and verifies their SHA-1, remaps it to
  named names for compilation, remaps the Celeritas classes back to official names, and patches.
- Builds are slow and memory-hungry, so run them one at a time.
- `:common` must stay compatible with fastutil 7.1.0 (the patched client runs against Tessera's
  fastutil 8.5.x, but `common` is compiled against the older API surface).
- The patched client must be byte-reproducible (including both mapping chains and both source sets):
  two builds of the same inputs must produce the same
  `client.jar`/`client.tpatch` so Tessera's AOT cache stays valid. That is why the patcher pins
  Mixin's random `@MixinMerged.sessionId` and the uid-based names of generated members, and it sorts
  the entries it adds. Check with `./gradlew :patched-client:generatePatch --rerun-tasks` twice and
  compare `sha256sum`.
- MixinExtras is copied into the output only where a patched class actually references it; the
  patcher fails the build if a reference would be left dangling. Do not go back to copying the whole
  runtime.
- Never run `taugradle_publish`, because it publishes artifacts.
- Releasing: bump `Celeritas.VERSION` (that is what F3 shows), run
  `./gradlew :patched-client:assemble`, commit, push, then publish with
  `gh release create v<version>-<mc> --title "Celeritas <version> for Minecraft 1.8.9"
  --notes-file ... --target <sha>` and upload `celeritas-1.8.9-<version>.tpatch` plus its
  `.sha256`. Finally update `ReleasesIndex.toml` (version/tag/url/sha256/size must match the
  uploaded asset; URLs must be `https://github.com/.../releases/download/...`).

## Licensing

- Never read, port or cherry-pick Sodium 0.6+ code, or commits that came from that lineage. Its
  license is incompatible and would contaminate our codebase. Don't pull upstream Iris changes
  that clearly depend on or derive from newer Sodium either.
- Celeritas has no addon API. Don't add compat shims or feature flags to keep third-party addons
  working.
- The generated `client.jar` embeds Tessera's LWJGL3 shim (GPL-3.0-or-later); Celeritas' own code
  stays LGPL-3.0.
