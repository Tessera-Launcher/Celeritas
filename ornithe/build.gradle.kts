import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.fabricmc.loom.task.RemapJarTask

plugins {
    id("celeritas.platform-conventions")
    id("net.fabricmc.fabric-loom-remap")
    id("ploceus") version "1.17.7"
}

repositories {
    exclusiveContent {
        forRepository { maven("https://maven.fabricmc.net/") }
        filter {
            includeGroup("net.fabricmc")
        }
    }
    exclusiveContent {
        forRepository { maven("https://maven.cloverclient.com/releases") }
        filter {
            includeGroup("pl.tomgirl")
        }
    }
}

// Pylon bundles LWJGL3 and replaces the LWJGL2 API, so no LWJGL artifact from the version manifest may stay on the
// classpath.
configurations.configureEach {
    exclude(group = "org.lwjgl.lwjgl")
    exclude(group = "org.lwjgl")
}

group = "org.embeddedt"
version = rootProject.version

evaluationDependsOn(":common")

base.archivesName = "celeritas-fabriclike-mc8.9"

loom {
    mixin {
        useLegacyMixinAp = false
    }
}

dependencies {
    minecraft("com.mojang:minecraft:1.8.9")
    mappings(ploceus.featherMappings("28"))

    implementation(project(":common")) {
        isTransitive = false
    }
    shadow(project(":common")) {
        isTransitive = false
    }

    implementation("org.joml:joml:1.10.5")
    shadow("org.joml:joml:1.10.5")
    implementation("it.unimi.dsi:fastutil:8.5.15")

    implementation("org.apache.logging.log4j:log4j-api:2.0-beta9")

    modImplementation("net.fabricmc:fabric-loader:${rootProject.property("fabricloader")}")
    modImplementation("pl.tomgirl:pylon:${rootProject.property("pylon_version")}")
}

tasks.named("validateAccessWidener") {
    enabled = false
}

val remapJarTask = tasks.named<RemapJarTask>("remapJar") {
    archiveClassifier.set("thin")
}

val shadowJar = tasks.register<ShadowJar>("shadowRemapJar") {
    archiveBaseName = base.archivesName
    archiveClassifier = ""
    configurations = listOf(project.configurations.shadow.get())
    from(zipTree(remapJarTask.get().archiveFile))
    manifest.from(tasks.named<Jar>("jar").get().manifest)
    mergeServiceFiles()

    from("COPYING", "COPYING.LESSER", "README.md")
}

val packageJar = tasks.register("packageJar", Copy::class) {
    from(shadowJar.get().archiveFile)
    into("${rootProject.layout.buildDirectory.get()}/libs/${project.version}")
}

tasks.named("assemble") {
    dependsOn(packageJar)
}
