# Celeritas 1.8.9 agent

One Java agent that applies Celeritas' 1.8.9 mixins and Tessera's LWJGL3
shim/rewriter.  The transformer order is fixed: Celeritas first, LWJGL3 second,
so calls Celeritas injects are rewritten too.

## Build

```sh
# 1. the ordinary Celeritas jar (Ornithe intermediary namespace)
./gradlew :ornithe:1.8.9:packageJar -Pceleritas_target_versions=1.8.9

# 2. remap it to Mojang official names for Tessera's normal client jar
./agent/remap-official.sh

# 3. the one agent jar
./gradlew :agent:jar -Pceleritas_target_versions=1.8.9 \
  -PceleritasAgentJar=agent/build/celeritas-official.jar
```

The result is `agent/build/libs/celeritas-agent.jar`.

fastutil is **not** bundled: it is about 23 MB and every launcher that can run
1.8.9 already has it (or can add it).  It must be on the game classpath; the
agent checks for `it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap` and fails
with a clear message if it is missing.

To build an agent for the Ornithe intermediary client jar instead, omit
`-PceleritasAgentJar`; the default input is the intermediary Celeritas jar.

## Run

```sh
./agent/run-test.sh
```

`TESSERA_DATA` defaults to `/home/ygbs/桌面/Tessera/target/debug/data`;
override it if the test install lives elsewhere.

## What it does

- `CeleritasAgent.premain` initializes a standalone Mixin service
  (`META-INF/services/org.spongepowered.asm.service.IMixinService`), adds
  `mixins.celeritas.agent.json`, and registers a `ClassFileTransformer`.
- The active Mixin transformer is installed before `MixinExtrasBootstrap.init()`
  so MixinExtras can register its extensions.
- Tessera's `TesseraLwjgl3Agent.premain` runs afterwards from the same jar.
- `mixins.celeritas.agent.json` lists only the mixins that exist for 1.8.9;
  `InGameHudMixin`, `BlockRendererMixin`, `MinecraftAppletMixin` and
  `TessellatorMixin` are compiled out for this target.

## AOT caveat

A Java agent that transforms classes cannot have those classes in the AOT
cache.  The 1.8.9 agent currently transforms seven Minecraft classes
(`Minecraft`, `WorldRenderer`, `World`, `Frustum`, `Tessellator`, `GameRenderer`
and the chunk-cache classes).  Celeritas' own classes are not transformed and
remain cacheable.  For a fully AOT-cacheable build, use this agent as a
generator: dump the transformed bytes and patch the client jar, then run
without the agent.

## Test result

Launched against `Tessera/target/debug/data/游戏/versions/1.8.9/1.8.9.jar` on
Java 28 EA:

```
LWJGL Version: 2.9.4 (LWJGL3 shim)
[tessera-lwjgl3] Display.create ok 854x480
Sound engine started
Created: 512x512 textures-atlas
```

Seven target classes were exported by Mixin under `.mixin.out`, including the
official `bfr` (`WorldRenderer`) with the `CeleritasWorldRenderer` field and
`ave` (`Minecraft`) with the `MinecraftAccessor` interface.  No Mixin apply
errors and no `NoSuchMethodError` from Celeritas' LWJGL2 calls.

## Licensing

The agent code in this directory is part of Celeritas (LGPL-3.0).  It embeds
Tessera's `lwjgl3-agent.jar`, which is GPL-3.0-or-later; the combined agent is
distributed under GPL-3.0-or-later.  Celeritas' own classes remain LGPL-3.0.
