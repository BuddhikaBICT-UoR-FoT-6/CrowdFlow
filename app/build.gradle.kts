// Edited: 2025-12-27
// Purpose: App module Gradle configuration enabling Jetpack Compose and declaring dependencies.

// Kotlin
// `app/build.gradle.kts`
plugins {
    id("com.android.application")
    kotlin("android")
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
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
    // Compose BOM to align versions
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))

    // Core Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Material 3
    implementation("androidx.compose.material3:material3")

    // Activity Compose
    implementation("androidx.activity:activity-compose:1.9.3")

    // Lifecycle + ViewModel for Compose
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // Optional: coroutines if your ViewModel uses Flow/StateFlow
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Google Play Services Location
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
