package com.ditto.api.match.service

import com.ditto.api.match.GroupResponseDeadline
import com.ditto.api.support.runCatchingExceptions
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.system.OperationWeek
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDateTime
import org.springframework.stereotype.Component

/**
 * 응답 마감까지 수락도 거절도 하지 않은 그룹 초대를 참여로 바꾼다. 그룹마다 자기 트랜잭션이라
 * 한 그룹이 실패해도 나머지는 처리되고, 실패한 그룹은 다음 주기에 다시 집힌다.
 *
 * 마감이 가장 최근에 지난 주만 본다. 여러 주를 함께 보면 서버 시각을 미래로 옮겼을 때 아직 응답할 수 있는
 * 이번 주 초대까지 바뀐다. 그 주 채팅이 끝난 뒤에는 하지 않는다. 지금 시각으로 방을 만들면 다음 주말 방이 된다.
 */
@Component
class UnansweredGroupInvitationJoiner(
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val groupMatchService: GroupMatchService,
    private val chatRoomRepository: ChatRoomRepository,
) {

    /** 새로 들어간 사람이 있는 열린 채팅방을 돌려준다. */
    fun joinUnanswered(now: LocalDateTime): List<ChatRoom> {
        val closedWeek = GroupResponseDeadline.latestClosedWeek(now)
        if (!GroupResponseDeadline.isAutoJoinPeriod(closedWeek, now)) return emptyList()

        val groupMatchIds = findGroupMatchIdsWithUnansweredInvitations(closedWeek)
        if (groupMatchIds.isEmpty()) return emptyList()

        val openedChatRoomIds = groupMatchIds.mapNotNull { joinUnansweredInGroup(it, now) }
        log.info {
            "그룹 미응답 자동 참여: 주 ${closedWeek.startedOn}, groupMatchIds=$groupMatchIds, 열린 방 chatRoomIds=$openedChatRoomIds"
        }
        return chatRoomRepository.findAllById(openedChatRoomIds)
    }

    private fun findGroupMatchIdsWithUnansweredInvitations(week: OperationWeek): List<Long> {
        val groupMatchIds = groupMatchRepository.findGroupMatchIdsByWeekStartedOn(week.startedOn)
        if (groupMatchIds.isEmpty()) return emptyList()

        return groupMatchMemberRepository.findByRoomIdIn(groupMatchIds)
            .filter { it.isPending() }
            .map { it.roomId }
            .distinct()
    }

    private fun joinUnansweredInGroup(groupMatchId: Long, now: LocalDateTime): Long? =
        runCatchingExceptions { groupMatchService.joinUnansweredInvitations(groupMatchId, now) }
            .onFailure { log.warn(it) { "그룹 미응답 자동 참여 실패: groupMatchId=$groupMatchId" } }
            .getOrNull()

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
