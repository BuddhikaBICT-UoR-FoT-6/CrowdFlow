// Edited: 2026-01-06
// Purpose: Use version catalog and pluginManagement to resolve plugins; apply Kotlin Compose compiler plugin per Kotlin 2.0 requirement; configure Hilt + WorkManager + Retrofit + Room deps.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    // Hilt Android Gradle plugin (resolved via settings pluginManagement)
    id("com.google.dagger.hilt.android")
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
    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))

    // Compose UI
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)

    // Material3
    implementation(libs.androidx.material3)
    // Material icons (required for Icons.* usage)
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    // Activity Compose
    implementation(libs.androidx.activity.compose)

    // Lifecycle + ViewModel Compose
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Google Play Services Location
    implementation(libs.play.services.location)

    // Room (KSP)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.converter.moshi)
    implementation(libs.moshi.kotlin)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Hilt core
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Hilt + WorkManager integration
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
}
