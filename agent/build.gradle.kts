import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar

plugins {
    java
}

group = "org.taumc.celeritas"
version = rootProject.version

val mcVersion = "1.8.9"
val officialJar = file("build/celeritas-official.jar")
val celeritasJar = providers.gradleProperty("celeritasAgentJar").orNull
    ?.let { rootProject.file(it) }
    ?: (if (officialJar.exists()) officialJar
        else file("../build/libs/${rootProject.version}/celeritas-fabriclike-mc8.9-${rootProject.version}.jar"))
val lwjgl3AgentJar = layout.projectDirectory.file("libs/lwjgl3-agent.jar")

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://repo.spongepowered.org/maven/")
}

dependencies {
    implementation("net.fabricmc:sponge-mixin:0.17.5+mixin.0.8.7")
    implementation("io.github.llamalad7:mixinextras-fabric:0.5.5")
    implementation("org.ow2.asm:asm:9.8")
    implementation("org.ow2.asm:asm-tree:9.8")
    implementation("org.ow2.asm:asm-analysis:9.8")
    implementation("org.ow2.asm:asm-commons:9.8")
    implementation("org.ow2.asm:asm-util:9.8")
    // fastutil is deliberately not bundled: it is a 23 MB library and the
    // launcher already has it (or can add it).  It is requested from the
    // launch classpath instead.
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.named<Jar>("jar") {
    archiveFileName = "celeritas-agent.jar"

    // Celeritas itself (intermediary names; this agent targets the Ornithe
    // intermediary client jar for the first bring-up).
    from(zipTree(celeritasJar)) {
        exclude("META-INF/MANIFEST.MF", "fabric.mod.json", "mixins.celeritas.json")
    }
    // Tessera's LWJGL3 shim and rewriter. Drop its bundled ASM so the agent
    // uses one ASM version; drop its manifest.
    from(zipTree(lwjgl3AgentJar)) {
        exclude("META-INF/MANIFEST.MF")
        exclude("org/objectweb/asm/**")
        exclude("META-INF/maven/**")
    }
    // Runtime libraries. Service files are provided by this project; Mixin's
    // built-in Fabric/ModLauncher service entries must not win.
    val runtimeJars = configurations.runtimeClasspath.get().files
    from(runtimeJars.map { zipTree(it) }) {
        exclude("META-INF/MANIFEST.MF")
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
        exclude("META-INF/services/org.spongepowered.asm.service.IMixinService")
        exclude("META-INF/services/org.spongepowered.asm.service.IMixinServiceBootstrap")
        exclude("META-INF/services/org.spongepowered.asm.service.IGlobalPropertyService")
        exclude("META-INF/services/cpw.mods.**")
        exclude("META-INF/services/javax.annotation.processing.Processor")
        exclude("META-INF/services/org.spongepowered.tools.**")
        exclude("module-info.class")
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes(
            "Premain-Class" to "org.taumc.celeritas.agent.CeleritasAgent",
            "Can-Retransform-Classes" to "true",
            "Can-Redefine-Classes" to "false",
            "Implementation-Title" to "Celeritas 1.8.9 Agent",
            "Implementation-Version" to project.version.toString()
        )
    }
}
