package com.healthautoexport.data.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.healthautoexport.domain.model.DataSourceId
import com.healthautoexport.domain.model.SourcePriority
import com.healthautoexport.domain.port.SourceToggleStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hiện thực [SourceToggleStore] trên Jetpack DataStore (Preferences) — task 15.2.
 *
 * Lưu **bền vững** hai nhóm tùy chọn người dùng và khôi phục đúng qua các phiên làm việc
 * (Requirement 3.2):
 *
 * - **Bật/tắt mỗi Data_Source** một cách độc lập (Requirements 3.1, 3.2): mỗi [DataSourceId]
 *   có một khóa boolean riêng. Khi chưa có giá trị lưu, nguồn nhận mặc định [DEFAULT_ENABLED].
 * - **Thứ hạng ưu tiên nguồn** dùng để giải quyết trùng lặp (Requirements 7.4, 7.8): mỗi
 *   [DataSourceId] có một khóa số nguyên riêng. Số nhỏ hơn = ưu tiên cao hơn (xem [SourcePriority]).
 *
 * Toàn bộ trạng thái nằm trong **một** instance [DataStore]<[Preferences]> duy nhất được tiêm
 * vào (đăng ký tại tầng DI ở task 22.1), bảo đảm chỉ có một nguồn sự thật và tránh tạo nhiều
 * tệp DataStore tranh chấp.
 *
 * @property dataStore instance DataStore<Preferences> dùng chung (một file trên đĩa).
 */
@Singleton
class DataStoreSourceToggleStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SourceToggleStore {

    /**
     * Luồng tập Data_Source đang **bật**; phát giá trị mới mỗi khi tùy chọn thay đổi
     * (Requirements 3.1, 3.2).
     */
    override fun observeEnabledSources(): Flow<Set<DataSourceId>> =
        dataStore.data.map { prefs -> prefs.readEnabledSources() }

    /** Đọc tập Data_Source đang bật tại thời điểm gọi (dùng trong Export_Job, Requirement 3.4). */
    override suspend fun enabledSources(): Set<DataSourceId> =
        dataStore.data.first().readEnabledSources()

    /** Bật/tắt một [source] một cách độc lập và lưu bền vững lựa chọn (Requirements 3.1, 3.2). */
    override suspend fun setEnabled(source: DataSourceId, enabled: Boolean) {
        dataStore.edit { prefs -> prefs[enabledKey(source)] = enabled }
    }

    /**
     * Đọc thứ hạng ưu tiên nguồn đã lưu để giải quyết trùng lặp (Requirements 7.4, 7.8).
     *
     * Chỉ những nguồn có giá trị đã lưu mới xuất hiện trong [SourcePriority.ranks]; nguồn chưa
     * cấu hình được [SourcePriority.rankOf] coi là ưu tiên thấp nhất ([Int.MAX_VALUE]).
     */
    override suspend fun sourcePriority(): SourcePriority {
        val prefs = dataStore.data.first()
        val ranks = DataSourceId.entries
            .mapNotNull { source -> prefs[priorityKey(source)]?.let { rank -> source to rank } }
            .toMap()
        return SourcePriority(ranks)
    }

    /**
     * Lưu bền vững thứ hạng ưu tiên nguồn (Requirement 7.8).
     *
     * Ghi đúng tập [SourcePriority.ranks] được cung cấp: nguồn có trong map được ghi giá trị
     * mới, nguồn vắng mặt bị xóa khỏi store để [sourcePriority] khứ hồi (round-trip) đúng với
     * giá trị đã ghi (Property 32, task 16.3).
     */
    override suspend fun setSourcePriority(priority: SourcePriority) {
        dataStore.edit { prefs ->
            DataSourceId.entries.forEach { source ->
                val rank = priority.ranks[source]
                if (rank != null) {
                    prefs[priorityKey(source)] = rank
                } else {
                    prefs.remove(priorityKey(source))
                }
            }
        }
    }

    /** Suy ra tập nguồn đang bật từ một snapshot [Preferences], áp mặc định khi thiếu khóa. */
    private fun Preferences.readEnabledSources(): Set<DataSourceId> =
        DataSourceId.entries
            .filter { source -> this[enabledKey(source)] ?: DEFAULT_ENABLED }
            .toSet()

    companion object {
        /**
         * Mặc định bật khi người dùng chưa từng chỉnh một nguồn: nguồn được coi là **bật** để
         * App có thể đọc dữ liệu ngay sau khi cài. Người dùng vẫn có thể tắt độc lập (Req 3.1);
         * khi đã tắt, lựa chọn được tôn trọng qua các phiên (Req 3.2).
         */
        const val DEFAULT_ENABLED: Boolean = true

        /** Khóa boolean cho trạng thái bật/tắt của một nguồn, dựa trên [DataSourceId.id] ổn định. */
        private fun enabledKey(source: DataSourceId) =
            booleanPreferencesKey("source_enabled_${source.id}")

        /** Khóa số nguyên cho thứ hạng ưu tiên của một nguồn, dựa trên [DataSourceId.id] ổn định. */
        private fun priorityKey(source: DataSourceId) =
            intPreferencesKey("source_priority_${source.id}")
    }
}
