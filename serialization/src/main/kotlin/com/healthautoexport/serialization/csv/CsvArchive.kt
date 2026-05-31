package com.healthautoexport.serialization.csv

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Một tập hợp tài liệu CSV per-metric, đóng gói được thành **một tệp lưu trữ ZIP duy nhất** cho một
 * Export_Job (Requirement 11.8) — kết quả trả về của
 * [CsvSerializer.serialize][CsvSerializer.serialize].
 *
 * Mỗi [Entry] tương ứng đúng một loại Health_Metric và mang nội dung CSV đã mã hóa **UTF-8 không
 * BOM** (Requirement 11.6). Thứ tự các entry được giữ nguyên theo thứ tự metric trong
 * `ExportDataset.metrics`, và [toZipBytes] phát ra ZIP một cách xác định để dễ kiểm thử và so sánh.
 *
 * Đây là kiểu thuần JVM (chỉ dùng `java.util.zip` của JDK) nên không thêm phụ thuộc nào.
 *
 * @property entries danh sách tài liệu CSV đã đặt tên; mỗi tên là định danh metric tương ứng
 *   (Requirement 11.8).
 */
data class CsvArchive(
    val entries: List<Entry>,
) {

    /**
     * Một tài liệu CSV đã đặt tên trong [CsvArchive].
     *
     * @property name định danh loại Health_Metric (tên canonical snake_case, vd `step_count`) dùng
     *   làm cơ sở tên tệp; **không** kèm đuôi `.csv` (xem [fileName]). Requirement 11.8.
     * @property content byte nội dung CSV, mã hóa UTF-8 không BOM (Requirement 11.6).
     */
    data class Entry(
        val name: String,
        val content: ByteArray,
    ) {
        /** Tên tệp của tài liệu trong archive: `<name>.csv`. */
        val fileName: String get() = "$name.csv"

        // data class với mảng byte cần equals/hashCode theo nội dung để so sánh trong test.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Entry) return false
            return name == other.name && content.contentEquals(other.content)
        }

        override fun hashCode(): Int = 31 * name.hashCode() + content.contentHashCode()
    }

    /**
     * Đóng gói toàn bộ [entries] vào một mảng byte ZIP, mỗi entry là một mục `<name>.csv`
     * (Requirement 11.8).
     *
     * Đầu ra mang tính xác định: thứ tự mục theo [entries] và mỗi mục dùng một mốc thời gian cố định
     * để hai lần serialize cùng dữ liệu cho cùng chuỗi byte.
     */
    fun toZipBytes(): ByteArray {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            for (entry in entries) {
                val zipEntry = ZipEntry(entry.fileName).apply {
                    // Mốc thời gian cố định ⇒ ZIP byte-for-byte ổn định giữa các lần chạy.
                    time = FIXED_ENTRY_TIME_MILLIS
                }
                zip.putNextEntry(zipEntry)
                zip.write(entry.content)
                zip.closeEntry()
            }
        }
        return buffer.toByteArray()
    }

    companion object {
        /**
         * Mốc thời gian cố định (epoch, giờ địa phương 1980-01-01 — mốc tối thiểu của định dạng ZIP)
         * gán cho mọi mục để đầu ra ZIP có tính tái lập.
         */
        private const val FIXED_ENTRY_TIME_MILLIS: Long = 315_532_800_000L

        /** Archive rỗng (không có tài liệu CSV nào) — vd khi Export_Job không có metric nào. */
        val EMPTY: CsvArchive = CsvArchive(emptyList())
    }
}
