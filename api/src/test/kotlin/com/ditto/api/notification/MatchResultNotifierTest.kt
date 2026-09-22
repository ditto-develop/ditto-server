package com.ditto.api.notification

import com.ditto.api.notification.notifier.MatchResultNotifier
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.match.MatchCandidateFixture
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

private const val QUIZ_SET = 1L
private const val MEMBER_A = 1L
private const val MEMBER_B = 2L
private const val MEMBER_C = 3L

class MatchResultNotifierTest(
    private val matchResultNotifier: MatchResultNotifier,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val quizSetRepository: QuizSetRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val memberRepository: MemberRepository,
    private val notificationRepository: NotificationRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveCandidatePair(quizSetId: Long = QUIZ_SET) {
        // 후보는 (A→B), (B→A) 두 방향 행으로 저장된다.
        matchCandidateRepository.save(
            MatchCandidateFixture.create(ownerMemberId = MEMBER_A, otherMemberId = MEMBER_B, quizSetId = quizSetId),
        )
        matchCandidateRepository.save(
            MatchCandidateFixture.create(ownerMemberId = MEMBER_B, otherMemberId = MEMBER_A, quizSetId = quizSetId),
        )
    }

    fun saveCompletedProgress(memberId: Long, quizSetId: Long) {
        quizProgressRepository.save(
            QuizProgressFixture.create(memberId = memberId, quizSetId = quizSetId, totalCount = 1)
                .apply { recordAnswer() },
        )
    }

    "후보가 생긴 회원에게 알린다" - {
        "쌍의 양쪽 모두 알림을 받는다" {
            saveCandidatePair()

            matchResultNotifier.notifyFor(listOf(QUIZ_SET)) shouldBe 2

            val notifications = notificationRepository.findAll()
            notifications.map { it.memberId }.toSet() shouldBe setOf(MEMBER_A, MEMBER_B)
            notifications.first().type shouldBe NotificationType.MATCH_RESULT
            notifications.first().title shouldBe "같은 답을 한 사람을 찾았어요"
            // 대상은 퀴즈셋이다 — "주마다 한 번"을 판정하는 기준이다.
            notifications.first().targetId shouldBe QUIZ_SET
        }

        "다시 불러도 중복으로 남지 않는다 — 어드민이 후보를 재생성할 수 있다" {
            saveCandidatePair()
            matchResultNotifier.notifyFor(listOf(QUIZ_SET))

            matchResultNotifier.notifyFor(listOf(QUIZ_SET)) shouldBe 0

            notificationRepository.count() shouldBe 2
        }

        "후보가 없는 퀴즈셋에는 알리지 않는다 — 빈 화면으로 보내지 않는다" {
            matchResultNotifier.notifyFor(listOf(QUIZ_SET)) shouldBe 0

            notificationRepository.count() shouldBe 0
        }

        "대상 퀴즈셋이 없으면 아무 것도 하지 않는다" {
            matchResultNotifier.notifyFor(emptyList()) shouldBe 0
        }
    }

    "퀴즈를 끝냈지만 후보가 없는 회원에게는 노매칭을 알린다" - {
        "매칭 풀에 들었으나 후보가 없는 회원만 받는다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())
            listOf(MEMBER_A, MEMBER_B, MEMBER_C).forEach { saveCompletedProgress(it, quizSet.id) }
            saveCandidatePair(quizSet.id)

            matchResultNotifier.notifyFor(listOf(quizSet.id)) shouldBe 3

            val noMatch = notificationRepository.findAll().single { it.type == NotificationType.NO_MATCH }
            noMatch.memberId shouldBe MEMBER_C
            noMatch.title shouldBe "이번 주는 답이 닿지 않았어요"
            noMatch.body shouldBe "다음 주에 새로운 질문으로 다시 찾아볼게요."
            noMatch.targetId shouldBe quizSet.id
        }

        "퀴즈를 끝내지 않은 회원은 받지 않는다 — 참여하지 않은 사람에게 '답이 닿지 않았다'는 성립하지 않는다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())
            quizProgressRepository.save(QuizProgressFixture.create(memberId = MEMBER_C, quizSetId = quizSet.id))

            matchResultNotifier.notifyFor(listOf(quizSet.id)) shouldBe 0

            notificationRepository.count() shouldBe 0
        }

        "매칭 풀에서 빠진 회원(탈퇴 등)은 받지 않는다 — 배치와 같은 제외 정책을 쓴다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())
            val left = memberRepository.save(MemberFixture.create(status = MemberStatus.LEFT))
            saveCompletedProgress(left.id, quizSet.id)

            matchResultNotifier.notifyFor(listOf(quizSet.id)) shouldBe 0

            notificationRepository.count() shouldBe 0
        }
    }
})
