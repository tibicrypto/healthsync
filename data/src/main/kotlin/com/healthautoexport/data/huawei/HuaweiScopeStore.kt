package com.healthautoexport.data.huawei

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Lưu trữ bền vững tập **phạm vi đã ủy quyền** của Huawei_Health_Kit trên thiết bị (Requirements
 * 2.3, 2.6).
 *
 * Tách thành interface để [HuaweiPermissionManager] không phụ thuộc trực tiếp vào DataStore, giúp
 * kiểm thử bằng một bản giả trong bộ nhớ. Bản hiện thực thật là [DataStoreHuaweiScopeStore].
 *
 * Đây là **nguồn sự thật** cho trạng thái "Đã/Chưa ủy quyền" hiển thị theo từng metric: theo
 * Requirement 2.7, một metric được coi là đã ủy quyền khi và chỉ khi scope đọc tương ứng nằm trong
 * tập **đã lưu trên thiết bị** này.
 */
internal interface HuaweiScopeStore {

    /** Tập [HuaweiScope] hiện đang được ủy quyền và đã lưu (rỗng nếu chưa từng ủy quyền). */
    suspend fun authorizedScopes(): Set<HuaweiScope>

    /** Lưu (ghi đè) tập scope đã ủy quyền sau một lần ủy quyền thành công (Requirement 2.3). */
    suspend fun saveScopes(scopes: Set<HuaweiScope>)

    /** Xóa toàn bộ scope đã lưu khi người dùng hủy ủy quyền (Requirement 2.6). */
    suspend fun clear()
}

/**
 * Bản hiện thực [HuaweiScopeStore] trên Jetpack [DataStore] Preferences.
 *
 * Tập scope được lưu dưới một khóa `Set<String>` ([SCOPES_KEY]); mỗi [HuaweiScope.value] là một
 * phần tử. Dùng Preferences DataStore (thay vì proto) cho đơn giản vì dữ liệu chỉ là tập chuỗi.
 *
 * @property dataStore DataStore Preferences được inject ở tầng DI (Hilt).
 */
internal class DataStoreHuaweiScopeStore(
    private val dataStore: DataStore<Preferences>,
) : HuaweiScopeStore {

    override suspend fun authorizedScopes(): Set<HuaweiScope> {
        val prefs = dataStore.data.first()
        return prefs[SCOPES_KEY].orEmpty().mapTo(mutableSetOf()) { HuaweiScope(it) }
    }

    override suspend fun saveScopes(scopes: Set<HuaweiScope>) {
        dataStore.edit { prefs ->
            prefs[SCOPES_KEY] = scopes.mapTo(mutableSetOf()) { it.value }
        }
    }

    override suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(SCOPES_KEY) }
    }

    private companion object {
        val SCOPES_KEY = stringSetPreferencesKey("huawei_authorized_scopes")
    }
}
