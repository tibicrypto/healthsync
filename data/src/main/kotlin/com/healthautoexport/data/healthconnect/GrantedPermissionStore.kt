package com.healthautoexport.data.healthconnect

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.HealthMetricType
import com.healthautoexport.domain.model.HealthPermission
import kotlinx.coroutines.flow.first

/**
 * Kho lưu **tập quyền đã cấp** trên thiết bị, hỗ trợ phát hiện thu hồi và giữ nguyên tập cũ khi
 * yêu cầu quyền lỗi/timeout (Requirements 1.3, 1.9).
 *
 * Lưu giữ là một hợp đồng thuần domain (`Set<HealthPermission>`), tách khỏi cách hiện thực lưu trữ
 * (DataStore) để tầng domain/test không phụ thuộc Android. `DataStorePermissionStore` là hiện thực
 * mặc định dùng Jetpack DataStore (Preferences) — thỏa Property 32 (round-trip lưu trữ cấu hình).
 */
interface GrantedPermissionStore {

    /** Đọc tập quyền đã lưu cho [source]; trả tập rỗng nếu chưa lưu gì. */
    suspend fun load(source: DataSourceId): Set<HealthPermission>

    /** Lưu (ghi đè) tập quyền [granted] cho [source] (Requirement 1.3). */
    suspend fun save(source: DataSourceId, granted: Set<HealthPermission>)

    /** Xóa tập quyền đã lưu cho [source] (vd khi người dùng hủy ủy quyền — Requirement 2.6). */
    suspend fun clear(source: DataSourceId)
}

/** Delegate DataStore cấp ứng dụng cho tập quyền Health_Connect đã cấp. */
private val Context.healthPermissionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "health_connect_permissions",
)

/**
 * Hiện thực [GrantedPermissionStore] trên Jetpack DataStore (Preferences) (Requirement 1.3).
 *
 * Mỗi [HealthPermission] được mã hóa thành một token chuỗi ổn định `source|metric|bg` (xem
 * [encode]/[decode]) và lưu thành một `Set<String>` theo từng [DataSourceId]. Phép mã hóa là
 * song ánh nên `save` rồi `load` trả về **đúng** tập đã lưu (round-trip — Property 32). Token
 * không nhận diện được khi đọc sẽ bị bỏ qua (chống hỏng dữ liệu khi enum thay đổi).
 *
 * @property dataStore kho Preferences; mặc định lấy từ delegate cấp ứng dụng theo [context].
 */
class DataStorePermissionStore(
    private val dataStore: DataStore<Preferences>,
) : GrantedPermissionStore {

    constructor(context: Context) : this(context.applicationContext.healthPermissionDataStore)

    override suspend fun load(source: DataSourceId): Set<HealthPermission> {
        val key = stringSetPreferencesKey(keyName(source))
        val tokens = dataStore.data.first()[key] ?: emptySet()
        return tokens.mapNotNull { decode(it) }.toSet()
    }

    override suspend fun save(source: DataSourceId, granted: Set<HealthPermission>) {
        val key = stringSetPreferencesKey(keyName(source))
        val tokens = granted.map { encode(it) }.toSet()
        dataStore.edit { prefs -> prefs[key] = tokens }
    }

    override suspend fun clear(source: DataSourceId) {
        val key = stringSetPreferencesKey(keyName(source))
        dataStore.edit { prefs -> prefs.remove(key) }
    }

    private fun keyName(source: DataSourceId): String = "granted_${source.id}"

    private companion object {
        /** Mã hóa một quyền thành token `source|metric|bg` (ổn định, dùng làm khóa lưu trữ). */
        fun encode(permission: HealthPermission): String =
            "${permission.source.name}|${permission.metric.name}|${if (permission.background) "1" else "0"}"

        /** Giải mã token; trả `null` nếu token hỏng/không nhận diện được (an toàn dữ liệu). */
        fun decode(token: String): HealthPermission? {
            val parts = token.split('|')
            if (parts.size != 3) return null
            val source = DataSourceId.entries.firstOrNull { it.name == parts[0] } ?: return null
            val metric = HealthMetricType.entries.firstOrNull { it.name == parts[1] } ?: return null
            val background = parts[2] == "1"
            return HealthPermission(source = source, metric = metric, background = background)
        }
    }
}
