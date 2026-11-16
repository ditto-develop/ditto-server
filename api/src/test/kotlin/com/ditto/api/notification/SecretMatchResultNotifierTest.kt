package com.ditto.api.notification

import com.ditto.api.match.service.SecretRoundResult
import com.ditto.api.notification.notifier.SecretMatchResultNotifier
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.match.entity.CandidateOrigin
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

private const val QUIZ_SET = 812L

class SecretMatchResultNotifierTest(
    private val secretMatchResultNotifier: SecretMatchResultNotifier,
    private val memberRepository: MemberRepository,
    private val notificationRepository: NotificationRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveActiveMember(nickname: String) =
        memberRepository.save(MemberFixture.create(nickname = nickname, email = "$nickname@ditto.pics", status = MemberStatus.ACTIVE))

    fun roundResult(secretRound: CandidateOrigin, ownerIds: Set<Long>) = SecretRoundResult(
        quizSetId = QUIZ_SET,
        secretRound = secretRound,
        alreadyGenerated = false,
        poolSize = ownerIds.size,
        savedCount = ownerIds.size,
        newCandidateOwnerIds = ownerIds,
    )

    "라운드에서 새 후보를 받은 회원에게만 알리고 1:1 결과 화면으로 보낸다" {
        val (a, b) = listOf("a", "b").map { saveActiveMember(it) }

        secretMatchResultNotifier.notifyFor(listOf(roundResult(CandidateOrigin.SECRET_FIRST, setOf(a.id, b.id)))) shouldBe 2

        notificationRepository.findAll().map { it.memberId }.toSet() shouldBe setOf(a.id, b.id)
        notificationRepository.findAll().first().let {
            it.type shouldBe NotificationType.SECRET_MATCH_RESULT
            it.title shouldBe "시크릿 매칭에서 인연을 찾았어요"
            it.body shouldBe "같은 답을 한 사람이 더 있어요. 일요일 밤까지 대화를 신청해보세요."
            it.targetId shouldBe QUIZ_SET
        }
    }

    "이미 돈 라운드라 새 후보가 없으면 알리지 않는다" {
        secretMatchResultNotifier.notifyFor(listOf(SecretRoundResult.skipped(QUIZ_SET, CandidateOrigin.SECRET_FIRST))) shouldBe 0

        notificationRepository.count() shouldBe 0
    }

    "금 알림을 읽지 않았으면 토 알림이 그 줄을 대신하고, 읽었으면 새로 쌓인다" {
        val (unread, read) = listOf("unread", "read").map { saveActiveMember(it) }
        secretMatchResultNotifier.notifyFor(listOf(roundResult(CandidateOrigin.SECRET_FIRST, setOf(unread.id, read.id))))
        notificationRepository.findAll().single { it.memberId == read.id }.let {
            it.markRead(LocalDateTime.now())
            notificationRepository.save(it)
        }

        secretMatchResultNotifier.notifyFor(listOf(roundResult(CandidateOrigin.SECRET_SECOND, setOf(unread.id, read.id))))

        notificationRepository.findAll().count { it.memberId == unread.id } shouldBe 1
        notificationRepository.findAll().count { it.memberId == read.id } shouldBe 2
    }
})
