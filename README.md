<img src="patched-client/src/celeritas/resources/assets/celeritas/textures/gui/logo.png" width="128">

# Celeritas（Minecraft 1.8.9 静态补丁客户端）

本仓库是 Celeritas 的一个**仅保留 1.8.9**的分支。它不产出模组（mod jar），只产出一个
**在构建期就把补丁打进原版 jar 的 1.8.9 客户端**，以及把这个客户端从原版还原出来的补丁文件：

```
patched-client/build/libs/
├── client-base.jar    # 归一化后的原版客户端（补丁基线，去掉签名/取消 sealed）
├── client.jar         # 打好补丁的客户端，直接上 classpath，无需 -javaagent
└── client.tpatch      # client-base.jar → client.jar 的二进制补丁（约 1.9 MB）
```

`client.jar` 里同时包含：

| 组件 | 说明 |
| --- | --- |
| **Celeritas** | Sodium 0.5 风格的渲染器。整个**地形（chunk）渲染管线**——网格化、缓冲管理、遮挡剔除、半透明排序、绘制——由 Celeritas 接管；实体/粒子/天空/天气/GUI 仍是原版。 |
| **Phosphor** | JellySquid 的**光照引擎**（[Phosphor-Legacy](https://github.com/Grayray75/Phosphor-Legacy) 的 1.8.9 移植版），替换原版的光照传播算法，但沿用原版的 `ChunkSection` 光照存储与读取 API。 |
| **Tessera LWJGL3 shim** | 用 LWJGL 3/GLFW 实现 LWJGL 2 API，并把客户端里的 LWJGL 2 调用点在构建期改写为 LWJGL 3。 |
| JOML、MixinExtras 的 3 个运行时类 | 其余按需并入。 |

**不包含**：Mixin、ASM、Java agent、mod loader、`fabric.mod.json`。补丁在构建期用 Mixin 应用完
之后，Mixin 运行时本身就被丢掉了（`CallbackInfo` 被换成两个本地 shim 类）。

## 来源与上游

| 项目 | 地址 | 说明 |
| --- | --- | --- |
| Celeritas（上游） | <https://git.taumc.org/embeddedt/celeritas> | 本项目 fork 自这里；本仓库是它裁剪后的 1.8.9 单目标版本 |
| Celeritas（GitHub 镜像） | <https://github.com/Tessera-Launcher/Celeritas> | 本分支 `stonecutter` 的推送目标 |
| Phosphor-Legacy | <https://github.com/Grayray75/Phosphor-Legacy> | `patched-client/src/phosphor` 的源码来源（`legacy/1.8.9` @ `d80f18b`） |
| Embeddium / Sodium | <https://github.com/embeddedt/Embeddium> | Celeritas 的模组血统（Sodium 0.5.11- 的最后 FOSS 版本） |
| Tessera | — | 提供 1.8.9 运行环境（LWJGL3 shim、AOT cache 等） |

## 发布索引

根目录的 **`ReleasesIndex.toml`** 是给启动器读的机器可读索引，记录最新一版的信息：

```toml
[latest]
version = "2.5.0p-patch5"
tag = "v2.5.0p-patch5-1.8.9"

[latest.asset]
url = "https://github.com/Tessera-Launcher/Celeritas/releases/download/v2.5.0p-patch5-1.8.9/celeritas-1.8.9-2.5.0p-patch5.tpatch"
sha256 = "bdf8175f60f07804ce8765ae3f1480677c8a27fd33464c94c28c31fee4a05e78"
size = 1947202
```

下载地址一律用 `https://github.com/<owner>/<repo>/releases/download/<tag>/<file>` 形式。

## 运行要求

- **不需要模组加载器**：不装任何模组、也没有 Mixin/ASM/agent；`client.jar` 直接放在 classpath
  最前面（它含有少量被改写的第三方类，必须盖过原始库 jar）。
- **Java 25+**：补丁里 Tessera 的 shim 类是 Java 25 字节码（`client.jar` 里还有原版/我们自己的
  Java 6/8/17/21 字节码，取最高要求）。实际验证环境是 Tessera 自带的 Temurin 28 EA。
- **fastutil 8.5.x**（Tessera 安装器会放进去）+ Tessera 的 1.8.9 库集合。
- 配置文件写在游戏目录的 `config/` 下：`embeddium-options.json`（Celeritas）、
  `phosphor-legacy.json`（Phosphor）。

## 构建

前置：JDK（Gradle 用，需 21+）、Rust/cargo（`patch-tool`）、首次构建需要网络。

```sh
./gradlew :patched-client:assemble
```

这一条命令就是全流程：

1. `downloadInputs`：从 Mojang 取 `version_manifest_v2.json`、1.8.9 版本 JSON、`client.jar`
   与全部库到 `patched-client/build/base/`，逐个校验 SHA-1；
2. `remapClientToIntermediary` / `remapClientToNamed`（Feather 链）与
   `remapClientToLegacyIntermediary` / `remapClientToYarn`（Legacy Fabric 链）：
   把原版客户端分别转成两套源码使用的命名空间，作为编译期 classpath；
3. `compileCeleritasJava` / `compilePhosphorJava`：编译 `src/celeritas`（+ `:common`）与
   `src/phosphor`；
4. `remapCeleritasTo*` / `remapPhosphorTo*`：把编译结果转回客户端使用的官方（混淆）名，
   Mixin 注解里的字符串（`@Mixin`、`@Shadow`、`@Inject(method=…)`、`@Accessor(…)`）由
   tiny-remapper 的 mixin extension 一并改写，因此**不需要 refmap**；
5. `celeritasJar`：把转好的类 + `:common` + JOML 合成一个 `celeritas-official.jar`；
6. `patchClientJar`：`OfflinePatcher` 用 Mixin 把 `mixins.celeritas.client.json` 和
   `phosphor.mixins.json` 应用到原版类上，跑 LWJGL3 调用点改写，并入 shim、JOML、
   MixinExtras 的可达部分，写出 `client-base.jar` + `client.jar`；
7. `generatePatch`：`patch-tool`（Rust）从 base 与 target 生成 `client.tpatch`，并在写出前
   重建一遍确认能逐字节还原 target。

也可以只跑其中一步，例如 `./gradlew :patched-client:generatePatch`（依赖前面所有步骤）。
本分支只配置了 1.8.9 一个目标，因此**不再需要** `-Pceleritas_target_versions=…`。

**可复现性**：同样的输入，两次 `./gradlew :patched-client:generatePatch --rerun-tasks` 得到的
`client.jar` 与 `client.tpatch` 逐字节一致。为此 patcher 会对 Mixin 的随机产物做归一化
（`@MixinMerged.sessionId`、`md<uid>$…`/`fd<uid>$…` 成员名）并对新增条目排序；这一点很重要，
因为 Tessera 的 AOT cache 用 classpath 的尺寸/时间戳做键。

## 本地测试运行

```sh
./patched-client/run-test.sh
```

脚本从 Tessera 的 1.8.9 安装里拼 classpath（`client.jar` 在最前），不带 `-javaagent`。可用环境
变量覆盖：`TESSERA_DATA`（默认指向作者的 Tessera 数据目录）、`RUNDIR`（游戏目录，默认
`/tmp/celeritas-run`）、`JAVA`（默认取 `$TESSERA_DATA/依赖/java/28+24-ea-beta/bin/java`）。

启动后可以这样确认两个组件都在工作：

- 日志里应出现 `Phosphor lighting engine active (config: config/phosphor-legacy.json)`；
- F3 里应出现 `Celeritas Renderer (2.5.0p-patch5)` 与渲染器统计；
- `config/embeddium-options.json`、`config/phosphor-legacy.json` 会被写出。

## 目录结构

| 路径 | 用途 |
| --- | --- |
| `common/` | 与 Minecraft 无关的渲染核心（`org.embeddedt.embeddium.impl.*`）。JMH 基准在 `common/src/jmh`，单测在 `common/src/test`。 |
| `patched-client/src/celeritas` | Celeritas 的 1.8.9 源码（mixin + 渲染器接线），用 Ornithe **Feather** 命名。 |
| `patched-client/src/phosphor` | 从上游 vendored 的 Phosphor 1.8.9 源码，用 Legacy Fabric **Yarn** 命名；本地改动见该目录的 `README.md` 与 `resources/LICENSE-phosphor.txt`。 |
| `patched-client/src/main` | 打补丁用的一次性程序：`OfflinePatcher`、`RemapJar`（tiny-remapper 封装）、Mixin 服务桩、`CallbackInfo` shim。 |
| `patched-client/libs/lwjgl3-shim.jar` | Tessera 的 LWJGL3 shim 提取版（GPL-3.0-or-later），会被并入客户端。 |
| `patch-tool/` | `.tpatch` 的生成/应用工具（Rust）。 |

## 补丁格式（`.tpatch`）速览

ZIP 容器，内含 `patch.toml` + 内容寻址的 `blobs/<sha256>`：

- `format = 1`，路径为 jar 条目名，拒绝绝对路径/`..`；
- `base.entries_sha256` 是基线的权威校验（对排序后的 `path\0sha256` 列表取摘要），所以重新压缩
  基线不会让补丁失效；
- 操作：`put`（`encoding = raw | qbsdiff`）与 `delete`；
- 生成器会先在内存里重建一遍 target，不能逐字节还原就拒绝写出补丁。

## 实现要点

- **不用 Loom、不用 mod 工程**：编译所需的只有“按某个命名空间重映射过的原版 jar”，
  所以用 `calamus-intermediary`/`feather`（Celeritas）和 Legacy Fabric
  `intermediary`/`yarn`（Phosphor）四份公开映射 + tiny-remapper 就够，没有 Loom、没有
  模组打包步骤。
- **两套命名空间共存**：两个源码集各自编译、各自转回官方名，运行时只有一套（官方）名字。
- **MixinExtras 按可达性裁剪**：只保留被补丁后的类真正引用的那 3 个类
  （`LocalBooleanRef`、其生成的实现、`LocalRefRuntime`）；如果还有悬空引用，构建会直接失败。
- **`@Accessor` 接口会被保留**：Celeritas/Phosphor 的代码在运行时会 cast 到这些接口（例如
  `ServerChunkProviderAccessor`），其余 mixin 类不进入产物。
- **Phosphor 的两处源码适配**（静态补丁没有模组加载器、也没有运行时 Mixin）：
  去掉 `ModInitializer`/`FabricLoader`；去掉 **static `@Accessor`** 调用（Mixin 只在加载期改写
  调用点，静态补丁里不会发生），改用 `Direction.values()` 等原版 API。
- **已知差异**：`client.jar` 里原版类只被“应用 mixin + 改写 LWJGL 调用”，并未删除任何原版
  代码；未使用的原版类仍然存在（删掉它们对运行时没有收益，只影响体积）。

## 许可证

- Celeritas 与 `common/`：**LGPL-3.0**。
- `src/phosphor`：**LGPL-3.0-only**（Phosphor-Legacy，许可证随 jar 以
  `LICENSE-phosphor.txt` 分发）。
- `libs/lwjgl3-shim.jar`（Tessera 的 LWJGL3 shim）：**GPL-3.0-or-later**。
- 因此**合并后的 `client.jar` 以 GPL-3.0-or-later 分发**；Celeritas 自身的代码仍是 LGPL-3.0。

本项目**不包含**、也没有计划包含任何 Sodium 0.6+/0.5.12+ 的代码（那些版本不是自由许可）。
如果对仓库内任何代码的许可证有疑问，请在 Discord 上联系 @embeddedt。

## 致谢

- **JellySquid / CaffeineMC**：Sodium 0.5.11- 及更早版本、Phosphor
- **embeddedt**：Embeddium、Celeritas
- **Grayray75**：Phosphor-Legacy（本仓库使用的 1.8.9 移植）
- **Asek3**：Rubidium（Sodium 0.5 最早的 Forge 移植）
- **CelestialAbyss**、input-Here：Celeritas logo
- **grondag**：bitraster（光栅化遮挡剔除）
- **douira**：半透明排序 v3 所依据的研究
- **rdh**、Ven、XFactHD、Pepper 以及其他提供思路与代码的人

[![YourKit logo](https://www.yourkit.com/images/yklogo.png)](https://www.yourkit.com/)

YourKit 为开源项目提供创新的智能工具，用于监控和剖析 Java 与 .NET 应用。
YourKit 是 [YourKit Java Profiler](https://www.yourkit.com/java/profiler/)、
[YourKit .NET Profiler](https://www.yourkit.com/.net/profiler/) 与
[YourKit YouMonitor](https://www.yourkit.com/youmonitor/) 的开发者。
感谢 YourKit 为本人的多个开源 Minecraft 项目提供免费许可。
