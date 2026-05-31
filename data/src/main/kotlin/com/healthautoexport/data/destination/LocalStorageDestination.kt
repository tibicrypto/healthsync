package com.healthautoexport.data.destination

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.system.Os
import androidx.documentfile.provider.DocumentFile
import com.healthautoexport.domain.logic.FileNameGenerator
import com.healthautoexport.domain.logic.LocalStorageFileName
import com.healthautoexport.domain.logic.StorageGuard
import com.healthautoexport.domain.model.DestinationType
import com.healthautoexport.domain.port.Destination
import com.healthautoexport.domain.port.DestinationConfig
import com.healthautoexport.domain.port.DestinationResult
import com.healthautoexport.domain.port.ExportPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Hiện thực [Destination] cho đích đến Local Storage (Requirement 21).
 *
 * Ghi [ExportPayload.bytes] vào một thư mục cây do người dùng chọn qua Storage Access Framework
 * (SAF). Cấu hình mang [DestinationConfig.LocalStorage.treeUri] — chuỗi tree URI nhận được khi người
 * dùng cấp quyền thư mục; lớp này dùng [DocumentFile] (androidx.documentfile) để thao tác trong cây đó.
 *
 * Hành vi theo Requirement 21:
 * - **21.3**: Tên tệp `YYYYMMDD-HHMMSS` (UTC theo thời điểm bắt đầu job) + đuôi định dạng, qua
 *   [LocalStorageFileName.forJob].
 * - **21.4, 21.5**: Trùng tên ⇒ thêm hậu tố `-N` (1..1000) qua [FileNameGenerator]; nếu không tạo
 *   được tên duy nhất sau 1000 lần thử ⇒ hủy ghi (không tạo tệp một phần) + [DestinationResult.Failure].
 * - **21.6**: Thiếu quyền ghi thư mục ⇒ [DestinationResult.Failure] nhắc người dùng chọn lại thư mục.
 * - **21.7, 21.8**: Kiểm tra dung lượng khả dụng so với kích thước payload qua [StorageGuard] **trước
 *   khi** ghi; không đủ ⇒ hủy ghi (không tạo tệp một phần) + [DestinationResult.Failure].
 * - **21.5 (no partial file)**: Ghi vào một tệp tạm rồi mới đổi tên (commit) sang tên cuối; nếu ghi
 *   thất bại, xóa tệp tạm để không còn tệp ghi dở.
 *
 * @property context application context (sẽ tiêm qua Hilt `@ApplicationContext`).
 * @property freeSpaceProvider giải pháp tra cứu dung lượng byte khả dụng của thư mục đích; tách ra để
 *   kiểm thử. Mặc định dùng `statvfs` trên file descriptor của thư mục, dự phòng [StatFs] trên bộ nhớ
 *   trong của thiết bị (Requirement 21.7).
 */
