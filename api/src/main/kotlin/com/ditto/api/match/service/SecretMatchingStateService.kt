package com.ditto.api.match.service

import com.ditto.api.match.SecretMatchingCard
import com.ditto.api.match.SecretMatchingFacts
import com.ditto.api.match.dto.SecretMatchingStateResponse
import com.ditto.api.system.featureflag.FeatureFlags
import com.ditto.domain.match.entity.CandidateOrigin
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.sanction.repository.SanctionRepository
import com.ditto.domain.system.OperationWeek
import com.ditto.domain.system.entity.FeatureFlagKey
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

@Service
@Transactional(readOnly = true)
class SecretMatchingStateService(
    private val featureFlags: FeatureFlags,
    private val quizSetRepository: QuizSetRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val sanctionRepository: SanctionRepository,
    private val groupMatchingStatusReader: GroupMatchingStatusReader,
) {

    fun getSecretMatchingState(memberId: Long, now: LocalDateTime): SecretMatchingStateResponse {
        val week = OperationWeek.containing(now.toLocalDate())
        if (!featureFlags.isEnabled(FeatureFlagKey.SECRET_MATCHING)) return SecretMatchingStateResponse.none(week)
        val quizSet = findActiveOneToOneQuizSet(week) ?: return SecretMatchingStateResponse.none(week)

        val facts = collectFacts(memberId, now, week, quizSet)
        val card = SecretMatchingCard.determine(facts)
        logCard(memberId, facts, card)
        return SecretMatchingStateResponse.of(card, quizSet.id, week)
    }

    // 주차·유형당 활성 셋은 하나다.
    private fun findActiveOneToOneQuizSet(week: OperationWeek): QuizSet? =
        quizSetRepository.findByWeekStartedOn(week.startedOn)
            .firstOrNull { it.isActive && it.matchingType == MatchingType.ONE_TO_ONE }

    private fun collectFacts(memberId: Long, now: LocalDateTime, week: OperationWeek, quizSet: QuizSet) =
        SecretMatchingFacts(
            now = now,
            week = week,
            oneToOneQuizSet = quizSet,
            oneToOneProgress = quizProgressRepository.findByMemberIdAndQuizSetId(memberId, quizSet.id),
            hasAcceptedOneToOneMatch = personalMatchRepository.findMatchByQuizSetIdAndStatusAndMemberId(
                quizSet.id,
                PersonalMatchStatus.ACCEPTED,
                memberId,
            ) != null,
            isAcceptedInFormedGroup = groupMatchMemberRepository.existsAcceptedInFormedGroupOfWeek(
                memberId,
                week.startedOn,
            ),
            groupMatchingStatus = quizSetRepository.findCompletedQuizSetInWeek(memberId, MatchingType.GROUP, week.startedOn)
                ?.let { groupMatchingStatusReader.read(memberId, it.id) },
            isUnderWarningSanction = sanctionRepository.existsActiveWarningAt(memberId, now),
            hasSecretCandidates = matchCandidateRepository.existsByOwnerMemberIdAndQuizSetIdAndCandidateOriginNot(
                memberId,
                quizSet.id,
                CandidateOrigin.REGULAR,
            ),
        )

    // 카드만 보고는 NONE 의 이유를 알 수 없어서 판단에 쓴 사실을 함께 남긴다.
    private fun logCard(memberId: Long, facts: SecretMatchingFacts, card: SecretMatchingCard) {
        val progress = facts.oneToOneProgress
        log.info {
            "시크릿 매칭 홈 카드: memberId=$memberId now=${facts.now} quizSetId=${facts.oneToOneQuizSet.id} " +
                "state=${card.state} nextRoundAt=${card.nextRoundAt} " +
                "progress=${progress?.status} completedAt=${progress?.completedAt} " +
                "accepted1on1=${facts.hasAcceptedOneToOneMatch} formedGroup=${facts.isAcceptedInFormedGroup} " +
                "groupStatus=${facts.groupMatchingStatus} warned=${facts.isUnderWarningSanction} " +
                "secretCandidates=${facts.hasSecretCandidates}"
        }
    }
}
