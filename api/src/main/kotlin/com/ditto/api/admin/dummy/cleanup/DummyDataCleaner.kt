package com.ditto.api.admin.dummy.cleanup

import com.ditto.domain.notification.entity.NotificationTarget
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import org.springframework.stereotype.Component

/**
 * 더미 회원을 지우기 전에 더미가 남긴 데이터를 지운다. 외래키가 없어 회원만 지우면 나머지 행이 사라진 회원을 가리킨 채 남는다.
 * 알림은 지운 방·매칭·신고 등의 id 가 다 모인 뒤 마지막에 지운다. 실회원이 받은 알림도 그것들을 가리키면 함께 지운다.
 */
@Component
class DummyDataCleaner(
    private val dummyChatDataCleaner: DummyChatDataCleaner,
    private val dummyMatchDataCleaner: DummyMatchDataCleaner,
    private val dummyMemberDataCleaner: DummyMemberDataCleaner,
    private val notificationRepository: NotificationRepository,
) {
    fun deleteDataOf(dummyIds: Collection<Long>): DummyCleanupSummary {
        val roomIds = dummyChatDataCleaner.deleteRoomsWith(dummyIds)
        val personalMatchIds = dummyMatchDataCleaner.deletePersonalMatchesWith(dummyIds)
        val groupMatchIds = dummyMatchDataCleaner.deleteGroupMatchesWith(dummyIds)
        val rematchIds = dummyMatchDataCleaner.deleteRematchesWith(dummyIds, roomIds)
        dummyMatchDataCleaner.deleteReviewsWith(dummyIds, roomIds)
        val reports = dummyMemberDataCleaner.deleteReportsAndSanctionsWith(dummyIds)
        dummyMemberDataCleaner.deleteOwnedDataOf(dummyIds)

        val deletedTargetIds = mapOf(
            NotificationTarget.CHAT_ROOM to roomIds,
            NotificationTarget.PERSONAL_MATCH to personalMatchIds,
            NotificationTarget.GROUP_MATCH to groupMatchIds,
            NotificationTarget.REMATCH to rematchIds,
            NotificationTarget.MEMBER_REPORT to reports.reportIds,
            NotificationTarget.SANCTION to reports.sanctionIds,
        )
        return DummyCleanupSummary(
            dummyCount = dummyIds.size,
            roomCount = roomIds.size,
            matchCount = personalMatchIds.size + groupMatchIds.size,
            notificationCount = deleteNotifications(dummyIds, deletedTargetIds),
        )
    }

    private fun deleteNotifications(
        dummyIds: Collection<Long>,
        deletedTargetIds: Map<NotificationTarget, Set<Long>>,
    ): Int {
        val pointingToDeleted = deletedTargetIds
            .filterValues { it.isNotEmpty() }
            .flatMap { (target, ids) ->
                notificationRepository.findByTypeInAndTargetIdIn(NotificationType.pointingTo(target), ids)
            }
        val notifications = (notificationRepository.findByMemberIdIn(dummyIds) + pointingToDeleted).distinctBy { it.id }
        notificationRepository.deleteAllInBatch(notifications)
        return notifications.size
    }
}

class DummyCleanupSummary(
    val dummyCount: Int,
    val roomCount: Int,
    val matchCount: Int,
    val notificationCount: Int,
) {
    companion object {
        val NONE = DummyCleanupSummary(dummyCount = 0, roomCount = 0, matchCount = 0, notificationCount = 0)
    }
}
