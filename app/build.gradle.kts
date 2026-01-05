// Edited: 2026-01-05
// Purpose: App module Gradle configuration enabling Jetpack Compose and declaring dependencies.

// Kotlin
// `app/build.gradle.kts`
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp) // Ensure KSP configuration is available with version from catalog
}

android {
    namespace = "com.example.ceylonqueuebuspulse"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.ceylonqueuebuspulse"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
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
    // Compose BOM to align versions (use catalog)
    implementation(platform(libs.androidx.compose.bom))

    // Core Compose UI (from catalog entries)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)

    // Material 3
    implementation(libs.androidx.material3)

    // Activity Compose
    implementation(libs.androidx.activity.compose)

    // Lifecycle + ViewModel for Compose (keep explicit versions if not in catalog)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // ViewModel Compose not in catalog file; keep direct coordinate for now
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // Coroutines (not in catalog):
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Google Play Services Location
    implementation(libs.play.services.location)

    // Room persistence library (KSP for Kotlin)
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
}
