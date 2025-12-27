// Edited: 2025-12-27
// Purpose: Root Gradle configuration for the project; manages repositories/plugins for all modules.

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}