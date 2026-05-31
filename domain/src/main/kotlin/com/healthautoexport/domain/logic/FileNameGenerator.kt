package com.healthautoexport.domain.logic

/**
 * Sinh tên tệp **duy nhất** bằng cách thêm hậu tố số tăng dần `-N` (N bắt đầu từ 1) vào trước
 * phần mở rộng, dùng chung cho các Destination ghi tệp (Google Drive 17.5, Dropbox 18.5,
 * Local Storage 21.4).
 *
 * Hàm thuần (pure): không thực hiện I/O. Tập tên đã tồn tại được truyền vào dưới dạng vị từ
 * [exists] để tầng dữ liệu tự quyết định cách kiểm tra (thư mục SAF, Drive, Dropbox...).
 *
 * Hành vi (Property 40 — *Validates: Requirements 17.5, 18.5, 21.4*):
 * - Tên trả về SHALL **không** nằm trong tập đã tồn tại (không bao giờ yêu cầu ghi đè).
 * - Nếu [desiredName] chưa tồn tại, trả về nguyên trạng (không thêm hậu tố).
 * - Ngược lại thử `stem-1.ext`, `stem-2.ext`, ... cho tới khi tìm được tên chưa tồn tại.
 * - Tối đa [maxAttempts] lần thêm hậu tố; vượt quá → trả về `null` (Requirement 21.5: hủy ghi,
 *   không tạo tệp một phần).
 */
object FileNameGenerator {

    /** Số lần thử thêm hậu tố tối đa cho Local Storage (Requirements 21.4, 21.5). */
    const val DEFAULT_MAX_ATTEMPTS: Int = 1000

    /**
     * Trả về một tên tệp duy nhất dựa trên [desiredName], hoặc `null` nếu không tìm được sau
     * [maxAttempts] lần thêm hậu tố.
     *
     * @param desiredName tên mong muốn ban đầu, ví dụ `"20240101-120000.json"`.
     * @param exists vị từ trả về `true` nếu tên đã tồn tại tại đích.
     * @param maxAttempts số lần thử thêm hậu tố `-N` tối đa (mặc định [DEFAULT_MAX_ATTEMPTS]);
     *   SHALL `>= 1`.
     * @return tên duy nhất chưa tồn tại, hoặc `null` khi đã cạn số lần thử.
     */
    fun generate(
        desiredName: String,
        exists: (String) -> Boolean,
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    ): String? {
        require(maxAttempts >= 1) { "maxAttempts ($maxAttempts) phải >= 1" }

        // Tên gốc còn trống ⇒ dùng luôn, không tiêu tốn lần thử nào.
        if (!exists(desiredName)) return desiredName

        val (stem, extension) = splitExtension(desiredName)
        for (suffix in 1..maxAttempts) {
            val candidate = "$stem-$suffix$extension"
            if (!exists(candidate)) return candidate
        }
        // Cạn số lần thử: không tạo tệp một phần (Requirement 21.5).
        return null
    }

    /**
     * Tách tên tệp thành cặp `(stem, extension)` trong đó `extension` đã kèm dấu chấm đầu
     * (ví dụ `".json"`), hoặc rỗng nếu không có phần mở rộng.
     *
     * Dấu chấm ở vị trí 0 (tệp ẩn như `".gitignore"`) được coi là **không** có phần mở rộng,
     * nên hậu tố vẫn được thêm vào cuối tên.
     */
    private fun splitExtension(name: String): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) {
            name to ""
        } else {
            name.substring(0, dot) to name.substring(dot)
        }
    }
}
