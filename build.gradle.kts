// Edited: 2026-01-06
// Purpose: Root Gradle config; declare only catalog-managed plugins at root to avoid classloader issues with Hilt+KSP.

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    // KSP plugin alias from version catalog is "ksp" (not "kotlin.ksp")
    alias(libs.plugins.ksp) apply false
}
