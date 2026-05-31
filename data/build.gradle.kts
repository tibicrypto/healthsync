// :data — Android library module holding framework adapters (Health Connect,
// Huawei stub, Room, DataStore, EncryptedSharedPreferences, the six
// Destinations, WorkManager scheduler).
//
// Dependency wiring (one-directional): :data depends on :domain and :serialization.
// Note: repositories are NOT declared here — settings.gradle.kts uses
// RepositoriesMode.FAIL_ON_PROJECT_REPOS and resolves them centrally.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.healthautoexport.data"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        // For a library module the effective targetSdk is carried by its
        // instrumentation tests; kept aligned with :app (35) for consistency.
        targetSdk = libs.versions.targetSdk.get().toInt()
        // Robolectric needs Android resources available to unit tests.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":serialization"))

    // --- Android core ---
    implementation(libs.androidx.core.ktx)

    // --- Health data source ---
    implementation(libs.androidx.health.connect.client)

    // --- Background work ---
    implementation(libs.androidx.work.runtime.ktx)

    // --- Persistence: Room ---
    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)

    // --- Persistence: DataStore ---
    implementation(libs.androidx.datastore.preferences)

    // --- Credential storage (Android Keystore backed) ---
    implementation(libs.androidx.security.crypto)

    // --- Networking (REST API + Home Assistant destinations) ---
    implementation(libs.bundles.networking)

    // --- Serialization & coroutines ---
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // --- Dependency injection (Hilt) ---
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // --- Test stack (Robolectric-driven JVM unit tests) ---
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit4)
}
