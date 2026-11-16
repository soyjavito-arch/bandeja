plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // Mismo applicationId y firma que el reloj: así se hablan por la capa de datos de Wear OS.
    namespace = "com.bandeja.movil"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bandeja.padel"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    val keystorePath = System.getenv("BANDEJA_KEYSTORE")
    if (keystorePath != null) {
        signingConfigs {
            create("bandeja") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("BANDEJA_KEYSTORE_PASSWORD")
                keyAlias = "bandeja"
                keyPassword = System.getenv("BANDEJA_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("bandeja") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":shared"))
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
}
