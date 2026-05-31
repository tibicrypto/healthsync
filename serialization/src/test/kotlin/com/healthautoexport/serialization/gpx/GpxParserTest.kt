package com.healthautoexport.serialization.gpx

import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.RoutePoint
import com.healthautoexport.domain.model.Workout
import com.healthautoexport.domain.model.WorkoutType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant

/**
 * Unit tests cho [GpxParser] (Task 5.2).
 *
 * Kiểm chứng phân tích tài liệu GPX 1.1 hợp lệ, khôi phục đúng số điểm/thứ tự, và từ chối
 * (Result.failure) các đầu vào không phải GPX 1.1 hợp lệ mà không trả về tuyến đường nào
 * (Requirement 12.7).
 */
class GpxParserTest : StringSpec({

    fun workout(id: String, route: List<RoutePoint>): Workout = Workout(
        id = id,
        type = WorkoutType.RUNNING,
        start = Instant.parse("2023-01-15T10:30:00Z"),
        end = Instant.parse("2023-01-15T11:30:00Z"),
        durationSeconds = 3600,
        route = route,
        dataSourceId = DataSourceId.HEALTH_CONNECT,
    )

    "phân tích tài liệu GPX 1.1 hợp lệ khôi phục số điểm và thứ tự" {
        val xml = GpxSerializer().serialize(
            listOf(
                workout(
                    "run-1",
                    listOf(
                        RoutePoint(37.123456, -122.654321, Instant.parse("2023-01-15T10:30:00Z"), 12.5),
                        RoutePoint(37.223456, -122.554321, Instant.parse("2023-01-15T10:31:00Z"), null),
                    ),
                ),
            ),
        ).xml

        val routes = GpxParser().parse(xml).getOrThrow()
        routes shouldHaveSize 1
        routes[0].workoutId shouldBe "run-1"
        routes[0].points shouldHaveSize 2
        routes[0].points[0].latitude shouldBe 37.123456
        routes[0].points[0].altitudeMeters shouldBe 12.5
        routes[0].points[1].altitudeMeters.shouldBeNull()
        routes[0].points[1].timestamp shouldBe Instant.parse("2023-01-15T10:31:00Z")
    }

    "nhiều track khôi phục thành nhiều WorkoutRoute theo thứ tự" {
        val xml = GpxSerializer().serialize(
            listOf(
                workout("a", listOf(RoutePoint(1.0, 2.0, Instant.parse("2023-01-15T10:00:00Z")))),
                workout("b", listOf(RoutePoint(3.0, 4.0, Instant.parse("2023-01-15T11:00:00Z")))),
            ),
        ).xml

        val routes = GpxParser().parse(xml).getOrThrow()
        routes shouldHaveSize 2
        routes.map { it.workoutId } shouldBe listOf("a", "b")
    }

    "XML không hợp lệ về cú pháp trả về failure" {
        val result = GpxParser().parse("<gpx version=\"1.1\"><trk></gpx>")
        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<GpxParseException>()
    }

    "phần tử gốc không phải <gpx> trả về failure" {
        val result = GpxParser().parse("<root version=\"1.1\"></root>")
        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<GpxParseException>()
    }

    "phiên bản GPX khác 1.1 trả về failure và không trả route" {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.0" xmlns="http://www.topografix.com/GPX/1/0"><trk><trkseg>
            <trkpt lat="1.0" lon="2.0"><time>2023-01-15T10:00:00Z</time></trkpt>
            </trkseg></trk></gpx>""".trimIndent()

        val result = GpxParser().parse(xml)
        result.isFailure shouldBe true
        result.getOrNull().shouldBeNull()
    }

    "trkpt thiếu thuộc tính lat trả về failure" {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1"><trk><trkseg>
            <trkpt lon="2.0"><time>2023-01-15T10:00:00Z</time></trkpt>
            </trkseg></trk></gpx>""".trimIndent()

        val result = GpxParser().parse(xml)
        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<GpxParseException>()
    }

    "trkpt có time không hợp lệ trả về failure" {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1"><trk><trkseg>
            <trkpt lat="1.0" lon="2.0"><time>not-a-time</time></trkpt>
            </trkseg></trk></gpx>""".trimIndent()

        val result = GpxParser().parse(xml)
        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<GpxParseException>()
    }
})
