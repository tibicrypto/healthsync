# Requirements Document

## Introduction

Tài liệu này đặc tả các yêu cầu cho **Health Auto Export cho Android** (gọi tắt là Ứng_Dụng), một ứng dụng Android gốc (native) tái hiện đầy đủ bộ tính năng của ứng dụng iOS "Health Auto Export", nhưng đọc dữ liệu sức khỏe và thể chất từ các nền tảng sức khỏe của Android thay vì từ Apple Health.

Ứng_Dụng đọc dữ liệu sức khỏe và thể chất từ hai nguồn trên thiết bị: **Google Health Connect** và **Huawei Health Service Kit (HMS Health Kit)**. Ứng_Dụng hợp nhất và loại bỏ trùng lặp dữ liệu chồng lấn từ cả hai nguồn, sau đó xuất dữ liệu đã hợp nhất ở các định dạng JSON, CSV và GPX. Việc xuất dữ liệu có thể được kích hoạt thủ công (Xuất nhanh) hoặc chạy tự động trong nền theo lịch. Dữ liệu xuất ra có thể được gửi tới một đích đến có thể cấu hình: một REST API endpoint, Google Drive, Dropbox, một MQTT broker, Home Assistant, hoặc bộ nhớ cục bộ trên thiết bị.

Ứng_Dụng đặt quyền riêng tư lên hàng đầu: không yêu cầu tài khoản người dùng, không thực hiện phân tích hành vi hay thu thập dữ liệu, và xử lý toàn bộ dữ liệu sức khỏe ngay trên thiết bị. Định dạng đầu ra JSON được thiết kế để tương thích với định dạng đã được tài liệu hóa của ứng dụng iOS gốc trong phạm vi mà dữ liệu Android cho phép.

Tài liệu này bao phủ các yêu cầu về: truy cập dữ liệu từ hai nguồn và cấp quyền (Health Connect + Huawei Health Kit), danh mục các chỉ số và loại bài tập được hỗ trợ, hợp nhất/loại trùng dữ liệu, tổng hợp (aggregation), chọn khoảng thời gian, ba định dạng xuất (với bảo đảm khứ hồi parser/serializer), xuất thủ công và xuất theo lịch, toàn bộ các đích đến xuất dữ liệu, cấu hình tự động hóa, quyền riêng tư, và xử lý lỗi.

Lưu ý về quy ước: Các từ khóa cấu trúc EARS (WHEN, WHILE, IF, THEN, WHERE, THE, SHALL) được giữ nguyên bằng tiếng Anh để bảo toàn ký pháp EARS chuẩn và khả năng kiểm tra. Các định danh thuật ngữ trong Bảng thuật ngữ (ví dụ: App, Health_Connect) cũng được giữ nguyên để dùng nhất quán. Phần mô tả nội dung được viết bằng tiếng Việt.

## Glossary

- **App**: Ứng dụng Health Auto Export cho Android được mô tả trong tài liệu này.
- **Health_Connect**: Nền tảng Google Health Connect và SDK client của nó, được dùng làm nguồn dữ liệu sức khỏe và thể chất trên thiết bị Android.
- **Huawei_Health_Kit**: Huawei Health Service Kit (HMS Health Kit) và SDK Android của nó, được dùng làm nguồn dữ liệu sức khỏe và thể chất trên thiết bị Huawei.
- **Data_Source**: Một trong hai nguồn Health_Connect hoặc Huawei_Health_Kit, được xem là nguồn gốc của các bản ghi sức khỏe.
- **Permission_Manager**: Thành phần của App chịu trách nhiệm yêu cầu, theo dõi và xác thực quyền truy cập dữ liệu thời gian chạy cho mỗi Data_Source.
- **Data_Reader**: Thành phần của App truy vấn các bản ghi sức khỏe từ một Data_Source theo loại chỉ số và khoảng thời gian.
- **Data_Merger**: Thành phần của App kết hợp các bản ghi đọc từ nhiều Data_Source thành một tập dữ liệu hợp nhất duy nhất và loại bỏ trùng lặp.
- **Health_Metric**: Một loại dữ liệu sức khỏe hoặc thể chất được định lượng (ví dụ: Số bước, Nhịp tim, Đường huyết) được App hỗ trợ.
- **Workout**: Một phiên tập luyện đã ghi lại, tùy chọn bao gồm tuyến đường GPS, chuỗi nhịp tim và các siêu dữ liệu khác.
- **Aggregation_Period**: Mức độ chi tiết của khung thời gian dùng để tổng hợp bản ghi, một trong: giây, phút, giờ, ngày, tuần, tháng, năm.
- **Aggregator**: Thành phần của App nhóm và tóm tắt các bản ghi theo Aggregation_Period đã chọn.
- **Date_Range**: Một mốc thời điểm bắt đầu và một mốc thời điểm kết thúc do người dùng chọn, dùng để giới hạn các bản ghi được đưa vào một lần xuất.
- **Export_Format**: Định dạng tuần tự hóa của một lần xuất, một trong: JSON, CSV, GPX.
- **JSON_Serializer**: Thành phần của App chuyển một tập dữ liệu hợp nhất thành Export_Format JSON của App.
- **JSON_Parser**: Thành phần của App đọc Export_Format JSON của App trở lại thành một tập dữ liệu hợp nhất.
- **CSV_Serializer**: Thành phần của App chuyển một tập dữ liệu hợp nhất thành Export_Format CSV.
- **GPX_Serializer**: Thành phần của App chuyển dữ liệu tuyến đường Workout thành Export_Format GPX.
- **GPX_Parser**: Thành phần của App đọc Export_Format GPX trở lại thành dữ liệu tuyến đường Workout.
- **Export_Job**: Một lần thực thi đơn lẻ đọc dữ liệu, dựng tập dữ liệu, tuần tự hóa và gửi tới một Destination.
- **Destination**: Một đích đến đã cấu hình để nhận dữ liệu xuất ra, một trong: REST API, Google Drive, Dropbox, MQTT, Home Assistant, Local Storage.
- **Automation**: Một cấu hình có thể lưu lại và tái sử dụng, định nghĩa các chỉ số được chọn, Export_Format, Aggregation_Period, lịch chạy và Destination cho các Export_Job tự động.
- **Scheduler**: Thành phần của App kích hoạt các Automation theo lịch đã cấu hình, kể cả khi App đang ở chế độ nền.
- **Quick_Export**: Một Export_Job được kích hoạt thủ công, theo yêu cầu tức thời.
- **Sync_Log**: Một bản ghi lưu trữ trên thiết bị về kết quả của các Export_Job, bao gồm dấu thời gian, trạng thái và chi tiết lỗi.
- **Unified_Record**: Một điểm dữ liệu đã chuẩn hóa trong tập dữ liệu hợp nhất, mang theo giá trị, đơn vị, dấu thời gian và định danh Data_Source gốc.

## Requirements

### Requirement 1: Truy cập và cấp quyền Nguồn dữ liệu Health Connect

**User Story:** Là một người dùng Android, tôi muốn App kết nối với Google Health Connect và yêu cầu quyền truy cập các loại dữ liệu sức khỏe tôi đã chọn, để App có thể đọc dữ liệu sức khỏe lưu trên thiết bị của tôi.

#### Acceptance Criteria

