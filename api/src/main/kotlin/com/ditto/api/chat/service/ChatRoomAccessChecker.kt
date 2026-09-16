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
    /**
     * 방 멤버가 아니거나 **이미 나갔으면** 거부한다. 조회처럼 종료 후에도 허용되는 경로가 쓴다.
     *
     * 나간 사람에게는 그 방이 아예 없는 것으로 다룬다. 예전에는 읽기 전용으로 남겼는데, 그러면
     * 나간 뒤 남은 사람들이 나눈 대화까지 계속 읽힌다 — 메시지 커서 조회에 `left_at` 컷오프가
     * 없어서 다시 들어가면 최신까지 따라잡혔다. 컷오프를 두는 대신 방 자체를 감추는 쪽으로 간다:
     * "어디까지 보여줄지"를 경로마다 되풀이해 정하지 않아도 되고, 목록에서 사라지는 것과
     * 결과가 어긋나지 않는다(2026-09-17 결정, 이슈 #196).
     *
     * **종료된 방은 계속 보인다** — 내가 나간 것이 아니라 방이 끝난 것이라 지난 대화를 볼 수 있다.
     * 이탈은 그룹에서만 생긴다(두 사람 방의 나가기는 종료와 같은 전이라 이탈자로 남지 않는다).
     */
    fun validateMember(roomId: Long, memberId: Long) {
        val roomMember = chatRoomMemberRepository.findByRoomIdAndMemberId(roomId, memberId)
            ?: throw notFoundOrForbidden(roomId)
        if (roomMember.hasLeft) {
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
