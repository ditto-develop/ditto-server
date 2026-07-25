package com.ditto.api.chat.dto

/**
 * [uploadUrl](presigned PUT)로 이미지를 업로드한 뒤, 메시지 전송 시 messageType=IMAGE, content=[objectKey] 로 보낸다.
 */
data class ChatImageUploadUrlResponse(
    val objectKey: String,
    val uploadUrl: String,
)
