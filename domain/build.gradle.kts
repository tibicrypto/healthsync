// :domain — pure Kotlin/JVM module (no Android dependencies) so property-based
// tests run fast on the JVM without an emulator. It holds the canonical data
// models, MetricCatalog and Ports, and is the innermost module: it depends on
// no other project module.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    // Domain may model time and use structured concurrency; no Android deps.
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    // Test stack: Kotest property + assertions + JUnit5 runner, coroutines test.
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.kotlinx.coroutines.test)

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
