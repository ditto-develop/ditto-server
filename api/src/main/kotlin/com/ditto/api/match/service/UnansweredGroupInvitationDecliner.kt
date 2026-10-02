package com.ditto.api.match.service

import com.ditto.api.match.GroupResponseDeadline
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDateTime
import org.springframework.stereotype.Component

/**
 * 응답 마감([GroupResponseDeadline])까지 수락도 거절도 하지 않은 그룹 초대를 거절로 바꾼다.
 *
 * 미응답과 직접 거절을 구분하지 않고, 자동 거절된 사람에게 알리지 않는다(#237).
 * 마감 뒤에는 응답 API가 막히므로 그 주에 대기 초대가 새로 생기지 않는다. 대기 초대만 바꾸므로
 * 매 주기 다시 돌아도 결과가 같다.
 *
 * 마감이 가장 최근에 지난 **한 주만** 본다. 범위를 넓히면 서버 시각 오버라이드를 미래 주로 옮겼을 때
 * 실제 이번 주까지 범위에 들어와, 아직 응답할 수 있는 초대가 되돌릴 수 없게 거절된다.
 * 한 주는 마감 뒤 7일(금~다음 주 목) 동안 대상이라 스케줄러가 그 사이 한 번만 돌면 된다. 그래도 놓친
 * 지난 주차의 대기 초대는 응답이 막혀 있어 남아도 해가 없고, 소급해 정리하지 않는다.
 */
@Component
class UnansweredGroupInvitationDecliner(
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
) {

    /** @return 거절로 바꾼 초대 수 */
    fun declineUnanswered(now: LocalDateTime): Long {
        val weekStartedOn = GroupResponseDeadline.latestPassedWeekStartedOn(now)
        val roomIds = groupMatchRepository.findGroupMatchIdsByWeekStartedOn(weekStartedOn)
        // updatedAt 은 감사 기록이라 서버 시각 오버라이드가 아닌 실제 시각을 남긴다.
        val declined = groupMatchMemberRepository.declinePendingByRoomIdIn(roomIds, LocalDateTime.now())

        if (declined > 0) {
            log.info { "그룹 미응답 자동 거절: ${declined}건 (주 $weekStartedOn, 그룹 ${roomIds.size}개)" }
        }
        return declined
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
