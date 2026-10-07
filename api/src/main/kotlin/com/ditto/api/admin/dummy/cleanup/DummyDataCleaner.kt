package com.ditto.api.admin.dummy.cleanup

import com.ditto.api.admin.cleanup.ChatRoomEraser
import com.ditto.api.admin.cleanup.MatchingRecordEraser
import com.ditto.api.admin.cleanup.MatchingRecordTargets
import com.ditto.api.admin.cleanup.ReviewEraser
import com.ditto.api.admin.sanction.MemberStatusRecalculator
import com.ditto.api.system.ServerTimeProvider
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.notification.entity.NotificationTarget
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import java.time.LocalDateTime
import org.springframework.stereotype.Component

/**
 * 더미 회원을 지우기 전에 더미가 남긴 데이터를 지운다. 더미가 낀 매칭 기록은 MatchingRecordEraser 가 지우고,
 * 여기서는 더미 본인의 데이터와 더미가 낀 평가·신고·제재를 지운다.
 */
@Component
class DummyDataCleaner(
    private val chatRoomEraser: ChatRoomEraser,
    private val matchingRecordEraser: MatchingRecordEraser,
    private val dummyMatchingTargetFinder: DummyMatchingTargetFinder,
    private val reviewEraser: ReviewEraser,
    private val dummyMemberDataCleaner: DummyMemberDataCleaner,
    private val notificationRepository: NotificationRepository,
    private val memberRepository: MemberRepository,
    private val memberStatusRecalculator: MemberStatusRecalculator,
    private val serverTimeProvider: ServerTimeProvider,
) {
    fun deleteDataOf(dummyIds: Collection<Long>): DummyCleanupSummary {
        val matchingTargets = findMatchingTargetsOf(dummyIds)
        val reportIds = dummyMemberDataCleaner.findReportIdsWith(dummyIds)
        val sanctions = dummyMemberDataCleaner.findSanctionsWith(dummyIds, reportIds)
        val sanctionIds = sanctions.map { it.id }.toSet()
        val sanctionedRealMemberIds = sanctions.map { it.memberId }.toSet() - dummyIds.toSet()

        val matchingNotificationCount = matchingRecordEraser.erase(matchingTargets)
        reviewEraser.eraseByMembers(dummyIds)
        dummyMemberDataCleaner.deleteReportsAndSanctions(reportIds, sanctionIds)
        val sanctionClearedMembers = recalculateStatusesAfterSanctionDeletion(sanctionedRealMemberIds)
        dummyMemberDataCleaner.deleteAccountDataOf(dummyIds)
        val dummyNotificationCount = deleteNotificationsOf(dummyIds, reportIds, sanctionIds)

        return DummyCleanupSummary(
            dummyCount = dummyIds.size,
            roomCount = matchingTargets.roomIds.size,
            matchCount = matchingTargets.personalMatchIds.size + matchingTargets.groupMatchIds.size,
            reportCount = reportIds.size,
            sanctionCount = sanctionIds.size,
            notificationCount = matchingNotificationCount + dummyNotificationCount,
            sanctionClearedMembers = sanctionClearedMembers,
        )
    }

    /** 안 하면 지운 제재로 걸린 정지·차단이 실회원에게 남는다. */
    private fun recalculateStatusesAfterSanctionDeletion(realMemberIds: Set<Long>): List<String> {
        val now = serverTimeProvider.now()
        realMemberIds.forEach { memberStatusRecalculator.recalculateFromRemainingSanctions(it, now) }
        return memberRepository.findAllById(realMemberIds)
            .sortedBy { it.id }
            .map { "${it.nickname}(#${it.id}) ${statusLabelOf(it, now)}" }
    }

    private fun statusLabelOf(member: Member, now: LocalDateTime): String = when {
        member.isBanned() -> "영구 차단 중"
        member.isSuspendedAt(now) -> "이용 정지 중"
        else -> "정상"
    }

    private fun findMatchingTargetsOf(dummyIds: Collection<Long>): MatchingRecordTargets {
        val groupMatchIds = dummyMatchingTargetFinder.findGroupMatchIdsWith(dummyIds)
        val rematchIds = dummyMatchingTargetFinder.findRematchIdsWith(dummyIds, groupMatchIds)
        return MatchingRecordTargets(
            roomIds = findRoomIdsToDelete(dummyIds, groupMatchIds, rematchIds),
            personalMatchIds = dummyMatchingTargetFinder.findPersonalMatchIdsWith(dummyIds),
            groupMatchIds = groupMatchIds,
            rematchIds = rematchIds,
        )
    }

    /**
     * 더미가 멤버였던 방에 더해, 지울 그룹·재매칭에서 나온 방까지 지운다. 더미가 초대를 거절해 방에 없어도
     * 그룹이 지워지면 실회원끼리 연 방이 사라진 원본을 가리키게 된다.
     */
    private fun findRoomIdsToDelete(
        dummyIds: Collection<Long>,
        groupMatchIds: Set<Long>,
        rematchIds: Set<Long>,
    ): Set<Long> =
        chatRoomEraser.findRoomIdsWithMembers(dummyIds) +
            chatRoomEraser.findRoomIdsFrom(ChatRoomType.GROUP, groupMatchIds) +
            chatRoomEraser.findRoomIdsFrom(ChatRoomType.REMATCH, rematchIds)

    private fun deleteNotificationsOf(
        dummyIds: Collection<Long>,
        reportIds: Set<Long>,
        sanctionIds: Set<Long>,
    ): Int {
        val pointingToReports = findNotificationsPointingTo(NotificationTarget.MEMBER_REPORT, reportIds)
        val pointingToSanctions = findNotificationsPointingTo(NotificationTarget.SANCTION, sanctionIds)
        val receivedByDummies = notificationRepository.findByMemberIdIn(dummyIds)
        val notificationIds = (receivedByDummies + pointingToReports + pointingToSanctions).map { it.id }.toSet()
        notificationRepository.deleteAllByIdInBatch(notificationIds)
        return notificationIds.size
    }

    private fun findNotificationsPointingTo(target: NotificationTarget, ids: Set<Long>) =
        if (ids.isEmpty()) emptyList()
        else notificationRepository.findByTypeInAndTargetIdIn(NotificationType.pointingTo(target), ids)
}

class DummyCleanupSummary(
    val dummyCount: Int,
    val roomCount: Int,
    val matchCount: Int,
    val reportCount: Int,
    val sanctionCount: Int,
    val notificationCount: Int,
    val sanctionClearedMembers: List<String>,
) {
    fun toDisplayText(): String =
        "더미 ${dummyCount}명, 채팅방 ${roomCount}개, 매칭 ${matchCount}건, " +
            "신고 ${reportCount}건, 제재 ${sanctionCount}건, 알림 ${notificationCount}개"

    fun toResultMessage(): String {
        val deleted = "${toDisplayText()}를 삭제했습니다."
        if (sanctionClearedMembers.isEmpty()) return deleted
        return "$deleted 더미 신고로 걸린 제재를 지운 실회원: ${sanctionClearedMembers.joinToString(", ")}. 앱에서 다시 로그인하세요."
    }

    companion object {
        val NONE = DummyCleanupSummary(
            dummyCount = 0,
            roomCount = 0,
            matchCount = 0,
            reportCount = 0,
            sanctionCount = 0,
            notificationCount = 0,
            sanctionClearedMembers = emptyList(),
        )
    }
}
