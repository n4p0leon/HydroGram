plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.hydrogram.mod"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.hydrogram.mod"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0-hydro"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
}
