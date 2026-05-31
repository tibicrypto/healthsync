package com.healthautoexport.serialization

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.property.PropertyTesting

/**
 * Project-wide Kotest configuration for the :serialization module.
 *
 * Establishes the default property-based test budget for the
 * health-auto-export-android feature: every `forAll`/`checkAll` that does not
 * override its own [io.kotest.property.PropTestConfig] runs at least
 * `iterations = 100`. Round-trip/fidelity serializer properties may opt into a
 * higher count locally, but 100 is the canonical default required by design
 * (Testing Strategy: "tối thiểu 100 vòng lặp" / PropTestConfig(iterations = 100)).
 *
 * Kotest auto-detects subclasses of [AbstractProjectConfig] on the test
 * classpath and applies the configuration before any spec runs.
 */
object KotestProjectConfig : AbstractProjectConfig() {
    override suspend fun beforeProject() {
        // Default iteration count for all property-based tests in this module.
        PropertyTesting.defaultIterationCount = 100
    }
}
