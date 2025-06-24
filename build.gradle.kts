plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "1.9.0"
}

group = "net.integr"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.8.1")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.0")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(23)
}