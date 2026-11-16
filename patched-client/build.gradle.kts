import groovy.json.JsonSlurper
import org.gradle.jvm.tasks.Jar

import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

plugins {
    java
}

group = "org.taumc.celeritas"
version = rootProject.version

val mcVersion = "1.8.9"
// Ornithe's Feather mappings for the names the Celeritas sources are written in.
val featherVersion = "1.8.9+build.28"
// Legacy Fabric's mappings, for the Phosphor sources (they are written in Yarn).
val legacyYarnVersion = "1.8.9+build.576"

val baseDir = layout.buildDirectory.dir("base")
val clientJar = baseDir.map { it.file("client.jar") }
val librariesDir = baseDir.map { it.dir("libraries") }
val libraryJars = fileTree(librariesDir) { include("**/*.jar") }

// Intermediate jars of the remapping steps; not part of the published output.
val workspace = layout.buildDirectory.dir("namespaces")
val clientIntermediary = workspace.map { it.file("client-intermediary.jar") }
val clientNamed = workspace.map { it.file("client-named.jar") }
val celeritasClasses = workspace.map { it.file("celeritas-classes.jar") }
val celeritasIntermediary = workspace.map { it.file("celeritas-intermediary.jar") }
val celeritasOfficialClasses = workspace.map { it.file("celeritas-official-classes.jar") }
// Phosphor is written against Legacy Fabric's Yarn names, so it gets its own chain.
val clientLegacyIntermediary = workspace.map { it.file("client-legacy-intermediary.jar") }
val clientYarn = workspace.map { it.file("client-yarn.jar") }
val phosphorClasses = workspace.map { it.file("phosphor-classes.jar") }
val phosphorIntermediary = workspace.map { it.file("phosphor-intermediary.jar") }
val phosphorOfficialClasses = workspace.map { it.file("phosphor-official-classes.jar") }

val clientOut = layout.buildDirectory.file("libs/client.jar")
val clientBaseOut = layout.buildDirectory.file("libs/client-base.jar")
val patchOut = layout.buildDirectory.file("libs/client.tpatch")
val celeritasOut = layout.buildDirectory.file("libs/celeritas-official.jar")

val patchToolDir = rootProject.layout.projectDirectory.dir("patch-tool")
val patchTool = patchToolDir.file(
    "target/release/celeritas-patch-tool" + if (System.getProperty("os.name").startsWith("Windows", true)) ".exe" else ""
).asFile
val shimJar = layout.projectDirectory.file("libs/lwjgl3-shim.jar").asFile

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://repo.spongepowered.org/maven/")
    maven("https://maven.ornithemc.net/releases")
    maven("https://maven.cloverclient.com/releases")
    maven("https://maven.legacyfabric.net/")
}

// The two mapping halves that make up Feather's official/intermediary/named chain.
val mappings: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

// Libraries that end up inside the patched client.
val celeritasShaded: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

sourceSets {
    // The Celeritas mod code, compiled straight against a named Minecraft jar.
    create("celeritas")
    // Phosphor's lighting engine, compiled against a Yarn-named client jar.
    create("phosphor")
}

dependencies {
    // Build-time only: Mixin, MixinExtras and ASM apply the patches, and
    // tiny-remapper moves the classes between mapping namespaces. None of them
    // are merged into the output client.jar.
    implementation("net.fabricmc:sponge-mixin:0.17.5+mixin.0.8.7")
    implementation("io.github.llamalad7:mixinextras-fabric:0.5.5")
    implementation("org.ow2.asm:asm:9.8")
    implementation("org.ow2.asm:asm-tree:9.8")
    implementation("org.ow2.asm:asm-analysis:9.8")
    implementation("org.ow2.asm:asm-commons:9.8")
    implementation("org.ow2.asm:asm-util:9.8")
    implementation("net.fabricmc:tiny-remapper:0.15.0")
    implementation("net.fabricmc:mapping-io:0.8.0")

    mappings("net.ornithemc:calamus-intermediary:$mcVersion:v2")
    mappings("net.ornithemc:feather:$featherVersion:v2")
    mappings("net.fabricmc:intermediary:$mcVersion:v2")
    mappings("net.legacyfabric:yarn:$legacyYarnVersion:v2")

    add("celeritasCompileOnly", "net.fabricmc:sponge-mixin:0.17.5+mixin.0.8.7")
    add("celeritasCompileOnly", "io.github.llamalad7:mixinextras-fabric:0.5.5")
    // The LWJGL 2 API the sources use; Pylon is the same shim Tessera installs.
    add("celeritasCompileOnly", "pl.tomgirl:pylon:0.2.8")
    add("celeritasCompileOnly", "it.unimi.dsi:fastutil:8.5.15")
    add("celeritasCompileOnly", "org.apache.logging.log4j:log4j-api:2.0-beta9")
    add("celeritasCompileOnly", "com.google.code.findbugs:jsr305:3.0.2")
    add("celeritasCompileOnly", "org.jetbrains:annotations:24.1.0")
    add("celeritasCompileOnly", "org.ow2.asm:asm:9.6")
    add("celeritasCompileOnly", "org.ow2.asm:asm-tree:9.6")
    add("celeritasCompileOnly", "org.projectlombok:lombok:${rootProject.property("lombok_version")}")
    add("celeritasAnnotationProcessor", "org.projectlombok:lombok:${rootProject.property("lombok_version")}")

    add("celeritasShaded", project(":common"))
    add("celeritasShaded", "org.joml:joml:1.10.5")

    add("phosphorCompileOnly", "net.fabricmc:sponge-mixin:0.17.5+mixin.0.8.7")
}