1. WHEN App khởi động và Health_Connect chưa được cài đặt hoặc không khả dụng trên thiết bị, THE App SHALL hiển thị, trong vòng 5 giây kể từ khi khởi động, một thông báo xác định Health_Connect là không khả dụng.
2. WHEN một người dùng khởi tạo kết nối tới Health_Connect, THE Permission_Manager SHALL yêu cầu quyền đọc chỉ cho các loại Health_Metric và loại Workout mà người dùng đã chọn để xuất.
3. WHEN Health_Connect trả về tập quyền đã được cấp, THE Permission_Manager SHALL lưu tập quyền đã cấp trên thiết bị.
4. IF một người dùng chưa cấp một quyền đọc cần thiết cho một Health_Metric đã chọn, THEN THE App SHALL loại Health_Metric đó khỏi Export_Job và SHALL ghi lại việc loại trừ vào Sync_Log.
5. WHERE một Export_Job nền theo lịch được cấu hình, THE Permission_Manager SHALL yêu cầu quyền đọc nền của Health_Connect để App có thể đọc dữ liệu khi ở chế độ nền.
6. WHEN một người dùng thu hồi một quyền Health_Connect đã cấp trước đó, THE Permission_Manager SHALL phát hiện quyền bị thu hồi ở Export_Job kế tiếp và SHALL loại Health_Metric bị ảnh hưởng khỏi Export_Job đó.
7. THE App SHALL cung cấp một màn hình hiển thị, cho mỗi Health_Metric đã chọn, trạng thái hiện tại (đã cấp hoặc chưa cấp) của quyền đọc Health_Connect tương ứng, và SHALL cập nhật trạng thái hiển thị này trong vòng 5 giây sau khi màn hình được mở hoặc được người dùng làm mới.
8. WHEN App hiển thị thông báo Health_Connect không khả dụng, THE App SHALL cung cấp một liên kết để cài đặt hoặc cập nhật Health_Connect.
9. IF yêu cầu quyền tới Health_Connect không hoàn tất trong vòng 30 giây hoặc Health_Connect trả về lỗi, THEN THE Permission_Manager SHALL hiển thị một thông báo lỗi cho biết yêu cầu quyền không thành công, SHALL giữ nguyên tập quyền đã lưu trước đó, và SHALL ghi sự kiện thất bại vào Sync_Log.
10. IF quyền đọc nền của Health_Connect không được cấp khi một Export_Job nền theo lịch được cấu hình, THEN THE App SHALL ghi việc thiếu quyền nền vào Sync_Log và SHALL thông báo cho người dùng rằng Export_Job nền không thể chạy ở chế độ nền cho đến khi quyền được cấp.

### Requirement 2: Truy cập và cấp quyền Nguồn dữ liệu Huawei Health Kit

**User Story:** Là một người dùng thiết bị Huawei, tôi muốn App kết nối với Huawei Health Service Kit và yêu cầu ủy quyền truy cập dữ liệu sức khỏe của tôi, để App có thể đọc dữ liệu sức khỏe khả dụng thông qua Huawei Health.

#### Acceptance Criteria

1. WHEN App khởi động trên một thiết bị mà Huawei_Health_Kit và các dịch vụ HMS Core cần thiết không khả dụng, THE App SHALL đánh dấu Huawei_Health_Kit là một Data_Source không khả dụng và SHALL tiếp tục hoạt động bằng Health_Connect.
2. WHEN một người dùng khởi tạo kết nối tới Huawei_Health_Kit, THE Permission_Manager SHALL khởi chạy luồng ủy quyền của Huawei_Health_Kit yêu cầu phạm vi đọc (read scope) chỉ cho các loại Health_Metric và loại Workout mà người dùng đã chọn để xuất.
3. WHEN Huawei_Health_Kit trả về các phạm vi đã được ủy quyền, THE Permission_Manager SHALL lưu tập phạm vi đã ủy quyền trên thiết bị.
4. IF luồng ủy quyền Huawei_Health_Kit trả về một thất bại ủy quyền, THEN THE App SHALL hiển thị lý do thất bại do Huawei_Health_Kit báo cáo và SHALL cho phép người dùng thử lại việc ủy quyền.
5. IF một người dùng chưa ủy quyền một phạm vi đọc cần thiết cho một Health_Metric đã chọn, THEN THE App SHALL loại Health_Metric đó khỏi Export_Job và SHALL ghi lại việc loại trừ vào Sync_Log.
6. WHEN một người dùng hủy việc ủy quyền Huawei_Health_Kit, THE Permission_Manager SHALL xóa tập phạm vi Huawei_Health_Kit đã lưu trên thiết bị.
7. WHEN một người dùng mở màn hình trạng thái ủy quyền, THE App SHALL hiển thị cho mỗi Health_Metric đã chọn đúng một trong hai trạng thái: "Đã ủy quyền" nếu phạm vi đọc Huawei_Health_Kit tương ứng hiện đã nằm trong tập phạm vi đã ủy quyền được lưu trên thiết bị, hoặc "Chưa ủy quyền" nếu không.
8. IF luồng ủy quyền Huawei_Health_Kit không hoàn tất trong vòng 60 giây kể từ thời điểm Permission_Manager khởi chạy luồng, THEN THE App SHALL hủy luồng ủy quyền, SHALL không lưu bất kỳ phạm vi đã ủy quyền nào, SHALL hiển thị thông báo lỗi cho biết yêu cầu ủy quyền đã hết thời gian chờ, và SHALL cho phép người dùng thử lại việc ủy quyền.

### Requirement 3: Lựa chọn và khả dụng của Nguồn dữ liệu

**User Story:** Là một người dùng, tôi muốn chọn nguồn dữ liệu sức khỏe nào mà App đọc, để tôi có thể dùng một hoặc cả hai nền tảng tùy theo thiết bị của mình.

#### Acceptance Criteria

1. THE App SHALL cho phép người dùng bật hoặc tắt một cách độc lập mỗi Data_Source trong hai Data_Source được hỗ trợ là Health_Connect và Huawei_Health_Kit.
2. WHEN người dùng thay đổi trạng thái bật hoặc tắt của một Data_Source, THE App SHALL lưu giữ lựa chọn bật/tắt đó và SHALL khôi phục đúng lựa chọn này khi App khởi động lại ở các phiên làm việc tiếp theo.
3. WHILE chỉ một Data_Source được bật, THE Data_Reader SHALL đọc bản ghi độc quyền từ Data_Source được bật đó.
4. WHILE cả hai Data_Source được bật, THE Data_Reader SHALL đọc bản ghi từ cả hai Data_Source cho mỗi Health_Metric đã chọn.
5. IF một Data_Source được bật không phản hồi trong vòng 30 giây tại thời điểm Export_Job, THEN THE App SHALL coi Data_Source đó là không khả dụng, tiếp tục Export_Job bằng Data_Source còn lại đang khả dụng, và SHALL ghi lại Data_Source không khả dụng vào Sync_Log.
6. IF tất cả các Data_Source được bật đều không khả dụng tại thời điểm Export_Job, THEN THE App SHALL hủy bỏ Export_Job và SHALL ghi lại một thất bại Export_Job với lý do cho biết không có nguồn dữ liệu khả dụng vào Sync_Log.
7. IF không có Data_Source nào được bật tại thời điểm Export_Job, THEN THE App SHALL từ chối Export_Job và SHALL ghi lại một thất bại Export_Job với lý do cho biết không có Data_Source nào được bật vào Sync_Log.

### Requirement 4: Danh mục các Chỉ số Sức khỏe được hỗ trợ

**User Story:** Là một người dùng, tôi muốn App hỗ trợ toàn bộ danh mục các chỉ số sức khỏe và thể chất khả dụng trên Android, để tôi có thể xuất cùng độ rộng dữ liệu mà ứng dụng gốc cung cấp.

#### Acceptance Criteria

