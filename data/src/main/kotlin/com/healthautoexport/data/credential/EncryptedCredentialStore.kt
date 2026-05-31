package com.healthautoexport.data.credential

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.healthautoexport.domain.port.CredentialStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hiện thực [CredentialStore] dùng [EncryptedSharedPreferences] được bảo vệ bởi một
 * [MasterKey] với sơ đồ **AES256-GCM**, khóa gốc nằm trong **Android Keystore** (Requirement
 * 22.9).
 *
 * Đặc tính bảo mật:
 * - Master key sinh/lưu trong Android Keystore (hardware-backed khi thiết bị hỗ trợ); ứng dụng
 *   không bao giờ thấy chất liệu khóa thô.
 * - **Khóa** (tên preference) mã hóa theo `AES256_SIV`, **giá trị** mã hóa theo `AES256_GCM`.
 * - Chỉ lưu credential của Destination, tách biệt khỏi Room và Sync_Log (Requirements 22.9, 23.4).
 * - Giá trị bí mật **không bao giờ được ghi log** — lớp này không in/log khóa hay giá trị.
 *
 * Thao tác I/O của SharedPreferences chạy trên [ioDispatcher] để không chặn luồng gọi; ghi dùng
 * `commit()` đồng bộ nhằm bảo đảm bền vững trước khi `suspend` trả về.
 *
 * @property prefs instance [EncryptedSharedPreferences] đã khởi tạo (xem [create]).
 * @property ioDispatcher dispatcher cho thao tác đọc/ghi đĩa (mặc định [Dispatchers.IO]).
 */
@Singleton
class EncryptedCredentialStore private constructor(
    private val prefs: SharedPreferences,
    private val ioDispatcher: CoroutineDispatcher,
) : CredentialStore {

    /**
     * Hàm khởi tạo dùng cho DI (Hilt): tạo store với file mặc định và [Dispatchers.IO].
     *
     * @param context application context dùng để tạo master key và mở preferences được mã hóa.
     */
    @Inject
    constructor(context: Context) : this(
        prefs = create(context.applicationContext),
        ioDispatcher = Dispatchers.IO,
    )

    override suspend fun put(key: String, secret: String): Unit = withContext(ioDispatcher) {
        // commit() (đồng bộ) bảo đảm dữ liệu đã ghi xong trước khi suspend trả về.
        prefs.edit().putString(key, secret).commit()
        Unit
    }

    override suspend fun get(key: String): String? = withContext(ioDispatcher) {
        prefs.getString(key, null)
    }

    override suspend fun remove(key: String): Unit = withContext(ioDispatcher) {
        prefs.edit().remove(key).commit()
        Unit
    }

    override suspend fun clear(): Unit = withContext(ioDispatcher) {
        prefs.edit().clear().commit()
        Unit
    }

    companion object {
        /** Tên file preferences được mã hóa chứa credential Destination. */
        const val PREFS_FILE_NAME: String = "destination_credentials"

        /**
         * Tạo [EncryptedSharedPreferences] với một [MasterKey] AES256-GCM (Android Keystore).
         *
         * Tách riêng để tiện cấu hình DI và kiểm thử (Robolectric). Master key dùng sơ đồ
         * [MasterKey.KeyScheme.AES256_GCM] theo Requirement 22.9.
         *
         * @param context context dùng để dựng master key và mở file mã hóa.
         */
        private fun create(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            return EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
