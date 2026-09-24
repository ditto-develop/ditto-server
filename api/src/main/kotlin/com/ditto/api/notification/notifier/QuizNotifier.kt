package com.ditto.api.notification.notifier

import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.api.support.runCatchingExceptions
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.entity.QuizProgressStatus
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDateTime
import org.springframework.stereotype.Component

/**
 * 이번 주 퀴즈의 오픈·마감 임박을 활성 회원에게 알린다. WeeklyNotificationScheduler 가 월·수요일에 부른다.
 *
 * 한 주에 1:1·그룹 셋이 나란히 열릴 수 있지만 알림은 주에 한 번이다. 문항이 있는 셋 중 id 가 가장 작은 셋을
 * 대표로 삼아 target_id 와 문구의 문항 수에 쓴다. 문항 있는 셋이 하나도 없으면(어드민이 아직 채우지 않음)
 * 풀 수 있는 퀴즈가 없으므로 알리지 않는다.
 *
 * 회원 수만큼 적재와 푸시가 한 번에 돈다. 규모가 커지면 여기서 끊어 넘긴다.
 */
@Component
class QuizNotifier(
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val memberRepository: MemberRepository,
    private val notificationAppender: NotificationAppender,
) {
    /** 활성 회원 전원에게. 조회 실패는 여기서 삼킨다. 실패하면 0. */
    fun notifyOpened(now: LocalDateTime): Int =
        runCatchingExceptions { appendOpened(now) }
            .onFailure { logger.warn(it) { "퀴즈 오픈 알림 실패 — 무시한다: now=$now" } }
            .getOrDefault(0)

    /** 이번 주 어느 셋도 끝내지 않은 활성 회원에게. 조회 실패는 여기서 삼킨다. 실패하면 0. */
    fun notifyClosingSoon(now: LocalDateTime): Int =
        runCatchingExceptions { appendClosingSoon(now) }
            .onFailure { logger.warn(it) { "퀴즈 마감 임박 알림 실패 — 무시한다: now=$now" } }
            .getOrDefault(0)

    private fun appendOpened(now: LocalDateTime): Int {
        val (quizSet, quizCount) = representativeQuizSet(quizSetRepository.findCurrentWeekActive(now)) ?: return 0
        val appended = notificationAppender.appendAll(
            memberIds = memberRepository.findAllIdsByStatus(MemberStatus.ACTIVE),
            content = NotificationMessages.quizOpened(quizCount),
            targetId = quizSet.id,
        )
        if (appended > 0) {
            logger.info { "퀴즈 오픈 알림: ${appended}건 (quizSetId=${quizSet.id})" }
        }
        return appended
    }

    private fun appendClosingSoon(now: LocalDateTime): Int {
        val activeQuizSets = quizSetRepository.findCurrentWeekActive(now)
        val (quizSet, _) = representativeQuizSet(activeQuizSets) ?: return 0
        // 1:1·그룹 중 하나라도 끝냈으면 참여자다. 문구가 하나라 회원당 한 번만 보낸다.
        val completedMemberIds = activeQuizSets
            .flatMap { quizProgressRepository.findByQuizSetIdAndStatus(it.id, QuizProgressStatus.COMPLETED) }
            .map { it.memberId }
            .toSet()
        val appended = notificationAppender.appendAll(
            memberIds = memberRepository.findAllIdsByStatus(MemberStatus.ACTIVE) - completedMemberIds,
            content = NotificationMessages.quizClosingSoon(),
            targetId = quizSet.id,
        )
        if (appended > 0) {
            logger.info { "퀴즈 마감 임박 알림: ${appended}건 (quizSetId=${quizSet.id})" }
        }
        return appended
    }

    /** 문항이 있는 셋 중 id 가 가장 작은 것과 그 문항 수. 없으면 null. */
    private fun representativeQuizSet(activeQuizSets: List<QuizSet>): Pair<QuizSet, Int>? =
        activeQuizSets
            .sortedBy { it.id }
            .firstNotNullOfOrNull { quizSet ->
                val quizCount = quizRepository.countByQuizSetId(quizSet.id).toInt()
                if (quizCount == 0) null else quizSet to quizCount
            }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
