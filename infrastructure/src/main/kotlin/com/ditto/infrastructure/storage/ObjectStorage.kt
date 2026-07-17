package com.ditto.infrastructure.storage

/**
 * 오브젝트 스토리지(S3) 어댑터.
 *
 * 업로드는 presigned URL 방식 — 서버는 URL만 발급하고 파일은 클라이언트가 스토리지에 직접 올린다.
 */
interface ObjectStorage {

    /**
     * 업로드용 URL(presigned PUT)을 발급한다.
     *
     * [contentType]·[contentLength]가 서명에 포함되므로 클라이언트는 발급받은 값 그대로만 업로드할 수 있다
     * (크기 상한 검증은 발급 전에 서버가 수행).
     */
    fun issueUploadUrl(key: String, contentType: String, contentLength: Long): String

    /** 해당 키의 객체가 업로드되어 있는지 확인한다. */
    fun exists(key: String): Boolean

    /** 객체를 다른 키로 이동한다 (copy + delete). 원본이 없으면 예외 — 호출 전 [exists]로 검증한다. */
    fun move(sourceKey: String, targetKey: String)
}
