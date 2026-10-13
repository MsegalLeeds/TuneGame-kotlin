plugins {
    kotlin("jvm")
    application
    kotlin("plugin.serialization") version "2.2.20"
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor server
    implementation("io.ktor:ktor-server-core:3.3.0")
    implementation("io.ktor:ktor-server-netty:3.3.0")
    implementation("io.ktor:ktor-server-content-negotiation:3.3.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.0")

    // Ktor HTTP client - used for Spotify
    implementation("io.ktor:ktor-client-core:3.3.0")
    implementation("io.ktor:ktor-client-cio:3.3.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.3.0")
    implementation("io.ktor:ktor-server-cors:3.3.0")
    implementation("io.ktor:ktor-server-sessions:3.3.0")

    implementation("io.ktor:ktor-server-status-pages:3.3.0")
    implementation("io.ktor:ktor-server-rate-limit:3.3.0")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")

    // Tests
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-client-mock:3.3.0")
    testImplementation("io.ktor:ktor-server-test-host:3.3.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    //SQL
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
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
// Ship the production browser bundle inside the server distribution.
tasks.processResources {
    dependsOn(":shared:jsBrowserDistribution")
    from(project(":shared").layout.buildDirectory.dir("dist/js/productionExecutable")) { into("web") }
}
