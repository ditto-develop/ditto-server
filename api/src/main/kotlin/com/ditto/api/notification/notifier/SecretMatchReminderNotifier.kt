package com.ditto.api.notification.notifier

import com.ditto.api.match.UpcomingSecretRoundFinder
import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.api.support.runCatchingExceptions
import com.ditto.api.system.featureflag.FeatureFlags
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizProgressStatus
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.sanction.repository.SanctionRepository
import com.ditto.domain.system.OperationWeek
import com.ditto.domain.system.entity.FeatureFlagKey
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 이번 주 퀴즈를 하나도 끝내지 않은 활성 회원에게 1:1 퀴즈를 풀면 시크릿 매칭에 들어간다고 알린다(기획 SM-08).
 * 그룹을 끝낸 회원은 대상이 아니다. 성사되지 않은 그룹의 수락자(마감에 자동 참여한 사람 포함)는 그룹 미성사 알림이 안내하고,
 * 그룹 노매칭·초대 거절 회원에게는 안내가 없다(기획대로 둔 빈틈). 경고 제재 중인 회원은 풀 수 없어 뺀다.
 *
 * 응답 기간에 보내면 그 주 안내를 미리 써 버려서 응답 마감 뒤에만 보낸다.
 */
@Component
class SecretMatchReminderNotifier(
    private val featureFlags: FeatureFlags,
    private val upcomingSecretRoundFinder: UpcomingSecretRoundFinder,
    private val quizSetRepository: QuizSetRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val memberRepository: MemberRepository,
    private val sanctionRepository: SanctionRepository,
    private val notificationAppender: NotificationAppender,
) {

    /** 조회 실패는 여기서 삼키고 [SecretMatchReminderResult.Status.FAILED]로 돌려준다. */
    fun notifyNonParticipants(now: LocalDateTime): SecretMatchReminderResult =
        runCatchingExceptions { appendReminders(now) }
            .onFailure { logger.warn(it) { "시크릿 매칭 안내 알림 실패 — 무시한다: now=$now" } }
            .getOrDefault(SecretMatchReminderResult.FAILED)
            .also { logger.info { "시크릿 매칭 안내 알림: now=$now status=${it.status} sent=${it.sentCount} roundAt=${it.roundAt}" } }

    // 플래그는 finder 도 보지만 어드민에 꺼짐을 따로 알리려고 먼저 본다.
    private fun appendReminders(now: LocalDateTime): SecretMatchReminderResult {
        if (!featureFlags.isEnabled(FeatureFlagKey.SECRET_MATCHING)) return SecretMatchReminderResult.FLAG_OFF
        val week = OperationWeek.containing(now.toLocalDate())
        val roundAt = upcomingSecretRoundFinder.nextRoundToAnnounce(week, now)
            ?: return SecretMatchReminderResult.NOT_ANNOUNCEABLE
        val activeQuizSets = quizSetRepository.findByWeekStartedOn(week.startedOn).filter { it.isActive }
        // 대상(target_id)으로 쓸 셋이다. 있는지는 finder 가 이미 봤다.
        val oneToOneQuizSet = activeQuizSets.firstOrNull { it.matchingType == MatchingType.ONE_TO_ONE }
            ?: return SecretMatchReminderResult.NOT_ANNOUNCEABLE
        if (now <= oneToOneQuizSet.endDate) return SecretMatchReminderResult.RESPONSE_PERIOD

        val sentCount = notificationAppender.appendAll(
            memberIds = nonParticipantIds(activeQuizSets, now),
            content = NotificationMessages.secretMatchReminder(roundAt),
            targetId = oneToOneQuizSet.id,
        )
        return SecretMatchReminderResult.sent(sentCount, roundAt)
    }

    private fun nonParticipantIds(activeQuizSets: List<QuizSet>, now: LocalDateTime): Set<Long> {
        val completedMemberIds = activeQuizSets
            .flatMap { quizProgressRepository.findByQuizSetIdAndStatus(it.id, QuizProgressStatus.COMPLETED) }
            .map { it.memberId }
            .toSet()
        return memberRepository.findAllIdsByStatus(MemberStatus.ACTIVE).toSet() -
            completedMemberIds -
            sanctionRepository.findActiveWarningMemberIdsAt(now)
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

/** 안내 알림을 보낸 결과. 어드민 화면이 상태마다 다른 안내를 보여 준다. */
data class SecretMatchReminderResult(
    val status: Status,
    val sentCount: Int,
    val roundAt: LocalDateTime?,
) {
    enum class Status {
        FLAG_OFF,

        /** 이번 주 활성 1:1 셋 없음, 남은 라운드 없음(토 20:00 이후) */
        NOT_ANNOUNCEABLE,

        /** 그 주 1:1 셋의 응답 마감 전 */
        RESPONSE_PERIOD,

        /** 보낼 회원이 없거나 모두 이미 받았다. 적재가 모두 실패해도 여기로 온다(적재는 실패를 삼킨다) */
        NO_RECIPIENT,
        SENT,
        FAILED,
    }

    companion object {
        val FLAG_OFF = SecretMatchReminderResult(Status.FLAG_OFF, 0, null)
        val NOT_ANNOUNCEABLE = SecretMatchReminderResult(Status.NOT_ANNOUNCEABLE, 0, null)
        val RESPONSE_PERIOD = SecretMatchReminderResult(Status.RESPONSE_PERIOD, 0, null)
        val FAILED = SecretMatchReminderResult(Status.FAILED, 0, null)

        fun sent(sentCount: Int, roundAt: LocalDateTime) = SecretMatchReminderResult(
            status = if (sentCount > 0) Status.SENT else Status.NO_RECIPIENT,
            sentCount = sentCount,
            roundAt = roundAt,
        )
    }
}
