package com.ditto.domain.chat.entity

enum class ChatMessageType {
    TEXT,
    IMAGE,
    SYSTEM,

    /** 방이 열리는 순간 서버가 남기는 첫 대화 힌트. 보낸 사람이 없고 안 읽음·푸시 대상이 아니다. */
    QUIZ_HINT,

    /** 회원이 퀴즈 문항을 대화방에 올린 카드. 일반 메시지처럼 안 읽음·푸시 대상이다. */
    QUIZ_CARD,
    ;

    /** 회원이 아니라 서버가 만든 메시지. 대화 메시지 집계(첫 메시지 리마인드·운영 지표)에서 뺀다. */
    val isServerGenerated: Boolean
        get() = this in SERVER_GENERATED

    companion object {
        val SERVER_GENERATED: Set<ChatMessageType> = setOf(SYSTEM, QUIZ_HINT)

        /** 클라이언트가 STOMP 로 보낼 수 있는 유형. SYSTEM·QUIZ_HINT 를 직접 보내면 사건·힌트를 위조할 수 있다. */
        val CLIENT_SENDABLE: Set<ChatMessageType> = setOf(TEXT, IMAGE, QUIZ_CARD)
    }
}
