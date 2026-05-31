package com.healthautoexport.data.huawei

/**
 * Một **phạm vi ủy quyền đọc** trung lập của Huawei_Health_Kit, biểu diễn dưới dạng một chuỗi
 * định danh ổn định ([value]).
 *
 * `HuaweiScope` là kiểu **trừu tượng hóa** đứng giữa logic domain ([com.healthautoexport.domain.logic.ReadScope])
 * và SDK độc quyền của Huawei (`com.huawei.hms.hihealth` / lớp `Scope`). Nhờ vậy `:data` có thể
 * build và chạy trên thiết bị không phải Huawei mà không cần phụ thuộc `com.huawei.hms:health`
 * (Requirement 2.1). Một bản hiện thực [HuaweiHealthClient] thật cho **Huawei flavor** sẽ dịch
 * [value] sang chuỗi scope chính thức của Huawei (vd `https://www.huawei.com/healthkit/step.read`)
 * khi gọi `HealthKitAuthClient`.
 *
 * @property value chuỗi định danh scope ổn định; nội dung do [HuaweiScopeMapper] sinh ra một cách
 *   xác định để ánh xạ hai chiều giữa metric/workout và scope luôn nhất quán.
 */
@JvmInline
internal value class HuaweiScope(val value: String)

/**
 * Kết quả của một luồng ủy quyền Huawei_Health_Kit do [HuaweiHealthClient.requestAuthorization]
 * trả về (Requirements 2.3, 2.4, 2.1).
 *
 * Đây là kiểu trung lập (không lộ kiểu của SDK Huawei) để tầng [HuaweiPermissionManager] xử lý
 * đồng nhất giữa bản hiện thực thật và [NoOpHuaweiHealthClient].
 */
internal sealed interface HuaweiAuthResult {

    /**
     * Người dùng đã hoàn tất ủy quyền; [grantedScopes] là tập phạm vi thực sự được cấp
     * (Requirement 2.3). Có thể là tập con của các scope đã yêu cầu nếu người dùng từ chối một phần.
     *
     * @property grantedScopes tập scope được người dùng cấp.
     */
    data class Authorized(val grantedScopes: Set<HuaweiScope>) : HuaweiAuthResult

    /**
     * Luồng ủy quyền thất bại với lý do do Huawei_Health_Kit báo cáo (Requirement 2.4). App hiển
     * thị lý do này và cho phép người dùng thử lại.
     *
     * @property reason lý do thất bại người dùng đọc được.
     */
    data class Failed(val reason: String) : HuaweiAuthResult

    /**
     * HMS Core / Huawei_Health_Kit không khả dụng trên thiết bị (Requirement 2.1); không thể chạy
     * luồng ủy quyền. App tiếp tục hoạt động bằng Health_Connect.
     */
    data object Unavailable : HuaweiAuthResult
}
