import org.embeddedt.embeddium.gradle.build.extensions.versionedProperty

plugins {
    id("com.gradleup.shadow")
    id("java-library")
}

evaluationDependsOn(":common")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven {
        name = "wagyourtail releases"
        url = uri("https://maven.wagyourtail.xyz/releases")
        content {
            includeGroupAndSubgroups("xyz.wagyourtail")
        }
    }
    maven {
        name = "sponge"
        url = uri("https://repo.spongepowered.org/maven")
        content {
            includeGroupAndSubgroups("org.spongepowered")
        }
    }
    maven {
        name = "Fabric Maven"
        url = uri("https://maven.fabricmc.net/")
        content {
            includeGroupAndSubgroups("net.fabricmc")
        }
    }
    maven {
        name = "taumc releases"
        url = uri("https://maven.taumc.org/releases")
        content {
            includeGroupAndSubgroups("org.taumc")
        }
    }
}

dependencies {
    val lombokVersion = rootProject.findProperty("lombok_version").toString()
    compileOnly("org.projectlombok:lombok:${lombokVersion}")
    annotationProcessor("org.projectlombok:lombok:${lombokVersion}")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.named<ProcessResources>("processResources") {
    val inputProps = mutableMapOf(
            "mod_version" to version,
            "mod_id" to rootProject.property("mod_id"),
            "mod_display_name" to rootProject.property("mod_display_name"),
            "mod_description" to rootProject.property("mod_description"),
            "homepage_url" to rootProject.property("homepage_url"),
            "sources_url" to rootProject.property("sources_url"),
            "issue_tracker_url" to rootProject.property("issue_tracker_url"),
            "license" to rootProject.property("license"),
            "fabricloader" to rootProject.property("fabricloader"),
            "contributors" to rootProject.property("mod_contributors").toString(),
            "authors" to rootProject.property("mod_authors").toString(),
            "fabric_api_modules" to rootProject.property("fabric_api_modules"),
            "minecraft" to (versionedProperty("minecraft_dependency") ?: "")
    )

    inputProps.forEach { (key, value) ->
        inputs.property(key, value)
    }

    filesMatching("fabric.mod.json") {
        expand(inputProps)
    }
}
