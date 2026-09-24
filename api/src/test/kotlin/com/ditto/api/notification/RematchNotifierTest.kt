package com.ditto.api.notification

import com.ditto.api.notification.notifier.RematchNotifier
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import com.ditto.domain.rematch.RematchFixture
import com.ditto.domain.rematch.entity.Rematch
import com.ditto.domain.rematch.repository.RematchRepository
import com.ditto.domain.review.MemberReviewFixture
import com.ditto.domain.review.repository.MemberReviewRepository
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

private const val GROUP_MATCH_ID = 7L
private val SUBMITTED_AT = LocalDateTime.of(2026, 7, 27, 10, 0)

class RematchNotifierTest(
    private val rematchNotifier: RematchNotifier,
    private val memberReviewRepository: MemberReviewRepository,
    private val rematchRepository: RematchRepository,
    private val memberRepository: MemberRepository,
    private val notificationRepository: NotificationRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveMember(nickname: String) =
        memberRepository.save(MemberFixture.create(nickname = nickname, email = "$nickname@ditto.pics"))

    fun saveGroupReview(authorId: Long) = memberReviewRepository.save(
        MemberReviewFixture.create(authorMemberId = authorId, matchType = ChatRoomType.GROUP, matchId = GROUP_MATCH_ID),
    )

    fun savePair(a: Long, b: Long, submit: Rematch.() -> Unit = {}) =
        rematchRepository.save(
            RematchFixture.create(sourceGroupMatchId = GROUP_MATCH_ID, memberIdA = a, memberIdB = b).apply(submit),
        )

    "먼저 원한다고 내면 상대에게 신청 알림이 간다" {
        val me = saveMember("나")
        val other = saveMember("상대")
        val review = saveGroupReview(me.id)
        val pair = savePair(me.id, other.id) { submitWants(me.id, true, SUBMITTED_AT) }

        rematchNotifier.notifySubmitted(review.id, submitterId = me.id, counterpartId = other.id) shouldBe true

        notificationRepository.findAll().single().let {
            it.memberId shouldBe other.id
            it.type shouldBe NotificationType.REMATCH_REQUESTED
            it.title shouldBe "나님이 다시 만나고 싶어 해요"
            it.body shouldBe "수락하면 대화방이 열려요."
            it.targetId shouldBe pair.id
        }
    }

    "상대가 원한다고 낸 뒤 내가 거절하면 상대에게 거절 알림이 간다" {
        val me = saveMember("나")
        val other = saveMember("상대")
        val review = saveGroupReview(me.id)
        savePair(me.id, other.id) {
            submitWants(other.id, true, SUBMITTED_AT)
            submitWants(me.id, false, SUBMITTED_AT.plusHours(1))
        }

        rematchNotifier.notifySubmitted(review.id, submitterId = me.id, counterpartId = other.id) shouldBe true

        notificationRepository.findAll().single().let {
            it.memberId shouldBe other.id
            it.type shouldBe NotificationType.REMATCH_REJECTED
            it.title shouldBe "나님과의 재매칭이 이루어지지 않았어요"
        }
    }

    "상대가 거절한 뒤 내가 원한다고 내면 나에게 거절 알림이 간다" {
        val me = saveMember("나")
        val other = saveMember("상대")
        val review = saveGroupReview(me.id)
        savePair(me.id, other.id) {
            submitWants(other.id, false, SUBMITTED_AT)
            submitWants(me.id, true, SUBMITTED_AT.plusHours(1))
        }

        rematchNotifier.notifySubmitted(review.id, submitterId = me.id, counterpartId = other.id) shouldBe true

        notificationRepository.findAll().single().let {
            it.memberId shouldBe me.id
            it.type shouldBe NotificationType.REMATCH_REJECTED
            it.title shouldBe "상대님과의 재매칭이 이루어지지 않았어요"
        }
    }

    // 성사 알림은 방이 예약될 때 RematchChatRoomOpener 가 보낸다.
    "상호 성사되면 여기서는 알리지 않는다" {
        val me = saveMember("나")
        val other = saveMember("상대")
        val review = saveGroupReview(me.id)
        savePair(me.id, other.id) {
            submitWants(other.id, true, SUBMITTED_AT)
            submitWants(me.id, true, SUBMITTED_AT.plusHours(1))
        }

        rematchNotifier.notifySubmitted(review.id, submitterId = me.id, counterpartId = other.id) shouldBe false

        notificationRepository.count() shouldBe 0
    }

    "먼저 원하지 않는다고 내면 알릴 것이 없다" {
        val me = saveMember("나")
        val other = saveMember("상대")
        val review = saveGroupReview(me.id)
        savePair(me.id, other.id) { submitWants(me.id, false, SUBMITTED_AT) }

        rematchNotifier.notifySubmitted(review.id, submitterId = me.id, counterpartId = other.id) shouldBe false
    }

    "재제출이 들어와도 쌍마다 한 번이다" {
        val me = saveMember("나")
        val other = saveMember("상대")
        val review = saveGroupReview(me.id)
        savePair(me.id, other.id) { submitWants(me.id, true, SUBMITTED_AT) }
        rematchNotifier.notifySubmitted(review.id, me.id, other.id)

        rematchNotifier.notifySubmitted(review.id, me.id, other.id) shouldBe false

        notificationRepository.count() shouldBe 1
    }

    "1:1 평가는 재매칭이 없어 알리지 않는다" {
        val me = saveMember("나")
        val review = memberReviewRepository.save(
            MemberReviewFixture.create(authorMemberId = me.id, matchType = ChatRoomType.PERSONAL),
        )

        rematchNotifier.notifySubmitted(review.id, submitterId = me.id, counterpartId = 2L) shouldBe false
    }
})
