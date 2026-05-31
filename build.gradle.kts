// Root build script. Plugins are declared here with `apply false` so that
// concrete versions are resolved once via the version catalog and applied
// per-module in each module's own build.gradle.kts (see tasks 1.2 / 1.3).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
