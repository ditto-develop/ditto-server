package com.ditto.api.match.service

import com.ditto.api.match.exclusion.MatchExclusionPolicy
import com.ditto.api.match.matching.MatchParticipant
import com.ditto.api.match.matching.MatchingProcessor
import com.ditto.api.match.matching.ScoredMatch
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.entity.MatchCandidate
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.member.repository.MemberBlockRepository
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizProgress
import com.ditto.domain.quiz.entity.QuizProgressStatus
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 한 퀴즈셋의 매칭 후보를 계산해 저장한다. 여러 셋을 도는 배치는 [MatchingBatchFacade]가 맡는다.
 *
 * 흐름: 참여자(완료자) 풀 → 제외 정책 적용 → 답변 로드 → 매칭 전략 실행 → 후보 저장.
 * 매칭 타입(1:1/그룹)별 차이는 [MatchingProcessor] 와 [MatchExclusionPolicy] 가 담당한다.
 * 저장 위치도 타입마다 다르다 — 1:1은 페어 테이블(`match_candidate`), 그룹은 [GroupCandidateWriter].
 */
@Service
class MatchmakingService(
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val memberRepository: MemberRepository,
    private val memberBlockRepository: MemberBlockRepository,
    private val exclusionPolicies: List<MatchExclusionPolicy>,
    private val groupCandidateWriter: GroupCandidateWriter,
    private val matchingProcessors: List<MatchingProcessor>,
) {

    /**
     * 해당 퀴즈셋의 매칭 후보를 계산해 저장한다. 재계산 시 기존 후보를 모두 대체한다.
     *
     * 퀴즈셋 하나가 트랜잭션 하나다 — 배치([MatchingBatchFacade])가 셋별로 격리해 부른다. 배경: ADR 0026.
     *
     * @return 생성 결과 요약. 어드민 화면·REST 응답에 실리고 로그에도 남지만 저장하지는 않는다.
     * @throws WarnException 응답이 시작된 그룹 퀴즈셋처럼 대체할 수 없는 상태면 아무것도 바꾸지 않고 던진다
     */
    @Transactional
    fun generateMatchingCandidates(quizSetId: Long): CandidateGenerationSummary {
        val quizSet = quizSetRepository.findById(quizSetId).orElseThrow { WarnException(ErrorCode.NOT_FOUND) }
        val matchingType = quizSet.matchingType
        val processor = matchingProcessors.firstOrNull { it.matchingType == matchingType }
            ?: throw ErrorException(ErrorCode.INTERNAL_ERROR, "매칭 전략이 없는 타입입니다: $matchingType")

        // 완료자 진행 기록을 한 번만 조회해 성별 선호까지 함께 활용한다.
        val completedProgresses =
            quizProgressRepository.findByQuizSetIdAndStatus(quizSetId, QuizProgressStatus.COMPLETED)
        val availableMemberIds = availableMemberIds(quizSetId, matchingType, completedProgresses)
        val matches =
            if (availableMemberIds.size < 2) emptyList()
            else processor.match(loadParticipants(quizSetId, availableMemberIds, completedProgresses))

        val summary = CandidateGenerationSummary(
            quizSetId = quizSetId,
            matchingType = matchingType,
            participantCount = availableMemberIds.size,
            rowCounts = replaceCandidates(quizSetId, matchingType, matches),
            matches = matches,
        )
        logGeneration(summary)
        return summary
    }

    private fun logGeneration(summary: CandidateGenerationSummary) {
        logger.info {
            "매칭 후보 생성: quizSetId=${summary.quizSetId} type=${summary.matchingType} " +
                "참여자=${summary.participantCount}명 삭제=${summary.rowCounts.deletedCount}행 " +
                "저장=${summary.rowCounts.savedCount}행 매칭=${summary.matches.size}건 " +
                summary.matches.joinToString(prefix = "[", postfix = "]") { "${it.memberIds.sorted()}:${it.score}" }
        }
    }

    /** 후보를 담는 곳이 타입마다 다르다 — 1:1은 페어 2행, 그룹은 방 하나에 멤버 3~6행. */
    private fun replaceCandidates(
        quizSetId: Long,
        matchingType: MatchingType,
        matches: List<ScoredMatch>,
    ): CandidateRowCounts = when (matchingType) {
        MatchingType.ONE_TO_ONE -> {
            val deletedCount = matchCandidateRepository.deleteByQuizSetId(quizSetId)
            val saved = matchCandidateRepository.saveAll(toCandidates(quizSetId, matches))
            CandidateRowCounts(deletedCount = deletedCount, savedCount = saved.size)
        }

        MatchingType.GROUP -> groupCandidateWriter.replace(quizSetId, matches)
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
        val blockedIdsByMember = loadBlockedIdsByMember(memberIds)
        return memberIds.mapNotNull { memberId ->
            // 성별·나이 미상 회원도 풀에 넣는다. 그 조건을 쓰는 1:1은 프로세서가 자격 미달로 걸러내고,
            // 쓰지 않는 그룹은 그대로 후보가 된다.
            val member = membersById[memberId] ?: return@mapNotNull null
            MatchParticipant(
                memberId = memberId,
                answers = answersByMember[memberId].orEmpty(),
                gender = member.gender,
                age = member.age,
                // memberIds 는 completedProgresses 에서 유래하므로 선호값은 항상 존재한다(기본값은 QuizProgress 가 보유).
                preferredGender = preferenceByMember.getValue(memberId),
                blockedMemberIds = blockedIdsByMember[memberId].orEmpty(),
            )
        }
    }

    /**
     * 후보 풀 안에서 차단 관계인 상대를 회원별로 모은다(방향 무관).
     * 차단은 회원 전체를 풀에서 빼는 게 아니라 **그 페어만** 깨야 하므로 제외 정책이 아니라 여기서 다룬다.
     */
    private fun loadBlockedIdsByMember(memberIds: Set<Long>): Map<Long, Set<Long>> {
        if (memberIds.isEmpty()) return emptyMap()
        val blocks = memberBlockRepository.findAllInvolving(memberIds)
        if (blocks.isEmpty()) return emptyMap()

        val blockedIdsByMember = mutableMapOf<Long, MutableSet<Long>>()
        blocks.forEach { block ->
            blockedIdsByMember.getOrPut(block.blockerId) { mutableSetOf() }.add(block.blockedMemberId)
            blockedIdsByMember.getOrPut(block.blockedMemberId) { mutableSetOf() }.add(block.blockerId)
        }
        return blockedIdsByMember
    }

    private fun toCandidates(quizSetId: Long, duos: List<ScoredMatch>): List<MatchCandidate> =
        duos.flatMap { duo -> toBidirectionalCandidates(quizSetId, duo) }

    /**
     * 페어(A,B) 하나를 (A→B), (B→A) 두 방향 행으로 변환한다.
     *
     * 1:1 프로세서 결과만 들어오므로 구성원은 2명이고 점수 근거(문항 수)도 채워져 있다.
     * 비어 있으면 그룹 결과가 1:1 저장 경로로 흘러든 것이라 매칭 파이프라인 버그다.
     */
    private fun toBidirectionalCandidates(quizSetId: Long, duo: ScoredMatch): List<MatchCandidate> {
        val (ownerMemberId, otherMemberId) = duo.memberIds.sorted()
        val matchedQuestionCount = duo.matchedQuestionCount
            ?: throw ErrorException(ErrorCode.INTERNAL_ERROR, "1:1 후보에 점수 근거가 없습니다: $duo")
        val totalQuestionCount = duo.totalQuestionCount
            ?: throw ErrorException(ErrorCode.INTERNAL_ERROR, "1:1 후보에 점수 근거가 없습니다: $duo")

        return listOf(
            MatchCandidate.create(
                ownerMemberId = ownerMemberId,
                otherMemberId = otherMemberId,
                quizSetId = quizSetId,
                score = duo.score,
                matchedQuestionCount = matchedQuestionCount,
                totalQuestionCount = totalQuestionCount,
            ),
            MatchCandidate.create(
                ownerMemberId = otherMemberId,
                otherMemberId = ownerMemberId,
                quizSetId = quizSetId,
                score = duo.score,
                matchedQuestionCount = matchedQuestionCount,
                totalQuestionCount = totalQuestionCount,
            ),
        )
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
