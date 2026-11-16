plugins {
    `kotlin-dsl`
    `java-library`
}

dependencies {
    implementation("org.ow2.asm:asm:9.10.1")
    implementation("org.ow2.asm:asm-tree:9.10.1")
}

repositories {
    mavenCentral()
}
