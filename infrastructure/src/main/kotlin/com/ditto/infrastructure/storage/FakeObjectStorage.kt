package com.ditto.infrastructure.storage

import java.util.concurrent.ConcurrentHashMap

/**
 * local/test 프로필용 인메모리 스토리지.
 *
 * 발급한 키를 업로드된 것으로 간주한다 — 발급받지 않은 키는 exists가 false이므로
 * "업로드되지 않은 키 거부" 흐름을 실제 S3 없이 테스트할 수 있다.
 */
class FakeObjectStorage : ObjectStorage {

    private val uploadedKeys = ConcurrentHashMap.newKeySet<String>()

    override fun issueUploadUrl(key: String, contentType: String, contentLength: Long): String {
        uploadedKeys.add(key)
        return "https://fake-storage.local/$key"
    }

    override fun issueViewUrl(key: String): String = "https://fake-storage.local/view/$key"

    override fun exists(key: String): Boolean = key in uploadedKeys

    override fun move(sourceKey: String, targetKey: String) {
        check(uploadedKeys.remove(sourceKey)) { "존재하지 않는 키는 이동할 수 없습니다: $sourceKey" }
        uploadedKeys.add(targetKey)
    }
}
