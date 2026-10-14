plugins { id("com.android.application") version "9.4.1" }

android {
    namespace = "com.msegal.tunegame.mobile"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.msegal.tunegame.mobile"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
