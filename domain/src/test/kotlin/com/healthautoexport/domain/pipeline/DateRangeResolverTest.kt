package com.healthautoexport.domain.pipeline

import com.healthautoexport.domain.model.Automation
import com.healthautoexport.domain.model.AggregationPeriod
import com.healthautoexport.domain.model.CanonicalUnit
import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.DateRange
import com.healthautoexport.domain.model.DestinationType
import com.healthautoexport.domain.model.ExportFormat
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.MetricSelection
import com.healthautoexport.domain.model.MetricValue
import com.healthautoexport.domain.model.UnifiedRecord
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Unit test theo ví dụ (example-based) cho [DateRangeResolver] — task 8.1.
 *
 * Bổ trợ cho property test của task 8.2 (Property 25, 26, 27): tập trung các trường hợp biên cụ
 * thể (khoảng bằng không, đúng đầu mút, đầu/cuối tương lai, cửa sổ nối tiếp Automation).
 */
class DateRangeResolverTest : FunSpec({

    // Một thời điểm "hiện tại" cố định để mọi test xác định: 2024-06-15 12:00:00 UTC.
    val nowFixed = Instant.parse("2024-06-15T12:00:00Z")
    val fixedClock = Clock.fixed(nowFixed, ZoneOffset.UTC)
    val resolver = DateRangeResolver(fixedClock)

    /** Tạo nhanh một UnifiedRecord tối thiểu với dấu thời gian cho trước. */
    fun recordAt(timestamp: Instant): UnifiedRecord =
        UnifiedRecord(
            metric = HealthMetricType.STEP_COUNT,
            value = MetricValue.Scalar(BigDecimal("1")),
            unit = CanonicalUnit.COUNT,
            timestamp = timestamp,
            zoneOffset = ZoneOffset.UTC,
            dataSourceId = DataSourceId.HEALTH_CONNECT,
        )

    // --- validate (Requirements 9.2, 9.3) ---

    test("validate chấp nhận khi end sau start") {
        val start = Instant.parse("2024-06-01T00:00:00Z")
        val end = Instant.parse("2024-06-02T00:00:00Z")
        val result = resolver.validate(start, end)
        result.shouldBeInstanceOf<DateRangeValidation.Valid>()
        result.range shouldBe DateRange(start, end)
    }

    test("validate chấp nhận khoảng bằng không (end == start)") {
        val t = Instant.parse("2024-06-01T00:00:00Z")
        val result = resolver.validate(t, t)
        result.shouldBeInstanceOf<DateRangeValidation.Valid>()
        result.range shouldBe DateRange(t, t)
    }

    test("validate từ chối khi end trước start") {
        val start = Instant.parse("2024-06-02T00:00:00Z")
        val end = Instant.parse("2024-06-01T00:00:00Z")
        resolver.validate(start, end).shouldBeInstanceOf<DateRangeValidation.Invalid>()
    }

    // --- filter (Requirements 9.4, 9.5) ---

    test("filter giữ bản ghi tại đúng hai đầu mút và bên trong, loại bản ghi ngoài khoảng") {
        val start = Instant.parse("2024-06-10T00:00:00Z")
        val end = Instant.parse("2024-06-12T00:00:00Z")
        val range = DateRange(start, end)

        val atStart = recordAt(start)
        val inside = recordAt(Instant.parse("2024-06-11T06:00:00Z"))
        val atEnd = recordAt(end)
        val beforeStart = recordAt(start.minusSeconds(1))
        val afterEnd = recordAt(end.plusSeconds(1))

        val filtered = resolver.filter(
            listOf(beforeStart, atStart, inside, atEnd, afterEnd),
            range,
        )

        filtered shouldBe listOf(atStart, inside, atEnd)
    }

    test("filter so sánh theo UTC bất kể zoneOffset hiển thị") {
        // Bản ghi mang offset +09:00 nhưng Instant là điểm tuyệt đối trên trục UTC.
        val start = Instant.parse("2024-06-10T00:00:00Z")
        val end = Instant.parse("2024-06-10T12:00:00Z")
        val range = DateRange(start, end)
        val rec = recordAt(Instant.parse("2024-06-10T06:00:00Z"))
            .copy(zoneOffset = ZoneOffset.ofHours(9))

        resolver.filter(listOf(rec), range) shouldBe listOf(rec)
    }

    // --- clampFutureEnd (Requirement 9.6) ---

    test("clampFutureEnd không đổi khi end không ở tương lai") {
        val range = DateRange(
            Instant.parse("2024-06-01T00:00:00Z"),
            Instant.parse("2024-06-10T00:00:00Z"),
        )
        val result = resolver.clampFutureEnd(range)
        result.adjusted.shouldBeFalse()
        result.range shouldBe range
    }

    test("clampFutureEnd kéo end về hiện tại khi end ở tương lai") {
        val start = Instant.parse("2024-06-01T00:00:00Z")
        val range = DateRange(start, nowFixed.plusSeconds(3600))
        val result = resolver.clampFutureEnd(range)
        result.adjusted.shouldBeTrue()
        result.range shouldBe DateRange(start, nowFixed)
    }

    test("clampFutureEnd kéo cả start khi toàn bộ khoảng ở tương lai") {
        val range = DateRange(nowFixed.plusSeconds(3600), nowFixed.plusSeconds(7200))
        val result = resolver.clampFutureEnd(range)
        result.adjusted.shouldBeTrue()
        result.range shouldBe DateRange(nowFixed, nowFixed)
    }

    // --- defaultQuickExportRange (Requirement 9.7) ---

    test("defaultQuickExportRange dùng 00:00:00 UTC hôm nay tới hiện tại") {
        val range = resolver.defaultQuickExportRange()
        range.startUtc shouldBe Instant.parse("2024-06-15T00:00:00Z")
        range.endUtc shouldBe nowFixed
    }

    // --- automationRange (Requirements 9.8, 9.9) ---

    fun automation(
        firstActivatedAtUtc: Instant? = null,
        lastSuccessfulEndUtc: Instant? = null,
    ): Automation = Automation(
        id = "auto-1",
        name = "Daily export",
        selection = MetricSelection(metrics = setOf(HealthMetricType.STEP_COUNT)),
        exportFormat = ExportFormat.JSON,
        aggregationPeriod = AggregationPeriod.DAY,
        scheduleIntervalMinutes = 60,
        enabled = true,
        destinationType = DestinationType.LOCAL_STORAGE,
        destinationConfigRef = "cfg-1",
        firstActivatedAtUtc = firstActivatedAtUtc,
        lastSuccessfulEndUtc = lastSuccessfulEndUtc,
    )

    test("automationRange dùng lastSuccessfulEndUtc khi đã có lần chạy thành công") {
        val lastEnd = Instant.parse("2024-06-15T11:00:00Z")
        val range = resolver.automationRange(
            automation(
                firstActivatedAtUtc = Instant.parse("2024-06-01T00:00:00Z"),
                lastSuccessfulEndUtc = lastEnd,
            ),
        )
        range.startUtc shouldBe lastEnd
        range.endUtc shouldBe nowFixed
    }

    test("automationRange dùng firstActivatedAtUtc khi chưa có lần chạy thành công") {
        val activated = Instant.parse("2024-06-14T08:00:00Z")
        val range = resolver.automationRange(automation(firstActivatedAtUtc = activated))
        range.startUtc shouldBe activated
        range.endUtc shouldBe nowFixed
    }
})
