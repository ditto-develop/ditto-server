package com.ditto.api.notification.notifier

import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.api.support.runCatchingExceptions
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomMember
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.member.repository.MemberRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 채팅방이 열리면 참여자에게 알린다.
 *
 * ChatRoomLifecycleScheduler 가 개방 커밋 뒤 이번 주기에 열린 방만 넘긴다. 방이 없는 회원은
 * 대상이 아니므로 "매칭 0건 시 미발송"은 따로 처리하지 않는다.
 *
 * 열린 방은 다음 주기에 다시 넘어오지 않는다. 조회 실패나 커밋 직후 재시작이면 그 방들은 알림이 없다.
 * REVIEW_REQUEST 와 같은 선택이다. 복구가 필요해지면 ChatEndingSoonNotifier 처럼 최근 개방 방을
 * 매 주기 다시 집어오는 방식으로 바꾼다.
 */
@Component
class ChatRoomOpenedNotifier(
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val memberRepository: MemberRepository,
    private val notificationAppender: NotificationAppender,
) {
    /** 조회 실패는 여기서 삼킨다. 스케줄러의 다른 처리를 막지 않기 위해서다. 실패하면 0. */
    fun notifyOpened(openedRooms: Collection<ChatRoom>): Int =
        runCatchingExceptions { appendToMembers(openedRooms) }
            .onFailure { logger.warn(it) { "채팅방 오픈 알림 실패 — 무시한다: roomIds=${openedRooms.map(ChatRoom::id)}" } }
            .getOrDefault(0)

    // 열리기 전에 나간 멤버는 받지 않는다. 재매칭 상대를 찾을 때는 나간 사람도 본다.
    private fun appendToMembers(rooms: Collection<ChatRoom>): Int {
        if (rooms.isEmpty()) {
            return 0
        }

        val membersByRoomId = chatRoomMemberRepository.findByRoomIdIn(rooms.map(ChatRoom::id))
            .groupBy { it.roomId }
        val rematchMemberIds = rooms.filter { it.sourceType == ChatRoomType.REMATCH }
            .flatMap { room -> membersByRoomId[room.id].orEmpty().map { it.memberId } }
        val nicknamesById = nicknamesOf(rematchMemberIds)

        val appended = rooms.sumOf { room ->
            val members = membersByRoomId[room.id].orEmpty()
            if (room.sourceType == ChatRoomType.REMATCH) {
                appendRematchRoomOpened(room.id, members, nicknamesById)
            } else {
                appendRoomOpened(room.id, members)
            }
        }

        if (appended > 0) {
            logger.info { "채팅방 오픈 알림: ${appended}건 (방 ${rooms.size}개)" }
        }
        return appended
    }

    // 조회가 실패해도 다른 방 알림까지 잃지 않게 빈 맵으로 두고 일반 문구로 보낸다.
    private fun nicknamesOf(memberIds: List<Long>): Map<Long, String> {
        if (memberIds.isEmpty()) {
            return emptyMap()
        }
        return runCatchingExceptions {
            memberRepository.findAllById(memberIds.distinct()).associate { it.id to it.nickname }
        }
            .onFailure { logger.warn(it) { "재매칭 방 닉네임 조회 실패, 일반 문구로 보낸다: memberIds=$memberIds" } }
            .getOrDefault(emptyMap())
    }

    // 방 단위로 묶어 넘겨 푸시 준비(방 조회)를 방마다 한 번만 한다.
    private fun appendRoomOpened(roomId: Long, members: List<ChatRoomMember>): Int =
        notificationAppender.appendAll(
            memberIds = members.filter { !it.hasLeft }.map { it.memberId },
            content = NotificationMessages.chatRoomOpened(),
            targetId = roomId,
        )

    // 문구에 상대 닉네임이 들어가 사람마다 따로 남긴다.
    private fun appendRematchRoomOpened(
        roomId: Long,
        members: List<ChatRoomMember>,
        nicknamesById: Map<Long, String>,
    ): Int {
        val receivers = members.filter { !it.hasLeft }
        return receivers.count { receiver ->
            val content = counterpartNicknameOf(receiver, members, nicknamesById)
                ?.let { NotificationMessages.rematchChatRoomOpened(it) }
                ?: NotificationMessages.chatRoomOpened().also {
                    logger.warn { "재매칭 상대 닉네임이 없어 일반 문구로 보낸다: roomId=$roomId, memberId=${receiver.memberId}" }
                }
            notificationAppender.append(receiver.memberId, content, targetId = roomId)
        }
    }

    private fun counterpartNicknameOf(
        receiver: ChatRoomMember,
        members: List<ChatRoomMember>,
        nicknamesById: Map<Long, String>,
    ): String? =
        members.firstOrNull { it.memberId != receiver.memberId }
            ?.let { nicknamesById[it.memberId] }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
