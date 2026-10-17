package com.ditto.api.support.image

// keyPrefix는 S3 키 경로라 배포 뒤에는 바꾸지 않는다.
enum class ImageUploadCategory(val keyPrefix: String) {
    USER_REPORT("user-reports"),
    FEEDBACK("feedbacks"),
}
