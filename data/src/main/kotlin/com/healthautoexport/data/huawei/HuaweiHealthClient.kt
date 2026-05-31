package com.healthautoexport.data.huawei

import com.healthautoexport.domain.model.DateRange
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.WorkoutType

/**
 * Ranh giới trừu tượng hóa **mọi** lời gọi tới SDK độc quyền của Huawei_Health_Kit
 * (`com.huawei.hms:health`).
 *
 * ## Vì sao tồn tại interface này
 *
 * SDK HMS Health Kit là phụ thuộc **độc quyền** chỉ phân phối qua kho Maven của Huawei và chỉ
 * hoạt động trên thiết bị có HMS Core. Để App vẫn **build và chạy trên thiết bị không phải
 * Huawei** (và trong môi trường kiểm thử CI không có HMS), `:data` **không** khai báo phụ thuộc
 * `com.huawei.hms:health`. Thay vào đó, toàn bộ tương tác với SDK được đặt sau `HuaweiHealthClient`:
 *
 * - Bản hiện thực mặc định là [NoOpHuaweiHealthClient] — báo HMS không khả dụng, mọi lần đọc trả
 *   về rỗng, ủy quyền trả về [HuaweiAuthResult.Unavailable]. Nhờ vậy [HuaweiHealthDataSource] báo
 *   `Unavailable` và App tiếp tục bằng Health_Connect (Requirement 2.1).
 * - Khi dựng một **Huawei flavor** thực sự (có thêm phụ thuộc `com.huawei.hms:health` và kho Maven
 *   của Huawei), một bản hiện thực thật bọc `HealthKitAuthClient`/`DataController`/`SettingController`
 *   sẽ được "drop-in" thay cho NoOp mà không cần đổi [HuaweiHealthDataSource] hay
 *   [HuaweiPermissionManager].
 *
 * ## Hợp đồng
 *
 * Mọi hàm là `suspend` để bọc các lời gọi bất đồng bộ của SDK (vốn dựa trên `Task`/callback) bằng
 * coroutine. Các hàm **không nên** tự áp đặt timeout của riêng mình; việc hủy theo thời gian
 * (vd 60 giây ở Requirement 2.8) do tầng gọi ([HuaweiPermissionManager]) điều phối qua
 * `withTimeoutOrNull` để có thể hủy luồng một cách hợp tác.
 */
internal interface HuaweiHealthClient {

    /**
     * `true` nếu HMS Core và Huawei_Health_Kit khả dụng và sẵn sàng trên thiết bị hiện tại
     * (Requirement 2.1).
     *
     * Bản hiện thực thật thường kiểm tra `HuaweiApiAvailability.isHuaweiMobileServicesAvailable`
     * và trạng thái Health Kit; [NoOpHuaweiHealthClient] luôn trả về `false`.
     */
    suspend fun isHmsAvailable(): Boolean

    /**
     * Khởi chạy luồng ủy quyền của Huawei_Health_Kit, yêu cầu đúng tập [scopes] đọc tương ứng với
     * lựa chọn của người dùng (Requirement 2.2) và trả về kết quả ([HuaweiAuthResult]).
     *
     * Hàm này có thể treo cho tới khi người dùng hoàn tất luồng đồng ý; tầng gọi bọc nó trong
     * `withTimeoutOrNull(60s)` để áp giới hạn thời gian và hủy (Requirement 2.8). Khi bị hủy do
     * timeout, bản hiện thực SHALL hợp tác hủy (không lưu scope) — xem [cancelAuthorization].
     *
     * @param scopes tập phạm vi đọc cần yêu cầu (đúng-một-một với lựa chọn — Requirement 2.2).
     * @return [HuaweiAuthResult.Authorized] kèm scope thực cấp, [HuaweiAuthResult.Failed] kèm lý
     *   do, hoặc [HuaweiAuthResult.Unavailable] khi HMS không khả dụng.
     */
    suspend fun requestAuthorization(scopes: Set<HuaweiScope>): HuaweiAuthResult

    /**
     * Trả về tập phạm vi đọc **hiện đang được ủy quyền** theo SDK Huawei (Requirements 2.3, 2.7).
     *
     * Dùng để làm mới/đồng bộ trạng thái ủy quyền và phát hiện thay đổi. [NoOpHuaweiHealthClient]
     * luôn trả về tập rỗng.
     */
    suspend fun getAuthorizedScopes(): Set<HuaweiScope>

    /**
     * Hủy/thu hồi ủy quyền Huawei_Health_Kit hiện tại (Requirement 2.6).
     *
     * Được gọi khi người dùng chủ động hủy ủy quyền, hoặc khi luồng ủy quyền bị hủy do hết thời
     * gian chờ để bảo đảm không giữ lại trạng thái dở dang (Requirement 2.8).
     * [NoOpHuaweiHealthClient] là no-op.
     */
    suspend fun cancelAuthorization()

    /**
     * Đọc các bản ghi thô trong [range] cho [metrics] đã chọn (Requirements 2.x, 4.2, 6.x).
     *
     * Trả về [HuaweiRawSample] **trung lập** (không phải kiểu của SDK Huawei) để
     * [HuaweiMetricMapper] dịch sang [com.healthautoexport.domain.model.UnifiedRecord] với đơn vị
     * canonical. [NoOpHuaweiHealthClient] luôn trả về danh sách rỗng.
     *
     * @param metrics tập metric cần đọc.
     * @param range khoảng thời gian đọc (UTC, bao gồm hai đầu mút — Requirement 9.5).
     * @return danh sách mẫu thô; rỗng nếu không có dữ liệu hoặc HMS không khả dụng.
     */
    suspend fun readRecords(
        metrics: Set<HealthMetricType>,
        range: DateRange,
    ): List<HuaweiRawSample>

    /**
     * Đọc các phiên tập thô trong [range] cho [workouts] đã chọn (Requirement 5.x).
     *
     * Trả về [HuaweiRawWorkout] trung lập để [HuaweiMetricMapper] dịch sang
     * [com.healthautoexport.domain.model.Workout]. [NoOpHuaweiHealthClient] luôn trả về rỗng.
     *
     * @param workouts tập loại Workout cần đọc.
     * @param range khoảng thời gian đọc (UTC).
     * @return danh sách phiên tập thô; rỗng nếu không có dữ liệu hoặc HMS không khả dụng.
     */
    suspend fun readWorkouts(
        workouts: Set<WorkoutType>,
        range: DateRange,
    ): List<HuaweiRawWorkout>
}
