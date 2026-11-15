package com.ditto.api.match

import com.ditto.api.match.service.MatchingBatchFacade
import com.ditto.api.match.service.MatchmakingService
import com.ditto.api.match.service.SecretRoundResult
import com.ditto.api.sanction.service.SanctionExpiryService
import com.ditto.api.system.featureflag.FeatureFlags
import com.ditto.domain.match.entity.CandidateOrigin
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.system.entity.FeatureFlagKey
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate

class MatchingBatchFacadeSecretRoundSkipTest : FreeSpec({
    val monday = LocalDate.of(2026, 11, 16)
    val fridayRun = monday.plusDays(4).atTime(20, 1)

    fun weekQuizSet(id: Long) = QuizSetFixture.create(
        startDate = monday.atStartOfDay(),
        endDate = monday.plusDays(2).atTime(23, 59, 59),
        id = id,
    )

    "정규 생성이 이번에도 실패한 셋은 시크릿 라운드에서 건너뛰고 실패로 알린다" {
        val failedRegularSet = weekQuizSet(10L)
        val healthySet = weekQuizSet(11L)
        val quizSetRepository = mockk<QuizSetRepository> {
            every { findEndedQuizSetsWithoutCandidates(any(), any()) } returns listOf(failedRegularSet)
            every { findByWeekStartedOn(monday) } returns listOf(failedRegularSet, healthySet)
        }
        val matchmakingService = mockk<MatchmakingService> {
            every { generateMatchingCandidates(10L) } throws IllegalStateException("잠금 대기 시간 초과")
            every { generateSecretRoundCandidates(11L, CandidateOrigin.SECRET_FIRST) } returns
                SecretRoundResult(11L, CandidateOrigin.SECRET_FIRST, false, 2, 2, setOf(1L, 2L))
        }
        val featureFlags = mockk<FeatureFlags> { every { isEnabled(FeatureFlagKey.SECRET_MATCHING) } returns true }
        val facade = MatchingBatchFacade(quizSetRepository, matchmakingService, mockk<SanctionExpiryService>(relaxed = true), featureFlags)

        val result = facade.runSecretRound(fridayRun)

        result.roundResults.map { it.quizSetId } shouldBe listOf(11L)
        result.failedQuizSetIds shouldBe listOf(10L)
        verify(exactly = 0) { matchmakingService.generateSecretRoundCandidates(10L, any()) }
    }
})
