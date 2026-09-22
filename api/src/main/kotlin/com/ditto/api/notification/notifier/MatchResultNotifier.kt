package com.ditto.api.notification.notifier

import com.ditto.api.match.service.MatchmakingService
import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.api.support.runCatchingExceptions
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.MatchCandidateRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 주간 매칭 결과를 참여자에게 알린다 — 매칭 배치와 알림을 잇는 어댑터.
 *
 * **배치 트랜잭션 밖에서 부른다.** 적재는 자기 트랜잭션에서 커밋되므로(`NotificationWriter`), 배치
 * 트랜잭션 안에서 부르면 배치가 롤백돼도 알림만 남는다. 그래서 스케줄러·어드민이 배치를 마친 뒤 부른다.
 *
 * 후보가 생긴 회원에게는 결과를(`MATCH_RESULT`), 매칭 풀에 들었지만 후보가 없는 회원에게는 노매칭을
 * (`NO_MATCH`) 알린다. 풀은 배치와 같은 계산([MatchmakingService.matchingPoolMemberIds])이다 — 완료자
 * 전체로 잡으면 정지·성사로 풀에서 빠진 회원에게 "답이 닿지 않았어요"가 간다.
 *
 * 알림은 퀴즈셋당 한 번이다(둘 다 `target_id` = 퀴즈셋 ID). 어드민이 같은 주의 후보를 여러 번 재생성해도
 * 알림은 하나다 — 다시 알릴 이유가 없고, 재생성은 대개 운영상의 수정이다.
 */
@Component
class MatchResultNotifier(
    private val matchCandidateRepository: MatchCandidateRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val matchmakingService: MatchmakingService,
    private val notificationAppender: NotificationAppender,
) {
    /**
     * 후보가 생성된 퀴즈셋들에 대해 알린다.
     *
     * **실패를 삼킨다.** 적재 자체는 [NotificationAppender]가 흡수하지만 그 앞의 조회는 흡수 범위 밖이라
     * 여기서 막는다 — 어드민 후보 재생성(`AdminMatchController`)이 부르는 경로라, 예외가 올라가면 이미
     * 커밋된 배치가 실패한 것처럼 보인다.
     *
     * @return 실제로 남긴 알림 수(결과·노매칭 합). 실패했으면 0
     */
    fun notifyFor(quizSetIds: Collection<Long>): Int =
        runCatchingExceptions { appendMatchResults(quizSetIds) }
            .onFailure { logger.warn(it) { "매칭 결과 알림 실패 — 무시한다: quizSetIds=$quizSetIds" } }
            .getOrDefault(0)

    private fun appendMatchResults(quizSetIds: Collection<Long>): Int {
        if (quizSetIds.isEmpty()) {
            return 0
        }

        var matchedCount = 0
        var unmatchedCount = 0
        quizSetIds.forEach { quizSetId ->
            val matchedMemberIds = notifiedMemberIds(quizSetId)
            val unmatchedMemberIds = matchmakingService.matchingPoolMemberIds(quizSetId) - matchedMemberIds.toSet()
            matchedCount += notificationAppender.appendAll(matchedMemberIds, NotificationMessages.matchResult(), quizSetId)
            unmatchedCount += notificationAppender.appendAll(unmatchedMemberIds, NotificationMessages.noMatch(), quizSetId)
        }

        if (matchedCount + unmatchedCount > 0) {
            logger.info { "매칭 결과 알림: 후보 있음 ${matchedCount}건, 노매칭 ${unmatchedCount}건 (퀴즈셋 ${quizSetIds.size}개)" }
        }
        return matchedCount + unmatchedCount
    }

    /**
     * 후보를 받은 회원. 후보를 담는 테이블이 매칭 타입마다 달라 양쪽을 모두 본다 —
     * 그룹은 `match_candidate` 에 아무것도 쓰지 않아 1:1만 보면 알림 대상이 0명이 된다.
     */
    private fun notifiedMemberIds(quizSetId: Long): List<Long> {
        val roomIds = groupMatchRepository.findByQuizSetId(quizSetId).map { it.id }
        val groupMemberIds =
            if (roomIds.isEmpty()) emptyList()
            else groupMatchMemberRepository.findByRoomIdIn(roomIds).map { it.memberId }

        return (matchCandidateRepository.findOwnerMemberIdsByQuizSetId(quizSetId) + groupMemberIds).distinct()
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
