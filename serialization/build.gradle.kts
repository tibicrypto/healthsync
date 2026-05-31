// :serialization — pure Kotlin/JVM module (no Android dependencies) holding the
// JSON/CSV/GPX serializers and parsers. Kept on the JVM so round-trip/fidelity
// property-based tests run without an emulator.
//
// Dependency wiring (one-directional): :serialization depends on :domain for the
// shared canonical data models.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.kotlinx.serialization.json)

    // Test stack: Kotest property + assertions + JUnit5 runner.
    testImplementation(libs.bundles.kotest)

    // JUnit5 (Jupiter) — the platform Kotest runs on; also lets plain JUnit5
    // example tests live alongside the property-based tests.
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter)
}

// Kotest 5 runs on the JUnit5 platform.
tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
