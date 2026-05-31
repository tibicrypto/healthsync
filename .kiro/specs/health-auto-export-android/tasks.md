# Implementation Plan: Health Auto Export cho Android

## Overview

Kế hoạch triển khai chia tính năng thành các bước viết mã tăng dần (incremental), bám theo kiến trúc Clean Architecture đa-module trong `design.md`. Thứ tự xây dựng: dựng dự án Gradle đa-module (`:app`, `:domain`, `:data`, `:serialization`) → mô hình dữ liệu canonical + `MetricCatalog` + các Ports trong `:domain` → pipeline thuần (Serializers/Parsers/Merge/Aggregate + logic xác thực thuần) kèm property-based test (Kotest Property) → bộ điều hợp tầng dữ liệu (Health Connect, Huawei stub, persistence, sáu Destination) → use case điều phối pipeline → Scheduler (WorkManager) → ViewModels/Compose UI → ráp nối bằng Hilt + AndroidManifest → build cuối.

Ngôn ngữ triển khai là **Kotlin** (theo `design.md`). Module `:domain` và `:serialization` là Kotlin/JVM thuần để PBT chạy nhanh trên JVM mà không cần emulator; `:serialization` phụ thuộc `:domain` cho các mô hình dữ liệu canonical dùng chung; `:data` phụ thuộc cả `:domain` và `:serialization`; `:app` phụ thuộc `:domain` và `:data`. Mỗi correctness property được hiện thực bằng đúng một test function (gắn nhãn `// Feature: health-auto-export-android, Property {n}: ...`), tối thiểu 100 vòng lặp; các sub-task test gom các property liên quan theo bảng PBT của thiết kế.

## Tasks