1. THE App SHALL hỗ trợ đọc và xuất các loại Health_Metric thuộc các nhóm: Vận động (Activity), Đo lường Cơ thể (Body Measurement), Tim mạch (Heart), Thính giác (Hearing), Dinh dưỡng (Nutrition), Chánh niệm (Mindfulness), Vận động chức năng (Mobility), Sức khỏe Sinh sản (Reproductive Health), Hô hấp (Respiratory), Giấc ngủ (Sleep), Sinh hiệu (Vitals), và Khác (Other).
2. WHERE một loại Health_Metric được một Data_Source cung cấp, THE Data_Reader SHALL ánh xạ bản ghi nguồn đó sang tên chỉ số của Unified_Record tương ứng trong App và SHALL chuyển đổi giá trị của bản ghi sang đơn vị chuẩn (canonical) duy nhất được định nghĩa cho loại Health_Metric đó.
3. IF một Health_Metric được App hỗ trợ nhưng không được bất kỳ Data_Source nào đang bật cung cấp, THEN THE App SHALL đánh dấu Health_Metric đó là không được hỗ trợ trên thiết bị hiện tại và SHALL loại nó khỏi lựa chọn chỉ số.
4. THE App SHALL cho phép người dùng chọn một tập con gồm từ 1 đến toàn bộ các loại Health_Metric được hỗ trợ cho một Export_Job.
5. THE App SHALL ghi lại, cho mỗi Unified_Record được xuất, định danh của Data_Source gốc cụ thể đã cung cấp bản ghi đó, kể cả khi nhiều Data_Source cùng cung cấp một loại Health_Metric.
6. WHEN một người dùng yêu cầu danh sách các chỉ số khả dụng, THE App SHALL hiển thị chỉ các loại Health_Metric mà ít nhất một Data_Source đang bật và đã được ủy quyền có thể cung cấp.
7. IF THE Data_Reader không thể ánh xạ một bản ghi nguồn sang một loại Health_Metric được hỗ trợ hoặc không thể chuyển đổi giá trị của bản ghi sang đơn vị chuẩn (canonical), THEN THE Data_Reader SHALL bỏ qua bản ghi đó, SHALL ghi một mục nhật ký cho biết lý do không ánh xạ được cùng định danh Data_Source gốc, và SHALL tiếp tục xử lý các bản ghi còn lại mà không hủy Export_Job.
8. IF một người dùng khởi tạo một Export_Job khi chưa chọn loại Health_Metric nào (0 loại được chọn), THEN THE App SHALL từ chối khởi tạo Export_Job, SHALL hiển thị thông báo lỗi cho biết cần chọn ít nhất một loại Health_Metric, và SHALL giữ nguyên các lựa chọn hiện tại của người dùng.

### Requirement 5: Xuất Bài tập kèm Tuyến đường và Siêu dữ liệu

**User Story:** Là một người dùng, tôi muốn xuất các bài tập cùng với tuyến đường GPS, dữ liệu nhịp tim và siêu dữ liệu của chúng, để tôi có một bản ghi đầy đủ về mỗi phiên tập luyện.

#### Acceptance Criteria

1. WHEN một người dùng đưa Workout vào một Export_Job, THE Data_Reader SHALL đọc loại, thời điểm bắt đầu, thời điểm kết thúc và thời lượng của mỗi Workout từ các Data_Source đang bật.
2. WHERE một Workout có một tuyến đường GPS đã ghi, THE Data_Reader SHALL đọc tuyến đường như một chuỗi điểm vị trí được sắp xếp theo dấu thời gian tăng dần, trong đó mỗi điểm chứa vĩ độ, kinh độ, dấu thời gian, và độ cao khi giá trị độ cao khả dụng.
3. IF một điểm vị trí trong tuyến đường không có giá trị độ cao khả dụng, THEN THE Data_Reader SHALL giữ lại điểm đó trong chuỗi và SHALL bỏ qua trường độ cao của riêng điểm đó mà không loại bỏ điểm khỏi tuyến đường.
4. WHERE một Workout có các mẫu nhịp tim đi kèm, THE Data_Reader SHALL đọc chuỗi nhịp tim được sắp xếp theo dấu thời gian tăng dần và SHALL gắn chuỗi đó vào Workout được xuất.
5. WHERE một Workout có dữ liệu hồi phục nhịp tim, năng lượng hoạt động, tổng năng lượng, quãng đường, tốc độ, độ cao chênh lệch, hoặc số bước, THE Data_Reader SHALL bao gồm mỗi trường khả dụng vào Workout được xuất và SHALL bỏ qua mỗi trường không khả dụng khỏi Workout được xuất.
6. IF một Workout không có tuyến đường GPS đã ghi, THEN THE GPX_Serializer SHALL loại Workout đó khỏi đầu ra GPX và SHALL ghi lại việc loại trừ vào Sync_Log.
7. THE App SHALL cho phép người dùng chọn một tập con riêng lẻ các loại Workout được hỗ trợ cho một Export_Job.
8. WHEN một Export_Job không khớp với Workout nào trong các Data_Source đang bật, THE Export_Job SHALL hoàn tất với một tập kết quả rỗng và THE App SHALL ghi lại kết quả rỗng vào Sync_Log.

### Requirement 6: Các Loại dữ liệu Chuyên biệt Chi tiết

**User Story:** Là một người dùng, tôi muốn xuất chi tiết cho các loại dữ liệu phức tạp như giai đoạn giấc ngủ, ECG, cảnh báo nhịp tim và siêu dữ liệu đường huyết, để tôi giữ lại toàn bộ chi tiết mà thiết bị của tôi đã ghi nhận.

#### Acceptance Criteria

1. WHERE dữ liệu giấc ngủ được chọn và một Data_Source cung cấp các giai đoạn giấc ngủ, THE Data_Reader SHALL đọc phân rã giai đoạn của mỗi phiên ngủ bao gồm thời lượng các giai đoạn thức (awake), REM, ngủ nông (core) và ngủ sâu (deep), trong đó mỗi thời lượng được biểu diễn dưới dạng số nguyên không âm (≥ 0) tính bằng giây.
2. WHERE dữ liệu ECG được chọn và một Data_Source cung cấp các bản ghi điện tâm đồ, THE Data_Reader SHALL đọc phân loại, nhịp tim trung bình (số nguyên trong khoảng 0 đến 300 bpm), tần số lấy mẫu (số dương tính bằng Hz) và toàn bộ chuỗi mẫu đo điện áp theo đúng thứ tự ghi nhận của mỗi bản ghi ECG.
3. WHERE dữ liệu cảnh báo nhịp tim được chọn và một Data_Source cung cấp các sự kiện nhịp tim cao, thấp hoặc bất thường, THE Data_Reader SHALL đọc thời điểm bắt đầu, thời điểm kết thúc (mỗi mốc thời gian kèm thông tin múi giờ), ngưỡng (tính bằng bpm) và các mẫu nhịp tim liên quan của mỗi sự kiện.
4. WHERE dữ liệu đường huyết được chọn và một Data_Source cung cấp siêu dữ liệu quan hệ với bữa ăn, THE Data_Reader SHALL bao gồm giá trị quan hệ bữa ăn cùng mỗi Unified_Record đường huyết.
5. IF một loại dữ liệu chuyên biệt đã chọn không được bất kỳ Data_Source nào đang bật cung cấp, THEN THE App SHALL loại loại dữ liệu đó khỏi Export_Job và SHALL ghi một mục vào Sync_Log nêu rõ tên loại dữ liệu bị loại trừ, lý do loại trừ (không có Data_Source đang bật cung cấp loại dữ liệu này) và mốc thời gian loại trừ kèm thông tin múi giờ.
6. IF một bản ghi từ Data_Source thiếu một trường bắt buộc của loại dữ liệu chuyên biệt đang được xử lý, THEN THE Data_Reader SHALL giữ lại các trường còn sẵn có của bản ghi đó, SHALL ghi một cảnh báo vào Sync_Log cho biết trường bắt buộc nào bị thiếu, và SHALL tiếp tục xử lý các bản ghi còn lại.

### Requirement 7: Hợp nhất và Loại trùng Dữ liệu Đa nguồn

**User Story:** Là một người dùng đọc dữ liệu từ cả Health Connect và Huawei Health, tôi muốn dữ liệu chồng lấn được hợp nhất và loại bỏ trùng lặp, để bản xuất của tôi chứa mỗi phép đo đúng một lần.

#### Acceptance Criteria

