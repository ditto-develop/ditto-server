package com.ditto.api.match.service

import com.ditto.api.match.exclusion.MatchExclusionPolicy
import com.ditto.api.match.matching.MatchParticipant
import com.ditto.api.match.matching.MatchingProcessor
import com.ditto.api.match.matching.ScoredDuo
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.entity.MatchCandidate
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizProgress
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
    private val memberRepository: MemberRepository,
    private val exclusionPolicies: List<MatchExclusionPolicy>,
    private val matchingProcessors: List<MatchingProcessor>,
) {

    /** 해당 퀴즈셋의 매칭 후보를 계산해 저장한다. 재계산 시 기존 후보를 모두 대체한다. */
    @Transactional
    fun generateMatchingCandidates(quizSetId: Long) {
        val quizSet = quizSetRepository.findById(quizSetId).orElseThrow { WarnException(ErrorCode.NOT_FOUND) }
        val matchingType = quizSet.matchingType
        val processor = matchingProcessors.firstOrNull { it.matchingType == matchingType } ?: return

        // 완료자 진행 기록을 한 번만 조회해 성별 선호까지 함께 활용한다.
        val completedProgresses =
            quizProgressRepository.findByQuizSetIdAndStatus(quizSetId, QuizProgressStatus.COMPLETED)
        val availableMemberIds = availableMemberIds(quizSetId, matchingType, completedProgresses)
        if (availableMemberIds.size < 2) {
            matchCandidateRepository.deleteByQuizSetId(quizSetId)
            return
        }

        val participants = loadParticipants(quizSetId, availableMemberIds, completedProgresses)
        val survivingDuos = processor.match(participants)

        matchCandidateRepository.deleteByQuizSetId(quizSetId)
        matchCandidateRepository.saveAll(toCandidates(quizSetId, survivingDuos))
    }

    /** 완료자 중 해당 매칭 타입의 제외 정책에 걸리지 않은 회원 */
    private fun availableMemberIds(
        quizSetId: Long,
        matchingType: MatchingType,
        completedProgresses: List<QuizProgress>,
    ): Set<Long> {
        val participantMemberIds = completedProgresses.map { it.memberId }.toSet()

        if (participantMemberIds.isEmpty()) return emptySet()

        val excludedMemberIds = exclusionPolicies
            .firstOrNull { it.matchingType == matchingType }
            ?.excludedMemberIds(quizSetId, participantMemberIds)
            ?: emptySet()

        return participantMemberIds - excludedMemberIds
    }

    private fun loadParticipants(
        quizSetId: Long,
        memberIds: Set<Long>,
        completedProgresses: List<QuizProgress>,
    ): List<MatchParticipant> {
        val quizIds = quizRepository.findByQuizSetIdInOrderByDisplayOrderAsc(listOf(quizSetId)).map { it.id }
        val answersByMember = quizAnswerRepository
            .findByMemberIdInAndQuizIdIn(memberIds.toList(), quizIds)
            .groupBy { it.memberId }
            .mapValues { (_, answers) -> answers.associate { it.quizId to it.choiceId } }
        val membersById = memberRepository.findAllById(memberIds).associateBy { it.id }
        val preferenceByMember = completedProgresses.associate { it.memberId to it.preferredGender }
        return memberIds.mapNotNull { memberId ->
            // 성별·나이 미상 회원은 성별·나이 기반 매칭이 불가하므로 후보 풀에서 제외한다.
            val member = membersById[memberId] ?: return@mapNotNull null
            val gender = member.gender ?: return@mapNotNull null
            val age = member.age ?: return@mapNotNull null
            MatchParticipant(
                memberId = memberId,
                answers = answersByMember[memberId].orEmpty(),
                gender = gender,
                age = age,
                // memberIds 는 completedProgresses 에서 유래하므로 선호값은 항상 존재한다(기본값은 QuizProgress 가 보유).
                preferredGender = preferenceByMember.getValue(memberId),
            )
        }
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
