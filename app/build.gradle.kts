// Edited: 2026-01-08
// Purpose: Use version catalog and pluginManagement to resolve plugins; apply Kotlin Compose compiler plugin per Kotlin 2.0 requirement; configure Koin + WorkManager + Retrofit + Room + Firebase deps.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    // Code generation
    alias(libs.plugins.ksp)     // Room ONLY

    // Firebase configuration processing
    alias(libs.plugins.google.services)
}

// Fix for: NoSuchMethodError: com.squareup.javapoet.ClassName.canonicalName()
// Some processors (Room/others) can pull older javapoet versions transitively.
// Force a compatible version ONLY on processor classpaths.
configurations.matching {
    it.name.contains("ksp", ignoreCase = true)
}.configureEach {
    resolutionStrategy.force("com.squareup:javapoet:1.13.0")
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

        // KSP arguments for Room
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
            arg("room.expandProjection", "true")
        }
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

    // Koin (DI)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.androidx.workmanager)

    // Firebase (Phase 3 remote backend / data sync)
    // Uses BOM to align Firebase component versions.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.auth.ktx)

    // Unit testing
    testImplementation(libs.junit)

    // (Optional) Kotlin test helpers that bridge to JUnit4.
    // Keep Kotlin version aligned via the Kotlin plugin (no hardcoded version here).
    testImplementation(kotlin("test-junit"))

    // (Optional but useful) Android instrumented test deps
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
