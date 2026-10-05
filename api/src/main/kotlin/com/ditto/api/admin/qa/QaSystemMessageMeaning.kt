package com.ditto.api.admin.qa

import com.ditto.api.chat.service.ChatRoomEndService
import com.ditto.api.chat.service.ChatVoteService

/** SYSTEM 메시지 사건 코드의 뜻. 화면은 코드 원문을 함께 보여줘 앱 계약과 대조할 수 있게 한다. */
object QaSystemMessageMeaning {
    fun of(content: String): String? =
        when (content.substringBefore(':')) {
            ChatRoomEndService.USER_LEFT -> "채팅 종료, 방 끝남"
            ChatRoomEndService.MEMBER_LEFT -> "참여자 나감, 방 유지"
            ChatRoomEndService.INSUFFICIENT_MEMBERS -> "인원 부족으로 해체"
            ChatVoteService.VOTE_CREATED -> "투표 생성"
            ChatVoteService.VOTE_CLOSED -> "투표 마감"
            else -> null
        }
}
