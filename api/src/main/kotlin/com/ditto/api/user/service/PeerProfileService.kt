package com.ditto.api.user.service

import com.ditto.api.match.matching.MatchScoreCalculator
import com.ditto.api.user.dto.AnswerMatchResponse
import com.ditto.api.user.dto.MyRatingsResponse
import com.ditto.domain.quiz.entity.QuizAnswer
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 타인 프로필 화면의 보조 정보 — 상대가 받은 평가와 "나와 같은 답" 비교.
 *
 * 열람 권한은 공개 프로필과 같은 규칙을 쓴다([UserService.checkProfileAccess]) — 프로필 본문은 못 보는데
 * 평점·답변 비교만 보이는 구멍이 생기지 않게 한 지점에서 판정한다.
 */
@Service
class PeerProfileService(
    private val userService: UserService,
    private val memberRatingService: MemberRatingService,
    private val quizProgressRepository: QuizProgressRepository,
    private val quizRepository: QuizRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
) {

    @Transactional(readOnly = true)
    fun getRatings(viewerId: Long, targetId: Long): MyRatingsResponse {
        userService.checkProfileAccess(viewerId, targetId)

        return memberRatingService.getRatings(targetId)
    }

    /**
     * 두 사람이 **함께 완주한 가장 최근 퀴즈셋**을 기준으로 답변 일치를 센다.
     * 둘 다 완주해야 문항 수가 같아 비교가 성립하고, 매칭 주에는 이것이 곧 "이번 주 퀴즈"다.
     * 함께 완주한 셋이 없으면 빈 요약을 반환한다 — 화면은 배지를 감춘다.
     */
    @Transactional(readOnly = true)
    fun getAnswerMatch(viewerId: Long, targetId: Long): AnswerMatchResponse {
        userService.checkProfileAccess(viewerId, targetId)

        val quizSetId = quizProgressRepository.findLatestQuizSetIdCompletedByBoth(viewerId, targetId)
            ?: return AnswerMatchResponse.empty()

        val quizIds = quizRepository.findByQuizSetIdInOrderByDisplayOrderAsc(listOf(quizSetId)).map { it.id }
        if (quizIds.isEmpty()) return AnswerMatchResponse.empty()

        val answersByMemberId = quizAnswerRepository
            .findByMemberIdInAndQuizIdIn(listOf(viewerId, targetId), quizIds)
            .groupBy { it.memberId }

        val score = MatchScoreCalculator.calculate(
            answersByMemberId[viewerId].toChoiceByQuizId(),
            answersByMemberId[targetId].toChoiceByQuizId(),
        )

        return AnswerMatchResponse(
            quizSetId = quizSetId,
            matchedCount = score.matchedQuestionCount,
            totalCount = score.totalQuestionCount,
            matchRate = score.score,
        )
    }

    private fun List<QuizAnswer>?.toChoiceByQuizId(): Map<Long, Long> =
        orEmpty().associate { it.quizId to it.choiceId }
}
