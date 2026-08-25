package com.ditto.api.chat.service

import com.ditto.api.chat.dto.ChatLeaveResult
import com.ditto.api.chat.dto.ChatMessageResponse
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.entity.ChatMessage
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomMember
import com.ditto.domain.chat.entity.ChatRoomStatus
import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.chat.repository.ChatVoteRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 채팅방을 여닫는 경로를 한곳에 모은다 — 만료 스케줄러와 사용자 종료가 같은 전이를 쓴다.
 *
 * **모든 전이는 방 행을 잠근 뒤 상태를 다시 확인하고 수행한다.** 잠금 없이 읽은 스냅샷을 변경하면,
 * Hibernate 가 전 컬럼 UPDATE 를 날리므로 그 사이 사용자가 커밋한 종료가 통째로 덮인다
 * (끝난 방이 `ACTIVE` 로 되살아나 단방향 불변식이 깨진다). 그래서 후보 조회는 ID 만 가져온다.
 *
 * 평가 생성은 여기서 하지 않는다. 이 서비스는 리뷰 도메인을 알지 않고 "끝났다"는 사실까지만 책임지며,
 * 실제 평가 열기는 후속 종료 어댑터(`I1P`·`I1G`)가 맡는다. 각 메서드가 **실제로 전이시킨 방**만
 * 돌려주므로, 어댑터를 붙일 때 그 목록을 그대로 후속 처리에 넘기면 중복 없이 한 번만 처리된다.
 */
