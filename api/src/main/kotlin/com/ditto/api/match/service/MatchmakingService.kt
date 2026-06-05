package com.ditto.api.match.service

import com.ditto.api.match.exclusion.MatchExclusionPolicy
import com.ditto.api.match.matching.MatchParticipant
import com.ditto.api.match.matching.MatchingProcessor
import com.ditto.api.match.matching.ScoredDuo
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.entity.MatchCandidate
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizProgressStatus
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 한 퀴즈셋의 매칭 후보를 계산해 저장하는 배치 오케스트레이션.
 *
 * 흐름: 참여자(완료자) 풀 → 제외 정책 적용 → 답변 로드 → 매칭 전략 실행 → match_candidate 갱신.
 * 매칭 타입(1:1/그룹)별 차이는 [MatchingProcessor] 와 [MatchExclusionPolicy] 가 담당하며,
 * 해당 타입의 전략이 없으면(예: GROUP) 건너뛴다.
 */
@Service
@Transactional(readOnly = true)
class MatchmakingService(
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val exclusionPolicies: List<MatchExclusionPolicy>,
    private val matchingProcessors: List<MatchingProcessor>,
) {

    /** 해당 퀴즈셋의 매칭 후보를 계산해 저장한다. 재계산 시 기존 후보를 모두 대체한다. */
    @Transactional
    fun generateMatchingCandidates(quizSetId: Long) {
        val quizSet = quizSetRepository.findById(quizSetId).orElseThrow { WarnException(ErrorCode.NOT_FOUND) }
        val matchingType = quizSet.matchingType
        val processor = matchingProcessors.firstOrNull { it.matchingType == matchingType } ?: return

        val availableMemberIds = findAvailableMembers(quizSetId, matchingType)
        if (availableMemberIds.size < 2) {
            matchCandidateRepository.deleteByQuizSetId(quizSetId)
            return
        }

        val participants = loadParticipants(quizSetId, availableMemberIds)
        val survivingDuos = processor.match(participants)

        matchCandidateRepository.deleteByQuizSetId(quizSetId)
        matchCandidateRepository.saveAll(toCandidates(quizSetId, survivingDuos))
    }

    /** 퀴즈를 완료(COMPLETED)한 참여자 중, 해당 매칭 타입의 제외 정책에 걸리지 않은 회원 */
    private fun findAvailableMembers(quizSetId: Long, matchingType: MatchingType): Set<Long> {
        val participantIds = quizProgressRepository
            .findByQuizSetIdAndStatus(quizSetId, QuizProgressStatus.COMPLETED)
            .map { it.memberId }
            .toSet()
        if (participantIds.isEmpty()) return emptySet()

        val excluded = exclusionPolicies
            .firstOrNull { it.matchingType == matchingType }
            ?.excludedMemberIds(quizSetId, participantIds)
            ?: emptySet()
        return participantIds - excluded
    }

    private fun loadParticipants(quizSetId: Long, memberIds: Set<Long>): List<MatchParticipant> {
        val quizIds = quizRepository.findByQuizSetIdInOrderByDisplayOrderAsc(listOf(quizSetId)).map { it.id }
        val answersByMember = quizAnswerRepository
            .findByMemberIdInAndQuizIdIn(memberIds.toList(), quizIds)
            .groupBy { it.memberId }
            .mapValues { (_, answers) -> answers.associate { it.quizId to it.choiceId } }
        return memberIds.map { memberId -> MatchParticipant(memberId, answersByMember[memberId].orEmpty()) }
    }

    /** 페어(A,B) 하나를 (A→B), (B→A) 두 방향 행으로 변환한다. */
    private fun toCandidates(quizSetId: Long, duos: List<ScoredDuo>): List<MatchCandidate> =
        duos.flatMap { duo ->
            listOf(
                MatchCandidate.create(
                    ownerMemberId = duo.memberId1,
                    otherMemberId = duo.memberId2,
                    quizSetId = quizSetId,
                    score = duo.score,
                    matchedQuestionCount = duo.matchedQuestionCount,
                    totalQuestionCount = duo.totalQuestionCount,
                ),
                MatchCandidate.create(
                    ownerMemberId = duo.memberId2,
                    otherMemberId = duo.memberId1,
                    quizSetId = quizSetId,
                    score = duo.score,
                    matchedQuestionCount = duo.matchedQuestionCount,
                    totalQuestionCount = duo.totalQuestionCount,
                ),
            )
        }
}
