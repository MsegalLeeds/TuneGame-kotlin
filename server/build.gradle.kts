plugins {
    kotlin("jvm")
    application
    kotlin("plugin.serialization") version "2.2.20"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core:3.3.0")
    implementation("io.ktor:ktor-server-netty:3.3.0")
    implementation("io.ktor:ktor-server-content-negotiation:3.3.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.0")


    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:3.3.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.msegal.tunegame.ApplicationKt")
}

tasks.test {
    useJUnitPlatform()
}