@Service
@Transactional(readOnly = true)
class ChatRoomEndService(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatVoteRepository: ChatVoteRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatRoomAccessChecker: ChatRoomAccessChecker,
) {
    /**
     * 기한이 지난 방을 마감한다. 아직 열리지 않은 예약 방(`SCHEDULED`)도 기한이 지났으면 함께 마감한다 —
     * 개방 스케줄러가 멈춰 있던 사이에 기한이 지나버린 방이 영원히 남지 않게 한다.
     */
    @Transactional
    fun endExpired(now: LocalDateTime): List<ChatRoom> {
        val ended = chatRoomRepository.findAllIdsDueToEnd(now)
            .mapNotNull { roomId -> lockIfStillOpen(roomId)?.also { it.expire(now) } }
        chatRoomRepository.saveAll(ended)
        ended.forEach { closeOpenVoteQuietly(it.id, now) }

        if (ended.isNotEmpty()) {
            logger.info { "채팅방 만료 마감: ${ended.size}건" }
        }
        return ended
    }

    /** 개방 시각이 된 예약 방을 연다. */
    @Transactional
    fun openDue(now: LocalDateTime): List<ChatRoom> {
        val opened = chatRoomRepository.findAllIdsDueToOpen(now)
            .mapNotNull { roomId -> lockIfStillScheduled(roomId)?.also { it.open(now) } }
        chatRoomRepository.saveAll(opened)

        if (opened.isNotEmpty()) {
            logger.info { "채팅방 개방: ${opened.size}건" }
        }
        return opened
    }

    /**
     * 참여자가 1:1 채팅을 종료한다. 나갔다는 사실을 SYSTEM 메시지로 남겨,
     * 조회자가 `senderId`를 보고 "상대방이 종료했다"를 판별할 수 있게 한다.
     *
     * 이번 호출이 실제로 끝냈을 때만 그 메시지를 돌려준다 — 호출자는 이 값으로 실시간 브로드캐스트
     * 여부를 정한다. 이미 끝난 방에 다시 요청하면 `null`이다(더블 탭·재시도 대비).
     *
     * 끝낼 수 있는 방인지는 방이 답한다([ChatRoom.canEndByUser]) — 재매칭으로 성사된 방도
     * 두 사람만 있어 같은 경로로 끝낸다.
     */
    @Transactional
    fun endByUser(roomId: Long, memberId: Long, now: LocalDateTime): ChatMessageResponse? {
        chatRoomAccessChecker.validateMember(roomId, memberId)
        val room = chatRoomRepository.findWithLockById(roomId)
            ?: throw chatRoomAccessChecker.notFoundOrForbidden(roomId)

        if (!room.canEndByUser()) {
            throw WarnException(ErrorCode.NOT_CHAT_ROOM_MEMBER, "그룹 채팅은 이 경로로 종료할 수 없습니다.")
        }
        return endLockedRoomByUser(room, memberId, now)
    }

    /**
     * 참여자가 방에서 나간다.
     *
     * - 두 사람 방(일반 1:1·재매칭)의 "나가기"는 종료와 같은 뜻이라 [endByUser]와 같은 전이를 탄다.
     * - 그룹 방은 나간 사실만 남기고(`MEMBER_LEFT`) 방은 유지하되, 잔여 인원이 1명이 되면
     *   해체한다(`INSUFFICIENT_MEMBERS`, `senderId` = 마지막 이탈자). 남은 한 명에게 방이
     *   계속 열려 있으면 아무도 없는 방에 말을 걸게 되기 때문이다.
     *
     * 이미 나갔거나 이미 끝난 방에 다시 요청하면 아무 것도 발행하지 않는다(더블 탭·재시도 대비).
     *
     * 동시 이탈 경합은 방 행 잠금이 직렬화하고, 멱등 검사·잔여 인원 카운트는 멤버 행 **잠금 조회**로
     * 읽는다 — 비잠금 조회는 잠금 대기 전에 고정된 InnoDB 읽기 스냅샷을 쓰므로, 그 사이 커밋된
     * 다른 멤버의 이탈이 보이지 않아 해체가 누락된다([ChatRoomMemberRepository.findAllWithLockByRoomId]).
     * 그래서 이 트랜잭션의 첫 조회가 방 행 잠금이어야 하며, 잠금 순서는 항상 방 → 멤버다(데드락 방지).
     */
    @Transactional
    fun leave(roomId: Long, memberId: Long, now: LocalDateTime): ChatLeaveResult {
        val room = chatRoomRepository.findWithLockById(roomId)
            ?: throw chatRoomAccessChecker.notFoundOrForbidden(roomId)
        val roomMembers = chatRoomMemberRepository.findAllWithLockByRoomId(roomId)
        val me = roomMembers.find { it.memberId == memberId }
            ?: throw chatRoomAccessChecker.notFoundOrForbidden(roomId)

        if (room.canEndByUser()) {
            val message = endLockedRoomByUser(room, memberId, now)
            return ChatLeaveResult(
                systemMessages = listOfNotNull(message),
                isRoomEnded = message != null,
            )
        }
        if (room.isEnded || me.hasLeft) {
            return ChatLeaveResult.nothing()
        }
        return leaveGroupRoom(room, roomMembers, me, now)
    }

    /** 잠근 그룹 방에서 [me]를 내보낸다. 잔여 인원이 1명이 되면 방을 해체한다. */
    private fun leaveGroupRoom(
        room: ChatRoom,
        roomMembers: List<ChatRoomMember>,
        me: ChatRoomMember,
        now: LocalDateTime,
    ): ChatLeaveResult {
        val remainingAfterLeave = roomMembers.count { !it.hasLeft } - 1
        me.leave(now)

        val messages = mutableListOf(
            chatMessageRepository.save(ChatMessage.system(roomId = room.id, senderId = me.memberId, content = MEMBER_LEFT)),
        )
        val shouldDissolve = remainingAfterLeave <= 1
        if (shouldDissolve) {
            room.endByInsufficientMembers(now)
            chatRoomRepository.save(room)
            closeOpenVoteQuietly(room.id, now)
            messages += chatMessageRepository.save(
                ChatMessage.system(roomId = room.id, senderId = me.memberId, content = INSUFFICIENT_MEMBERS),
            )
        }
        return ChatLeaveResult(
            systemMessages = messages.map { ChatMessageResponse.of(it, imageUrl = null) },
            isRoomEnded = shouldDissolve,
        )
    }

    /** 잠근 방을 사용자 종료로 전이시킨다. 이미 끝났으면 `null` — 멱등 재요청이다. */
    private fun endLockedRoomByUser(room: ChatRoom, memberId: Long, now: LocalDateTime): ChatMessageResponse? {
        if (room.isEnded) {
            return null
        }
        room.endByUser(now)
        chatRoomRepository.save(room)
        val message = chatMessageRepository.save(
            ChatMessage.system(roomId = room.id, senderId = memberId, content = USER_LEFT),
        )
        return ChatMessageResponse.of(message, imageUrl = null)
    }

    /**
     * 끝난 방의 열린 투표를 함께 닫는다 — 안 닫으면 종료된 방에 영원히 OPEN 인 투표가 남는다.
     *
     * SYSTEM 메시지는 남기지 않는다: 방이 이미 끝나 구독이 막혀 있고(7004), 만료 마감이 원래
     * 메시지를 만들지 않는 계약과 같은 결이다. 마감자가 없으므로 사유가 ROOM_ENDED 로 남는다.
     * 투표 행을 잠그고 닫는다 — 진행 중인 cast·close 와의 경합을 직렬화한다(잠금 순서 방 → 투표).
     */
    private fun closeOpenVoteQuietly(roomId: Long, now: LocalDateTime) {
        val openVote = chatVoteRepository.findWithLockByOpenRoomId(roomId) ?: return
        openVote.closeBecauseRoomEnded(at = now)
        chatVoteRepository.save(openVote)
    }

    /** 잠근 뒤에도 여전히 열려 있는 방만 돌려준다 — 잠금을 기다리는 사이 사용자가 먼저 끝냈을 수 있다. */
    private fun lockIfStillOpen(roomId: Long): ChatRoom? =
        chatRoomRepository.findWithLockById(roomId)?.takeIf { !it.isEnded }

    /** 잠근 뒤에도 여전히 예약 상태인 방만 돌려준다 — 그 사이 종료되거나 이미 열렸을 수 있다. */
    private fun lockIfStillScheduled(roomId: Long): ChatRoom? =
        chatRoomRepository.findWithLockById(roomId)?.takeIf { it.status == ChatRoomStatus.SCHEDULED }

    companion object {
        private val logger = KotlinLogging.logger {}

        /** 참여자가 채팅을 종료했다는 사건 코드. 표시 문구는 클라이언트가 만든다(`docs/domains/chat.md`). */
        const val USER_LEFT = "USER_LEFT"

        /** 그룹 멤버가 방을 나갔고 방은 계속된다는 사건 코드. `USER_LEFT`(방이 끝남)와 뜻이 정반대라 분리한다. */
        const val MEMBER_LEFT = "MEMBER_LEFT"

        /** 이탈로 잔여 인원이 1명이 되어 방이 해체됐다는 사건 코드. `senderId`는 마지막 이탈자다. */
        const val INSUFFICIENT_MEMBERS = "INSUFFICIENT_MEMBERS"
    }
}
