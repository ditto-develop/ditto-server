package com.ditto.api.match.service

import com.ditto.api.match.SecretMatchingSchedule
import com.ditto.api.match.exclusion.MatchExclusionPolicy
import com.ditto.api.match.matching.MatchParticipant
import com.ditto.api.match.matching.MatchingProcessor
import com.ditto.api.match.matching.ScoredMatch
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.entity.CandidateOrigin
import com.ditto.domain.match.entity.MatchCandidate
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.repository.MemberBlockRepository
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizProgress
import com.ditto.domain.quiz.entity.QuizProgressStatus
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.time.Duration
import kotlin.time.measureTimedValue

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
    private val personalMatchRepository: PersonalMatchRepository,
) {

    /**
     * 해당 퀴즈셋의 매칭 후보를 계산해 저장한다. 재계산 시 기존 후보를 모두 대체한다.
     *
     * 퀴즈셋 하나가 트랜잭션 하나다 — 배치([MatchingBatchFacade])가 셋별로 격리해 부른다. 배경: ADR 0027.
     *
     * @return 생성 결과 요약. 어드민 화면·REST 응답에 실리고 로그에도 남지만 저장하지는 않는다.
     * @throws WarnException 응답이 시작된 그룹 퀴즈셋, 시크릿 매칭 후보가 생긴 1:1 퀴즈셋처럼 대체할 수 없는 상태면
     *   아무것도 바꾸지 않고 던진다
     */
    @Transactional
    fun generateMatchingCandidates(quizSetId: Long): CandidateGenerationSummary {
        val quizSet = quizSetRepository.findWithLockById(quizSetId) ?: throw WarnException(ErrorCode.NOT_FOUND)
        validateNoSecretRoundCandidates(quizSet)
        val matchingType = quizSet.matchingType
        val processor = matchingProcessors.firstOrNull { it.matchingType == matchingType }
            ?: throw ErrorException(ErrorCode.INTERNAL_ERROR, "매칭 전략이 없는 타입입니다: $matchingType")

        val pool = findRegularPool(quizSet)
        val participants = if (pool.memberIds.size < 2) emptyList() else loadParticipants(pool)
        val (matches, matchingDuration) = measureTimedValue { processor.match(participants) }

        val summary = CandidateGenerationSummary(
            quizSetId = quizSetId,
            matchingType = matchingType,
            participantCount = pool.memberIds.size,
            rowCounts = replaceCandidates(quizSetId, matchingType, matches),
            matches = matches,
        )
        logGeneration(summary, pool.completedAfterCutoffCount, matchingDuration)
        return summary
    }

    /**
     * 시크릿 매칭 라운드 하나를 이 셋에 돌려 1:1 후보를 더한다. 기존 후보는 지우지 않는다.
     * [secretRound]는 SECRET_FIRST·SECRET_SECOND 만 받는다. 같은 라운드 후보가 이미 있으면 건너뛴다.
     */
    @Transactional
    fun generateSecretRoundCandidates(quizSetId: Long, secretRound: CandidateOrigin): SecretRoundResult {
        val quizSet = quizSetRepository.findWithLockById(quizSetId) ?: throw WarnException(ErrorCode.NOT_FOUND)
        if (matchCandidateRepository.existsByQuizSetIdAndCandidateOrigin(quizSetId, secretRound)) {
            return SecretRoundResult.skipped(quizSetId, secretRound)
        }

        val roundStartsAt = SecretMatchingSchedule.roundStartsAt(secretRound, quizSet.operationWeek)
        val pool = findPool(quizSet) { it.isCompletedBefore(roundStartsAt) }
        val participants = if (pool.memberIds.size < 2) emptyList() else loadSecretRoundParticipants(pool)
        val matches = oneToOneProcessor().match(participants)
        val saved = matchCandidateRepository.saveAll(toCandidates(quizSetId, matches, secretRound))

        val result = SecretRoundResult(
            quizSetId = quizSetId,
            secretRound = secretRound,
            alreadyGenerated = false,
            poolSize = pool.memberIds.size,
            savedCount = saved.size,
            newCandidateOwnerIds = saved.mapTo(mutableSetOf()) { it.ownerMemberId },
        )
        logSecretRound(result, participants, pool.completedAfterCutoffCount)
        return result
    }

    /**
     * 매칭 풀에 든 활성 회원. 그룹에는 제외 정책이 없어 정지·탈퇴 회원이 풀에 남기 때문에 활성 회원만 남긴다.
     * NO_MATCH 알림의 모집단으로 쓴다. 퀴즈셋이 없으면 빈 집합.
     */
    @Transactional(readOnly = true)
    fun matchingPoolMemberIds(quizSetId: Long): Set<Long> {
        val quizSet = quizSetRepository.findById(quizSetId).orElse(null) ?: return emptySet()
        return memberRepository.findAllById(findRegularPool(quizSet).memberIds)
            .filter { it.isActive() }
            .map { it.id }
            .toSet()
    }

    /** 매칭 풀 참여자(답변·성별·선호·차단 포함). 어드민이 후보가 없는 이유를 다시 계산할 때 쓴다. 퀴즈셋이 없으면 빈 목록. */
    @Transactional(readOnly = true)
    fun loadMatchingPoolParticipants(quizSetId: Long): List<MatchParticipant> {
        val quizSet = quizSetRepository.findById(quizSetId).orElse(null) ?: return emptyList()
        val pool = findRegularPool(quizSet)
        if (pool.memberIds.isEmpty()) return emptyList()
        return loadParticipants(pool)
    }

    private fun logGeneration(
        summary: CandidateGenerationSummary,
        lateCompletedCount: Int,
        matchingDuration: Duration,
    ) {
        logger.info {
            "매칭 후보 생성: quizSetId=${summary.quizSetId} type=${summary.matchingType} " +
                "참여자=${summary.participantCount}명 늦은완주제외=${lateCompletedCount}명 삭제=${summary.rowCounts.deletedCount}행 " +
                "저장=${summary.rowCounts.savedCount}행 매칭=${summary.matches.size}건 " +
                "계산=${matchingDuration.inWholeMilliseconds}ms"
        }
    }

    private fun logSecretRound(
        result: SecretRoundResult,
        participants: List<MatchParticipant>,
        completedAfterRoundCount: Int,
    ) {
        val genderCounts = participants.groupingBy { it.gender }.eachCount()
        val withoutNewCandidate = result.poolSize - result.newCandidateOwnerIds.size
        logger.info {
            "시크릿 매칭 후보 생성: quizSetId=${result.quizSetId} round=${result.secretRound} " +
                "풀=${result.poolSize}명 성별=$genderCounts 라운드뒤완주제외=${completedAfterRoundCount}명 " +
                "새후보없음=${withoutNewCandidate}명 저장=${result.savedCount}행"
        }
    }

    // 시크릿 후보가 생긴 뒤 정규 후보를 지우고 다시 뽑으면 열린 결과 화면의 후보가 바뀐다.
    private fun validateNoSecretRoundCandidates(quizSet: QuizSet) {
        if (quizSet.matchingType != MatchingType.ONE_TO_ONE) return
        if (matchCandidateRepository.existsByQuizSetIdAndCandidateOriginNot(quizSet.id, CandidateOrigin.REGULAR)) {
            throw WarnException(ErrorCode.MATCH_CANDIDATES_SECRET_ROUND_STARTED)
        }
    }

    private fun oneToOneProcessor(): MatchingProcessor =
        matchingProcessors.firstOrNull { it.matchingType == MatchingType.ONE_TO_ONE }
            ?: throw ErrorException(ErrorCode.INTERNAL_ERROR, "1:1 매칭 전략이 없습니다")

    /** 후보를 담는 곳이 타입마다 다르다 — 1:1은 페어 2행, 그룹은 방 하나에 멤버 3~6행. */
    private fun replaceCandidates(
        quizSetId: Long,
        matchingType: MatchingType,
        matches: List<ScoredMatch>,
    ): CandidateRowCounts = when (matchingType) {
        MatchingType.ONE_TO_ONE -> {
            val deletedCount = matchCandidateRepository.deleteByQuizSetId(quizSetId)
            val saved = matchCandidateRepository.saveAll(toCandidates(quizSetId, matches, CandidateOrigin.REGULAR))
            CandidateRowCounts(deletedCount = deletedCount, savedCount = saved.size)
        }

        MatchingType.GROUP -> groupCandidateWriter.replace(quizSetId, matches)
    }

    // 정규 배치·NO_MATCH 알림·어드민 재계산이 모두 이 풀을 쓴다. 한쪽만 바뀌면 화면 이유가 배치와 어긋난다.
    private fun findRegularPool(quizSet: QuizSet): MatchingPool =
        findPool(quizSet) { it.isCompletedOnTime(quizSet.endDate) }

    private fun findPool(quizSet: QuizSet, isInPool: (QuizProgress) -> Boolean): MatchingPool {
        val (completedProgresses, completedAfterCutoffProgresses) =
            quizProgressRepository.findByQuizSetIdAndStatus(quizSet.id, QuizProgressStatus.COMPLETED)
                .partition(isInPool)
        val memberIds = availableMemberIds(quizSet.id, quizSet.matchingType, completedProgresses)
        return MatchingPool(quizSet.id, completedProgresses, memberIds, completedAfterCutoffProgresses.size)
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

    private fun loadParticipants(pool: MatchingPool): List<MatchParticipant> {
        val (quizSetId, completedProgresses, memberIds) = pool
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

    private fun loadSecretRoundParticipants(pool: MatchingPool): List<MatchParticipant> {
        val excludedPartnerIdsByMember = loadExcludedPartnerIdsByMember(pool.quizSetId, pool.memberIds)
        return loadParticipants(pool).map {
            it.copy(excludedPartnerIds = excludedPartnerIdsByMember[it.memberId].orEmpty())
        }
    }

    /**
     * 이 셋에서 이미 후보였거나 신청을 거절한 적이 있는 상대를 회원별로 모은다(방향 무관).
     * 대기·자동 취소 신청 쌍은 넣지 않는다.
     */
    private fun loadExcludedPartnerIdsByMember(quizSetId: Long, memberIds: Set<Long>): Map<Long, Set<Long>> {
        val candidatePairs = matchCandidateRepository.findByQuizSetIdInAndOwnerMemberIdIn(listOf(quizSetId), memberIds)
            .map { it.ownerMemberId to it.otherMemberId }
        val rejectedPairs = personalMatchRepository.findByQuizSetIdAndStatus(quizSetId, PersonalMatchStatus.REJECTED)
            .map { it.memberId1 to it.memberId2 }

        val excludedPartnerIdsByMember = mutableMapOf<Long, MutableSet<Long>>()
        (candidatePairs + rejectedPairs).forEach { (memberA, memberB) ->
            excludedPartnerIdsByMember.getOrPut(memberA) { mutableSetOf() }.add(memberB)
            excludedPartnerIdsByMember.getOrPut(memberB) { mutableSetOf() }.add(memberA)
        }
        return excludedPartnerIdsByMember
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

    private fun toCandidates(
        quizSetId: Long,
        duos: List<ScoredMatch>,
        origin: CandidateOrigin,
    ): List<MatchCandidate> =
        duos.flatMap { duo -> toBidirectionalCandidates(quizSetId, duo, origin) }

    /**
     * 페어(A,B) 하나를 (A→B), (B→A) 두 방향 행으로 변환한다.
     *
     * 1:1 프로세서 결과만 들어오므로 구성원은 2명이고 점수 근거(문항 수)도 채워져 있다.
     * 비어 있으면 그룹 결과가 1:1 저장 경로로 흘러든 것이라 매칭 파이프라인 버그다.
     */
    private fun toBidirectionalCandidates(
        quizSetId: Long,
        duo: ScoredMatch,
        origin: CandidateOrigin,
    ): List<MatchCandidate> {
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
                candidateOrigin = origin,
            ),
            MatchCandidate.create(
                ownerMemberId = otherMemberId,
                otherMemberId = ownerMemberId,
                quizSetId = quizSetId,
                score = duo.score,
                matchedQuestionCount = matchedQuestionCount,
                totalQuestionCount = totalQuestionCount,
                candidateOrigin = origin,
            ),
        )
    }

    private data class MatchingPool(
        val quizSetId: Long,
        val completedProgresses: List<QuizProgress>,
        val memberIds: Set<Long>,
        val completedAfterCutoffCount: Int,
    )

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