1. WHEN các bản ghi cho cùng một Health_Metric được đọc từ cả hai Data_Source, THE Data_Merger SHALL kết hợp các bản ghi thành một chuỗi có thứ tự duy nhất cho Health_Metric đó.
2. THE Data_Merger SHALL định nghĩa cho mỗi Health_Metric một dung sai trùng lặp gồm một dung sai dấu thời gian (khoảng thời gian không âm tính bằng giây) và một dung sai giá trị (độ lớn không âm theo đơn vị đo của Health_Metric đó).
3. IF hai bản ghi có cùng Health_Metric, có dấu thời gian chênh lệch không quá dung sai dấu thời gian của chỉ số đó, và có giá trị chênh lệch không quá dung sai giá trị của chỉ số đó, THEN THE Data_Merger SHALL giữ lại đúng một bản ghi và SHALL loại bỏ (các) bản còn lại.
4. WHEN Data_Merger loại bỏ một bản ghi trùng lặp, THE Data_Merger SHALL giữ lại bản ghi có Data_Source gốc ứng với mức ưu tiên nguồn cao nhất do người dùng cấu hình.
5. IF các bản ghi trùng lặp có Data_Source gốc với mức ưu tiên do người dùng cấu hình bằng nhau, THEN THE Data_Merger SHALL giữ lại bản ghi có định danh Data_Source đứng trước theo thứ tự bảng chữ cái tăng dần.
6. THE Data_Merger SHALL sắp xếp các bản ghi của mỗi Health_Metric trong tập dữ liệu hợp nhất theo dấu thời gian tăng dần, và đối với các bản ghi có cùng dấu thời gian, sắp xếp tiếp theo định danh Data_Source tăng dần rồi theo giá trị tăng dần.
7. IF hai bản ghi cho cùng một Health_Metric có dấu thời gian chênh lệch không quá dung sai dấu thời gian của chỉ số nhưng có giá trị chênh lệch vượt quá dung sai giá trị của chỉ số, THEN THE Data_Merger SHALL giữ lại cả hai bản ghi và SHALL gắn nhãn mỗi bản với Data_Source gốc của nó.
8. THE App SHALL cho phép người dùng gán một thứ hạng ưu tiên cho mỗi Data_Source được dùng để giải quyết trùng lặp.
9. WHEN Data_Merger xử lý một tập dữ liệu đã được hợp nhất và loại trùng trước đó, THE Data_Merger SHALL tạo ra kết quả giống hệt đầu vào, không loại bỏ thêm bản ghi nào và không thay đổi thứ tự.

### Requirement 8: Điều khiển Tổng hợp Dữ liệu

**User Story:** Là một người dùng, tôi muốn kiểm soát mức độ chi tiết thời gian để tổng hợp dữ liệu của mình, để tôi có thể xuất các mẫu thô hoặc bản tóm tắt theo phút, giờ, ngày, tuần, tháng hoặc năm.

#### Acceptance Criteria

1. THE App SHALL cho phép người dùng chọn một Aggregation_Period trong số giây, phút, giờ, ngày, tuần, tháng và năm cho một Export_Job.
2. WHEN một Aggregation_Period được chọn, THE Aggregator SHALL nhóm các bản ghi của mỗi Health_Metric vào các khung thời gian liền kề, không chồng lấn theo period đã chọn, và SHALL gán mỗi bản ghi vào đúng một khung dựa trên dấu thời gian của bản ghi.
3. THE Aggregator SHALL định nghĩa mỗi khung thời gian là một khoảng nửa mở [thời điểm bắt đầu, thời điểm kết thúc), được căn chỉnh theo ranh giới lịch tính theo múi giờ cục bộ của thiết bị tại thời điểm Export_Job: phút bắt đầu tại giây 00, giờ tại phút 00, ngày tại 00:00:00, tuần tại 00:00:00 ngày Thứ Hai, tháng tại 00:00:00 ngày đầu tháng, và năm tại 00:00:00 ngày đầu năm.
4. WHEN tổng hợp một Health_Metric tích lũy (cumulative) trong một khung thời gian, THE Aggregator SHALL xuất giá trị tổng cộng cho khung đó.
5. WHEN tổng hợp một Health_Metric tức thời (instantaneous) trong một khung thời gian, THE Aggregator SHALL xuất các giá trị nhỏ nhất, trung bình, lớn nhất và số lượng bản ghi (count) đã dùng để tính cho khung đó.
6. IF một khung thời gian không chứa bản ghi nào của một Health_Metric, THEN THE Aggregator SHALL bỏ qua (omit) khung đó cho Health_Metric đó và SHALL không phát ra một khung trống hoặc giá trị bằng không.
7. WHERE người dùng chọn Aggregation_Period là giây, THE Aggregator SHALL xuất các bản ghi riêng lẻ chưa tổng hợp mà không kết hợp chúng.
8. WHEN tổng hợp dữ liệu giấc ngủ theo ngày, THE Aggregator SHALL xuất tổng thời lượng ngủ và thời lượng theo từng giai đoạn cho mỗi khung ngày, với mỗi khung ngày được căn chỉnh tại 00:00:00 theo múi giờ cục bộ của thiết bị.

### Requirement 9: Chọn Khoảng Thời gian

**User Story:** Là một người dùng, tôi muốn chọn khoảng thời gian cho một lần xuất, để tôi chỉ xuất dữ liệu của một giai đoạn cụ thể.

#### Acceptance Criteria

1. THE App SHALL cho phép người dùng chỉ định một Date_Range với một thời điểm bắt đầu và một thời điểm kết thúc cho một Export_Job.
2. IF một người dùng chỉ định một Date_Range có thời điểm kết thúc trước thời điểm bắt đầu, THEN THE App SHALL từ chối Date_Range đó, SHALL giữ nguyên các giá trị người dùng đã nhập để chỉnh sửa, và SHALL hiển thị một thông báo xác thực chỉ ra lỗi thứ tự.
3. WHERE một Date_Range có thời điểm bắt đầu và thời điểm kết thúc trùng nhau (khoảng thời gian bằng không), THE App SHALL chấp nhận Date_Range đó.
4. THE App SHALL diễn giải mọi thời điểm bắt đầu và thời điểm kết thúc của Date_Range theo UTC (Giờ Phối hợp Quốc tế).
5. WHEN một Export_Job chạy với một Date_Range được chỉ định, THE Data_Reader SHALL đọc chỉ các bản ghi có dấu thời gian nằm trong Date_Range bao gồm cả hai đầu mút, với việc so sánh dấu thời gian được thực hiện theo UTC.
6. IF một người dùng chỉ định một Date_Range có thời điểm kết thúc nằm sau thời điểm hiện tại, THEN THE App SHALL điều chỉnh thời điểm kết thúc về thời điểm hiện tại và SHALL hiển thị một thông báo chỉ ra việc điều chỉnh đã được áp dụng.
7. WHERE một người dùng không chỉ định Date_Range cho một Quick_Export, THE App SHALL áp dụng một Date_Range mặc định có thời điểm bắt đầu là 00:00:00 và thời điểm kết thúc là thời điểm hiện tại của ngày theo lịch hiện tại, được tính theo UTC.
8. WHERE một Automation chạy theo lịch và tồn tại ít nhất một Export_Job thành công trước đó của Automation đó, THE App SHALL đặt Date_Range có thời điểm bắt đầu bằng thời điểm kết thúc của Export_Job thành công gần nhất và thời điểm kết thúc bằng thời điểm chạy hiện tại.
9. IF một Automation chạy theo lịch và không tồn tại Export_Job thành công trước đó của Automation đó, THEN THE App SHALL đặt Date_Range có thời điểm bắt đầu bằng thời điểm Automation được kích hoạt lần đầu và thời điểm kết thúc bằng thời điểm chạy hiện tại.

### Requirement 10: Định dạng Xuất JSON

**User Story:** Là một nhà phát triển tích hợp với App, tôi muốn các bản xuất JSON khớp với cấu trúc đã tài liệu hóa của ứng dụng gốc, để công cụ và máy chủ hiện có của tôi tiếp tục hoạt động.

#### Acceptance Criteria

