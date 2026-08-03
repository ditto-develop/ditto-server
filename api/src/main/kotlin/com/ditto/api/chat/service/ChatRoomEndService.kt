package com.ditto.api.chat.service

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.entity.ChatMessage
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 채팅방을 여닫는 경로를 한곳에 모은다 — 만료 스케줄러와 사용자 종료가 같은 전이를 쓴다.
 *
 * 평가 생성은 여기서 하지 않는다. 이 서비스는 리뷰 도메인을 알지 않고 "끝났다"는 사실까지만 책임지며,
 * 실제 평가 열기는 후속 종료 어댑터(`I1P`·`I1G`)가 맡는다. 각 메서드가 **실제로 전이시킨 방**만
 * 돌려주므로, 어댑터를 붙일 때 그 목록을 그대로 후속 처리에 넘기면 중복 없이 한 번만 처리된다.
 */
@Service
@Transactional(readOnly = true)
class ChatRoomEndService(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatRoomAccessChecker: ChatRoomAccessChecker,
) {
    /**
     * 기한이 지난 방을 마감한다. 아직 열리지 않은 예약 방(`SCHEDULED`)도 기한이 지났으면 함께 마감한다 —
     * 개방 스케줄러가 멈춰 있던 사이에 기한이 지나버린 방이 영원히 남지 않게 한다.
     */
    @Transactional
    fun endExpired(now: LocalDateTime): List<ChatRoom> {
        val expired = chatRoomRepository.findAllDueToEnd(now)
        expired.forEach { it.expire(now) }
        chatRoomRepository.saveAll(expired)

        if (expired.isNotEmpty()) {
            logger.info { "채팅방 만료 마감: ${expired.size}건" }
        }
        return expired
    }

    /** 개방 시각이 된 예약 방을 연다. */
    @Transactional
    fun openDue(now: LocalDateTime): List<ChatRoom> {
        val due = chatRoomRepository.findAllDueToOpen(now)
        due.forEach { it.open(now) }
        chatRoomRepository.saveAll(due)

        if (due.isNotEmpty()) {
            logger.info { "채팅방 개방: ${due.size}건" }
        }
        return due
    }

    /**
     * 참여자가 1:1 채팅을 종료한다. 나갔다는 사실을 SYSTEM 메시지로 남겨,
     * 조회자가 `senderId`를 보고 "상대방이 종료했다"를 판별할 수 있게 한다.
     *
     * 이미 끝난 방에 다시 요청하면 아무 일도 하지 않는다(더블 탭·재시도 대비) — 종료 메시지도 한 번만 남는다.
     * 겹친 요청은 서로의 커밋 전 상태를 보므로 이 판정만으로는 부족해 **방 행을 잠근 뒤** 진행한다.
     *
     * 그룹은 한 명이 나가도 방이 끝나지 않아 이 경로를 쓰지 않는다(멤버 이탈은 별도 트랙).
     */
    @Transactional
    fun endByUser(roomId: Long, memberId: Long, now: LocalDateTime): ChatRoom {
        chatRoomAccessChecker.validateMember(roomId, memberId)
        val room = chatRoomRepository.findWithLockById(roomId)
            ?: throw chatRoomAccessChecker.notFoundOrForbidden(roomId)

        if (room.roomType != ChatRoomType.PERSONAL) {
            throw WarnException(ErrorCode.NOT_CHAT_ROOM_MEMBER, "그룹 채팅은 이 경로로 종료할 수 없습니다.")
        }
        if (room.isEnded) {
            return room
        }

        room.endByUser(now)
        chatRoomRepository.save(room)
        chatMessageRepository.save(
            ChatMessage.system(roomId = roomId, senderId = memberId, content = USER_LEFT),
        )
        return room
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        /** 참여자가 채팅을 종료했다는 사건 코드. 표시 문구는 클라이언트가 만든다(`docs/domains/chat.md`). */
        const val USER_LEFT = "USER_LEFT"
    }
}
