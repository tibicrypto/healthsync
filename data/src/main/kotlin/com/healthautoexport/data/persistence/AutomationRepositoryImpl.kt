package com.healthautoexport.data.persistence

import com.healthautoexport.domain.model.Automation
import com.healthautoexport.domain.port.AutomationRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Hiện thực Room của [AutomationRepository] (Requirements 14.5, 14.7).
 *
 * Ủy thác lưu trữ cho [AutomationDao] và ánh xạ entity ⇄ domain qua [PersistenceMappers]. Việc
 * phát hiện trùng tên không phân biệt hoa/thường ([findByNameIgnoreCase]) so trên cột
 * `nameLower` đã thường-hóa; chỉ mục unique của entity là lớp thực thi cuối cùng (Req 14.7).
 *
 * @property dao DAO Room cho bảng `automations`.
 */
@Singleton
class AutomationRepositoryImpl @Inject constructor(
    private val dao: AutomationDao,
) : AutomationRepository {

    override fun observeAll(): Flow<List<Automation>> =
        dao.observeAll().map { entities -> entities.map(PersistenceMappers::toDomain) }

    override suspend fun findById(id: String): Automation? =
        dao.findById(id)?.let(PersistenceMappers::toDomain)

    override suspend fun findByNameIgnoreCase(name: String): Automation? =
        dao.findByNameLower(PersistenceMappers.normalizeName(name))
            ?.let(PersistenceMappers::toDomain)

    override suspend fun upsert(automation: Automation) {
        dao.upsert(PersistenceMappers.toEntity(automation))
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}