class LocalStorageDestination(
    private val context: Context,
    private val freeSpaceProvider: (Uri) -> Long = { uri -> resolveFreeSpace(context, uri) },
) : Destination {

    override val type: DestinationType get() = DestinationType.LOCAL_STORAGE

    override suspend fun send(payload: ExportPayload, config: DestinationConfig): DestinationResult {
        require(config is DestinationConfig.LocalStorage) {
            "LocalStorageDestination yêu cầu DestinationConfig.LocalStorage nhưng nhận ${config::class.simpleName}"
        }

        return withContext(Dispatchers.IO) {
            val treeUri = Uri.parse(config.treeUri)
            val directory = DocumentFile.fromTreeUri(context, treeUri)

            // Requirement 21.6: thiếu quyền ghi (hoặc thư mục không truy cập được) ⇒ nhắc chọn lại.
            if (directory == null || !directory.isDirectory || !directory.canWrite()) {
                return@withContext DestinationResult.Failure(
                    reason = "Không có quyền ghi vào thư mục đã chọn; vui lòng chọn lại thư mục.",
                    retryEligible = false,
                )
            }

            // Requirement 21.3: tên cơ sở theo dấu thời gian UTC + đuôi định dạng.
            val desiredName = LocalStorageFileName.forJob(payload.jobStartUtc, payload.format)

            // Requirements 21.4, 21.5: giải quyết trùng tên bằng hậu tố -N (tối đa 1000 lần).
            val finalName = FileNameGenerator.generate(
                desiredName = desiredName,
                exists = { name -> directory.findFile(name) != null },
            ) ?: return@withContext DestinationResult.Failure(
                reason = "Không tạo được tên tệp duy nhất cho \"$desiredName\" sau " +
                    "${FileNameGenerator.DEFAULT_MAX_ATTEMPTS} lần thử; hủy ghi.",
                retryEligible = false,
            )

            // Requirements 21.7, 21.8: kiểm tra dung lượng trước khi ghi.
            val freeSpace = freeSpaceProvider(treeUri)
            if (!StorageGuard.canWrite(payload.bytes.size.toLong(), freeSpace)) {
                return@withContext DestinationResult.Failure(
                    reason = "Dung lượng khả dụng ($freeSpace byte) nhỏ hơn kích thước bản xuất " +
                        "(${payload.bytes.size} byte); hủy ghi.",
                    retryEligible = false,
                )
            }

            writeAtomically(directory, finalName, payload)
        }
    }

    /**
     * Ghi nguyên tử theo kiểu "ghi tạm rồi commit" (Requirement 21.5):
     * 1. Tạo một tệp tạm với tên duy nhất (`<finalName>.part`, thêm hậu tố nếu cần).
     * 2. Ghi toàn bộ byte vào tệp tạm.
     * 3. Đổi tên tệp tạm sang [finalName] (commit).
     *
     * Nếu bất kỳ bước ghi nào thất bại, xóa tệp tạm để không để lại tệp ghi dở.
     */
    private fun writeAtomically(
        directory: DocumentFile,
        finalName: String,
        payload: ExportPayload,
    ): DestinationResult {
        // Tên tạm duy nhất để không đụng tệp hiện có.
        val tempName = FileNameGenerator.generate(
            desiredName = "$finalName.part",
            exists = { name -> directory.findFile(name) != null },
        ) ?: return DestinationResult.Failure(
            reason = "Không tạo được tên tệp tạm cho \"$finalName\"; hủy ghi.",
            retryEligible = false,
        )

        val tempFile = directory.createFile(payload.contentType, tempName)
            ?: return DestinationResult.Failure(
                reason = "Không tạo được tệp tạm trong thư mục đã chọn; hủy ghi.",
                retryEligible = false,
            )

        try {
            val wrote = context.contentResolver.openOutputStream(tempFile.uri)?.use { out ->
                out.write(payload.bytes)
                out.flush()
                true
            } ?: false

            if (!wrote) {
                tempFile.delete()
                return DestinationResult.Failure(
                    reason = "Không mở được luồng ghi cho tệp tạm; hủy ghi.",
                    retryEligible = true,
                )
            }
        } catch (e: IOException) {
            // Ghi thất bại: dọn tệp tạm để không còn tệp một phần (Requirement 21.5).
            tempFile.delete()
            return DestinationResult.Failure(
                reason = "Ghi tệp cục bộ thất bại: ${e.message ?: e::class.simpleName}",
                retryEligible = true,
            )
        }

        // Commit: đổi tên tệp tạm sang tên cuối.
        val renamed = runCatching { tempFile.renameTo(finalName) }.getOrDefault(false)
        if (!renamed) {
            tempFile.delete()
            return DestinationResult.Failure(
                reason = "Không hoàn tất (đổi tên) tệp \"$finalName\"; hủy ghi.",
                retryEligible = true,
            )
        }

        return DestinationResult.Success(detail = "Đã lưu tệp \"$finalName\".")
    }

    private companion object {

        /**
         * Tra cứu dung lượng byte khả dụng cho [treeUri].
         *
         * Trước tiên thử `statvfs` trên file descriptor của thư mục gốc trong cây tài liệu; nếu không
         * khả dụng (provider không hỗ trợ mở thư mục), dự phòng bằng [StatFs] trên bộ nhớ trong thiết
         * bị. Trả về `0` khi không thể xác định, khiến [StorageGuard] coi như không đủ chỗ — an toàn
         * theo hướng bảo thủ.
         */
        fun resolveFreeSpace(context: Context, treeUri: Uri): Long {
            val viaDocument = runCatching {
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                context.contentResolver.openFileDescriptor(docUri, "r")?.use { pfd ->
                    val stat = Os.fstatvfs(pfd.fileDescriptor)
                    stat.f_bavail * stat.f_bsize
                }
            }.getOrNull()

            if (viaDocument != null && viaDocument > 0) return viaDocument

            return runCatching {
                StatFs(Environment.getDataDirectory().path).availableBytes
            }.getOrDefault(0L)
        }
    }
}
