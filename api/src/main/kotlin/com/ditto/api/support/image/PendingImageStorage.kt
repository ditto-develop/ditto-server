package com.ditto.api.support.image

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.infrastructure.storage.ObjectStorage
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID
import org.springframework.stereotype.Component

// 접수 전 첨부 이미지는 pending/{category}/{memberId}/ 아래에 두고, 접수할 때 pending/을 뗀 확정 키로 옮긴다.
@Component
class PendingImageStorage(
    private val objectStorage: ObjectStorage,
) {

    fun issueUploadUrls(
        category: ImageUploadCategory,
        memberId: Long,
        files: List<ImageUploadFileRequest>,
    ): ImageUploadUrlsResponse {
        val prefix = pendingPrefix(category, memberId)
        val uploads = files.map { file ->
            val objectKey = "$prefix${UUID.randomUUID()}"
            ImageUploadUrlResponse(
                objectKey = objectKey,
                uploadUrl = objectStorage.issueUploadUrl(objectKey, file.contentType, file.contentLength),
            )
        }
        return ImageUploadUrlsResponse(uploads = uploads)
    }

    fun toPermanentKeys(pendingKeys: List<String>): List<String> = pendingKeys.map { toPermanentKey(it) }

    /** 본인이 발급받아 실제 업로드를 마친 키만 통과한다. */
    fun validateUploadedBy(category: ImageUploadCategory, memberId: Long, pendingKeys: List<String>) {
        val prefix = pendingPrefix(category, memberId)
        pendingKeys.forEach { key ->
            if (!key.startsWith(prefix) || !objectStorage.exists(key)) {
                throw WarnException(ErrorCode.INVALID_ATTACHMENT_IMAGE_KEY)
            }
        }
    }

    /**
     * DB 작업을 다 끝낸 뒤 마지막에 부른다.
     * 중간에 실패하면 이미 옮긴 것은 pending으로 되돌린다.
     */
    fun moveToPermanent(pendingKeys: List<String>) {
        val movedPendingKeys = mutableListOf<String>()
        pendingKeys.forEach { pendingKey ->
            runCatching {
                objectStorage.move(pendingKey, toPermanentKey(pendingKey))
            }.onFailure { exception ->
                restoreToPending(movedPendingKeys)
                throw exception
            }
            movedPendingKeys.add(pendingKey)
        }
    }

    private fun restoreToPending(movedPendingKeys: List<String>) {
        movedPendingKeys.forEach { pendingKey ->
            val permanentKey = toPermanentKey(pendingKey)
            runCatching {
                objectStorage.move(permanentKey, pendingKey)
            }.onFailure { exception ->
                logger.warn(exception) { "첨부 이미지 되돌리기 실패, 확정 영역에 고아 객체가 남았을 수 있음: $permanentKey" }
            }
        }
    }

    private fun toPermanentKey(pendingKey: String): String = pendingKey.removePrefix(PENDING_ROOT)

    private fun pendingPrefix(category: ImageUploadCategory, memberId: Long): String =
        "$PENDING_ROOT${category.keyPrefix}/$memberId/"

    companion object {
        private val logger = KotlinLogging.logger {}

        private const val PENDING_ROOT = "pending/"
    }
}
