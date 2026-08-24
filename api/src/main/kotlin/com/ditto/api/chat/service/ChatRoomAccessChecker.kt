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
     * 방 멤버이면서 방이 **열려 있어야** 한다. 대화를 이어가는 경로(전송·이미지 발급·구독)가 쓴다.
     *
     * 개방 전과 종료 후를 다른 코드로 구분한다 — 클라이언트가 "금요일에 열려요"와 "이미 끝났어요"를
     * 가려 보여줘야 한다. 둘 다 **읽는 것**은 막지 않는다(지난 대화·평가 안내·개방 예정 시각을 봐야
     * 하므로 조회는 [validateMember]를 쓴다).
     *
     * 개방 전 차단이 없으면 금요일 개방이 서버에서 지켜지지 않는다. 매칭 수락은 주중에도 일어나므로
     * 방은 며칠간 `SCHEDULED`로 존재하고, 그 사이 대화가 오가면 "금~일 72시간"이 클라이언트 렌더링
     * 규칙에 불과해진다(계획서 ⑧-13 확정).
     */
    fun validateActiveMember(roomId: Long, memberId: Long) {
        val roomMember = chatRoomMemberRepository.findByRoomIdAndMemberId(roomId, memberId)
            ?: throw notFoundOrForbidden(roomId)
        // 이탈자는 대화를 이어갈 수 없다. 조회는 [validateMember]를 타므로 여전히 허용된다(읽기 전용).
        if (roomMember.hasLeft) {
            throw WarnException(ErrorCode.NOT_CHAT_ROOM_MEMBER, "이미 나간 채팅방입니다.")
        }
        val room = chatRoomRepository.findById(roomId).orElseThrow { notFoundOrForbidden(roomId) }
        if (room.isBeforeOpen) {
            throw WarnException(ErrorCode.CHAT_ROOM_NOT_OPENED)
        }
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
