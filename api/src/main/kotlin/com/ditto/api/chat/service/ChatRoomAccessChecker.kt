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
    /** 방 멤버가 아니면 거부한다. 조회처럼 종료 후에도 허용되는 경로가 쓴다. */
    fun validateMember(roomId: Long, memberId: Long) {
        if (!chatRoomMemberRepository.existsByRoomIdAndMemberId(roomId, memberId)) {
            throw notFoundOrForbidden(roomId)
        }
    }

    /**
     * 방 멤버이면서 방이 아직 열려 있어야 한다. 대화를 이어가는 경로(전송·이미지 발급·구독)가 쓴다.
     *
     * 종료된 방을 **읽는 것**은 막지 않는다 — 지난 대화와 평가 안내를 봐야 하므로, 조회는 [validateMember]를 쓴다.
     */
    fun validateActiveMember(roomId: Long, memberId: Long) {
        validateMember(roomId, memberId)
        val room = chatRoomRepository.findById(roomId).orElseThrow { notFoundOrForbidden(roomId) }
        if (room.isEnded) {
            throw WarnException(ErrorCode.CHAT_ROOM_ENDED)
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
