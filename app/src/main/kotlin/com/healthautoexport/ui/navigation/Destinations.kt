package com.healthautoexport.ui.navigation

/**
 * Các đích điều hướng (route) cấp cao của App, dùng cho [AppNavGraph] và thanh điều hướng dưới
 * (task 21.2).
 *
 * Mỗi mục mang [route] ổn định để `NavHost` định tuyến và [label] hiển thị trên bottom navigation.
 * `QUICK_EXPORT` là điểm khởi đầu (start destination) vì xuất nhanh là tác vụ chính.
 *
 * @property route khóa route duy nhất dùng trong Navigation Compose.
 * @property label nhãn tiếng Việt hiển thị trên thanh điều hướng.
 */
enum class TopDestination(val route: String, val label: String) {
    QUICK_EXPORT("quick_export", "Xuất nhanh"),
    METRICS("metrics", "Chỉ số"),
    AUTOMATIONS("automations", "Tự động"),
    DESTINATIONS("destinations", "Đích đến"),
    PERMISSIONS("permissions", "Quyền"),
    SYNC_LOG("sync_log", "Nhật ký"),
    SETTINGS("settings", "Cài đặt"),
    ;

    companion object {
        /** Điểm khởi đầu của đồ thị điều hướng. */
        val START: TopDestination = QUICK_EXPORT

        /** Các đích hiển thị trên thanh điều hướng dưới (giữ gọn để vừa màn hình). */
        val bottomBar: List<TopDestination> = listOf(
            QUICK_EXPORT,
            METRICS,
            AUTOMATIONS,
            DESTINATIONS,
            SETTINGS,
        )
    }
}