1. WHEN JSON_Serializer tạo đầu ra, THE JSON_Serializer SHALL đóng gói toàn bộ dữ liệu trong một đối tượng cấp cao nhất `data` luôn chứa đủ tám mảng `metrics`, `workouts`, `stateOfMind`, `medications`, `symptoms`, `cycleTracking`, `ecg`, và `heartRateNotifications`.
2. WHEN JSON_Serializer ghi đầu ra, THE JSON_Serializer SHALL mã hóa toàn bộ văn bản JSON bằng UTF-8 mà không thêm Byte Order Mark (BOM).
3. IF một trong tám danh mục cấp cao nhất không có bản ghi nào, THEN THE JSON_Serializer SHALL phát ra khóa mảng tương ứng với giá trị là một mảng rỗng `[]` thay vì bỏ qua khóa hoặc đặt giá trị `null`.
4. WHEN JSON_Serializer ghi một Health_Metric tiêu chuẩn, THE JSON_Serializer SHALL phát ra một đối tượng có `name`, `units`, và một mảng `data` mà các phần tử của nó chứa giá trị `qty` và một `date` được định dạng theo `yyyy-MM-dd HH:mm:ss Z`.
5. WHEN JSON_Serializer ghi giá trị `qty`, THE JSON_Serializer SHALL biểu diễn giá trị đó dưới dạng số JSON theo ký pháp thập phân (không dùng ký pháp khoa học/lũy thừa), giữ nguyên dấu của giá trị và giữ nguyên độ chính xác tối thiểu 6 chữ số sau dấu thập phân mà không làm tròn mất dữ liệu.
6. WHEN JSON_Serializer ghi một Health_Metric có lược đồ riêng theo cấu trúc, THE JSON_Serializer SHALL phát ra chỉ số đó bằng lược đồ đã tài liệu hóa cho loại chỉ số đó.
7. WHEN JSON_Serializer ghi một dấu thời gian, THE JSON_Serializer SHALL định dạng dấu thời gian theo `yyyy-MM-dd HH:mm:ss Z`.
8. WHEN JSON_Serializer tuần tự hóa một tập dữ liệu hợp nhất hợp lệ và sau đó JSON_Parser phân tích kết quả đó, THE JSON_Parser SHALL tạo ra một tập dữ liệu bằng với tập dữ liệu ban đầu (thuộc tính khứ hồi - round-trip), trong đó "bằng" được xác định là: tất cả tám danh mục hiện diện như nhau, mọi bản ghi và mọi cặp khóa-giá trị khớp nhau, thứ tự phần tử trong từng mảng được giữ nguyên, và mọi giá trị `qty` cùng mọi dấu thời gian khớp chính xác trong phạm vi độ chính xác đã quy định.
9. IF JSON_Parser nhận đầu vào không tuân theo Export_Format JSON của App, THEN THE JSON_Parser SHALL trả về một lỗi phân tích mô tả chỉ ra phần tử không tuân thủ và SHALL không tạo ra tập dữ liệu một phần.

### Requirement 11: Định dạng Xuất CSV

**User Story:** Là một người dùng, tôi muốn xuất dữ liệu sức khỏe ở định dạng CSV, để tôi có thể mở dữ liệu trong bảng tính để xem lại và phân tích.

#### Acceptance Criteria

1. WHEN CSV_Serializer tạo đầu ra cho một Health_Metric, THE CSV_Serializer SHALL phát ra một dòng tiêu đề đặt tên cho mỗi trường theo một thứ tự cột cố định và xác định, theo sau là một dòng dữ liệu cho mỗi bản ghi.
2. WHEN CSV_Serializer ghi các dòng dữ liệu cho một Health_Metric, THE CSV_Serializer SHALL sắp xếp các giá trị trường theo đúng thứ tự cột đã dùng ở dòng tiêu đề trong mọi dòng dữ liệu.
3. WHEN CSV_Serializer ghi một giá trị trường chứa dấu phẩy, dấu nháy kép, hoặc ký tự xuống dòng, THE CSV_Serializer SHALL bao giá trị trường trong dấu nháy kép và SHALL thoát (escape) các dấu nháy kép nằm bên trong.
4. WHEN CSV_Serializer ghi một dấu thời gian, THE CSV_Serializer SHALL định dạng dấu thời gian theo `yyyy-MM-dd HH:mm:ss Z`.
5. WHEN CSV_Serializer ghi một Health_Metric có giá trị bản ghi rỗng cho một trường, THE CSV_Serializer SHALL phát ra một trường rỗng trong cột đó.
6. THE CSV_Serializer SHALL mã hóa mỗi tài liệu CSV bằng UTF-8 không kèm dấu thứ tự byte (BOM).
7. WHEN CSV_Serializer kết thúc một dòng tiêu đề hoặc một dòng dữ liệu, THE CSV_Serializer SHALL kết thúc dòng bằng cặp ký tự CRLF (carriage return theo sau bởi line feed).
8. WHERE một Export_Job ở Export_Format CSV bao gồm nhiều loại Health_Metric, THE CSV_Serializer SHALL phát ra một tài liệu CSV riêng cho mỗi loại Health_Metric, SHALL đặt tên mỗi tài liệu theo định danh loại Health_Metric tương ứng, và SHALL đóng gói tất cả các tài liệu CSV vào một tệp lưu trữ (archive) duy nhất cho Export_Job đó.

### Requirement 12: Định dạng Xuất GPX cho Tuyến đường Bài tập

**User Story:** Là một người dùng, tôi muốn xuất tuyến đường bài tập của mình ở định dạng GPX, để tôi có thể xem và sử dụng tuyến đường trong các ứng dụng bản đồ và thể chất.

#### Acceptance Criteria

1. WHEN GPX_Serializer tạo đầu ra cho một Workout có tuyến đường, THE GPX_Serializer SHALL phát ra một tài liệu GPX 1.1 chứa đúng một track, và track đó SHALL chứa đúng một track segment cho Workout.
2. WHEN GPX_Serializer ghi track segment của một Workout, THE GPX_Serializer SHALL phát ra đúng một track point cho mỗi vị trí tuyến đường, theo cùng thứ tự với chuỗi vị trí tuyến đường ban đầu.
3. WHEN GPX_Serializer ghi một vị trí tuyến đường, THE GPX_Serializer SHALL phát ra vĩ độ và kinh độ làm thuộc tính của track point và SHALL phát ra độ cao và dấu thời gian làm các phần tử con của track point.
4. WHEN GPX_Serializer ghi một dấu thời gian tuyến đường, THE GPX_Serializer SHALL định dạng dấu thời gian theo ISO 8601 UTC ở độ chính xác giây.
5. WHEN GPX_Serializer tạo đầu ra cho nhiều Workout có tuyến đường trong cùng một thao tác xuất, THE GPX_Serializer SHALL phát ra một tài liệu GPX 1.1 duy nhất chứa đúng một track cho mỗi Workout, theo thứ tự các Workout được cung cấp cho thao tác xuất.
6. FOR ALL các chuỗi tuyến đường Workout, áp dụng GPX_Serializer rồi áp dụng GPX_Parser SHALL tạo ra một chuỗi tuyến đường có cùng số lượng điểm và cùng thứ tự với chuỗi ban đầu, trong đó vĩ độ và kinh độ bằng với giá trị ban đầu khi làm tròn đến 6 chữ số thập phân (độ), độ cao bằng với giá trị ban đầu khi làm tròn đến 2 chữ số thập phân (mét), và dấu thời gian bằng với giá trị ban đầu ở độ chính xác giây (thuộc tính khứ hồi - round-trip).
7. IF GPX_Parser nhận đầu vào không phải là một tài liệu GPX 1.1 hợp lệ, THEN THE GPX_Parser SHALL trả về một lỗi phân tích chỉ rõ nguyên nhân đầu vào không hợp lệ và SHALL không trả về chuỗi tuyến đường nào.

### Requirement 13: Xuất nhanh Thủ công (Quick Export)

**User Story:** Là một người dùng, tôi muốn xuất dữ liệu sức khỏe theo yêu cầu tức thời, để tôi có được ảnh chụp hiện tại bất cứ khi nào cần.

#### Acceptance Criteria

