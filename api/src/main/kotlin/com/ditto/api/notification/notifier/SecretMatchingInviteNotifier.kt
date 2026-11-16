package com.ditto.api.notification.notifier

import com.ditto.api.match.UpcomingSecretRoundFinder
import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.api.support.runCatchingExceptions
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizProgressStatus
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.sanction.repository.SanctionRepository
import com.ditto.domain.system.OperationWeek
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 이번 주 퀴즈를 하나도 끝내지 않은 활성 회원에게 1:1 퀴즈를 풀면 시크릿 매칭에 들어간다고 알린다(기획 SM-08).
 * 그룹을 끝낸 회원은 그룹 미성사 알림이 안내를 맡아서 빼고, 경고 제재 중인 회원은 풀 수 없어서 뺀다.
 *
 * 기능 플래그가 꺼져 있거나, 그 주 활성 1:1 셋이 없거나, 남은 라운드가 없으면 보내지 않는다.
 * 그 주 1:1 셋을 대상으로 주마다 한 번이다.
 */
@Component
class SecretMatchingInviteNotifier(
    private val upcomingSecretRoundFinder: UpcomingSecretRoundFinder,
    private val quizSetRepository: QuizSetRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val memberRepository: MemberRepository,
    private val sanctionRepository: SanctionRepository,
    private val notificationAppender: NotificationAppender,
) {

    /** 실제로 남긴 알림 수. 조회 실패는 여기서 삼키고 0 이다. */
    fun notifyNotParticipated(now: LocalDateTime): Int =
        runCatchingExceptions { appendInvites(now) }
            .onFailure { logger.warn(it) { "시크릿 매칭 안내 알림 실패 — 무시한다: now=$now" } }
            .getOrDefault(0)

    private fun appendInvites(now: LocalDateTime): Int {
        val week = OperationWeek.containing(now.toLocalDate())
        val roundAt = upcomingSecretRoundFinder.findFor(week, now) ?: return 0
        val activeQuizSets = quizSetRepository.findByWeekStartedOn(week.startedOn).filter { it.isActive }
        val oneToOneQuizSet = activeQuizSets.firstOrNull { it.matchingType == MatchingType.ONE_TO_ONE } ?: return 0

        val completedMemberIds = activeQuizSets
            .flatMap { quizProgressRepository.findByQuizSetIdAndStatus(it.id, QuizProgressStatus.COMPLETED) }
            .map { it.memberId }
            .toSet()
        val recipientIds = memberRepository.findAllIdsByStatus(MemberStatus.ACTIVE) -
            completedMemberIds -
            sanctionRepository.findActiveWarningMemberIdsAt(now)

        val appended = notificationAppender.appendAll(
            memberIds = recipientIds,
            content = NotificationMessages.secretMatchingInvited(roundAt),
            targetId = oneToOneQuizSet.id,
        )
        if (appended > 0) {
            logger.info { "시크릿 매칭 안내 알림: ${appended}건 (quizSetId=${oneToOneQuizSet.id})" }
        }
        return appended
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
