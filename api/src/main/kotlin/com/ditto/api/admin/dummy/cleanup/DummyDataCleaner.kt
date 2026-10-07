package com.ditto.api.admin.dummy.cleanup

import com.ditto.api.admin.cleanup.ChatRoomEraser
import com.ditto.api.admin.cleanup.MatchingRecordEraser
import com.ditto.api.admin.cleanup.MatchingRecordTargets
import com.ditto.api.admin.cleanup.ReviewEraser
import com.ditto.api.admin.sanction.MemberStatusRecalculator
import com.ditto.api.system.ServerTimeProvider
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.notification.entity.NotificationTarget
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import com.ditto.domain.sanction.entity.Sanction
import com.ditto.domain.sanction.entity.SanctionLevel
import com.ditto.domain.sanction.entity.SanctionStatus
import com.ditto.domain.sanction.repository.SanctionRepository
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
    private val sanctionRepository: SanctionRepository,
    private val memberStatusRecalculator: MemberStatusRecalculator,
    private val serverTimeProvider: ServerTimeProvider,
) {
    fun deleteDataOf(dummyIds: Collection<Long>): DummyCleanupSummary {
        val matchingTargets = findMatchingTargetsOf(dummyIds)
        val reportIds = dummyMemberDataCleaner.findReportIdsWith(dummyIds)
        val sanctions = dummyMemberDataCleaner.findSanctionsWith(dummyIds, reportIds)
        val sanctionIds = sanctions.map { it.id }.toSet()
        val realMemberSanctions = sanctions.filterNot { it.memberId in dummyIds }
        val sanctionedRealMemberIds = realMemberSanctions.map { it.memberId }.toSortedSet()
        lockMembersBeforeTouchingSanctions(sanctionedRealMemberIds)

        val matchingNotificationCount = matchingRecordEraser.erase(matchingTargets)
        reviewEraser.eraseByMembers(dummyIds)
        dummyMemberDataCleaner.deleteReportsAndSanctions(reportIds, sanctionIds)
        val now = serverTimeProvider.now()
        recalculateStatusesAfterSanctionDeletion(sanctionedRealMemberIds, now)
        val sanctionRemovedMembers = composeSanctionRemovedMembers(sanctionedRealMemberIds, realMemberSanctions, now)
        dummyMemberDataCleaner.deleteAccountDataOf(dummyIds)
        val dummyNotificationCount = deleteNotificationsOf(dummyIds, reportIds, sanctionIds)

        return DummyCleanupSummary(
            dummyCount = dummyIds.size,
            roomCount = matchingTargets.roomIds.size,
            matchCount = matchingTargets.personalMatchIds.size + matchingTargets.groupMatchIds.size,
            reportCount = reportIds.size,
            sanctionCount = sanctionIds.size,
            notificationCount = matchingNotificationCount + dummyNotificationCount,
            sanctionRemovedMembers = sanctionRemovedMembers,
        )
    }

    /** 잠금 순서는 회원 → 제재(해제와 같다). id 순으로 잠가 여러 회원을 잡을 때도 순서가 매번 같다. */
    private fun lockMembersBeforeTouchingSanctions(sortedMemberIds: Set<Long>) {
        sortedMemberIds.forEach { memberRepository.findWithLockById(it) }
    }

    /** 안 하면 지운 제재로 걸린 정지·차단이 실회원에게 남는다. */
    private fun recalculateStatusesAfterSanctionDeletion(realMemberIds: Set<Long>, now: LocalDateTime) {
        realMemberIds.forEach { memberStatusRecalculator.recalculateFromRemainingSanctions(it, now) }
    }

    /** 재계산이 끝난 뒤 불러야 지금 상태가 나온다. */
    private fun composeSanctionRemovedMembers(
        memberIds: Set<Long>,
        removedSanctions: List<Sanction>,
        now: LocalDateTime,
    ): List<SanctionRemovedMember> {
        val liftedSuspensionOrBanMemberIds = removedSanctions
            .filter { it.isEffectiveAt(now) && it.level != SanctionLevel.WARNING }
            .map { it.memberId }
            .toSet()
        return memberRepository.findAllById(memberIds).sortedBy { it.id }.map { member ->
            val facts = SanctionRemovalFacts(
                liftedSuspensionOrBan = member.id in liftedSuspensionOrBanMemberIds,
                hasRemainingWarning = hasRemainingWarning(member.id, now),
            )
            SanctionRemovedMember.of(member, now, facts)
        }
    }

    private fun hasRemainingWarning(memberId: Long, now: LocalDateTime): Boolean =
        sanctionRepository.findAllByMemberIdAndStatus(memberId, SanctionStatus.ACTIVE)
            .any { it.level == SanctionLevel.WARNING && it.isEffectiveAt(now) }

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
