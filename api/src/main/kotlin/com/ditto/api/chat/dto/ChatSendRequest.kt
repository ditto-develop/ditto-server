package com.ditto.api.chat.dto

import com.ditto.domain.chat.entity.ChatMessageType

data class ChatSendRequest(
    // TEXT: 본문 / IMAGE: 업로드한 S3 key / QUIZ_CARD: 덧붙인 한마디(빈 문자열 허용)
    val content: String = "",
    // 클라이언트가 보낼 수 있는 유형은 ChatMessageType.CLIENT_SENDABLE 뿐이다.
    val messageType: ChatMessageType = ChatMessageType.TEXT,
    // QUIZ_CARD 일 때 올릴 문항. 그 방의 퀴즈셋 문항이어야 한다.
    val quizId: Long? = null,
)
