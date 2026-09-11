package com.ditto.api.match.service

import com.ditto.api.match.dto.Candidate
import com.ditto.api.match.dto.MatchCandidateResponse
import com.ditto.api.match.matching.MatchScore
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.common.exception.WarnException
import com.ditto.domain.intronote.entity.IntroQuestion
import com.ditto.domain.intronote.repository.IntroNoteRepository
import com.ditto.domain.match.entity.MatchCandidate
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.repository.QuizSetRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class MatchCandidateService(
    private val quizSetRepository: QuizSetRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val memberRepository: MemberRepository,
    private val introNoteRepository: IntroNoteRepository,
) {

    /**
     * 회원의 1:1 추천 후보 목록 조회.
     * 후보는 마감된 퀴즈셋에만 생성되므로, 회원이 최근 완료(COMPLETED)한 1:1 퀴즈셋을 기준으로 후보를 조회한다.
     * 참여한 1:1 퀴즈셋이 없으면 NOT_FOUND.
     */
    fun getMatchCandidates(memberId: Long): MatchCandidateResponse {
        val quizSet = quizSetRepository.findLatestCompletedQuizSet(memberId, MatchingType.ONE_TO_ONE)
            ?: throw WarnException(ErrorCode.NOT_FOUND)

        val matchCandidates = matchCandidateRepository
            .findByOwnerMemberIdAndQuizSetId(memberId, quizSet.id)
            // 점수 내림차순, 동점이면 otherMemberId 오름차순으로 노출 순서를 결정적으로 고정
            .sortedWith(compareByDescending<MatchCandidate> { it.score }.thenBy { it.otherMemberId })

        return MatchCandidateResponse(
            quizSetId = quizSet.id,
            matchingType = quizSet.matchingType,
            algorithmVersion = ALGORITHM_VERSION,
            candidates = toCandidates(matchCandidates),
        )
    }

    private fun toCandidates(matchCandidates: List<MatchCandidate>): List<Candidate> {
        if (matchCandidates.isEmpty()) return emptyList()

        val otherMemberIds = matchCandidates.map { it.otherMemberId }
        val membersById = memberRepository.findAllById(otherMemberIds).associateBy { it.id }
        val introductionsByMemberId = loadOneWordIntroductions(otherMemberIds)

        return matchCandidates.mapNotNull { matchCandidate ->
            val member = membersById[matchCandidate.otherMemberId] ?: return@mapNotNull null
            toCandidate(matchCandidate, member, introductionsByMemberId[matchCandidate.otherMemberId])
        }
    }

    /** 후보들의 소개노트 ONE_WORD 답변을 한 번에 조회해 회원ID로 매핑한다. 공백 답변은 제외. */
    private fun loadOneWordIntroductions(memberIds: List<Long>): Map<Long, String> =
        introNoteRepository
            .findByMemberIdInAndQuestion(memberIds, IntroQuestion.ONE_WORD)
            .mapNotNull { note -> note.answer.ifBlank { null }?.let { note.memberId to it } }
            .toMap()

    private fun toCandidate(
        matchCandidate: MatchCandidate,
        member: Member,
        introduction: String?,
    ): Candidate = Candidate.of(
        member = member,
        introduction = introduction,
        matchScore = MatchScore(
            score = matchCandidate.score,
            matchedQuestionCount = matchCandidate.matchedQuestionCount,
            totalQuestionCount = matchCandidate.totalQuestionCount,
        ),
    )

    companion object {
        // TODO: 알고리즘 버전이 match_candidate 에 영속화되면 그 값을 사용한다. 현재는 고정 상수.
        private const val ALGORITHM_VERSION = "1.0"
    }
}
