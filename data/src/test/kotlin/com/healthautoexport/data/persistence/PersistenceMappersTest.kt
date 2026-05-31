package com.healthautoexport.data.persistence

import com.healthautoexport.domain.model.AggregationPeriod
import com.healthautoexport.domain.model.Automation
import com.healthautoexport.domain.model.DestinationType
import com.healthautoexport.domain.model.ExportFormat
import com.healthautoexport.domain.model.ExportStatus
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.MetricSelection
import com.healthautoexport.domain.model.SyncLogEntry
import com.healthautoexport.domain.model.WorkoutType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant

/**
 * Unit test thuần (JVM, không cần Room/Robolectric) cho [PersistenceMappers] — task 16.1.
 *
 * Tập trung kiểm chứng việc ánh xạ entity ⇄ domain: round-trip Automation/SyncLogEntry, mã hóa
 * tập metric/workout sang JSON, thường-hóa tên cho chỉ mục unique (Requirement 14.7), và quy ước
 * Instant ⇄ epoch-millis. Round-trip qua **cơ sở dữ liệu Room thật** thuộc test task 16.3
 * (Property 32) nên không lặp lại tại đây.
 */
class PersistenceMappersTest : FunSpec({

    fun sampleAutomation(): Automation = Automation(
        id = "auto-1",
        name = "Daily Export",
        selection = MetricSelection(
            metrics = setOf(HealthMetricType.STEP_COUNT, HealthMetricType.HEART_RATE),
            workouts = setOf(WorkoutType.RUNNING, WorkoutType.CYCLING),
        ),
        exportFormat = ExportFormat.JSON,
        aggregationPeriod = AggregationPeriod.DAY,
        scheduleIntervalMinutes = 60,
        enabled = true,
        destinationType = DestinationType.LOCAL_STORAGE,
        destinationConfigRef = "cfg-1",
        firstActivatedAtUtc = Instant.parse("2024-06-01T00:00:00Z"),
        lastSuccessfulEndUtc = Instant.parse("2024-06-15T11:00:00Z"),
    )

    // --- Automation round-trip ---

    test("Automation round-trip qua entity giữ nguyên mọi trường") {
        val original = sampleAutomation()
        val restored = PersistenceMappers.toDomain(PersistenceMappers.toEntity(original))
        restored shouldBe original
    }

    test("Automation round-trip với các mốc thời gian null") {
        val original = sampleAutomation().copy(
            firstActivatedAtUtc = null,
            lastSuccessfulEndUtc = null,
        )
        val restored = PersistenceMappers.toDomain(PersistenceMappers.toEntity(original))
        restored shouldBe original
    }

    test("Automation round-trip với lựa chọn rỗng") {
        val original = sampleAutomation().copy(selection = MetricSelection())
        val restored = PersistenceMappers.toDomain(PersistenceMappers.toEntity(original))
        restored.selection shouldBe MetricSelection()
    }

    test("toEntity thường-hóa nameLower cho chỉ mục unique không phân biệt hoa thường") {
        val entity = PersistenceMappers.toEntity(sampleAutomation().copy(name = "Daily Export"))
        entity.nameLower shouldBe "daily export"
    }

    test("normalizeName dùng để so trùng tên không phân biệt hoa thường") {
        PersistenceMappers.normalizeName("MyAutomation") shouldBe
            PersistenceMappers.normalizeName("myautomation")
    }

    test("Instant được lưu dạng epoch-millis Long") {
        val instant = Instant.parse("2024-06-01T00:00:00Z")
        val entity = PersistenceMappers.toEntity(
            sampleAutomation().copy(firstActivatedAtUtc = instant),
        )
        entity.firstActivatedAtUtc shouldBe instant.toEpochMilli()
    }

    // --- SyncLogEntry round-trip ---

    test("SyncLogEntry round-trip qua entity giữ nguyên mọi trường") {
        val original = SyncLogEntry(
            id = "log-1",
            startUtc = Instant.parse("2024-06-15T10:00:00Z"),
            completionUtc = Instant.parse("2024-06-15T10:00:05Z"),
            automationId = "auto-1",
            exportFormat = ExportFormat.CSV,
            destinationType = DestinationType.REST_API,
            status = ExportStatus.SUCCESS,
            message = "Exported 42 records",
        )
        val restored = PersistenceMappers.toDomain(PersistenceMappers.toEntity(original))
        restored shouldBe original
    }

    test("SyncLogEntry round-trip với các trường nullable rỗng (Quick_Export đang chạy)") {
        val original = SyncLogEntry(
            id = "log-2",
            startUtc = Instant.parse("2024-06-15T10:00:00Z"),
            completionUtc = null,
            automationId = null,
            exportFormat = null,
            destinationType = null,
            status = ExportStatus.CANCELLED,
            message = null,
        )
        val restored = PersistenceMappers.toDomain(PersistenceMappers.toEntity(original))
        restored shouldBe original
        restored.completionUtc.shouldBeNull()
    }

    test("completionUtc null được giữ nguyên qua ánh xạ") {
        val entity = PersistenceMappers.toEntity(
            SyncLogEntry(
                id = "log-3",
                startUtc = Instant.now(),
                completionUtc = null,
                automationId = null,
                exportFormat = null,
                destinationType = null,
                status = ExportStatus.FAILURE,
                message = null,
            ),
        )
        entity.completionUtc.shouldBeNull()
    }
})
