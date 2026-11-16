<img src="patched-client/src/celeritas/resources/assets/celeritas/textures/gui/logo.png" width="128">

# Celeritas

Celeritas is a free and open-source performance mod for Minecraft clients, forked from Embeddium (which itself was based
on the last FOSS-licensed version of Sodium 0.5.11-).

This tree is trimmed down to the one thing it is actually used for: producing the **statically patched 1.8.9 client**
(`client.jar`) and the patch that turns a vanilla 1.8.9 client into it (`client.tpatch`). There is no mod-loader jar for
1.8.9 and no support for any other Minecraft version here.

## Project layout

| Path | Purpose |
| --- | --- |
| `common/` | The Minecraft-independent rendering core (`org.embeddedt.embeddium.impl.*`). |
| `patched-client/` | The 1.8.9 Celeritas sources (`src/celeritas`) plus the build that compiles them against a Feather-named client jar, applies them and Tessera's LWJGL3 shim to the vanilla client, and emits `client.jar` + `client.tpatch`. |
| `patch-tool/` | The Rust generator/applier for the `.tpatch` format. |

The 1.8.9 sources were flattened out of the multi-version [Stonecutter](https://codeberg.org/stonecutter/stonecutter)
tree, so some files still carry inert `//? if` comment markers. There is no mod jar and no mod loader:
the only output is the patched client.

## How to build

```sh
./gradlew :patched-client:assemble
```

The result lands in `patched-client/build/libs/`:

```text
client-base.jar   normalized vanilla client (the patch base)
client.jar        patched client
client.tpatch     patch from client-base.jar -> client.jar
```

`patched-client/README.md` describes what the patcher does in detail, and `patched-client/run-test.sh` launches the
result against a Tessera installation without any `-javaagent`.

## How to use

The patched client needs a modernized runtime: Tessera's 1.8.9 installation ships Pylon (LWJGL3) and fastutil, which is
what the client jar expects. There is no agent, no Mixin and no ASM on its runtime class path.

## License

Celeritas is licensed under the Lesser GNU General Public License version 3, as it only uses code from Oculus/Iris,
Sodium 0.5.11-, and other FOSS projects. The patcher and shim code in `patched-client/` are part of Celeritas (LGPL-3.0);
the generated `client.jar` also embeds Tessera's LWJGL3 shim, which is GPL-3.0-or-later, so that jar is distributed
under GPL-3.0-or-later.

This project does not include and has no plans to include any code from Sodium 0.6+ or 0.5.12+, as these versions of
Sodium are not available under a free and open-source license.
Please reach out to @embeddedt on Discord if you have concerns regarding the license of any code in this project.

## Credits

* The CaffeineMC team, for developing Sodium 0.5.11 & older, and making it open source
* Asek3, for developing Rubidium, the original port of Sodium 0.5 to Forge
* CelestialAbyss, for developing the Embeddium logo (which is reused here aside from recoloring), and input-Here for some very good visual touchups
* Ven ([@basdxz](https://github.com/basdxz)), for help with translucency sorting, suggesting the general approach for async occlusion culling, and other suggestions during development
* rdh, for leading several key improvements, including faster & better occlusion culling, better AO rendering, etc
* douira, for researching and writing the [paper](https://douira.dev/assets/document/douira-master-thesis.pdf) on which the translucency sorting v3 implementation is based
* grondag, for developing [bitraster](https://github.com/vram-guild/bitraster), the library we vendor for rasterized occlusion culling
* XFactHD, Pepper, and anyone else I've forgotten to mention, for providing valuable code insights

[![YourKit logo](https://www.yourkit.com/images/yklogo.png)](https://www.yourkit.com/)

YourKit supports open source projects with innovative and intelligent tools
for monitoring and profiling Java and .NET applications.
YourKit is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>,
<a href="https://www.yourkit.com/.net/profiler/">YourKit .NET Profiler</a>,
and <a href="https://www.yourkit.com/youmonitor/">YourKit YouMonitor</a>.

Special thanks to YourKit for providing a free license for my various open-source Minecraft projects.
