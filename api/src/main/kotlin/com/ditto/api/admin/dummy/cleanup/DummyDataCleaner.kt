package com.ditto.api.admin.dummy.cleanup

import com.ditto.domain.chat.entity.ChatRoomType
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
        val groupMatchIds = dummyMatchDataCleaner.findGroupMatchIdsWith(dummyIds)
        val rematchIds = dummyMatchDataCleaner.findRematchIdsWith(dummyIds, groupMatchIds)
        val roomIds = findRoomIdsToDelete(dummyIds, groupMatchIds, rematchIds)

        dummyChatDataCleaner.deleteRooms(roomIds)
        val personalMatchIds = dummyMatchDataCleaner.deletePersonalMatchesWith(dummyIds)
        dummyMatchDataCleaner.deleteGroupMatches(groupMatchIds)
        dummyMatchDataCleaner.deleteRematches(rematchIds)
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

    /**
     * 더미가 멤버였던 방에 더해, 지울 그룹·재매칭에서 나온 방까지 지운다. 더미가 초대를 거절해 방에 없어도
     * 그룹이 지워지면 실회원끼리 연 방이 사라진 원본을 가리키게 된다.
     */
    private fun findRoomIdsToDelete(
        dummyIds: Collection<Long>,
        groupMatchIds: Set<Long>,
        rematchIds: Set<Long>,
    ): Set<Long> =
        dummyChatDataCleaner.findRoomIdsWithMembers(dummyIds) +
            dummyChatDataCleaner.findRoomIdsFrom(ChatRoomType.GROUP, groupMatchIds) +
            dummyChatDataCleaner.findRoomIdsFrom(ChatRoomType.REMATCH, rematchIds)

    private fun deleteNotifications(
        dummyIds: Collection<Long>,
        deletedTargetIds: Map<NotificationTarget, Set<Long>>,
    ): Int {
        val pointingToDeleted = deletedTargetIds
            .filterValues { it.isNotEmpty() }
            .flatMap { (target, ids) ->
                notificationRepository.findByTypeInAndTargetIdIn(NotificationType.pointingTo(target), ids)
            }
        val receivedByDummies = notificationRepository.findByMemberIdIn(dummyIds)
        val notificationIds = (receivedByDummies + pointingToDeleted).map { it.id }.toSet()
        notificationRepository.deleteAllByIdInBatch(notificationIds)
        return notificationIds.size
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
