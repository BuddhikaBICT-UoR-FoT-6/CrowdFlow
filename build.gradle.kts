// Edited: 2026-01-06
// Purpose: Root Gradle config; declare only catalog-managed plugins at root to avoid classloader issues with Hilt+KSP.

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    // Removed Hilt plugin from root to declare in app module with explicit version alongside KSP
    // Removed KSP plugin from root to declare in app module with explicit version
}