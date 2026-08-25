package com.ditto.api.notification.notifier

import com.ditto.api.chat.dto.ChatMessageResponse
import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.api.support.runCatchingExceptions
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.member.repository.MemberRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 새 메시지를 상대에게 알린다 — 채팅 전송과 알림을 잇는 어댑터.
 *
 * **전송 트랜잭션 밖에서, 브로드캐스트 뒤에 부른다**(`ChatStompController`). 알림 적재가 실시간 전달을
 * 늦추지 않아야 하고, 적재가 실패해도 메시지는 이미 전달돼 있어야 한다.
 *
 * 같은 방의 **안읽은** 새 메시지 알림은 접힌다(`CHAT_MESSAGE`의 중복 정책 = `COLLAPSE_UNREAD`).
 * 메시지마다 행을 쌓으면 알림 센터가 메시지 목록이 되는데, 화면은 방당 한 줄이다.
 *
 * 보낸 사람에게는 남기지 않는다. 방 참여자 전원이 아니라 **보낸 사람을 뺀 사람들**이 대상이다.
 */
@Component
class ChatMessageNotifier(
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val memberRepository: MemberRepository,
    private val notificationAppender: NotificationAppender,
) {
    /**
     * 방금 저장된 메시지를 상대에게 알린다.
     *
     * **실패를 삼킨다.** 적재 자체는 [NotificationAppender]가 흡수하지만 그 앞의 조회는 흡수 범위 밖이라
     * 여기서 막는다 — STOMP 핸들러에 있어 예외가 올라가면 이미 저장·전달된 메시지에 대해 보낸 사람이
     * ERROR 프레임을 받는다.
     *
     * @return 실제로 남긴 알림 수. 실패했으면 0
     */
    fun notifyNewMessage(message: ChatMessageResponse): Int =
        runCatchingExceptions { appendToReceivers(message) }
            .onFailure { logger.warn(it) { "새 메시지 알림 실패 — 무시한다: roomId=${message.roomId}" } }
            .getOrDefault(0)

    private fun appendToReceivers(message: ChatMessageResponse): Int {
        // 방을 나간 멤버에게는 알리지 않는다 — 이탈자는 더 이상 이 방의 수신자가 아니다.
        val receiverIds = chatRoomMemberRepository.findByRoomIdIn(listOf(message.roomId))
            .filter { !it.hasLeft }
            .map { it.memberId }
            .filter { it != message.senderId }
        if (receiverIds.isEmpty()) {
            return 0
        }

        // 보낸 사람 닉네임이 없으면(탈퇴 등) 알리지 않는다 — "님의 새 메시지"에서 이름이 비면 문구가 깨진다.
        val senderNickname = memberRepository.findById(message.senderId).orElse(null)?.nickname ?: return 0

        val content = NotificationMessages.chatMessage(
            senderNickname = senderNickname,
            messageType = message.messageType,
            content = message.content,
        )
        return notificationAppender.appendAll(receiverIds, content, targetId = message.roomId)
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
