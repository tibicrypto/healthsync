package com.healthautoexport.serialization.gpx

import com.healthautoexport.domain.model.RoutePoint
import com.healthautoexport.domain.model.WorkoutRoute
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Lỗi phân tích GPX, chỉ rõ nguyên nhân đầu vào không hợp lệ (Requirement 12.7).
 *
 * Được bọc trong [Result.failure] trả về từ [GpxParser.parse]; thông điệp [message] mô tả
 * phần tử/thuộc tính vi phạm để người gọi ghi log hoặc hiển thị.
 */
class GpxParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Phân tích một tài liệu **GPX 1.1** trở lại thành danh sách [WorkoutRoute] (Requirements 12.6, 12.7).
 *
 * Bộ phân tích dùng API DOM tích hợp sẵn của JDK (`javax.xml.parsers` / `org.w3c.dom`) và
 * **vô hiệu hóa thực thể ngoài** (XXE) vì đầu vào được coi là không tin cậy.
 *
 * Hợp lệ hóa (Requirement 12.7) — mọi vi phạm dưới đây trả về [Result.failure] với
 * [GpxParseException] và **không** trả về chuỗi tuyến đường nào:
 * - XML không hợp lệ về cú pháp (không parse được).
 * - Phần tử gốc không phải `<gpx>`.
 * - Thuộc tính `version` của `<gpx>` khác `"1.1"`.
 * - `<trkpt>` thiếu thuộc tính `lat`/`lon`, hoặc tọa độ không phải số hợp lệ.
 * - `<time>` của một điểm không phải dấu thời gian ISO 8601 hợp lệ.
 *
 * Khi hợp lệ, mỗi phần tử `<trk>` được khôi phục thành một [WorkoutRoute] (theo thứ tự xuất
 * hiện); tất cả các `<trkpt>` thuộc mọi `<trkseg>` của track được gộp vào [WorkoutRoute.points]
 * theo đúng thứ tự tài liệu (Requirement 12.2). [WorkoutRoute.workoutId] được lấy từ phần tử
 * con `<name>` của track nếu có, ngược lại `null`.
 */
class GpxParser {

    /**
     * Phân tích [xml] thành danh sách [WorkoutRoute].
     *
     * @param xml nội dung tài liệu GPX 1.1.
     * @return [Result.success] với danh sách tuyến đường khi hợp lệ; [Result.failure] kèm
     *   [GpxParseException] khi đầu vào không phải GPX 1.1 hợp lệ (không trả về tuyến đường nào).
     */
    fun parse(xml: String): Result<List<WorkoutRoute>> {
        val document = try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                // Khóa thực thể ngoài (bảo vệ XXE) với đầu vào không tin cậy.
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
                isXIncludeAware = false
                isExpandEntityReferences = false
                isNamespaceAware = true
            }
            val builder = factory.newDocumentBuilder()
            // Triệt tiêu mọi đầu ra lỗi mặc định; lỗi được ném dưới dạng exception và bắt ở dưới.
            builder.setErrorHandler(null)
            ByteArrayInputStream(xml.toByteArray(StandardCharsets.UTF_8)).use { input ->
                builder.parse(input)
            }
        } catch (e: Exception) {
            return Result.failure(
                GpxParseException("Đầu vào không phải XML hợp lệ: ${e.message}", e),
            )
        }

        val root: Element = document.documentElement
            ?: return Result.failure(GpxParseException("Tài liệu GPX rỗng: không có phần tử gốc."))

        if (root.localName?.let { it != "gpx" } ?: (root.tagName != "gpx")) {
            return Result.failure(
                GpxParseException(
                    "Phần tử gốc phải là <gpx>, nhưng nhận được <${root.tagName}>.",
                ),
            )
        }

        val version = root.getAttribute("version")
        if (version != GPX_VERSION) {
            return Result.failure(
                GpxParseException(
                    "Phiên bản GPX phải là \"$GPX_VERSION\", nhưng nhận được " +
                        "\"${version.ifEmpty { "(thiếu)" }}\".",
                ),
            )
        }

        return try {
            val routes = mutableListOf<WorkoutRoute>()
            for (trk in root.childElements("trk")) {
                routes += parseTrack(trk)
            }
            Result.success(routes)
        } catch (e: GpxParseException) {
            Result.failure(e)
        }
    }

    private fun parseTrack(trk: Element): WorkoutRoute {
        val workoutId = trk.firstChildElement("name")?.textContent?.takeIf { it.isNotEmpty() }
        val points = mutableListOf<RoutePoint>()
        // Gộp mọi <trkpt> của mọi <trkseg> theo đúng thứ tự tài liệu (Requirement 12.2).
        for (seg in trk.childElements("trkseg")) {
            for (trkpt in seg.childElements("trkpt")) {
                points += parseTrackPoint(trkpt)
            }
        }
        return WorkoutRoute(workoutId = workoutId, points = points)
    }

    private fun parseTrackPoint(trkpt: Element): RoutePoint {
        val latRaw = requireAttribute(trkpt, "lat")
        val lonRaw = requireAttribute(trkpt, "lon")
        val latitude = latRaw.toDoubleOrNull()
            ?: throw GpxParseException("Thuộc tính lat không phải số hợp lệ: \"$latRaw\".")
        val longitude = lonRaw.toDoubleOrNull()
            ?: throw GpxParseException("Thuộc tính lon không phải số hợp lệ: \"$lonRaw\".")

        val elevation = trkpt.firstChildElement("ele")?.textContent?.trim()?.takeIf { it.isNotEmpty() }
            ?.let {
                it.toDoubleOrNull()
                    ?: throw GpxParseException("Phần tử <ele> không phải số hợp lệ: \"$it\".")
            }

        val timeText = trkpt.firstChildElement("time")?.textContent?.trim()
        val timestamp = if (timeText.isNullOrEmpty()) {
            throw GpxParseException("Track point thiếu phần tử <time> bắt buộc.")
        } else {
            try {
                Instant.parse(timeText)
            } catch (e: DateTimeParseException) {
                throw GpxParseException("Phần tử <time> không phải ISO 8601 hợp lệ: \"$timeText\".", e)
            }
        }

        return RoutePoint(
            latitude = latitude,
            longitude = longitude,
            timestamp = timestamp,
            altitudeMeters = elevation,
        )
    }

    private fun requireAttribute(element: Element, name: String): String {
        if (!element.hasAttribute(name)) {
            throw GpxParseException("Track point thiếu thuộc tính bắt buộc \"$name\".")
        }
        return element.getAttribute(name)
    }

    private companion object {
        const val GPX_VERSION = "1.1"
    }
}

/** So khớp tên cục bộ của phần tử bất kể có namespace hay không. */
private fun Element.matches(localName: String): Boolean =
    (this.localName ?: this.tagName) == localName

/** Trả về các phần tử con trực tiếp có tên cục bộ [localName], theo thứ tự tài liệu. */
private fun Element.childElements(localName: String): List<Element> {
    val result = mutableListOf<Element>()
    val children = childNodes
    for (i in 0 until children.length) {
        val node = children.item(i)
        if (node.nodeType == Node.ELEMENT_NODE && (node as Element).matches(localName)) {
            result += node
        }
    }
    return result
}

/** Trả về phần tử con trực tiếp đầu tiên có tên cục bộ [localName], hoặc `null`. */
private fun Element.firstChildElement(localName: String): Element? =
    childElements(localName).firstOrNull()
