package com.ditto.api.notification

import com.ditto.api.notification.notifier.PersonalMatchNotifier
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

private const val MATCH_ID = 77L

class PersonalMatchNotifierTest(
    private val personalMatchNotifier: PersonalMatchNotifier,
    private val memberRepository: MemberRepository,
    private val notificationRepository: NotificationRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "대화 신청이 거절되면 신청자에게 알린다" - {
        "거절한 사람의 닉네임이 문구에 들어간다" {
            val requester = memberRepository.save(MemberFixture.create(nickname = "신청자", email = "a@ditto.pics"))
            val rejecter = memberRepository.save(MemberFixture.create(nickname = "거절한사람", email = "b@ditto.pics"))

            personalMatchNotifier.notifyRejected(
                matchId = MATCH_ID,
                requesterId = requester.id,
                rejectedBy = rejecter.id,
            ) shouldBe true

            notificationRepository.findAll().single().let {
                // 받는 사람은 신청자 한 명뿐이다 — 거절한 본인은 자기가 누른 것이라 받지 않는다
                it.memberId shouldBe requester.id
                it.type shouldBe NotificationType.MATCH_REJECTED
                it.title shouldBe "거절한사람님이 대화 신청을 거절했어요"
                it.body shouldBe "새로운 인연에게 대화를 신청해보세요."
                it.targetId shouldBe MATCH_ID
            }
        }

        // 같은 매칭 건을 두 번 알리지 않는다(ONCE_PER_TARGET). 거절 자체가 PENDING 에서만 일어나
        // 두 번 날 수 없지만, 적재 지점이 늘어도 판단이 갈리지 않게 유형이 막는다.
        "같은 매칭 건은 한 번만 알린다" {
            val requester = memberRepository.save(MemberFixture.create(nickname = "신청자", email = "a@ditto.pics"))
            val rejecter = memberRepository.save(MemberFixture.create(nickname = "거절한사람", email = "b@ditto.pics"))
            personalMatchNotifier.notifyRejected(MATCH_ID, requester.id, rejecter.id) shouldBe true

            personalMatchNotifier.notifyRejected(MATCH_ID, requester.id, rejecter.id) shouldBe false

            notificationRepository.findAll().size shouldBe 1
        }

        // 문구에 닉네임이 들어가므로 주어가 비는 알림("님이 대화 신청을 거절했어요")을 내보내느니 알리지 않는다.
        "거절한 사람이 없으면(탈퇴 등) 알리지 않는다" {
            val requester = memberRepository.save(MemberFixture.create(nickname = "신청자", email = "a@ditto.pics"))

            personalMatchNotifier.notifyRejected(
                matchId = MATCH_ID,
                requesterId = requester.id,
                rejectedBy = 9999L,
            ) shouldBe false

            notificationRepository.findAll().shouldBeEmpty()
        }
    }
})
