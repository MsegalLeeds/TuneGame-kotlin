plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
}

kotlin {
    jvm()

    js(IR) {
        browser()
        binaries.executable()
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(
                "org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0"
            )

            implementation(
                "io.ktor:ktor-client-core:3.3.0"
            )

            implementation(
                "io.ktor:ktor-client-content-negotiation:3.3.0"
            )

            implementation(
                "io.ktor:ktor-serialization-kotlinx-json:3.3.0"
            )
            implementation(
                "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0"
            )
        }

        commonTest.dependencies {
            implementation(kotlin("test"))

            implementation(
                "io.ktor:ktor-client-mock:3.3.0"
            )

            implementation(
                "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2"
            )
        }

        jvmMain.dependencies {
            implementation(
                "io.ktor:ktor-client-cio:3.3.0"
            )
        }

        jsMain.dependencies {
            implementation(
                "io.ktor:ktor-client-js:3.3.0"
            )
        }

        iosMain.dependencies {
            implementation(
                "io.ktor:ktor-client-darwin:3.3.0"
            )
        }
    }
}