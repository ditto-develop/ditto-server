package com.ditto.api.match

import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizProgress
import com.ditto.domain.system.OperationWeek
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

class SecretMatchingStageTest : FreeSpec({
    // 2026-11-16(월) 시작 주. 응답 마감 수 23:59:59, 그룹 응답 마감 금 00:00, 라운드 금·토 20:00.
    val week = OperationWeek(LocalDate.of(2026, 11, 16))
    val responseDeadline = LocalDateTime.of(2026, 11, 18, 23, 59, 59)
    val thursday = LocalDateTime.of(2026, 11, 19, 12, 0)
    val friday = LocalDateTime.of(2026, 11, 20, 12, 0)
    val firstRound = LocalDateTime.of(2026, 11, 20, 20, 0)
    val secondRound = LocalDateTime.of(2026, 11, 21, 20, 0)
    val oneToOneQuizSet = QuizSetFixture.create(
        startDate = week.startedOn.atStartOfDay(),
        endDate = responseDeadline,
        matchingType = MatchingType.ONE_TO_ONE,
    )

    fun completedAt(time: LocalDateTime): QuizProgress =
        QuizProgressFixture.create(totalCount = 1).apply { recordAnswer(time) }

    fun inProgress(): QuizProgress = QuizProgressFixture.create(totalCount = 2).apply { recordAnswer() }

    fun facts(
        now: LocalDateTime,
        secretMatchingEnabled: Boolean = true,
        hasOneToOneQuizSet: Boolean = true,
        oneToOneProgress: QuizProgress? = null,
        hasOneToOneAcceptance: Boolean = false,
        isAcceptedInFormedGroup: Boolean = false,
        hasCompletedGroupQuiz: Boolean = false,
        isQuizBlocked: Boolean = false,
        hasSecretCandidates: Boolean = false,
    ) = SecretMatchingFacts(
        now = now,
        week = week,
        secretMatchingEnabled = secretMatchingEnabled,
        oneToOneQuizSet = oneToOneQuizSet.takeIf { hasOneToOneQuizSet },
        oneToOneProgress = oneToOneProgress,
        hasOneToOneAcceptance = hasOneToOneAcceptance,
        isAcceptedInFormedGroup = isAcceptedInFormedGroup,
        hasCompletedGroupQuiz = hasCompletedGroupQuiz,
        isQuizBlocked = isQuizBlocked,
        hasSecretCandidates = hasSecretCandidates,
    )

    fun stageOf(facts: SecretMatchingFacts) = SecretMatchingStage.determine(facts)

    "카드를 띄우지 않는 경우" - {
        "플래그가 꺼져 있으면 NONE 이다" {
            stageOf(facts(friday, secretMatchingEnabled = false)) shouldBe SecretMatchingStage(SecretMatchingState.NONE, null)
        }

        "응답 마감까지는 NONE 이다" {
            stageOf(facts(responseDeadline)).state shouldBe SecretMatchingState.NONE
        }

        "이번 주 1:1 셋이 없으면 NONE 이다" {
            stageOf(facts(friday, hasOneToOneQuizSet = false)).state shouldBe SecretMatchingState.NONE
        }

        "1:1 성사, 성사된 그룹 수락, 경고 제재 중이면 시크릿 후보가 있어도 NONE 이다" {
            stageOf(facts(friday, hasOneToOneAcceptance = true, hasSecretCandidates = true)).state shouldBe SecretMatchingState.NONE
            stageOf(facts(friday, isAcceptedInFormedGroup = true)).state shouldBe SecretMatchingState.NONE
            stageOf(facts(friday, isQuizBlocked = true)).state shouldBe SecretMatchingState.NONE
        }

        "1:1 을 안 푼 그룹 완주자는 그룹 응답 마감 전까지 NONE 이다" {
            stageOf(facts(thursday, hasCompletedGroupQuiz = true)).state shouldBe SecretMatchingState.NONE
        }
    }

    "1:1 을 완주하지 않은 회원" - {
        "목요일부터 토 20:00 전까지 ELIGIBLE 이고 다음 라운드 시각을 준다" {
            stageOf(facts(thursday)) shouldBe SecretMatchingStage(SecretMatchingState.ELIGIBLE, firstRound)
            stageOf(facts(firstRound, oneToOneProgress = inProgress())) shouldBe SecretMatchingStage(SecretMatchingState.ELIGIBLE, secondRound)
        }

        "그룹 완주자도 그룹 응답 마감부터 ELIGIBLE 이다" {
            stageOf(facts(LocalDateTime.of(2026, 11, 20, 0, 0), hasCompletedGroupQuiz = true)).state shouldBe SecretMatchingState.ELIGIBLE
        }

        "토 20:00 부터 ENDED 다" {
            stageOf(facts(secondRound)) shouldBe SecretMatchingStage(SecretMatchingState.ENDED, null)
        }
    }

    "1:1 을 늦게 완주한 회원" - {
        "금 20:00 전에 완주했으면 금 라운드까지 WAITING 이다" {
            val progress = completedAt(thursday)
            stageOf(facts(friday, oneToOneProgress = progress)) shouldBe SecretMatchingStage(SecretMatchingState.WAITING, firstRound)
        }

        "금 20:00 정각에 완주했으면 토 라운드를 기다린다" {
            val progress = completedAt(firstRound)
            stageOf(facts(firstRound, oneToOneProgress = progress)) shouldBe SecretMatchingStage(SecretMatchingState.WAITING, secondRound)
        }

        "자기 라운드가 지나고 후보가 없으면 토 20:00 전까지 AUTO_ENTERED 다" {
            val progress = completedAt(thursday)
            stageOf(facts(firstRound, oneToOneProgress = progress)) shouldBe SecretMatchingStage(SecretMatchingState.AUTO_ENTERED, secondRound)
        }
    }

    "1:1 을 마감 전에 완주한 회원" - {
        "목요일부터 토 20:00 전까지 AUTO_ENTERED 다" {
            val progress = completedAt(responseDeadline.minusHours(1))
            stageOf(facts(thursday, oneToOneProgress = progress)) shouldBe SecretMatchingStage(SecretMatchingState.AUTO_ENTERED, firstRound)
        }

        "완주 시각이 없는 진행(컬럼 전 완주·더미)도 마감 전 완주로 본다" {
            val progress = QuizProgressFixture.create(totalCount = 1).apply { recordAnswer() }
            stageOf(facts(thursday, oneToOneProgress = progress)).state shouldBe SecretMatchingState.AUTO_ENTERED
        }

        "토 20:00 부터는 목요일 후보가 남아 있어도 ENDED 다" {
            val progress = completedAt(responseDeadline.minusHours(1))
            stageOf(facts(secondRound, oneToOneProgress = progress)) shouldBe SecretMatchingStage(SecretMatchingState.ENDED, null)
        }
    }

    "시크릿 후보가 있는 회원" - {
        "토 라운드 전이면 SECRET_RESULT 와 토 라운드 시각을 준다" {
            val progress = completedAt(thursday)
            stageOf(facts(firstRound, oneToOneProgress = progress, hasSecretCandidates = true)) shouldBe
                SecretMatchingStage(SecretMatchingState.SECRET_RESULT, secondRound)
        }

        "토 라운드 뒤에도 SECRET_RESULT 이고 남은 라운드가 없다" {
            val progress = completedAt(thursday)
            stageOf(facts(secondRound.plusHours(3), oneToOneProgress = progress, hasSecretCandidates = true)) shouldBe
                SecretMatchingStage(SecretMatchingState.SECRET_RESULT, null)
        }
    }
})