configurations["celeritasCompileOnly"].extendsFrom(celeritasShaded)

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
}

fun sha1(file: File): String {
    val digest = MessageDigest.getInstance("SHA-1")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun download(url: String, target: File, expectedSha1: String?) {
    if (expectedSha1 != null && target.isFile && sha1(target).equals(expectedSha1, ignoreCase = true)) {
        return
    }
    target.parentFile?.mkdirs()
    val tmp = File(target.parentFile, target.name + ".tmp")
    val connection = URI(url).toURL().openConnection()
    connection.setRequestProperty("User-Agent", "celeritas-build")
    connection.getInputStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
    if (expectedSha1 != null) {
        val actual = sha1(tmp)
        if (!actual.equals(expectedSha1, ignoreCase = true)) {
            tmp.delete()
            throw GradleException("checksum mismatch for $url: expected $expectedSha1, got $actual")
        }
    }
    Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
}

val downloadInputs = tasks.register("downloadInputs") {
    description = "Download the 1.8.9 client jar and libraries used by the patcher"
    group = "build"
    outputs.dir(baseDir)
    outputs.upToDateWhen { false }
    notCompatibleWithConfigurationCache("downloads the 1.8.9 inputs and verifies them")
    doLast {
        @Suppress("UNCHECKED_CAST")
        fun map(value: Any?): Map<String, Any?> = value as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        fun list(value: Any?): List<Any?> = value as List<Any?>

        val manifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
        val manifest = map(JsonSlurper().parse(URI(manifestUrl).toURL()))
        val entry = list(manifest["versions"])
            .map(::map)
            .firstOrNull { it["id"] == mcVersion }
            ?: throw GradleException("Mojang's version manifest has no $mcVersion")
        val version = map(JsonSlurper().parse(URI(entry["url"] as String).toURL()))
        val downloads = map(version["downloads"])

        val client = map(downloads["client"])
        download(
            client["url"] as String,
            clientJar.get().asFile,
            client["sha1"] as? String
        )
        println("client: ${clientJar.get().asFile}")

        for (raw in list(version["libraries"])) {
            val library = map(raw)
            val artifact = map(library["downloads"] ?: continue)["artifact"] ?: continue
            val fileInfo = map(artifact)
            val path = fileInfo["path"] as? String ?: continue
            val url = fileInfo["url"] as? String ?: continue
            download(url, librariesDir.get().dir(path).asFile, fileInfo["sha1"] as? String)
        }
        println("libraries: ${librariesDir.get().asFile}")
    }
}

/** Runs tiny-remapper over one jar; see {@link org.taumc.celeritas.patcher.RemapJar}. */
fun registerRemapTask(
    name: String,
    description: String,
    from: String,
    to: String,
    mappingPrefix: String,
    input: Provider<RegularFile>,
    output: Provider<RegularFile>,
    classpath: (Project) -> FileCollection
): TaskProvider<JavaExec> = tasks.register<JavaExec>(name) {
    this.description = description
    group = "build"
    dependsOn(tasks.named("classes"))
    mainClass.set("org.taumc.celeritas.patcher.RemapJar")
    this.classpath = sourceSets.main.get().runtimeClasspath
    inputs.file(input)
    outputs.file(output)
    notCompatibleWithConfigurationCache("resolves the mapping artifacts at execution time")
    doFirst {
        val mapping = mappings.files.single { it.name.startsWith(mappingPrefix) }
        args = listOf(
            input.get().asFile.absolutePath,
            output.get().asFile.absolutePath,
            mapping.absolutePath,
            from,
            to
        ) + classpath(project).files
            // Pylon ships Java 27 multi-release classes that tiny-remapper's ASM cannot read,
            // and nothing under org/lwjgl is remapped anyway.
            .filter { it.isFile && !it.name.startsWith("pylon") }
            .sortedBy { it.name }
            .map { it.absolutePath }
    }
}

/**
 * The patcher needs the client in the official namespace, but Celeritas' sources
 * are written against Feather's named Minecraft. The client is remapped down to
 * named once (so it can be compiled against) and the compiled classes are
 * remapped back: named -> intermediary -> official.
 */
val remapClientToIntermediary = registerRemapTask(
    name = "remapClientToIntermediary",
    description = "Remap the vanilla 1.8.9 client to intermediary names",
    from = "official",
    to = "intermediary",
    mappingPrefix = "calamus",
    input = clientJar,
    output = clientIntermediary,
    classpath = { libraryJars }
).also { it.configure { dependsOn(downloadInputs) } }

val remapClientToNamed = registerRemapTask(
    name = "remapClientToNamed",
    description = "Remap the 1.8.9 client to the Feather names the sources use",
    from = "intermediary",
    to = "named",
    mappingPrefix = "feather",
    input = clientIntermediary,
    output = clientNamed,
    classpath = { libraryJars }
).also { it.configure { dependsOn(remapClientToIntermediary) } }

val celeritasClassesJar = tasks.register<Jar>("celeritasClassesJar") {
    from(sourceSets["celeritas"].output)
    archiveFileName = "celeritas-classes.jar"
    destinationDirectory = workspace
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val remapCeleritasToIntermediary = registerRemapTask(
    name = "remapCeleritasToIntermediary",
    description = "Remap the Celeritas classes from named to intermediary names",
    from = "named",
    to = "intermediary",
    mappingPrefix = "feather",
    input = celeritasClasses,
    output = celeritasIntermediary,
    classpath = { files(clientNamed) + sourceSets["celeritas"].compileClasspath + libraryJars }
).also {
    it.configure {
        dependsOn(celeritasClassesJar, remapClientToNamed)
    }
}

val remapCeleritasToOfficial = registerRemapTask(
    name = "remapCeleritasToOfficial",
    description = "Remap the Celeritas classes from intermediary to official names",
    from = "intermediary",
    to = "official",
    mappingPrefix = "calamus",
    input = celeritasIntermediary,
    output = celeritasOfficialClasses,
    classpath = {
        files(clientIntermediary) +
            configurations["celeritasCompileClasspath"] +
            libraryJars
    }
).also { it.configure { dependsOn(remapCeleritasToIntermediary, remapClientToIntermediary) } }

// The mod classes are Java 21, matching the removed Loom build. The patcher and
// its CallbackInfo shims are Java 25, like the prebuilt LWJGL3 shim they ship with.
tasks.named<JavaCompile>("compileCeleritasJava") {
    javaCompiler.set(javaToolchains.compilerFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })
    options.encoding = "UTF-8"
    dependsOn(remapClientToNamed, downloadInputs)
    classpath += files(clientNamed)
    // The game's own libraries (Guava, LWJGL 2, ...) are part of Minecraft's API surface.
    classpath += libraryJars
}

val remapClientToLegacyIntermediary = registerRemapTask(
    name = "remapClientToLegacyIntermediary",
    description = "Remap the vanilla 1.8.9 client to Legacy Fabric intermediary names",
    from = "official",
    to = "intermediary",
    mappingPrefix = "intermediary",
    input = clientJar,
    output = clientLegacyIntermediary,
    classpath = { libraryJars }
).also { it.configure { dependsOn(downloadInputs) } }

val remapClientToYarn = registerRemapTask(
    name = "remapClientToYarn",
    description = "Remap the 1.8.9 client to the Legacy Fabric Yarn names Phosphor uses",
    from = "intermediary",
    to = "named",
    mappingPrefix = "yarn",
    input = clientLegacyIntermediary,
    output = clientYarn,
    classpath = { libraryJars }
).also { it.configure { dependsOn(remapClientToLegacyIntermediary) } }

val phosphorClassesJar = tasks.register<Jar>("phosphorClassesJar") {
    from(sourceSets["phosphor"].output)
    archiveFileName = "phosphor-classes.jar"
    destinationDirectory = workspace
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val remapPhosphorToIntermediary = registerRemapTask(
    name = "remapPhosphorToIntermediary",
    description = "Remap the Phosphor classes from Yarn to Legacy Fabric intermediary names",
    from = "named",
    to = "intermediary",
    mappingPrefix = "yarn",
    input = phosphorClasses,
    output = phosphorIntermediary,
    classpath = { files(clientYarn) + sourceSets["phosphor"].compileClasspath + libraryJars }
).also { it.configure { dependsOn(phosphorClassesJar, remapClientToYarn) } }

val remapPhosphorToOfficial = registerRemapTask(
    name = "remapPhosphorToOfficial",
    description = "Remap the Phosphor classes from intermediary to official names",
    from = "intermediary",
    to = "official",
    mappingPrefix = "intermediary",
    input = phosphorIntermediary,
    output = phosphorOfficialClasses,
    classpath = {
        files(clientLegacyIntermediary) +
            configurations["phosphorCompileClasspath"] +
            libraryJars
    }
).also { it.configure { dependsOn(remapPhosphorToIntermediary, remapClientToLegacyIntermediary) } }

// The Phosphor sources are Java 8; the bytecode level does not matter here, so
// it gets the same compiler as the rest of the 1.8.9 code.
tasks.named<JavaCompile>("compilePhosphorJava") {
    javaCompiler.set(javaToolchains.compilerFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })
    options.encoding = "UTF-8"
    dependsOn(remapClientToYarn, downloadInputs)
    classpath += files(clientYarn)
    classpath += libraryJars
}

/**
 * The single jar the patcher merges into the client: the Celeritas classes in
 * official names, the shared core, JOML, and the assets (language keys, shaders,
 * textures) that ship with them.
 */
val celeritasJar = tasks.register<Jar>("celeritasJar") {
    description = "Merge the remapped Celeritas classes with :common and JOML"
    group = "build"
    dependsOn(remapCeleritasToOfficial, remapPhosphorToOfficial)
    destinationDirectory = layout.buildDirectory.dir("libs")
    archiveFileName = "celeritas-official.jar"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    notCompatibleWithConfigurationCache("merges the remapped tree with the :common and JOML jars")
    from(celeritasOfficialClasses.map { zipTree(it.asFile) })
    from(phosphorOfficialClasses.map { zipTree(it.asFile) })
    from(celeritasShaded.elements.map { elements -> elements.map { zipTree(it.asFile) } })
}

/**
 * Applies Celeritas' mixins and the LWJGL3 rewrite directly to the downloaded
 * client jar. The result has no agent manifest and no Mixin/ASM classes; it is
 * the client jar a launcher should put on the class path.
 */
val patchClientJar = tasks.register<JavaExec>("patchClientJar") {
    description = "Build a client.jar with Celeritas and the LWJGL3 shim applied"
    group = "build"
    dependsOn(downloadInputs, tasks.named("classes"), celeritasJar)
    mainClass.set("org.taumc.celeritas.patcher.OfflinePatcher")
    outputs.file(clientOut)
    outputs.file(clientBaseOut)
    inputs.files(sourceSets.main.get().output, celeritasOut, clientJar, shimJar, libraryJars)
    notCompatibleWithConfigurationCache("runs the offline patcher with a downloaded class path")

    doFirst {
        val mixinExtras = configurations.runtimeClasspath.get().files
            .firstOrNull { it.name.startsWith("mixinextras") }
            ?: throw GradleException("MixinExtras is missing from the patcher classpath")
        val libraries = libraryJars.files.filter { it.isFile }.sortedBy { it.name }
        classpath = files(
            sourceSets.main.get().output,
            configurations.runtimeClasspath,
            celeritasOut.get().asFile,
            clientJar.get().asFile,
            libraries
        )
        args = listOf(
            clientJar.get().asFile.absolutePath,
            clientOut.get().asFile.absolutePath,
            clientBaseOut.get().asFile.absolutePath,
            celeritasOut.get().asFile.absolutePath,
            shimJar.absolutePath,
            mixinExtras.absolutePath
        ) + libraries.map { it.absolutePath }
        jvmArgs("-Xmx2g")
    }
}

val buildPatchTool = tasks.register<Exec>("buildPatchTool") {
    description = "Build the Rust patch generator/applier"
    group = "build"
    workingDir = patchToolDir.asFile
    commandLine("cargo", "build", "--release")
    inputs.files(fileTree(patchToolDir) {
        include("Cargo.toml", "Cargo.lock", "src/**")
    })
    outputs.file(patchTool)
    notCompatibleWithConfigurationCache("runs cargo outside Gradle")
}

val generatePatch = tasks.register<Exec>("generatePatch") {
    description = "Generate client.tpatch from client-base.jar and client.jar"
    group = "build"
    dependsOn(patchClientJar, buildPatchTool)
    inputs.files(clientBaseOut, clientOut)
    outputs.file(patchOut)
    notCompatibleWithConfigurationCache("runs the Rust patch generator")
    doFirst {
        commandLine(
            patchTool.absolutePath,
            "generate",
            clientBaseOut.get().asFile.absolutePath,
            clientOut.get().asFile.absolutePath,
            patchOut.get().asFile.absolutePath,
            "celeritas-1.8.9"
        )
    }
}

tasks.named("assemble") {
    dependsOn(patchClientJar, generatePatch)
}

// The old target packaged a -javaagent jar. Only the patched client is built
// now, so the default jar task must not produce a second, misleading artifact.
tasks.named<Jar>("jar") {
    enabled = false
}
