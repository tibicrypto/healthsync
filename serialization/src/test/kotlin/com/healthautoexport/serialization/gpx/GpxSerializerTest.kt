package com.healthautoexport.serialization.gpx

import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.RoutePoint
import com.healthautoexport.domain.model.Workout
import com.healthautoexport.domain.model.WorkoutType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.time.Instant

/**
 * Unit tests cho [GpxSerializer] (Task 5.1).
 *
 * Kiểm chứng các ví dụ cụ thể cho cấu trúc GPX 1.1, vị trí thuộc tính/phần tử con, loại trừ
 * Workout không route, và bỏ qua `<ele>` khi thiếu độ cao. Các thuộc tính khứ hồi tổng quát
 * (Property 5–7) được phủ riêng ở task 5.3.
 */
class GpxSerializerTest : StringSpec({

    fun workout(
        id: String,
        route: List<RoutePoint>?,
    ): Workout = Workout(
        id = id,
        type = WorkoutType.entries.first(),
        start = Instant.parse("2023-01-15T10:30:00Z"),
        end = Instant.parse("2023-01-15T11:30:00Z"),
        durationSeconds = 3600,
        route = route,
        dataSourceId = DataSourceId.HEALTH_CONNECT,
    )

    "tạo tài liệu GPX 1.1 với namespace đúng" {
        val result = GpxSerializer().serialize(
            listOf(
                workout(
                    "w1",
                    listOf(
                        RoutePoint(37.123456, -122.654321, Instant.parse("2023-01-15T10:30:00Z"), 12.5),
                    ),
                ),
            ),
        )

        result.xml shouldContain "<gpx version=\"1.1\""
        result.xml shouldContain "xmlns=\"http://www.topografix.com/GPX/1/1\""
    }

    "mỗi Workout có route sinh đúng một <trk> và một <trkseg> theo thứ tự" {
        val result = GpxSerializer().serialize(
            listOf(
                workout("first", listOf(RoutePoint(1.0, 2.0, Instant.parse("2023-01-15T10:00:00Z")))),
                workout("second", listOf(RoutePoint(3.0, 4.0, Instant.parse("2023-01-15T11:00:00Z")))),
            ),
        )

        Regex("<trk>").findAll(result.xml).count() shouldBe 2
        Regex("<trkseg>").findAll(result.xml).count() shouldBe 2
        // Thứ tự cung cấp được giữ nguyên (Requirement 12.5).
        result.xml.indexOf("first") shouldBe result.xml.indexOf("first")
        (result.xml.indexOf("first") < result.xml.indexOf("second")) shouldBe true
    }

    "lat/lon là thuộc tính; ele/time là phần tử con" {
        val result = GpxSerializer().serialize(
            listOf(
                workout(
                    "w1",
                    listOf(
                        RoutePoint(37.123456, -122.654321, Instant.parse("2023-01-15T10:30:45Z"), 12.5),
                    ),
                ),
            ),
        )

        result.xml shouldContain "<trkpt lat=\"37.123456\" lon=\"-122.654321\">"
        result.xml shouldContain "<ele>12.50</ele>"
        result.xml shouldContain "<time>2023-01-15T10:30:45Z</time>"
    }

    "dấu thời gian định dạng ISO 8601 UTC ở độ chính xác giây (bỏ phần nano)" {
        val result = GpxSerializer().serialize(
            listOf(
                workout(
                    "w1",
                    listOf(
                        RoutePoint(1.0, 2.0, Instant.parse("2023-01-15T10:30:45.987654321Z")),
                    ),
                ),
            ),
        )

        result.xml shouldContain "<time>2023-01-15T10:30:45Z</time>"
    }

    "điểm không có độ cao vẫn được giữ nhưng bỏ qua <ele>" {
        val result = GpxSerializer().serialize(
            listOf(
                workout(
                    "w1",
                    listOf(
                        RoutePoint(1.0, 2.0, Instant.parse("2023-01-15T10:00:00Z"), altitudeMeters = null),
                        RoutePoint(3.0, 4.0, Instant.parse("2023-01-15T10:01:00Z"), altitudeMeters = 5.0),
                    ),
                ),
            ),
        )

        Regex("<trkpt").findAll(result.xml).count() shouldBe 2
        Regex("<ele>").findAll(result.xml).count() shouldBe 1
    }

    "Workout không có route bị loại khỏi đầu ra kèm cảnh báo" {
        val result = GpxSerializer().serialize(
            listOf(
                workout("no-route-null", route = null),
                workout("no-route-empty", route = emptyList()),
                workout("has-route", listOf(RoutePoint(1.0, 2.0, Instant.parse("2023-01-15T10:00:00Z")))),
            ),
        )

        Regex("<trk>").findAll(result.xml).count() shouldBe 1
        result.xml shouldContain "has-route"
        result.xml shouldNotContain "no-route-null"
        result.excludedWorkouts shouldHaveSize 2
        result.excludedWorkouts.map { it.workoutId }.toSet() shouldBe setOf("no-route-null", "no-route-empty")
    }

    "danh sách Workout rỗng tạo tài liệu GPX rỗng hợp lệ" {
        val result = GpxSerializer().serialize(emptyList())

        result.xml shouldContain "<gpx version=\"1.1\""
        result.xml shouldContain "</gpx>"
        Regex("<trk>").findAll(result.xml).count() shouldBe 0
        result.excludedWorkouts shouldHaveSize 0
    }
})
