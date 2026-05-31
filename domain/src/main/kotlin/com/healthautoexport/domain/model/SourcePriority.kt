package com.healthautoexport.domain.model

/**
 * Thứ hạng ưu tiên nguồn do người dùng cấu hình, dùng để giải quyết trùng lặp đa nguồn
 * (Requirements 7.4, 7.8).
 *
 * Khi hai bản ghi trùng nhau, `Data_Merger` giữ lại bản ghi có Data_Source ứng với **mức ưu
 * tiên cao nhất** (Requirement 7.4). Nếu mức ưu tiên bằng nhau, tie-break theo định danh
 * Data_Source đứng trước theo bảng chữ cái tăng dần ([DataSourceId.id]) (Requirement 7.5).
 *
 * Quy ước: số [ranks] **nhỏ hơn** nghĩa là ưu tiên **cao hơn** (rank 0 ưu tiên nhất). Dùng
 * [rankOf] để tra cứu an toàn; nguồn không có trong bảng nhận mức ưu tiên thấp nhất
 * ([Int.MAX_VALUE]).
 *
 * @property ranks ánh xạ mỗi [DataSourceId] tới thứ hạng ưu tiên (số nhỏ = ưu tiên cao).
 */
data class SourcePriority(
    val ranks: Map<DataSourceId, Int>,
) {
    /**
     * Thứ hạng ưu tiên của [source]; trả về [Int.MAX_VALUE] (ưu tiên thấp nhất) nếu nguồn không
     * được cấu hình trong [ranks].
     */
    fun rankOf(source: DataSourceId): Int = ranks[source] ?: Int.MAX_VALUE
}
