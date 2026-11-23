package com.ditto.api.match.service

import com.ditto.api.match.GroupResponseDeadline
import com.ditto.api.support.runCatchingExceptions
import com.ditto.domain.chat.entity.ChatPeriod
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.system.OperationWeek
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDateTime
import org.springframework.stereotype.Component

/**
 * 응답 마감까지 수락도 거절도 하지 않은 그룹 초대를 참여로 바꾼다. 그룹은 각자 트랜잭션으로 처리해
 * 한 그룹이 실패해도 나머지는 성사된다. 실패한 그룹은 다음 주기에 다시 집힌다.
 *
 * 마감이 가장 최근에 지난 한 주만 본다. 더 넓히면 서버 시각을 미래로 옮겼을 때 아직 응답할 수 있는
 * 이번 주 초대까지 바뀐다. 그 주 채팅이 끝난 뒤에는 하지 않는다. 지금 시각으로 방을 만들면 다음 주말 방이 된다.
 */
@Component
class UnansweredGroupInvitationJoiner(
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val groupMatchService: GroupMatchService,
    private val chatRoomRepository: ChatRoomRepository,
) {

    /** 이번에 성사돼 새로 연 채팅방을 돌려준다. 채팅방 오픈 알림을 받을 방이다. */
    fun joinUnanswered(now: LocalDateTime): List<ChatRoom> {
        val closedWeek = GroupResponseDeadline.latestClosedWeek(now)
        if (isChatOver(closedWeek, now)) return emptyList()

        val groupMatchIds = findGroupMatchIdsWithUnanswered(closedWeek)
        if (groupMatchIds.isEmpty()) return emptyList()

        val formedChatRoomIds = groupMatchIds.mapNotNull { groupMatchId ->
            runCatchingExceptions { groupMatchService.joinUnansweredInvitations(groupMatchId) }
                .onFailure { log.warn(it) { "그룹 미응답 자동 참여 실패, 다음 주기에 다시 한다: groupMatchId=$groupMatchId" } }
                .getOrNull()
        }
        log.info {
            "그룹 미응답 자동 참여: 그룹 ${groupMatchIds.size}개, 새로 성사 ${formedChatRoomIds.size}개, 주 ${closedWeek.startedOn}"
        }
        return chatRoomRepository.findAllById(formedChatRoomIds)
    }

    private fun isChatOver(week: OperationWeek, now: LocalDateTime): Boolean =
        now >= ChatPeriod.weekendOf(week.startedOn.atStartOfDay()).expiresAt

    private fun findGroupMatchIdsWithUnanswered(week: OperationWeek): List<Long> {
        val groupMatchIds = groupMatchRepository.findGroupMatchIdsByWeekStartedOn(week.startedOn)
        if (groupMatchIds.isEmpty()) return emptyList()

        return groupMatchMemberRepository.findByRoomIdIn(groupMatchIds)
            .filter { it.isPending() }
            .map { it.roomId }
            .distinct()
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
