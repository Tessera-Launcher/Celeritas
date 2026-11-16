plugins {
    `kotlin-dsl`
    `java-library`
}

dependencies {
    implementation("org.ow2.asm:asm:9.10.1")
    implementation("org.ow2.asm:asm-tree:9.10.1")
    implementation("com.gradleup.shadow:shadow-gradle-plugin:9.6.1")
}

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository { maven("https://maven.fabricmc.net/") }
        filter {
            includeGroupAndSubgroups("net.fabricmc")
        }
    }
}
