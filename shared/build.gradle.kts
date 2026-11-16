plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bandeja.shared"
    compileSdk = 34
    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    api("com.google.android.gms:play-services-wearable:18.2.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
