package com.ditto.api.notification

import com.ditto.api.notification.notifier.QuizOpenedNotifier
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

/** QuizSetFixture 기본 기간(4/6 ~ 4/12) 안의 월요일 00:00. */
private val MONDAY_MIDNIGHT = LocalDateTime.of(2026, 4, 6, 0, 0)

class QuizOpenedNotifierTest(
    private val quizOpenedNotifier: QuizOpenedNotifier,
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val memberRepository: MemberRepository,
    private val notificationRepository: NotificationRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveMember(nickname: String, status: MemberStatus = MemberStatus.ACTIVE) =
        memberRepository.save(MemberFixture.create(nickname = nickname, email = "$nickname@ditto.pics", status = status))

    fun saveQuizSetWithQuizzes(quizCount: Int, matchingType: MatchingType = MatchingType.ONE_TO_ONE): Long {
        val quizSetId = quizSetRepository.save(QuizSetFixture.create(matchingType = matchingType)).id
        repeat(quizCount) { quizRepository.save(QuizFixture.create(quizSetId = quizSetId, displayOrder = it + 1)) }
        return quizSetId
    }

    "이번 주 퀴즈가 열리면 활성 회원에게 알린다" - {
        "활성 회원만 받고, 문항 수가 문구에 들어간다" {
            val quizSetId = saveQuizSetWithQuizzes(quizCount = 3)
            val active = listOf("a", "b").map { saveMember(it) }
            saveMember("left", status = MemberStatus.LEFT)
            saveMember("pending", status = MemberStatus.PENDING)

            quizOpenedNotifier.notifyOpened(MONDAY_MIDNIGHT) shouldBe 2

            val notifications = notificationRepository.findAll()
            notifications.map { it.memberId }.toSet() shouldBe active.map { it.id }.toSet()
            notifications.first().let {
                it.type shouldBe NotificationType.QUIZ_OPENED
                it.title shouldBe "이번 주 퀴즈가 열렸어요"
                it.body shouldBe "수요일 자정까지 3문항에 답하면 매칭이 시작돼요."
                it.targetId shouldBe quizSetId
            }
        }

        "다시 불러도 주에 한 번이다" {
            saveQuizSetWithQuizzes(quizCount = 1)
            saveMember("a")
            quizOpenedNotifier.notifyOpened(MONDAY_MIDNIGHT)

            quizOpenedNotifier.notifyOpened(MONDAY_MIDNIGHT.plusMinutes(1)) shouldBe 0

            notificationRepository.count() shouldBe 1
        }

        "1:1·그룹 셋이 함께 열려도 알림은 하나고 대상은 id 가 작은 셋이다" {
            val first = saveQuizSetWithQuizzes(quizCount = 1, matchingType = MatchingType.ONE_TO_ONE)
            saveQuizSetWithQuizzes(quizCount = 1, matchingType = MatchingType.GROUP)
            saveMember("a")

            quizOpenedNotifier.notifyOpened(MONDAY_MIDNIGHT) shouldBe 1

            notificationRepository.findAll().single().targetId shouldBe first
        }

        "이번 주 활성 셋이 없으면 알리지 않는다" {
            saveMember("a")

            quizOpenedNotifier.notifyOpened(MONDAY_MIDNIGHT) shouldBe 0
        }

        "문항이 없는 셋이면 알리지 않는다" {
            saveQuizSetWithQuizzes(quizCount = 0)
            saveMember("a")

            quizOpenedNotifier.notifyOpened(MONDAY_MIDNIGHT) shouldBe 0
        }
    }
})
