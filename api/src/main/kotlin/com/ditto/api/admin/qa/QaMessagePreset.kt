package com.ditto.api.admin.qa

import com.ditto.domain.chat.entity.ChatMessage

/** 방 화면의 빠른 입력. 경계값 확인에 자주 쓰는 메시지를 한 번에 보낸다. */
enum class QaMessagePreset(private val composeContents: () -> List<String>) {
    GREETING({ listOf(GREETINGS.random()) }),

    /** 전송 상한 그대로. */
    MAX_LENGTH({ listOf("가".repeat(ChatMessage.MAX_CONTENT_LENGTH)) }),

    /** 앱의 한 페이지를 넘겨 커서 페이징과 안읽음 수를 확인한다. */
    BURST({ (1..BURST_COUNT).map { "연속 메시지 $it/$BURST_COUNT" } }),
    ;

    fun contents(): List<String> = composeContents()
}

private val GREETINGS = listOf("안녕하세요!", "반가워요 :)", "주말에 뭐 하세요?", "ㅋㅋㅋ 그러게요", "오 저도요!")
private const val BURST_COUNT = 35