- [ ] 1. Khởi tạo dự án Gradle đa-module và scaffolding
  - [x] 1.1 Dựng cấu trúc 4 module, build script gốc và version catalog
    - Tạo `settings.gradle.kts` khai báo `:app`, `:domain`, `:data`, `:serialization`; tạo `build.gradle.kts` gốc (plugins `apply false`); tạo Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`)
    - Tạo version catalog `gradle/libs.versions.toml` khai báo phiên bản: Kotlin, AGP, Compose BOM/compiler, Hilt, Health Connect client, WorkManager, Room, DataStore, Jetpack Security, OkHttp/Retrofit, kotlinx.serialization, Kotest Property
    - Thiết lập phụ thuộc một chiều: `:serialization → :domain`, `:data → :domain` + `:data → :serialization`, `:app → :domain` + `:app → :data`
    - _Requirements: 22.2_
  - [ ] 1.2 Cấu hình module Android (`:app`, `:data`) và module JVM thuần (`:domain`, `:serialization`)
    - Áp Android Gradle Plugin cho `:app` (application) và `:data` (library) với `minSdk 26`, `targetSdk 35`, `compileSdk 35`; cấu hình `:domain` và `:serialization` là `org.jetbrains.kotlin.jvm` thuần (không phụ thuộc Android SDK)
    - Bật Jetpack Compose cho `:app` (Compose BOM, `buildFeatures.compose = true`, compose compiler); áp Hilt plugin + `kapt`/`ksp` cho `:app` (và `:data` nếu cần cho Room/Hilt)
    - Tạo `AndroidManifest.xml` cơ sở cho `:app` (thẻ `<application>` trỏ tới Application class sẽ tạo ở task 22.1) và `:data` (manifest tối thiểu); thêm khai báo phụ thuộc Health Connect client, WorkManager, Room, DataStore, Jetpack Security, OkHttp/Retrofit vào `:data`, và kotlinx.serialization vào `:serialization`
    - _Requirements: 22.2_
  - [ ] 1.3 Cấu hình thư viện và công cụ kiểm thử dùng chung
    - Thêm `io.kotest:kotest-property`, Kotest assertions, JUnit5 vào `:domain` và `:serialization`; thêm MockK, Turbine, Robolectric, `androidx.work:work-testing` (`WorkManagerTestInitHelper`/`TestDriver`), OkHttp `MockWebServer` vào `:data`; thêm tiện ích test cho `:app`
    - Đặt `PropTestConfig(iterations = 100)` làm cấu hình mặc định cho toàn bộ property-based test
    - _Requirements: 22.2_

- [ ] 2. Mô hình dữ liệu canonical, MetricCatalog và Ports (`:domain`)
  - [ ] 2.1 Định nghĩa enum và định danh nền tảng
    - Tạo `DataSourceId`, `HealthMetricType` (phủ 12 nhóm metric của Requirement 4.1), `WorkoutType`, `AggregationPeriod`, `MetricKind` (CUMULATIVE/INSTANTANEOUS), `MetricSchema` (STANDARD/BLOOD_PRESSURE/SLEEP/ECG/HR_NOTIFICATION/...), `ExportFormat`, `DestinationType`, `SleepState`, `PermissionState`, `ExportStatus`, `CanonicalUnit`
    - _Requirements: 4.1, 3.1, 8.1, 10.1_
  - [ ] 2.2 Định nghĩa `MetricValue` và `UnifiedRecord`
    - `sealed interface MetricValue` với `Scalar`, `BloodPressure`, `HeartRateStat`, `SleepSegment`, `Ecg`, ...; dùng `BigDecimal` để giữ độ chính xác `qty`
    - `UnifiedRecord(metric, value, unit, timestamp, zoneOffset, dataSourceId, extras)` với `extras` mang `mealTime`/`reason`/`state`...
    - _Requirements: 4.2, 4.5, 6.1, 6.2, 6.4, 10.5_
  - [ ] 2.3 Định nghĩa `Workout`, `RoutePoint`, `WorkoutMetrics`, `HeartRateSample`
    - `RoutePoint` cho phép `altitudeMeters` null; `WorkoutMetrics` chỉ chứa trường tùy chọn (null khi vắng); `route`/`heartRateSeries` nullable
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_
  - [ ] 2.4 Định nghĩa envelope `ExportDataset` và các mô hình 8 danh mục
    - `ExportDataset` chứa đủ 8 danh sách (`metrics`, `workouts`, `stateOfMind`, `medications`, `symptoms`, `cycleTracking`, `ecg`, `heartRateNotifications`); `MetricSeries(name, units, data)` và các model `StateOfMind`, `Medication`, `Symptom`, `CycleTrackingEntry`, `EcgRecord`, `HeartRateNotification`
    - _Requirements: 10.1, 10.3_
  - [ ] 2.5 Định nghĩa `DateRange`, bảng dung sai/ưu tiên nguồn, `MetricSelection` và `MetricCatalog`
    - `DateRange(startUtc, endUtc)` với `require(!endUtc.isBefore(startUtc))`; `DuplicateTolerance` + `DuplicateToleranceTable`; `SourcePriority`; `MetricSelection`
    - `MetricCatalog` (thuần JVM): bảng tra cứu `Spec(canonicalName, unit, kind, schema, defaultTolerance)` cho mỗi `HealthMetricType` + `isSupportedBy(type, source)` — nguồn sự thật duy nhất cho Aggregator/Serializer/UI
    - _Requirements: 9.1, 9.2, 9.3, 7.2, 7.4, 7.8, 4.1, 4.2, 4.3, 4.6_
  - [ ] 2.6 Định nghĩa các Ports (interface) và kiểu kết quả I/O
    - Interface: `HealthDataSource`, `PermissionManager`, `Destination`, `AutomationRepository`, `SyncLogRepository`, `CredentialStore`, `Scheduler`, `SourceToggleStore`
    - Kiểu hỗ trợ: `SourceAvailability`, `SourceReadResult`, `ReadOutcome`, `ReadWarning`, `PermissionRequestResult`, `ExportPayload`, `DestinationResult`, `DestinationConfig`, `ExportProgress`, `JobReport`
    - _Requirements: 1.2, 2.2, 3.3, 16.2, 22.9_

- [ ] 3. Triển khai JSON serializer/parser (`:serialization`)
  - [ ] 3.1 Triển khai `JsonSerializer`
    - Đóng gói trong `data` với đủ 8 mảng (rỗng là `[]`, không null/không bỏ khóa); UTF-8 không BOM; `qty` ghi bằng `BigDecimal.toPlainString()` giữ dấu và ≥ 6 chữ số thập phân, không làm tròn mất dữ liệu; `date` theo `yyyy-MM-dd HH:mm:ss Z`; metric tiêu chuẩn (`name`/`units`/`data[]`) và metric có lược đồ riêng theo `MetricSchema`
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7_
  - [ ] 3.2 Triển khai `JsonParser`
    - Khôi phục `ExportDataset` từ văn bản JSON; đầu vào không tuân thủ → `Result.failure` mô tả phần tử vi phạm, không tạo dataset một phần
    - _Requirements: 10.8, 10.9_
  - [ ]* 3.3 Viết property test JSON round-trip và fidelity
    - **Property 1: JSON round-trip; Property 2: qty numeric fidelity; Property 3: envelope completeness; Property 12: UTF-8 không BOM; Property 13: định dạng dấu thời gian thống nhất**
    - _Properties: 1, 2, 3, 12, 13_ — _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7, 10.8_
  - [ ]* 3.4 Viết property test JSON parser từ chối đầu vào sai
    - **Property 4: JSON parser từ chối đầu vào không hợp lệ**
    - _Properties: 4_ — _Requirements: 10.9_

- [ ] 4. Triển khai CSV serializer (`:serialization`)
  - [ ] 4.1 Triển khai `CsvSerializer`
    - Một tài liệu CSV mỗi `MetricSeries`: dòng tiêu đề theo thứ tự cột cố định của catalog + 1 dòng dữ liệu/bản ghi; escape RFC-4180 (dấu phẩy/nháy kép/xuống dòng); trường rỗng để trống; UTF-8 không BOM; kết thúc dòng CRLF; `date` theo `yyyy-MM-dd HH:mm:ss Z`
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7_
  - [ ] 4.2 Triển khai đóng gói archive CSV
    - Gom nhiều tài liệu CSV vào một archive ZIP (`CsvArchive`), đặt tên mỗi tài liệu theo định danh `HealthMetricType`
    - _Requirements: 11.8_
  - [ ]* 4.3 Viết property test CSV
    - **Property 8: cell round-trip escaping; Property 9: column-order consistency; Property 10: CRLF line endings; Property 11: archive packaging**
    - _Properties: 8, 9, 10, 11_ — _Requirements: 11.1, 11.2, 11.3, 11.5, 11.7, 11.8_

- [ ] 5. Triển khai GPX serializer/parser (`:serialization`)
  - [ ] 5.1 Triển khai `GpxSerializer`
    - GPX 1.1: mỗi Workout một `<trk>` theo thứ tự cung cấp, mỗi route một `<trkseg>`, mỗi điểm một `<trkpt>` với `lat`/`lon` là thuộc tính và `<ele>`/`<time>` là phần tử con; dấu thời gian ISO 8601 UTC giây; Workout không route bị loại khỏi đầu ra GPX (kèm cảnh báo để ghi Sync_Log)
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 5.6_
  - [ ] 5.2 Triển khai `GpxParser`
    - Đọc tài liệu GPX 1.1 trở lại danh sách `WorkoutRoute`; đầu vào không hợp lệ → `Result.failure` chỉ rõ nguyên nhân, không trả route nào
    - _Requirements: 12.6, 12.7_
  - [ ]* 5.3 Viết property test GPX
    - **Property 5: GPX round-trip; Property 6: cấu trúc tài liệu; Property 7: parser từ chối đầu vào sai**
    - _Properties: 5, 6, 7_ — _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.6, 12.7_

- [ ] 6. Triển khai `DataMerger` (`:domain`)
  - [ ] 6.1 Triển khai thuật toán hợp nhất + loại trùng xác định
    - Gộp bản ghi mọi nguồn theo từng `HealthMetricType`; sắp xếp theo khóa `(timestamp, dataSourceId, value)`; loại trùng theo dung sai thời gian/giá trị; chọn bản sống sót theo ưu tiên nguồn rồi `dataSourceId` theo bảng chữ cái; giữ cả hai khi giá trị phân kỳ + gắn nhãn nguồn; bảo đảm idempotence
    - _Requirements: 7.1, 7.3, 7.4, 7.5, 7.6, 7.7, 7.9_
  - [ ]* 6.2 Viết property test merge/dedup/idempotence
    - **Property 14: khử trùng theo dung sai; Property 15: lựa chọn bản sống sót; Property 16: giữ bản phân kỳ giá trị; Property 17: thứ tự sắp xếp tổng; Property 18: idempotence**
    - _Properties: 14, 15, 16, 17, 18_ — _Requirements: 7.1, 7.3, 7.4, 7.5, 7.6, 7.7, 7.9_

- [ ] 7. Triển khai `Aggregator` (`:domain`)
  - [ ] 7.1 Triển khai tổng hợp theo `AggregationPeriod`
    - Khung nửa mở `[start, end)` căn ranh giới lịch theo múi giờ thiết bị (`java.time`, `TemporalAdjusters`); CUMULATIVE → sum, INSTANTANEOUS → `{min, avg, max, count}`; bỏ qua khung rỗng; `SECOND` trả bản ghi thô; giấc ngủ theo ngày xuất tổng thời lượng + thời lượng từng giai đoạn căn @ 00:00:00
    - _Requirements: 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8_
  - [ ]* 7.2 Viết property test aggregation
    - **Property 19: phân hoạch khung; Property 20: căn ranh giới lịch; Property 21: tổng metric tích lũy; Property 22: thống kê metric tức thời; Property 23: bỏ qua khung rỗng; Property 24: SECOND đồng nhất**
    - _Properties: 19, 20, 21, 22, 23, 24_ — _Requirements: 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8_

- [ ] 8. Triển khai phân giải `Date_Range` (`:domain`)
  - [ ] 8.1 Triển khai `DateRangeResolver`
    - Xác thực thứ tự (chấp nhận khi `end ≥ start`); lọc bản ghi theo `[start, end]` bao gồm hai đầu mút (so sánh UTC); clamp `end` về hiện tại khi ở tương lai; mặc định Quick_Export (00:00:00 UTC hôm nay → hiện tại); cửa sổ nối tiếp cho Automation (lần thành công gần nhất / lần kích hoạt đầu)
    - _Requirements: 9.2, 9.3, 9.4, 9.5, 9.6, 9.7, 9.8, 9.9_
  - [ ]* 8.2 Viết property test date range
    - **Property 25: xác thực thứ tự; Property 26: lọc bao gồm hai đầu mút; Property 27: clamp thời điểm kết thúc tương lai**
    - _Properties: 25, 26, 27_ — _Requirements: 9.2, 9.3, 9.4, 9.5, 9.6_

- [ ] 9. Triển khai logic chọn lựa & quyền thuần (`:domain`)
  - [ ] 9.1 Triển khai `MetricSelectionResolver` và logic quyền/nguồn thuần
    - Tính tập metric hiệu lực `selection ∩ granted ∩ (enabled ∩ available ∩ supported)` kèm cảnh báo loại trừ; ánh xạ quyền/scope yêu cầu đúng theo lựa chọn (không thừa/thiếu); bản đồ trạng thái quyền toàn phần theo từng metric; tập nguồn truy vấn `enabled ∩ available`; bảo toàn `dataSourceId`; logic bỏ-qua-và-tiếp-tục cho bản ghi lỗi/thiếu trường
    - _Requirements: 1.2, 1.4, 1.6, 1.7, 2.2, 2.5, 2.7, 3.3, 3.4, 4.3, 4.5, 4.6, 4.7, 6.6_
  - [ ]* 9.2 Viết property test logic chọn lựa & quyền
    - **Property 28: lọc metric hiệu lực; Property 29: yêu cầu quyền chỉ cho lựa chọn; Property 30: trạng thái quyền toàn phần; Property 31: tập nguồn được truy vấn; Property 33: bảo toàn định danh nguồn; Property 34: bỏ qua-và-tiếp-tục**
    - _Properties: 28, 29, 30, 31, 33, 34_ — _Requirements: 1.2, 1.4, 1.6, 1.7, 2.2, 2.5, 2.7, 3.3, 3.4, 4.3, 4.5, 4.6, 4.7, 6.6_

- [ ] 10. Triển khai logic xác thực Destination & lịch thuần (`:domain`)
  - [ ] 10.1 Triển khai bộ validator/guard thuần
    - `FileNameGenerator` (hậu tố số duy nhất, không ghi đè); tên Local Storage `YYYYMMDD-HHMMSS` (UTC) + đuôi định dạng; `StorageGuard` (`freeSpace ≥ payloadSize`); `HttpStatusClassifier` `[200,299]`; `PayloadSizeGuard` ≤ 100MB; `RestConfigValidator` (scheme HTTP/HTTPS, URL ≤ 2048, ≤ 50 header); `ContentTypeMapper`; `MqttPortValidator` `[1,65535]`; `ScheduleIntervalValidator` `[15 phút, 30 ngày]`; `BackoffPolicy` `min(30s × 2^(n-1), 30 phút)` tối đa 5 lần
    - _Requirements: 16.1, 16.3, 16.5, 16.6, 16.8, 17.5, 18.5, 19.1, 21.3, 21.4, 21.8, 15.3, 15.4, 15.7_
  - [ ]* 10.2 Viết property test guard Destination & lịch
    - **Property 40: tên tệp duy nhất; Property 41: định dạng tên Local Storage; Property 42: guard dung lượng; Property 43: phân loại HTTP; Property 44: guard kích thước payload; Property 45: xác thực REST URL/header; Property 46: ánh xạ Content-Type; Property 47: giới hạn cổng MQTT; Property 48: giới hạn khoảng lặp lịch; Property 49: exponential backoff**
    - _Properties: 40, 41, 42, 43, 44, 45, 46, 47, 48, 49_ — _Requirements: 16.1, 16.3, 16.5, 16.6, 16.8, 17.5, 18.5, 19.1, 21.3, 21.4, 21.8, 15.3, 15.4, 15.7_

- [ ] 11. Triển khai logic Automation/Privacy/Sync_Log thuần (`:domain`)
  - [ ] 11.1 Triển khai validator/policy thuần
    - `AutomationNameValidator` (trùng không phân biệt hoa/thường); `DeepLinkConfigParser` (xác thực tham số, từ chối khi thiếu/sai/ngoài tập); `NetworkEgressGuard` (không Destination → chặn egress); `SyncLogOrdering` comparator (giảm dần `completionUtc`, tie-break `startUtc`); `SyncLogEvictionPolicy` (xóa sớm nhất tới khi đạt giới hạn 50–5000)
    - _Requirements: 14.7, 14.8, 22.3, 22.4, 23.3, 23.5_
  - [ ]* 11.2 Viết property test Automation/Privacy/Sync_Log
    - **Property 50: tên Automation duy nhất; Property 51: xác thực deep link; Property 52: không Destination thì không egress; Property 53: thứ tự hiển thị Sync_Log; Property 54: eviction Sync_Log theo giới hạn**
    - _Properties: 50, 51, 52, 53, 54_ — _Requirements: 14.7, 14.8, 22.3, 22.4, 23.3, 23.5_

- [ ] 12. Checkpoint - Bảo đảm toàn bộ test pipeline thuần & logic domain pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 13. Triển khai bộ điều hợp Health Connect (`:data`)
  - [ ] 13.1 Triển khai `HealthConnectMetricMapper` và đọc bản ghi chuẩn hóa
    - Ánh xạ loại bản ghi Health_Connect → `HealthMetricType` và chuyển về đơn vị canonical theo `MetricCatalog`; đọc Workout (loại/start/end/duration), tuyến đường (lat/lon/timestamp tăng dần, độ cao tùy chọn), chuỗi nhịp tim, trường tùy chọn; đọc dữ liệu chuyên biệt (giai đoạn giấc ngủ, ECG, cảnh báo nhịp tim, mealTime đường huyết); bỏ qua bản ghi không ánh xạ kèm cảnh báo
    - _Requirements: 4.2, 4.7, 5.1, 5.2, 5.3, 5.4, 5.5, 6.1, 6.2, 6.3, 6.4, 6.6_
  - [ ] 13.2 Triển khai `HealthConnectDataSource`
    - Dùng `HealthConnectClient`/`TimeRangeFilter`; `availability()` trả `Unavailable` + link Play Store khi chưa cài; `supportedMetrics()`; `readRecords()` trong `DateRange`
    - _Requirements: 1.1, 1.8, 4.6, 5.1_
  - [ ] 13.3 Triển khai `HealthConnectPermissionManager`
    - Yêu cầu quyền đọc chỉ cho metric/workout đã chọn; quyền đọc nền (`PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND`); `grantedStatus`/`refreshGrants` phát hiện thu hồi; lưu tập quyền qua DataStore; timeout 30s giữ tập quyền cũ + ghi Sync_Log
    - _Requirements: 1.2, 1.3, 1.5, 1.6, 1.7, 1.9, 1.10_
  - [ ]* 13.4 Viết property test bất biến dữ liệu chuyên biệt & Workout
    - **Property 35: thời lượng giai đoạn giấc ngủ không âm; Property 36: bất biến ECG; Property 37: giữ mealTime đường huyết; Property 38: thứ tự tăng dần route/nhịp tim; Property 39: trường tùy chọn Workout khi và chỉ khi khả dụng**
    - _Properties: 35, 36, 37, 38, 39_ — _Requirements: 5.2, 5.3, 5.4, 5.5, 6.1, 6.2, 6.4_
  - [ ]* 13.5 Viết unit test cho thông báo không khả dụng & timeout quyền
    - Test thông báo Health_Connect không khả dụng + link cài đặt; timeout yêu cầu quyền giữ tập cũ + ghi Sync_Log
    - _Requirements: 1.1, 1.8, 1.9_

- [ ] 14. Triển khai bộ điều hợp Huawei Health Kit (`:data`)
  - [ ] 14.1 Triển khai `HuaweiHealthDataSource` với stub fallback
    - Abstraction sau `HealthDataSource`; tách phần gọi SDK Huawei qua interface nội bộ `HuaweiHealthClient` có triển khai `NoOpHuaweiClient` (để build/test không cần SDK độc quyền); khi HMS Core không khả dụng → `Unavailable` để App tiếp tục bằng Health_Connect; `HuaweiMetricMapper` chuẩn hóa đơn vị, gắn `dataSourceId = HUAWEI_HEALTH_KIT`
    - _Requirements: 2.1, 4.2, 4.5_
  - [ ] 14.2 Triển khai luồng ủy quyền Huawei trong `PermissionManager`
    - Yêu cầu read scope chỉ cho lựa chọn; lưu/xóa scope qua DataStore; `grantedStatus` Đã/Chưa ủy quyền; timeout 60s hủy luồng không lưu scope; báo lý do thất bại + cho phép thử lại
    - _Requirements: 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8_
  - [ ]* 14.3 Viết unit test luồng ủy quyền Huawei
    - Test thất bại ủy quyền (báo lý do + retry) và timeout 60s (không lưu scope)
    - _Requirements: 2.4, 2.8_

- [ ] 15. Triển khai `DataReader` và lựa chọn nguồn (`:data`)
  - [ ] 15.1 Triển khai `DataReader` điều phối đa nguồn
    - Đọc theo `SourceToggleStore`; `withTimeoutOrNull(30s)`/nguồn coi nguồn quá hạn là không khả dụng + ghi Sync_Log; tiếp tục nguồn còn lại; trả `Success`/`NoEnabledSource`/`AllSourcesUnavailable` đúng tình huống
    - _Requirements: 3.3, 3.4, 3.5, 3.6, 3.7, 4.7_
  - [ ] 15.2 Triển khai `SourceToggleStore` trên DataStore
    - Lưu/khôi phục lựa chọn bật/tắt mỗi Data_Source và thứ hạng ưu tiên nguồn qua các phiên làm việc
    - _Requirements: 3.1, 3.2, 7.8_
  - [ ]* 15.3 Viết unit test cho `DataReader`
    - Một nguồn/hai nguồn, timeout nguồn, all-unavailable, no-enabled
    - _Requirements: 3.3, 3.4, 3.5, 3.6, 3.7_

- [ ] 16. Triển khai persistence: Room, DataStore, CredentialStore (`:data`)
  - [ ] 16.1 Triển khai Room cho Automation và Sync_Log
    - `AutomationEntity` (index unique `nameLower`), `SyncLogEntity` (chỉ metadata, không dữ liệu thô); DAO; mapper entity ↔ domain; hiện thực `AutomationRepository`, `SyncLogRepository` áp `SyncLogOrdering` + `SyncLogEvictionPolicy`
    - _Requirements: 14.5, 23.1, 23.2, 23.3, 23.4, 23.5, 23.6_
  - [ ] 16.2 Triển khai `EncryptedCredentialStore`
    - `EncryptedSharedPreferences` với master key Android Keystore (AES-256-GCM); chỉ lưu credential Destination, không vào Room/log
    - _Requirements: 22.9_
  - [ ]* 16.3 Viết unit/integration test round-trip persistence (Robolectric)
    - **Property 32: round-trip lưu trữ cấu hình** — lưu rồi đọc lại trả về giá trị bằng nhau cho permission set, source toggle/priority, Automation, credential
    - _Properties: 32_ — _Requirements: 1.3, 2.3, 3.2, 14.5, 22.9_

- [ ] 17. Triển khai sáu Destination (`:data`)
  - [ ] 17.1 Triển khai `RestApiDestination`
    - OkHttp/Retrofit; gửi body + Content-Type theo định dạng; timeout 30s; 2xx = thành công, ngoài 2xx ghi mã + body; cảnh báo non-HTTPS; chặn payload > 100MB; áp validator từ task 10.1
    - _Requirements: 16.1, 16.2, 16.3, 16.4, 16.5, 16.6, 16.7, 16.8_
  - [ ] 17.2 Triển khai `GoogleDriveDestination` (abstracted client + test fake)
    - Drive REST v3 file-creation scope qua interface `DriveClient` (có fake để test); upload vào thư mục cấu hình; trùng tên → hậu tố số (không ghi đè); reauth khi thiếu/hết hạn; retry ≤ 3 lần ≥ 30s; timeout 120s/50MB; không partial
    - _Requirements: 17.1, 17.2, 17.3, 17.4, 17.5, 17.6, 17.7_
  - [ ] 17.3 Triển khai `DropboxDestination` (abstracted client + test fake)
    - App-folder scope qua interface `DropboxClient` (có fake để test); ủy quyền khởi tạo ≤ 2s; trùng tên → hậu tố phân biệt; reauth; retry ≤ 3 lần ≥ 5s; không partial
    - _Requirements: 18.1, 18.2, 18.3, 18.4, 18.5, 18.6, 18.7_
  - [ ] 17.4 Triển khai `MqttDestination` (abstracted client + test fake)
    - Interface `MqttClient` (có fake để test) bọc HiveMQ/Paho; host/port (1–65535)/topic/auth; QoS 0/1/2; TLS tùy chọn; timeout kết nối 30s; QoS0 fire-and-forget; QoS1/2 chờ ack ≤ 30s
    - _Requirements: 19.1, 19.2, 19.3, 19.4, 19.5, 19.6, 19.7, 19.8_
  - [ ] 17.5 Triển khai `HomeAssistantDestination`
    - OkHttp/Retrofit; base URL + long-lived token; cảnh báo non-HTTPS; timeout 30s; lỗi auth → nhắc cập nhật token; giữ dữ liệu để thử lại
    - _Requirements: 20.1, 20.2, 20.3, 20.4, 20.5, 20.6_
  - [ ] 17.6 Triển khai `LocalStorageDestination`
    - Storage Access Framework (`DocumentFile`); tên `YYYYMMDD-HHMMSS` (UTC) + đuôi; trùng tên → hậu tố `-N` (1..1000); kiểm tra dung lượng trước ghi; ghi tạm rồi commit để không partial; báo thiếu quyền ghi
    - _Requirements: 21.1, 21.2, 21.3, 21.4, 21.5, 21.6, 21.7, 21.8_
  - [ ]* 17.7 Viết integration test cho các Destination
    - REST qua MockWebServer (16.2, 16.7); Drive/Dropbox upload qua test fake (17.2, 18.2); MQTT publish/QoS/ack qua test fake (19.2, 19.4, 19.7, 19.8); Home Assistant auth (20.3, 20.5); Local Storage ghi tệp qua SAF (21.2)
    - _Requirements: 16.2, 16.7, 17.2, 18.2, 19.2, 19.4, 19.7, 19.8, 20.3, 20.5, 21.2_

- [ ] 18. Checkpoint - Bảo đảm test tầng dữ liệu pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 19. Triển khai use case điều phối Export_Job (`:domain`)
  - [ ] 19.1 Triển khai `RunExportJobUseCase`
    - Ráp pipeline: `refreshGrants` đầu job → `DataReader.read` → `DataMerger.merge` → `Aggregator.aggregate` → chọn Serializer theo `ExportFormat` → `Destination.send` → ghi đúng một mục Sync_Log; phát `Flow<ExportProgress>` (0–100, cập nhật ≥ mỗi 2s); thu thập warning thành `JobReport`; không partial khi lỗi/hủy; `ExportJobMutex` cho hủy hợp tác trong 5s; mọi egress qua `NetworkEgressGuard`
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5, 13.6, 4.4, 4.8, 5.6, 6.5, 22.2, 22.3, 22.4, 23.1, 23.2_
  - [ ] 19.2 Triển khai `ConfigureAutomationUseCase` và `DeepLinkHandler`
    - CRUD Automation (tên 1–100 ký tự, bắt buộc Destination + ≥1 metric/workout, trùng tên bị từ chối); phân tích deep link, từ chối tham số thiếu/sai/ngoài tập, trình bày xác nhận trước khi lưu; dừng + dọn partial khi xóa giữa lúc chạy
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5, 14.6, 14.7, 14.8, 14.9_
  - [ ] 19.3 Triển khai `DataWipeUseCase`
    - Xóa Automation + credential + Sync_Log trong 10s; xác nhận liệt kê từng loại đã xóa; giữ nguyên dữ liệu nếu xóa thất bại
    - _Requirements: 22.6, 22.7, 22.8_
  - [ ]* 19.4 Viết unit test use case
    - Tiến trình phần trăm, hủy/đồng thời Quick_Export, không partial; CRUD/deep link Automation; data wipe
    - _Requirements: 13.2, 13.5, 13.6, 14.1, 14.2, 14.3, 14.4, 14.6, 14.9, 22.6, 22.7, 22.8_

- [ ] 20. Triển khai Scheduler nền (`:data`)
  - [ ] 20.1 Triển khai `ExportWorker` và `WorkManagerScheduler`
    - `CoroutineWorker` gọi `RunExportJobUseCase`; `PeriodicWorkRequest` (15 phút–30 ngày) với `setBackoffCriteria(EXPONENTIAL, 30s)` tối đa 5 lần; unique name = `automationId`, `ExistingPeriodicWorkPolicy.UPDATE`; dedupe lần chạy chồng lấn ghi "bị bỏ qua do trùng lặp"; cửa sổ Date_Range nối tiếp; thiếu quyền nền ghi thất bại + thông báo; hết retry ghi "đã vượt số lần thử lại"; hướng dẫn miễn trừ khi hạn chế nền; `cancel(automationId)`
    - _Requirements: 15.1, 15.2, 15.3, 15.5, 15.6, 15.7, 15.8, 15.9, 15.10, 1.10, 14.9_
  - [ ]* 20.2 Viết integration test Scheduler
    - Kích hoạt theo lịch + dedupe qua `WorkManager TestDriver`
    - _Requirements: 15.1, 15.2, 15.5_

- [ ] 21. Triển khai Presentation Layer (Compose + ViewModel) (`:app`)
  - [ ] 21.1 Triển khai ViewModels (StateFlow)
    - `MetricsViewModel`, `PermissionsViewModel`, `AutomationsViewModel`, `QuickExportViewModel`, `SyncLogViewModel`, `DestinationsViewModel`, `SettingsViewModel`; nối tới use case; xác thực Date_Range/Automation; cập nhật trạng thái quyền trong 5s khi mở/refresh
    - _Requirements: 1.7, 2.7, 3.1, 4.4, 4.8, 9.1, 9.2, 9.6, 13.2, 13.3, 13.4, 13.5, 13.6, 14.1, 14.2, 14.3, 14.4_
  - [ ] 21.2 Triển khai Compose Screens + Navigation
    - Màn hình Metrics (chọn chỉ số/loại Workout), Permissions (trạng thái quyền HC/Huawei), Automations (CRUD), QuickExport (tiến trình + hủy), SyncLog, Destinations (cấu hình + cảnh báo non-HTTPS), Settings (bật/tắt nguồn, ưu tiên nguồn, xóa dữ liệu); Navigation Compose; hiển thị thông báo Health_Connect không khả dụng + link cài đặt, thông báo hạn chế nền, màn hình xác nhận xóa dữ liệu
    - _Requirements: 1.1, 1.7, 1.8, 2.7, 3.1, 4.4, 4.8, 9.2, 9.6, 13.2, 13.3, 13.4, 13.6, 14.2, 14.6, 15.10, 16.4, 19.1, 20.2, 21.1, 22.6, 22.7, 23.3, 23.6_
  - [ ]* 21.3 Viết unit test ViewModel
    - Xác thực Date_Range/clamp, tiến trình Quick_Export, CRUD Automation
    - _Requirements: 9.2, 9.6, 13.2, 14.3, 14.4_

- [ ] 22. Ráp nối bằng Hilt, Application và AndroidManifest (`:app`)
  - [ ] 22.1 Triển khai Hilt module, Application entry và khai báo AndroidManifest
    - Bind các Ports (`HealthDataSource` map theo `DataSourceId`, `PermissionManager`, `Destination` map theo `DestinationType`, repositories, `CredentialStore`, `Scheduler`, `SourceToggleStore`, `Clock`/`ZoneIdProvider`); cấu hình `HiltWorkerFactory` cho `ExportWorker`; tiêm lớp mạng qua `NetworkEgressGuard`; `@HiltAndroidApp` Application + `MainActivity` (Navigation host) nhận deep link
    - Khai báo `AndroidManifest.xml`: quyền đọc Health_Connect (`android.permission.health.READ_*` cho từng metric/workout), `READ_HEALTH_DATA_IN_BACKGROUND` (Requirement 1.5), `INTERNET`, foreground service (`FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` nếu cần cho Export_Job dài), activity rationale quyền Health_Connect (intent-filter `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`), và intent-filter deep link cấu hình Automation
    - _Requirements: 22.3, 22.4, 15.1, 1.5, 1.10, 14.6_
  - [ ]* 22.2 Viết smoke test cấu hình
    - Catalog phủ đủ nhóm metric (4.1); không SDK telemetry/analytics, không màn hình đăng nhập (22.1, 22.5); bảng dung sai entry không âm mỗi metric (7.2); Sync_Log entity chỉ metadata (23.4)
    - _Requirements: 4.1, 7.2, 22.1, 22.5, 23.4_

- [ ] 23. Build cuối và chạy toàn bộ test suite
  - [ ] 23.1 Chạy `./gradlew assembleDebug` và toàn bộ test
    - Xác nhận biên dịch toàn bộ module; chạy `./gradlew test` (PBT + unit) cho `:domain`/`:serialization` và `./gradlew testDebugUnitTest` cho `:data`/`:app`; sửa lỗi cho đến khi xanh
    - _Requirements: 22.2_

## Notes

- Các sub-task gắn hậu tố `*` (unit/property/integration/smoke test) là tùy chọn; task triển khai lõi không bao giờ tùy chọn.
- Mỗi correctness property được hiện thực bằng đúng một property-based test (Kotest Property), tối thiểu 100 vòng lặp (`PropTestConfig(iterations = 100)`), gắn nhãn `// Feature: health-auto-export-android, Property {n}: {property_text}`.
- 54 property được phủ qua các task: 3.3 (1,2,3,12,13), 3.4 (4), 4.3 (8,9,10,11), 5.3 (5,6,7), 6.2 (14–18), 7.2 (19–24), 8.2 (25,26,27), 9.2 (28,29,30,31,33,34), 10.2 (40–49), 11.2 (50–54), 13.4 (35–39), 16.3 (32).
- Module `:domain` và `:serialization` là Kotlin/JVM thuần để PBT chạy nhanh trên JVM; Huawei Health Kit trừu tượng hóa sau `HealthDataSource` với `NoOpHuaweiClient`, và các đích đám mây/MQTT đứng sau interface client có test fake để build/test không cần SDK độc quyền hay broker thật.
- Checkpoint (task 12, 18, 23) bảo đảm kiểm chứng tăng dần: pipeline thuần & logic domain → tầng dữ liệu → toàn hệ thống.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "2.1"] },
    { "id": 2, "tasks": ["1.3", "2.2", "2.3", "2.4", "2.5"] },
    { "id": 3, "tasks": ["2.6", "3.1", "3.2", "4.1", "5.1", "5.2", "6.1", "7.1", "8.1", "9.1", "10.1", "11.1"] },
    { "id": 4, "tasks": ["3.3", "3.4", "4.2", "5.3", "6.2", "7.2", "8.2", "9.2", "10.2", "11.2"] },
    { "id": 5, "tasks": ["4.3"] },
    { "id": 6, "tasks": ["13.1", "14.1", "15.2", "16.1", "16.2", "17.1"] },
    { "id": 7, "tasks": ["13.2", "13.3", "14.2", "15.1", "16.3", "17.2", "17.3", "17.4", "17.5", "17.6"] },
    { "id": 8, "tasks": ["13.4", "13.5", "14.3", "15.3", "17.7", "19.1", "19.2", "19.3"] },
    { "id": 9, "tasks": ["19.4", "20.1"] },
    { "id": 10, "tasks": ["20.2", "21.1"] },
    { "id": 11, "tasks": ["21.2", "21.3", "22.1"] },
    { "id": 12, "tasks": ["22.2", "23.1"] }
  ]
}
```
