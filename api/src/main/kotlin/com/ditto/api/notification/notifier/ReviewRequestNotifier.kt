package com.ditto.api.notification.notifier

import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.member.repository.MemberRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 채팅이 끝나 평가가 열린 방의 참여자에게 알린다 — 채팅 종료 트랙과 알림을 잇는 어댑터.
 *
 * **재매칭 방은 제외한다.** 재매칭 채팅이 끝나면 평가를 열지 않기로 했으므로(#132 결정), 알리면
 * 평가할 것이 없는 화면으로 보낸다. `EndedChatReviewOpener`와 같은 기준을 쓰지만 그 클래스에 얹지
 * 않는다 — 평가 열기는 실패해도 복구되는 at-least-once 경로이고, 알림은 못 남기면 그냥 없다.
 *
 * 방마다 한 번만 알린다(`REVIEW_REQUEST`의 `target_id` = 방 ID). 사용자 종료와 만료 스케줄러가 같은
 * 방을 겹쳐 넘겨도 알림은 하나다.
 */
@Component
class ReviewRequestNotifier(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val memberRepository: MemberRepository,
    private val notificationAppender: NotificationAppender,
) {
    /**
     * 끝난 방들의 참여자에게 평가 요청 알림을 남긴다.
     *
     * @return 실제로 남긴 알림 수
     */
    fun notifyFor(endedRoomIds: Collection<Long>): Int {
        if (endedRoomIds.isEmpty()) {
            return 0
        }

        val reviewableRoomIds = chatRoomRepository.findAllById(endedRoomIds)
            .filter { it.sourceType != ChatRoomType.REMATCH }
            .map { it.id }
        if (reviewableRoomIds.isEmpty()) {
            return 0
        }

        val membersByRoomId = chatRoomMemberRepository.findByRoomIdIn(reviewableRoomIds).groupBy { it.roomId }
        val nicknamesById = nicknamesOf(membersByRoomId.values.flatten().map { it.memberId })

        val appended = reviewableRoomIds.sumOf { roomId ->
            val memberIds = membersByRoomId[roomId].orEmpty().map { it.memberId }
            memberIds.count { memberId ->
                val counterpartNicknames = memberIds
                    .filter { it != memberId }
                    .mapNotNull { nicknamesById[it] }
                notificationAppender.append(
                    memberId = memberId,
                    content = NotificationMessages.reviewRequest(counterpartNicknames),
                    targetId = roomId,
                )
            }
        }

        if (appended > 0) {
            logger.info { "평가 요청 알림: ${appended}건 (방 ${reviewableRoomIds.size}개)" }
        }
        return appended
    }

    private fun nicknamesOf(memberIds: Collection<Long>): Map<Long, String> =
        memberRepository.findAllById(memberIds.distinct()).associate { it.id to it.nickname }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