1. WHEN một người dùng kích hoạt một Quick_Export, THE App SHALL chạy một Export_Job sử dụng các chỉ số, loại Workout, Export_Format, Aggregation_Period, Date_Range và Destination do người dùng chọn.
2. WHILE một Export_Job Quick_Export đang chạy, THE App SHALL hiển thị trạng thái tiến trình của Export_Job bao gồm phần trăm hoàn thành (0 đến 100), cập nhật ít nhất một lần mỗi 2 giây.
3. WHEN một Export_Job Quick_Export hoàn thành thành công, THE App SHALL hiển thị một xác nhận thành công và SHALL ghi lại kết quả vào Sync_Log.
4. IF một Export_Job Quick_Export thất bại, THEN THE App SHALL hiển thị lý do thất bại, SHALL không để lại dữ liệu xuất một phần tại Destination, và SHALL ghi lại thất bại vào Sync_Log.
5. IF một người dùng kích hoạt một Quick_Export trong khi một Export_Job Quick_Export khác đang chạy, THEN THE App SHALL từ chối yêu cầu mới, SHALL giữ nguyên Export_Job đang chạy, và SHALL hiển thị một thông báo cho biết một Export_Job đang chạy.
6. WHEN một người dùng hủy một Export_Job Quick_Export đang chạy, THE App SHALL dừng Export_Job trong vòng 5 giây, SHALL không để lại dữ liệu xuất một phần tại Destination, SHALL hiển thị một xác nhận đã hủy, và SHALL ghi lại việc hủy vào Sync_Log.

### Requirement 14: Cấu hình Tự động hóa (Automation)

**User Story:** Là một người dùng, tôi muốn tạo các automation có thể tái sử dụng xác định xuất cái gì, như thế nào và đến đâu, để tôi cấu hình các lần đồng bộ theo lịch một lần và tái sử dụng chúng.

#### Acceptance Criteria

1. THE App SHALL cho phép người dùng tạo một Automation chỉ định một tên (name) dài từ 1 đến 100 ký tự, các loại Health_Metric được chọn, các loại Workout được chọn, Export_Format, Aggregation_Period, lịch chạy và Destination.
2. THE App SHALL cho phép người dùng chỉnh sửa, bật, tắt và xóa một Automation hiện có.
3. IF một người dùng cố lưu một Automation mà không có Destination được chọn, THEN THE App SHALL từ chối việc lưu và SHALL hiển thị một thông báo xác thực chỉ ra Destination còn thiếu.
4. IF một người dùng cố lưu một Automation mà không có ít nhất một loại Health_Metric hoặc loại Workout được chọn, THEN THE App SHALL từ chối việc lưu và SHALL hiển thị một thông báo xác thực chỉ ra lựa chọn còn thiếu.
5. THE App SHALL lưu mỗi Automation đã lưu trên thiết bị.
6. WHERE một người dùng tạo một Automation thông qua một liên kết sâu (deep link) cấu hình hợp lệ, THE App SHALL điền các trường của Automation từ các tham số của deep link và SHALL trình bày Automation để người dùng xác nhận trước khi lưu.
7. IF một người dùng cố lưu một Automation với tên trùng (không phân biệt chữ hoa/thường) với tên của một Automation đã tồn tại trên thiết bị, THEN THE App SHALL từ chối việc lưu, SHALL hiển thị một thông báo xác thực chỉ ra tên bị trùng, và SHALL giữ nguyên dữ liệu người dùng đã nhập.
8. IF một deep link cấu hình chứa tham số bị thiếu, sai định dạng, hoặc có giá trị nằm ngoài tập giá trị được hỗ trợ cho Export_Format, Aggregation_Period, hoặc Destination, THEN THE App SHALL từ chối việc điền tự động, SHALL không tạo Automation, và SHALL hiển thị một thông báo lỗi chỉ ra tham số không hợp lệ.
9. WHEN một người dùng xóa một Automation đang trong quá trình chạy (mid-run), THE App SHALL dừng lần chạy đang diễn ra, SHALL hủy bỏ mọi kết quả xuất một phần (partial export) chưa hoàn tất, và SHALL xóa Automation khỏi thiết bị.

### Requirement 15: Xuất Nền theo Lịch

**User Story:** Là một người dùng, tôi muốn các automation của mình tự động chạy trong nền theo lịch, để dữ liệu của tôi được xuất mà không cần thao tác thủ công.

#### Acceptance Criteria

1. WHEN thời điểm theo lịch của một Automation đang bật đến và không có Export_Job nào của cùng Automation đó đang chạy, THE Scheduler SHALL bắt đầu một Export_Job cho Automation đó trong vòng 60 giây kể từ thời điểm theo lịch.
2. WHILE App ở chế độ nền, THE Scheduler SHALL tiếp tục kích hoạt các Export_Job theo lịch cho các Automation đang bật.
3. THE App SHALL cho phép người dùng cấu hình lịch của một Automation dưới dạng một khoảng lặp định kỳ với khoảng tối thiểu là 15 phút và khoảng tối đa là 30 ngày.
4. IF người dùng cố cấu hình một khoảng lặp nhỏ hơn 15 phút hoặc lớn hơn 30 ngày, THEN THE App SHALL từ chối cấu hình, giữ nguyên giá trị lịch hợp lệ trước đó, và hiển thị một thông báo lỗi cho biết khoảng lặp nằm ngoài phạm vi cho phép.
5. IF thời điểm theo lịch của một Automation đến trong khi một Export_Job trước đó của cùng Automation đó vẫn đang chạy, THEN THE Scheduler SHALL bỏ qua lần chạy theo lịch mới, không khởi tạo một Export_Job trùng lặp, và ghi lại một mục với lý do "bị bỏ qua do trùng lặp" vào Sync_Log.
6. IF một Export_Job theo lịch không thể chạy vì một quyền cần thiết chưa được cấp, THEN THE App SHALL ghi lại một thất bại với lý do "thiếu quyền" vào Sync_Log và SHALL thông báo cho người dùng.
7. IF một Export_Job theo lịch thất bại do một lỗi gửi tạm thời (transient), THEN THE Scheduler SHALL thử lại Export_Job tối đa 5 lần bằng độ trễ tăng theo cấp số nhân (exponential backoff) bắt đầu từ 30 giây và giới hạn ở mức tối đa 30 phút cho mỗi lần thử.
8. IF một Export_Job theo lịch vẫn thất bại sau 5 lần thử lại, THEN THE Scheduler SHALL dừng việc thử lại, ghi lại một thất bại với lý do "đã vượt số lần thử lại cho phép" vào Sync_Log, và SHALL thông báo cho người dùng.
9. WHEN một Export_Job theo lịch hoàn thành, THE App SHALL ghi lại kết quả và dấu thời gian hoàn thành vào Sync_Log.
10. WHERE thiết bị hạn chế việc thực thi nền cho App, THE App SHALL thông báo cho người dùng rằng việc xuất nền cần một miễn trừ (exemption) và SHALL cung cấp hướng dẫn để cấp miễn trừ đó.

### Requirement 16: Đích đến REST API

**User Story:** Là một người dùng, tôi muốn gửi dữ liệu đã xuất tới một REST API endpoint, để tôi có thể tích hợp với các dịch vụ web và webhook của riêng mình.

#### Acceptance Criteria

1. THE App SHALL cho phép người dùng cấu hình một Destination REST API với một URL đích dùng giao thức HTTP hoặc HTTPS (tối đa 2.048 ký tự), một phương thức HTTP, và tối đa 50 header yêu cầu tùy chỉnh.
2. WHEN một Export_Job nhắm tới một Destination REST API, THE App SHALL gửi bản xuất đã tuần tự hóa làm thân (body) yêu cầu HTTP tới URL đã cấu hình.
3. WHEN một Export_Job nhắm tới một Destination REST API, THE App SHALL đặt header Content-Type của yêu cầu khớp với loại nội dung của Export_Format.
4. IF URL đã cấu hình không dùng HTTPS, THEN THE App SHALL cảnh báo người dùng rằng dữ liệu sẽ được truyền mà không có mã hóa truyền tải trước khi lưu Destination.
5. IF REST API phản hồi với một mã trạng thái HTTP nằm ngoài dải thành công 2xx (200–299), THEN THE App SHALL ghi lại mã trạng thái và thân phản hồi vào Sync_Log và SHALL coi Export_Job là thất bại.
6. WHEN REST API phản hồi với một mã trạng thái HTTP trong dải thành công 2xx (200–299), THE App SHALL ghi lại Export_Job là thành công vào Sync_Log.
7. IF REST API không trả về một phản hồi hoàn chỉnh trong vòng 30 giây kể từ khi yêu cầu được gửi, hoặc kết nối thất bại trước khi nhận được phản hồi, THEN THE App SHALL hủy yêu cầu, ghi lại nguyên nhân thất bại vào Sync_Log, và SHALL coi Export_Job là thất bại.
8. IF bản xuất đã tuần tự hóa vượt quá kích thước thân yêu cầu tối đa 100 MB, THEN THE App SHALL không gửi yêu cầu, ghi lại một lỗi cho biết bản xuất vượt quá giới hạn kích thước vào Sync_Log, và SHALL coi Export_Job là thất bại.

