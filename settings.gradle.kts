pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        exclusiveContent {
            forRepository { maven("https://maven.fabricmc.net/") }
            filter {
                includeGroupAndSubgroups("net.fabricmc")
                includeGroup("fabric-loom")
            }
        }
        exclusiveContent {
            forRepository { maven("https://maven.ornithemc.net/releases") }
            filter {
                includeModule("io.github.gaming32", "signature-changer")
                includeGroupAndSubgroups("net.ornithemc")
                includeGroup("ploceus")
            }
        }
        exclusiveContent {
            forRepository { maven("https://maven.wagyourtail.xyz/snapshots") }
            forRepository { maven("https://maven.wagyourtail.xyz/releases") }
            filter {
                includeGroupAndSubgroups("xyz.wagyourtail")
            }
        }
        maven("https://maven.taumc.org/releases") {
            content {
                includeGroupAndSubgroups("org.taumc")
            }
        }
        maven("https://repo.spongepowered.org/maven-public/") {
            content {
                includeGroupAndSubgroups("org.spongepowered")
            }
        }
    }

    plugins {
        id("org.taumc.gradle.versioning") version(extra["taugradle_version"].toString())
        id("org.taumc.gradle.publishing") version(extra["taugradle_version"].toString())
        id("net.fabricmc.fabric-loom-remap") version(extra["loom_version"].toString())
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
}

rootProject.name = "celeritas"

// Shared, Minecraft-independent rendering core. The 1.8.9 target shades it in.
include("common")

// The Celeritas classes the patched client embeds.
include("ornithe")

// Applies those classes plus Tessera's LWJGL3 shim to the vanilla 1.8.9 client
// and produces client.jar / client.tpatch.
include("patched-client")
