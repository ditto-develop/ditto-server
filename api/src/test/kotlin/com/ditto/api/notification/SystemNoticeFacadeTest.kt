package com.ditto.api.notification

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.notification.facade.SystemNoticeFacade
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import com.ditto.domain.notification.repository.SystemNoticeRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

private val ADMIN = AdminPrincipal(memberId = 99L, name = "관리자", email = "admin@ditto.pics")

class SystemNoticeFacadeTest(
    private val systemNoticeFacade: SystemNoticeFacade,
    private val systemNoticeRepository: SystemNoticeRepository,
    private val memberRepository: MemberRepository,
    private val notificationRepository: NotificationRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveMember(nickname: String, status: MemberStatus = MemberStatus.ACTIVE) =
        memberRepository.save(
            MemberFixture.create(nickname = nickname, email = "$nickname@ditto.pics", status = status),
        )

    "공지를 보내면 활성 회원 전원에게 알림이 남고 이력에 수신 수가 기록된다" {
        val active = listOf("a", "b").map { saveMember(it) }
        saveMember("left", MemberStatus.LEFT)

        val notice = systemNoticeFacade.publish("ditto가 업데이트됐어요", "이번에 달라진 점을 확인해보세요.", ADMIN)

        notice.recipientCount shouldBe 2
        notice.authorMemberId shouldBe 99L
        notice.authorName shouldBe "관리자"
        systemNoticeRepository.findById(notice.id).get().recipientCount shouldBe 2

        val notifications = notificationRepository.findAll()
        notifications.map { it.memberId }.toSet() shouldBe active.map { it.id }.toSet()
        notifications.first().let {
            it.type shouldBe NotificationType.SYSTEM_NOTICE
            it.title shouldBe "ditto가 업데이트됐어요"
            it.body shouldBe "이번에 달라진 점을 확인해보세요."
            it.targetId shouldBe notice.id
        }
    }

    // SYSTEM_NOTICE 는 중복을 막지 않는다. 운영이 같은 공지를 다시 보낼 수 있어야 한다.
    "같은 공지를 다시 보내면 새 이력과 새 알림이 생긴다" {
        saveMember("a")
        systemNoticeFacade.publish("점검 안내", null, ADMIN)

        systemNoticeFacade.publish("점검 안내", null, ADMIN).recipientCount shouldBe 1

        systemNoticeRepository.count() shouldBe 2
        notificationRepository.count() shouldBe 2
    }

    "본문은 비워도 되고, 앞뒤 공백은 지운다" {
        saveMember("a")

        val notice = systemNoticeFacade.publish("  공지  ", "   ", ADMIN)

        notice.title shouldBe "공지"
        notice.body shouldBe null
    }

    "제목이 비어 있으면 거부하고 이력도 남기지 않는다" {
        shouldThrow<WarnException> { systemNoticeFacade.publish("   ", "본문", ADMIN) }

        systemNoticeRepository.count() shouldBe 0
    }

    "제목이 100자를 넘으면 거부한다" {
        shouldThrow<WarnException> { systemNoticeFacade.publish("가".repeat(101), null, ADMIN) }
    }

    "활성 회원이 없으면 수신 수 0 으로 이력만 남는다" {
        systemNoticeFacade.publish("공지", null, ADMIN).recipientCount shouldBe 0

        systemNoticeRepository.count() shouldBe 1
    }
})