### Requirement 17: Đích đến Google Drive

**User Story:** Là một người dùng, tôi muốn sao lưu các bản xuất của mình lên Google Drive, để dữ liệu sức khỏe của tôi được lưu trong tài khoản đám mây của tôi.

#### Acceptance Criteria

1. WHEN một người dùng chọn Destination Google Drive, THE App SHALL khởi tạo luồng ủy quyền Google Drive và SHALL chỉ yêu cầu phạm vi tạo tệp (file-creation scope).
2. WHEN một Export_Job nhắm tới Destination Google Drive, THE App SHALL tải bản xuất đã tuần tự hóa lên dưới dạng một tệp vào thư mục Google Drive do người dùng cấu hình và SHALL hoàn tất quá trình tải lên trong vòng 120 giây cho mỗi 50 MB dữ liệu.
3. IF việc ủy quyền Google Drive bị thiếu hoặc hết hạn tại thời điểm Export_Job, THEN THE App SHALL ghi lại một thất bại ủy quyền vào Sync_Log và SHALL nhắc người dùng ủy quyền lại.
4. WHEN một lần tải lên Google Drive hoàn thành thành công, THE App SHALL ghi lại tên tệp đã tải lên và kết quả Export_Job vào Sync_Log.
5. IF một tệp có cùng tên đã tồn tại trong thư mục Google Drive đích tại thời điểm tải lên, THEN THE App SHALL tải bản xuất lên dưới dạng một tệp mới với tên duy nhất bằng cách thêm hậu tố số tăng dần, SHALL giữ nguyên tệp hiện có (không ghi đè), và SHALL ghi lại tên tệp cuối cùng vào Sync_Log.
6. IF một lần tải lên Google Drive thất bại do lỗi mạng trước khi hoàn tất, THEN THE App SHALL hủy bỏ phần dữ liệu đã tải lên một phần, SHALL ghi lại một thất bại tải lên cùng chỉ báo nguyên nhân lỗi mạng vào Sync_Log, và SHALL đánh dấu Export_Job là đủ điều kiện thử lại (retry-eligible) với tối đa 3 lần thử lại tự động cách nhau ít nhất 30 giây.
7. IF số lần thử lại tự động cho một Export_Job tải lên Google Drive đạt 3 lần mà vẫn thất bại, THEN THE App SHALL dừng việc thử lại tự động, SHALL ghi lại trạng thái thất bại cuối cùng vào Sync_Log, và SHALL nhắc người dùng thử lại thủ công.

### Requirement 18: Đích đến Dropbox

**User Story:** Là một người dùng, tôi muốn sao lưu các bản xuất của mình lên Dropbox, để tôi có thể lưu dữ liệu sức khỏe trong tài khoản Dropbox của mình.

#### Acceptance Criteria

1. WHEN một người dùng chọn Destination Dropbox, THE App SHALL khởi tạo luồng ủy quyền Dropbox trong vòng 2 giây và SHALL chỉ yêu cầu quyền truy cập tệp giới hạn trong thư mục ứng dụng (app-folder).
2. WHEN một Export_Job nhắm tới Destination Dropbox, THE App SHALL tải bản xuất đã tuần tự hóa lên dưới dạng một tệp đơn vào thư mục Dropbox do người dùng cấu hình.
3. IF việc ủy quyền Dropbox bị thiếu hoặc hết hạn tại thời điểm Export_Job, THEN THE App SHALL ghi lại một thất bại ủy quyền kèm dấu thời gian vào Sync_Log và SHALL nhắc người dùng ủy quyền lại.
4. WHEN một lần tải lên Dropbox hoàn thành thành công, THE App SHALL ghi lại tên tệp đã tải lên, dấu thời gian và kết quả Export_Job vào Sync_Log.
5. IF một tệp có cùng tên đã tồn tại trong thư mục Dropbox được cấu hình tại thời điểm tải lên, THEN THE App SHALL tạo một tên tệp duy nhất bằng cách thêm hậu tố phân biệt và SHALL không ghi đè tệp hiện có.
6. IF kết nối mạng thất bại hoặc bị gián đoạn trong khi tải lên Dropbox, THEN THE App SHALL hủy bỏ phần tệp đã tải lên dở dang, SHALL ghi lại một thất bại tải lên do mạng vào Sync_Log, và SHALL đánh dấu Export_Job là đủ điều kiện thử lại (retry-eligible).
7. WHEN một Export_Job được đánh dấu đủ điều kiện thử lại, THE App SHALL tự động thực hiện tối đa 3 lần thử lại tải lên với khoảng nghỉ tối thiểu 5 giây giữa các lần thử; IF cả 3 lần thử lại đều thất bại, THEN THE App SHALL ghi lại trạng thái thất bại cuối cùng của Export_Job vào Sync_Log và SHALL ngừng thử lại tự động.

### Requirement 19: Đích đến MQTT

**User Story:** Là một người dùng có thiết lập nhà thông minh, tôi muốn công bố các bản xuất của mình tới một MQTT broker, để các hệ thống khác có thể đăng ký nhận dữ liệu sức khỏe của tôi.

#### Acceptance Criteria

1. THE App SHALL cho phép người dùng cấu hình một Destination MQTT với một broker host, một port broker là số nguyên trong khoảng từ 1 đến 65535, một topic và thông tin xác thực tùy chọn.
2. WHEN một Export_Job nhắm tới một Destination MQTT, THE App SHALL công bố bản xuất đã tuần tự hóa tới topic đã cấu hình trên broker đã cấu hình bằng mức QoS đã cấu hình.
3. THE App SHALL cho phép người dùng chọn mức chất lượng dịch vụ (quality-of-service) MQTT cho việc công bố từ các giá trị 0, 1 hoặc 2.
4. IF App không thể thiết lập kết nối tới MQTT broker đã cấu hình trong vòng 30 giây, THEN THE App SHALL ghi lại một thất bại kết nối vào Sync_Log và SHALL coi Export_Job là thất bại.
5. WHERE Destination MQTT được cấu hình để dùng TLS, THE App SHALL thiết lập kết nối broker qua TLS.
6. WHERE Destination MQTT được cấu hình với QoS mức 0, WHEN App đã truyền bản xuất tới broker, THE App SHALL coi việc công bố là hoàn tất mà không chờ xác nhận từ broker (fire-and-forget) và SHALL coi Export_Job là thành công.
7. WHERE Destination MQTT được cấu hình với QoS mức 1 hoặc 2, WHEN App nhận được xác nhận công bố từ broker trong vòng 30 giây, THE App SHALL coi Export_Job là thành công.
8. WHERE Destination MQTT được cấu hình với QoS mức 1 hoặc 2, IF App không nhận được xác nhận công bố từ broker trong vòng 30 giây, THEN THE App SHALL ghi lại một thất bại công bố vào Sync_Log và SHALL coi Export_Job là thất bại.

### Requirement 20: Đích đến Home Assistant

**User Story:** Là một người dùng Home Assistant, tôi muốn gửi dữ liệu sức khỏe của mình tới Home Assistant, để tôi có thể dùng các chỉ số của mình trong các bảng điều khiển tự động hóa nhà.

