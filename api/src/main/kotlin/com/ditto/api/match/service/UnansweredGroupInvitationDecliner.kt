package com.ditto.api.match.service

import com.ditto.api.match.GroupResponseDeadline
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDateTime
import org.springframework.stereotype.Component

/**
 * 응답 마감까지 수락도 거절도 하지 않은 그룹 초대를 거절로 바꾼다. 직접 거절과 구분하지 않고 알리지도 않는다.
 *
 * 마감이 가장 최근에 지난 한 주만 본다. 더 넓히면 서버 시각을 미래로 옮겼을 때 아직 응답할 수 있는
 * 이번 주 초대까지 거절된다.
 */
@Component
class UnansweredGroupInvitationDecliner(
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
) {

    /** 거절로 바꾼 초대 수를 돌려준다. */
    fun declineUnanswered(now: LocalDateTime): Long {
        val closedWeek = GroupResponseDeadline.latestClosedWeek(now)
        val groupMatchIds = groupMatchRepository.findGroupMatchIdsByWeekStartedOn(closedWeek.startedOn)
        // 수정 시각은 감사용이라 서버 시각 오버라이드가 아닌 실제 시각을 쓴다.
        val declinedCount = groupMatchMemberRepository.declinePendingInvitations(groupMatchIds, LocalDateTime.now())

        if (declinedCount > 0) {
            log.info { "그룹 미응답 자동 거절: ${declinedCount}건, 주 ${closedWeek.startedOn}, 그룹 ${groupMatchIds.size}개" }
        }
        return declinedCount
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
