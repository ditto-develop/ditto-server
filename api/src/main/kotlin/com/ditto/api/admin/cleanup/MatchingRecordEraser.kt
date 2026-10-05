package com.ditto.api.admin.cleanup

import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.notification.entity.NotificationTarget
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import com.ditto.domain.rematch.repository.RematchRepository
import com.ditto.domain.review.repository.MemberReviewRepository
import com.ditto.domain.review.repository.ReviewAnswerRepository
import org.springframework.stereotype.Component

/**
 * 매칭에서 이어지는 기록(채팅방·평가·1:1 신청·그룹·재매칭)을 대상 id 로 받아 지우고, 그것들을 가리키는 알림까지 지운다.
 * 어떤 대상을 지울지는 호출자가 정한다. 더미 정리는 더미가 낀 기록을, QA 도구는 퀴즈셋 하나의 기록을 넘긴다.
 */
@Component
class MatchingRecordEraser(
    private val chatRoomEraser: ChatRoomEraser,
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val rematchRepository: RematchRepository,
    private val memberReviewRepository: MemberReviewRepository,
    private val reviewAnswerRepository: ReviewAnswerRepository,
    private val notificationRepository: NotificationRepository,
) {
    /** 지운 알림 수를 돌려준다. */
    fun erase(targets: MatchingRecordTargets): Int {
        deleteReviewsIn(targets.roomIds)
        chatRoomEraser.deleteRooms(targets.roomIds)
        personalMatchRepository.deleteAllByIdInBatch(targets.personalMatchIds)
        deleteGroupMatches(targets.groupMatchIds)
        rematchRepository.deleteAllByIdInBatch(targets.rematchIds)
        return deleteNotificationsPointingTo(targets)
    }

    private fun deleteReviewsIn(roomIds: Set<Long>) {
        if (roomIds.isEmpty()) return

        val reviewIds = memberReviewRepository.findByChatRoomIdInOrAuthorMemberIdIn(roomIds, emptyList()).map { it.id }
        val answerIds = reviewAnswerRepository.findByMemberReviewIdInOrReviewedMemberIdIn(reviewIds, emptyList())
            .map { it.id }
        reviewAnswerRepository.deleteAllByIdInBatch(answerIds)
        memberReviewRepository.deleteAllByIdInBatch(reviewIds)
    }

    /** 그룹에 묶인 행을 응답 상태와 상관없이 모두 지운다. */
    private fun deleteGroupMatches(groupMatchIds: Set<Long>) {
        if (groupMatchIds.isEmpty()) return

        val groupMatchMemberIds = groupMatchMemberRepository.findByRoomIdIn(groupMatchIds.toList()).map { it.id }
        groupMatchMemberRepository.deleteAllByIdInBatch(groupMatchMemberIds)
        groupMatchRepository.deleteAllByIdInBatch(groupMatchIds)
    }

    private fun deleteNotificationsPointingTo(targets: MatchingRecordTargets): Int {
        val notificationIds = NotificationTarget.entries
            .map { target -> target to targets.idsOf(target) }
            .filter { (_, ids) -> ids.isNotEmpty() }
            .flatMap { (target, ids) ->
                notificationRepository.findByTypeInAndTargetIdIn(NotificationType.pointingTo(target), ids)
            }
            .map { it.id }
            .toSet()
        notificationRepository.deleteAllByIdInBatch(notificationIds)
        return notificationIds.size
    }
}

class MatchingRecordTargets(
    val roomIds: Set<Long>,
    val personalMatchIds: Set<Long>,
    val groupMatchIds: Set<Long>,
    val rematchIds: Set<Long>,
) {
    /** 대상 종류가 늘면 여기서 컴파일이 막혀, 정리가 그 대상을 가리키는 알림을 지울지 정하게 된다. */
    fun idsOf(target: NotificationTarget): Set<Long> =
        when (target) {
            NotificationTarget.CHAT_ROOM -> roomIds
            NotificationTarget.PERSONAL_MATCH -> personalMatchIds
            NotificationTarget.GROUP_MATCH -> groupMatchIds
            NotificationTarget.REMATCH -> rematchIds
            NotificationTarget.MEMBER_REPORT, NotificationTarget.SANCTION,
            NotificationTarget.QUIZ_SET, NotificationTarget.SYSTEM_NOTICE -> emptySet()
        }
}
