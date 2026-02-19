// Edited: 2026-01-08
// Purpose: Root Gradle config; declare only catalog-managed plugins at root to avoid classloader issues.

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    // KSP plugin alias from version catalog is "ksp" (not "kotlin.ksp")
    alias(libs.plugins.ksp) apply false
    // COmment
    // Google Services plugin for Firebase (processes google-services.json)
    alias(libs.plugins.google.services) apply false
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
}
