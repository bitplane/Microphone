// Top-level build file. The kotlin-android plugin is declared (not applied) only to pin the Kotlin
// Gradle plugin version that AGP 9's built-in Kotlin uses; it must not be applied to :app.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
}