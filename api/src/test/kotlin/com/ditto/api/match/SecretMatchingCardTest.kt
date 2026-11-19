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

class SecretMatchingCardTest : FreeSpec({
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

    fun progressCompletedAt(time: LocalDateTime): QuizProgress =
        QuizProgressFixture.create(totalCount = 1).apply { recordAnswer(time) }

    fun inProgress(): QuizProgress = QuizProgressFixture.create(totalCount = 2).apply { recordAnswer() }

    fun progressCompletedWithoutTime(): QuizProgress =
        QuizProgressFixture.create(totalCount = 1).apply { recordAnswer() }

    fun facts(
        now: LocalDateTime,
        oneToOneProgress: QuizProgress? = null,
        hasAcceptedOneToOneMatch: Boolean = false,
        isAcceptedInFormedGroup: Boolean = false,
        groupMatchingStatus: GroupMatchingStatus? = null,
        isUnderWarningSanction: Boolean = false,
        hasSecretCandidates: Boolean = false,
    ) = SecretMatchingFacts(
        now = now,
        week = week,
        oneToOneQuizSet = oneToOneQuizSet,
        oneToOneProgress = oneToOneProgress,
        hasAcceptedOneToOneMatch = hasAcceptedOneToOneMatch,
        isAcceptedInFormedGroup = isAcceptedInFormedGroup,
        groupMatchingStatus = groupMatchingStatus,
        isUnderWarningSanction = isUnderWarningSanction,
        hasSecretCandidates = hasSecretCandidates,
    )

    fun cardOf(facts: SecretMatchingFacts) = SecretMatchingCard.determine(facts)

    "카드를 띄우지 않는 경우" - {
        "응답 마감까지는 NONE 이다" {
            cardOf(facts(responseDeadline)) shouldBe SecretMatchingCard(SecretMatchingState.NONE, null)
        }

        "1:1 성사, 성사된 그룹 수락, 경고 제재 중이면 시크릿 후보가 있어도 NONE 이다" {
            listOf(
                facts(friday, hasAcceptedOneToOneMatch = true, hasSecretCandidates = true),
                facts(friday, isAcceptedInFormedGroup = true, hasSecretCandidates = true),
                facts(friday, isUnderWarningSanction = true, hasSecretCandidates = true),
            ).forEach { cardOf(it).state shouldBe SecretMatchingState.NONE }
        }

        "1:1 을 안 푼 그룹 완주자는 그룹 응답을 기다리는 동안 NONE 이다" {
            listOf(
                GroupMatchingStatus.NO_CANDIDATE_GROUPS,
                GroupMatchingStatus.NOT_RESPONDED,
                GroupMatchingStatus.WAITING_FOR_GROUP,
            ).forEach { cardOf(facts(thursday, groupMatchingStatus = it)).state shouldBe SecretMatchingState.NONE }
        }
    }

    "1:1 을 완주하지 않은 회원" - {
        "목요일부터 토 20:00 전까지 ELIGIBLE 이고 다음 라운드 시각을 준다" {
            cardOf(facts(thursday)) shouldBe SecretMatchingCard(SecretMatchingState.ELIGIBLE, firstRound)
            cardOf(facts(firstRound, oneToOneProgress = inProgress())) shouldBe
                SecretMatchingCard(SecretMatchingState.ELIGIBLE, secondRound)
        }

        "후보 그룹을 받지 못했거나 거절한 그룹 완주자는 목요일부터 ELIGIBLE 이다" {
            listOf(GroupMatchingStatus.NOT_INVITED, GroupMatchingStatus.DECLINED).forEach {
                cardOf(facts(thursday, groupMatchingStatus = it)).state shouldBe SecretMatchingState.ELIGIBLE
            }
        }

        "그룹 성사를 기다리던 그룹 완주자도 그룹 응답 마감부터 ELIGIBLE 이다" {
            val groupResponseDeadline = LocalDateTime.of(2026, 11, 20, 0, 0)
            cardOf(facts(groupResponseDeadline, groupMatchingStatus = GroupMatchingStatus.WAITING_FOR_GROUP)).state shouldBe
                SecretMatchingState.ELIGIBLE
        }

        "토 20:00 부터 ENDED 다" {
            cardOf(facts(secondRound)) shouldBe SecretMatchingCard(SecretMatchingState.ENDED, null)
        }
    }

    "1:1 을 늦게 완주한 회원" - {
        "금 20:00 전에 완주했으면 금 라운드까지 WAITING 이다" {
            val progress = progressCompletedAt(thursday)
            cardOf(facts(friday, oneToOneProgress = progress)) shouldBe
                SecretMatchingCard(SecretMatchingState.WAITING, firstRound)
        }

        "금 20:00 정각에 완주했으면 토 라운드를 기다린다" {
            val progress = progressCompletedAt(firstRound)
            cardOf(facts(firstRound, oneToOneProgress = progress)) shouldBe
                SecretMatchingCard(SecretMatchingState.WAITING, secondRound)
        }

        "자기 라운드가 지나고 후보가 없으면 토 20:00 전까지 AUTO_ENTERED 다" {
            val progress = progressCompletedAt(thursday)
            cardOf(facts(firstRound, oneToOneProgress = progress)) shouldBe
                SecretMatchingCard(SecretMatchingState.AUTO_ENTERED, secondRound)
        }
    }

    "1:1 을 마감 전에 완주한 회원" - {
        "목요일부터 토 20:00 전까지 AUTO_ENTERED 다" {
            val progress = progressCompletedAt(responseDeadline.minusHours(1))
            cardOf(facts(thursday, oneToOneProgress = progress)) shouldBe
                SecretMatchingCard(SecretMatchingState.AUTO_ENTERED, firstRound)
        }

        "완주 시각이 없는 진행(컬럼 전 완주·더미)도 마감 전 완주로 본다" {
            cardOf(facts(thursday, oneToOneProgress = progressCompletedWithoutTime())).state shouldBe
                SecretMatchingState.AUTO_ENTERED
        }

        "토 20:00 부터는 목요일 후보가 남아 있어도 ENDED 다" {
            val progress = progressCompletedAt(responseDeadline.minusHours(1))
            cardOf(facts(secondRound, oneToOneProgress = progress)) shouldBe
                SecretMatchingCard(SecretMatchingState.ENDED, null)
        }
    }

    "시크릿 후보가 있는 회원" - {
        "토 라운드 전이면 SECRET_RESULT 와 토 라운드 시각을 준다" {
            val progress = progressCompletedAt(thursday)
            cardOf(facts(firstRound, oneToOneProgress = progress, hasSecretCandidates = true)) shouldBe
                SecretMatchingCard(SecretMatchingState.SECRET_RESULT, secondRound)
        }

        "토 라운드 뒤에도 SECRET_RESULT 이고 남은 라운드가 없다" {
            val progress = progressCompletedAt(thursday)
            cardOf(facts(secondRound.plusHours(3), oneToOneProgress = progress, hasSecretCandidates = true)) shouldBe
                SecretMatchingCard(SecretMatchingState.SECRET_RESULT, null)
        }
    }
})
