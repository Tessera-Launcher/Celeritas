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
val baseDir = layout.buildDirectory.dir("base")
val clientJar = baseDir.map { it.file("client.jar") }
val librariesDir = baseDir.map { it.dir("libraries") }
val clientOut = layout.buildDirectory.file("libs/client.jar")
val clientBaseOut = layout.buildDirectory.file("libs/client-base.jar")
val patchOut = layout.buildDirectory.file("libs/client.tpatch")
val patchToolDir = rootProject.layout.projectDirectory.dir("patch-tool")
val patchTool = patchToolDir.file(
    "target/release/celeritas-patch-tool" + if (System.getProperty("os.name").startsWith("Windows", true)) ".exe" else ""
).asFile
val shimJar = layout.projectDirectory.file("libs/lwjgl3-shim.jar").asFile
val celeritasJar = providers.gradleProperty("celeritasJar")
    .map { file(it) }
    .orElse(provider { file("build/celeritas-official.jar") })

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://repo.spongepowered.org/maven/")
}

dependencies {
    // Build-time only: Mixin and ASM apply the patches and never reach the
    // output client.jar. MixinExtras does supply a few runtime classes, but the
    // patcher copies only the ones the patched classes reference.
    implementation("net.fabricmc:sponge-mixin:0.17.5+mixin.0.8.7")
    implementation("io.github.llamalad7:mixinextras-fabric:0.5.5")
    implementation("org.ow2.asm:asm:9.8")
    implementation("org.ow2.asm:asm-tree:9.8")
    implementation("org.ow2.asm:asm-analysis:9.8")
    implementation("org.ow2.asm:asm-commons:9.8")
    implementation("org.ow2.asm:asm-util:9.8")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
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

val libraryJars = fileTree(librariesDir) { include("**/*.jar") }

/**
 * Applies Celeritas' mixins and the LWJGL3 rewrite directly to the downloaded
 * client jar. The result has no agent manifest and no Mixin/ASM classes; it is
 * the client jar a launcher should put on the class path.
 */
val patchClientJar = tasks.register<JavaExec>("patchClientJar") {
    description = "Build a client.jar with Celeritas and the LWJGL3 shim applied"
    group = "build"
    dependsOn(downloadInputs, tasks.named("classes"))
    mainClass.set("org.taumc.celeritas.patcher.OfflinePatcher")
    outputs.file(clientOut)
    outputs.file(clientBaseOut)
    inputs.files(sourceSets.main.get().output, celeritasJar, clientJar, shimJar, libraryJars)
    notCompatibleWithConfigurationCache("runs the offline patcher with a downloaded class path")

    doFirst {
        if (!celeritasJar.get().isFile) {
            throw GradleException(
                "missing ${celeritasJar.get()}; build :ornithe:$mcVersion:packageJar " +
                    "and run patched-client/remap-official.sh first (or pass -PceleritasJar=...)"
            )
        }
        val mixinExtras = configurations.runtimeClasspath.get().files
            .firstOrNull { it.name.startsWith("mixinextras") }
            ?: throw GradleException("MixinExtras is missing from the patcher classpath")
        val libraries = libraryJars.files.filter { it.isFile }.sortedBy { it.name }
        classpath = files(
            sourceSets.main.get().output,
            configurations.runtimeClasspath,
            celeritasJar.get(),
            clientJar.get().asFile,
            libraries
        )
        args = listOf(
            clientJar.get().asFile.absolutePath,
            clientOut.get().asFile.absolutePath,
            clientBaseOut.get().asFile.absolutePath,
            celeritasJar.get().absolutePath,
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
