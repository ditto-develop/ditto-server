package com.ditto.api.chat.service

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import org.springframework.stereotype.Component

/**
 * 채팅방 접근 권한 판정. 조회·전송·종료가 같은 규칙을 쓰도록 한곳에 모은다
 * (매칭 관계 판정을 `MatchAccessChecker`가 맡는 것과 같은 결).
 */
@Component
class ChatRoomAccessChecker(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
) {
    /** 방 멤버가 아니면 거부한다. */
    fun validateMember(roomId: Long, memberId: Long) {
        if (!chatRoomMemberRepository.existsByRoomIdAndMemberId(roomId, memberId)) {
            throw notFoundOrForbidden(roomId)
        }
    }

    /** 방이 아예 없는 것과 남의 방인 것을 구분해 답한다. */
    fun notFoundOrForbidden(roomId: Long): WarnException =
        if (chatRoomRepository.existsById(roomId)) {
            WarnException(ErrorCode.NOT_CHAT_ROOM_MEMBER)
        } else {
            WarnException(ErrorCode.CHAT_ROOM_NOT_FOUND)
        }
}
