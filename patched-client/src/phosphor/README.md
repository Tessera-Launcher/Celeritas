# Phosphor (1.8.9), vendored

This source tree is a copy of [Phosphor-Legacy](https://github.com/Grayray75/Phosphor-Legacy),
branch `legacy/1.8.9`, commit `d80f18bd51a0d81bce7a21330159a82645721b37` ("Merge branch
'legacy/1.12.2' into legacy/1.8.9", 2025-12-05).

Phosphor is JellySquid's lighting engine; this branch is a Legacy Fabric port of the 1.12.2 code.
It is licensed LGPL-3.0-only, see `resources/LICENSE-phosphor.txt` (shipped in the patched jar).

## Local changes

The patched client has no mod loader and no runtime Mixin, so two things had to change:

1. **No Fabric API.**
   - `mod/PhosphorMod`: no longer implements `ModInitializer`; `LOGGER`/`CONFIG` are initialized
     statically instead of from the `main` entrypoint.
   - `mod/PhosphorConfig`: writes to `config/phosphor-legacy.json` in the working directory
     (`FabricLoader.getInstance().getConfigDir()` is gone).
   - `mod/world/lighting/LightingEngine`: dropped the `@Environment(EnvType.CLIENT)` annotation.
2. **No static accessor mixins.** `mixins/common/DirectionAccessor` was deleted: Mixin rewrites
   calls to *static* `@Accessor` methods in classes it transforms at load time, and in a statically
   patched client the interface stub would just throw. Its two call sites now use the enum API
   directly (`Direction.values()`, `Direction.DirectionType.HORIZONTAL.getDirections()`).

Everything else is untouched. The mixin annotations are still written in Legacy Fabric Yarn names;
`patched-client/build.gradle.kts` compiles this source set against a Yarn-remapped client jar and
then remaps the result to the vanilla client's official names with tiny-remapper, which also
rewrites the annotation strings (`@Mixin`, `@Shadow`, `@Inject(method = ...)`, `@Accessor(...)`).

When updating from upstream, keep these changes in mind and keep the mixin config
(`patched-client/src/main/resources/phosphor.mixins.json`) in sync with the classes.
