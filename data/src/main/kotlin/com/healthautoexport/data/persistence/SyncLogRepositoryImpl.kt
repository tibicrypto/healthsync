package com.healthautoexport.data.persistence

import androidx.room.withTransaction
import com.healthautoexport.domain.logic.SyncLogEvictionPolicy
import com.healthautoexport.domain.logic.SyncLogOrdering
import com.healthautoexport.domain.model.SyncLogEntry
import com.healthautoexport.domain.port.SyncLogRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Hiện thực Room của [SyncLogRepository] (Requirement 23).
 *
 * Tái sử dụng các chính sách **thuần** của domain để giữ một nguồn sự thật duy nhất:
 * - [SyncLogOrdering.sortForDisplay] cho thứ tự hiển thị (giảm dần `completionUtc`, tie-break
 *   `startUtc`; mục đang chạy `completionUtc == null` lên đầu) — Requirement 23.3.
 * - [SyncLogEvictionPolicy.evict] cho thu hồi khi vượt giới hạn — Requirement 23.5. Giới hạn mặc
 *   định 500, cấu hình trong dải 50..5000 (được kẹp bên trong policy).
 *
 * [append] chèn mục mới rồi thu hồi trong **một giao dịch** để tránh trạng thái trung gian. Mục
 * log chỉ chứa metadata — không có dữ liệu thô (Requirement 23.4).
 *
 * @property database cơ sở dữ liệu, dùng để chạy [append] trong một giao dịch.
 * @property dao DAO Room cho bảng `sync_log`.
 */
@Singleton
class SyncLogRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val dao: SyncLogDao,
) : SyncLogRepository {

    override fun observeAll(): Flow<List<SyncLogEntry>> =
        dao.observeAll().map { entities ->
            // Sắp xếp lần cuối bằng comparator domain để nhất quán tuyệt đối với SyncLogOrdering,
            // kể cả khi sắp xếp SQL không khớp hoàn toàn cách xử lý null.
            SyncLogOrdering.sortForDisplay(entities.map(PersistenceMappers::toDomain))
        }

    override suspend fun append(entry: SyncLogEntry, maxEntries: Int) {
        database.withTransaction {
            dao.insert(PersistenceMappers.toEntity(entry))

            val limit = SyncLogEvictionPolicy.clampMax(maxEntries)
            // Chỉ tính thu hồi khi thực sự vượt giới hạn để tránh đọc/ghi thừa.
            if (dao.count() > limit) {
                val all = dao.getAll().map(PersistenceMappers::toDomain)
                val retainedIds = SyncLogEvictionPolicy.evict(all, limit)
                    .map { it.id }
                    .toSet()
                val idsToEvict = all.map { it.id }.filterNot { it in retainedIds }
                if (idsToEvict.isNotEmpty()) {
                    dao.deleteByIds(idsToEvict)
                }
            }
        }
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}