#### Acceptance Criteria

1. THE App SHALL cho phép người dùng cấu hình một Destination Home Assistant với một base URL và một access token dài hạn (long-lived).
2. IF base URL được cấu hình cho một Destination Home Assistant không dùng HTTPS (TLS), THEN THE App SHALL hiển thị một cảnh báo cho biết kết nối không được mã hóa và SHALL cho phép người dùng tiếp tục lưu cấu hình hoặc chỉnh sửa lại base URL.
3. WHEN một Export_Job nhắm tới một Destination Home Assistant, THE App SHALL gửi dữ liệu đã tuần tự hóa tới endpoint Home Assistant dùng access token đã cấu hình để ủy quyền, với thời gian chờ (timeout) cho mỗi request là 30 giây.
4. IF Home Assistant phản hồi với một lỗi xác thực, THEN THE App SHALL ghi lại Export_Job là thất bại xác thực vào Sync_Log và SHALL nhắc người dùng cập nhật access token.
5. IF request tới Home Assistant không hoàn tất trong vòng 30 giây, hoặc thất bại do lỗi mạng hoặc do Home Assistant trả về lỗi máy chủ, THEN THE App SHALL ghi lại Export_Job là thất bại vào Sync_Log kèm chỉ báo nguyên nhân thất bại và SHALL giữ nguyên dữ liệu chưa gửi để có thể thử lại.
6. WHEN Home Assistant chấp nhận dữ liệu, THE App SHALL ghi lại Export_Job là thành công vào Sync_Log.

### Requirement 21: Đích đến Lưu trữ Tệp Cục bộ

**User Story:** Là một người dùng quan tâm đến quyền riêng tư, tôi muốn lưu các bản xuất vào bộ nhớ thiết bị của mình, để dữ liệu của tôi không bao giờ rời khỏi thiết bị.

#### Acceptance Criteria

1. THE App SHALL cho phép người dùng chọn một thư mục cục bộ trên thiết bị làm Destination Local Storage.
2. WHEN một Export_Job nhắm tới Destination Local Storage, THE App SHALL ghi bản xuất đã tuần tự hóa dưới dạng một tệp trong thư mục đã chọn.
3. WHEN App ghi một tệp xuất, THE App SHALL đặt tên tệp theo định dạng dấu thời gian UTC "YYYYMMDD-HHMMSS" (năm 4 chữ số, tháng/ngày/giờ/phút/giây mỗi thành phần 2 chữ số) của thời điểm bắt đầu Export_Job, theo sau là phần mở rộng tương ứng với Export_Format.
4. IF tại thời điểm ghi tệp đã tồn tại một tệp khác có cùng tên trong thư mục đã chọn, THEN THE App SHALL thêm một hậu tố số nguyên tăng dần theo định dạng "-N" (bắt đầu từ 1) vào trước phần mở rộng cho đến khi tên tệp là duy nhất, với tối đa 1000 lần thử.
5. IF App không tạo được tên tệp duy nhất sau 1000 lần thử, THEN THE App SHALL hủy bỏ việc ghi mà không tạo tệp một phần và SHALL ghi lại một thất bại đặt tên vào Sync_Log.
6. IF App thiếu quyền ghi cho thư mục đã chọn tại thời điểm Export_Job, THEN THE App SHALL ghi lại một thất bại quyền-ghi vào Sync_Log và SHALL nhắc người dùng chọn lại thư mục.
7. WHEN App chuẩn bị ghi một tệp xuất tới Destination Local Storage, THE App SHALL kiểm tra trước dung lượng bộ nhớ thiết bị khả dụng so với kích thước byte của bản xuất đã tuần tự hóa trước khi bắt đầu ghi.
8. IF dung lượng bộ nhớ thiết bị khả dụng nhỏ hơn kích thước byte của bản xuất đã tuần tự hóa, THEN THE App SHALL hủy bỏ việc ghi mà không tạo tệp một phần và SHALL ghi lại một thất bại lưu trữ vào Sync_Log.

### Requirement 22: Quyền riêng tư và Xử lý Trên thiết bị

**User Story:** Là một người dùng, tôi muốn App giữ dữ liệu sức khỏe của tôi riêng tư và trên thiết bị của tôi, để không bên thứ ba nào thu thập hoặc nhận dữ liệu của tôi mà không có hành động từ tôi.

#### Acceptance Criteria

1. THE App SHALL hoạt động mà không yêu cầu một tài khoản người dùng hoặc đăng nhập vào App.
2. THE App SHALL thực hiện toàn bộ việc đọc dữ liệu sức khỏe, hợp nhất, tổng hợp và tuần tự hóa ngay trên thiết bị.
3. THE App SHALL truyền dữ liệu sức khỏe qua mạng chỉ tới một Destination mà người dùng đã cấu hình một cách rõ ràng.
4. IF chưa có Destination nào được cấu hình, THEN THE App SHALL không khởi tạo bất kỳ kết nối mạng đi (outbound) nào chứa dữ liệu sức khỏe, sao cho số kết nối mạng đi chứa dữ liệu sức khỏe bằng 0.
5. THE App SHALL loại trừ việc phân tích, theo dõi và đo từ xa (telemetry) dữ liệu sức khỏe.
6. WHEN một người dùng yêu cầu xóa dữ liệu App, THE App SHALL xóa các Automation đã lưu, thông tin xác thực Destination và Sync_Log khỏi thiết bị trong vòng 10 giây.
7. WHEN việc xóa dữ liệu App hoàn tất, THE App SHALL hiển thị một thông báo xác nhận liệt kê rõ từng loại dữ liệu đã được xóa (Automation, thông tin xác thực Destination và Sync_Log).
8. IF việc xóa dữ liệu App thất bại, THEN THE App SHALL giữ nguyên dữ liệu chưa được xóa và hiển thị thông báo lỗi cho biết việc xóa chưa hoàn tất.
9. THE App SHALL lưu trữ thông tin xác thực Destination bằng cơ chế lưu trữ được bảo vệ bởi Android keystore.

### Requirement 23: Ghi nhật ký Đồng bộ và Báo cáo Lỗi

**User Story:** Là một người dùng, tôi muốn có một bản ghi về lịch sử xuất và bất kỳ lỗi nào, để tôi có thể xác nhận dữ liệu đang đồng bộ và khắc phục sự cố khi thất bại.

#### Acceptance Criteria

1. WHEN một Export_Job hoàn thành, THE App SHALL thêm một mục vào Sync_Log chứa dấu thời gian bắt đầu, dấu thời gian hoàn thành, định danh Automation, Export_Format, Destination và trạng thái kết quả.
2. IF một Export_Job thất bại, THEN THE App SHALL thêm hoặc cập nhật mục Sync_Log tương ứng với trạng thái kết quả là thất bại kèm một mô tả lý do thất bại mà người dùng đọc được.
3. WHEN người dùng mở Sync_Log, THE App SHALL hiển thị các mục theo thứ tự giảm dần của dấu thời gian hoàn thành (mới nhất trước); và đối với các mục có cùng dấu thời gian hoàn thành, THE App SHALL sắp xếp theo dấu thời gian bắt đầu giảm dần.
4. THE App SHALL loại trừ các giá trị dữ liệu sức khỏe thô khỏi các mục Sync_Log.
5. WHEN một mục mới được thêm vào Sync_Log và tổng số mục vượt quá giới hạn tối đa đã cấu hình (mặc định 500 mục, có thể cấu hình trong khoảng từ 50 đến 5.000 mục), THE App SHALL xóa lần lượt các mục có dấu thời gian hoàn thành sớm nhất trước (nếu trùng dấu thời gian hoàn thành thì xóa mục có dấu thời gian bắt đầu sớm nhất trước) cho đến khi tổng số mục bằng giới hạn tối đa đã cấu hình.
6. WHEN người dùng xác nhận hành động xóa Sync_Log, THE App SHALL xóa vĩnh viễn tất cả các mục Sync_Log và hiển thị một Sync_Log rỗng.
