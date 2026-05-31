package com.healthautoexport.domain.logic

/**
 * Chính sách **thuần** (không I/O, không phụ thuộc Android) kiểm soát egress mạng cho dữ liệu sức
 * khỏe (Requirements 22.3, 22.4) — nền tảng cho Property 52.
 *
 * Quy tắc cốt lõi: **khi chưa có Destination nào được cấu hình, App không được khởi tạo bất kỳ kết
 * nối mạng đi nào chứa dữ liệu sức khỏe**, sao cho số kết nối ra bằng 0 (Requirement 22.4). Mọi
 * lệnh gửi dữ liệu (qua interface `Destination`) PHẢI hỏi guard này **trước** khi thực hiện kết
 * nối mạng; nếu [allowEgress] trả `false`, pipeline phải bỏ qua bước gửi.
 *
 * Guard cố tình giữ thuần và tối giản: nó chỉ phụ thuộc **số lượng** Destination đã cấu hình, nên
 * dễ kiểm thử dựa-trên-thuộc-tính và dễ tiêm vào tầng mạng (Hilt) để đếm/kiểm chứng.
 */
object NetworkEgressGuard {

    /**
     * Cho phép egress hay không dựa trên **số lượng** Destination đã cấu hình.
     *
     * @param configuredDestinationCount số Destination đã cấu hình (≥ 0).
     * @return `true` chỉ khi có ít nhất một Destination; `false` khi không có Destination nào
     *   (chặn mọi kết nối ra chứa dữ liệu sức khỏe — Requirement 22.4).
     * @throws IllegalArgumentException nếu [configuredDestinationCount] âm.
     */
    fun allowEgress(configuredDestinationCount: Int): Boolean {
        require(configuredDestinationCount >= 0) {
            "configuredDestinationCount phải ≥ 0 nhưng nhận $configuredDestinationCount"
        }
        return configuredDestinationCount > 0
    }

    /**
     * Biến thể tiện dụng nhận trực tiếp danh sách Destination đã cấu hình.
     *
     * @param configuredDestinations danh sách Destination đã cấu hình (kiểu phần tử không quan
     *   trọng — guard chỉ xét việc danh sách có rỗng hay không).
     * @return `true` chỉ khi danh sách không rỗng; `false` khi rỗng (Requirement 22.4).
     */
    fun allowEgress(configuredDestinations: Collection<*>): Boolean =
        configuredDestinations.isNotEmpty()
}
