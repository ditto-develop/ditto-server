package com.ditto.api.notification

import com.ditto.api.notification.notifier.ReportSanctionNotifier
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.memberreport.MemberReportFixture
import com.ditto.domain.memberreport.repository.MemberReportRepository
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import com.ditto.domain.sanction.SanctionFixture
import com.ditto.domain.sanction.entity.SanctionLevel
import com.ditto.domain.sanction.entity.SanctionOrigin
import com.ditto.domain.sanction.repository.SanctionRepository
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

class ReportSanctionNotifierTest(
    private val reportSanctionNotifier: ReportSanctionNotifier,
    private val memberRepository: MemberRepository,
    private val memberReportRepository: MemberReportRepository,
    private val sanctionRepository: SanctionRepository,
    private val notificationRepository: NotificationRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun reportedSanction(level: SanctionLevel, startsAt: LocalDateTime = LocalDateTime.of(2026, 7, 15, 10, 30)) =
        run {
            val reporter = memberRepository.save(MemberFixture.create(nickname = "신고자", email = "a@ditto.pics"))
            val reported = memberRepository.save(MemberFixture.create(nickname = "피신고자", email = "b@ditto.pics"))
            val report = memberReportRepository.save(
                MemberReportFixture.create(reporterId = reporter.id, reportedMemberId = reported.id),
            )
            val sanction = sanctionRepository.save(
                SanctionFixture.create(
                    memberId = reported.id,
                    origin = SanctionOrigin.REPORTED,
                    level = level,
                    startsAt = startsAt,
                    memberReportId = report.id,
                ),
            )
            Triple(reporter, report, sanction)
        }

    "신고로 제재가 걸리면 신고자와 피신고자에게 한 번씩 알린다" {
        val (reporter, report, sanction) = reportedSanction(SanctionLevel.SUSPENSION)

        reportSanctionNotifier.notifyImposed(sanction) shouldBe 2

        val byType = notificationRepository.findAll().associateBy { it.type }
        byType.getValue(NotificationType.REPORT_ACTIONED).let {
            it.memberId shouldBe reporter.id
            it.targetId shouldBe report.id
            it.title shouldBe "신고하신 내용을 처리했어요"
            // 신고자에게는 제재 수위를 밝히지 않는다
            it.body shouldBe "검토 결과 운영 정책에 따라 조치했어요. 알려주셔서 고마워요."
        }
        byType.getValue(NotificationType.SANCTION_IMPOSED).let {
            it.memberId shouldBe sanction.memberId
            it.targetId shouldBe sanction.id
            it.title shouldBe "운영 정책 위반으로 이용이 정지됐어요"
            it.body shouldBe "7월 29일 10:30까지 서비스를 이용할 수 없어요."
        }
    }

    "경고는 퀴즈 참여가 막히는 기간을 알린다" {
        val (_, _, warning) = reportedSanction(SanctionLevel.WARNING, startsAt = LocalDateTime.of(2026, 7, 20, 0, 0))

        reportSanctionNotifier.notifyImposed(warning)

        notificationRepository.findAll().single { it.type == NotificationType.SANCTION_IMPOSED }.let {
            it.title shouldBe "운영 정책 위반으로 경고를 받았어요"
            it.body shouldBe "7월 20일부터 일주일 동안 퀴즈에 참여할 수 없어요."
        }
    }

    "영구 차단은 더 이상 이용할 수 없음을 알린다" {
        val (_, _, ban) = reportedSanction(SanctionLevel.PERMANENT_BAN)

        reportSanctionNotifier.notifyImposed(ban)

        notificationRepository.findAll().single { it.type == NotificationType.SANCTION_IMPOSED }.let {
            it.title shouldBe "운영 정책 위반으로 이용이 제한됐어요"
            it.body shouldBe "더 이상 서비스를 이용할 수 없어요."
        }
    }

    "같은 제재로 다시 불려도 알림은 늘지 않는다" {
        val (_, _, sanction) = reportedSanction(SanctionLevel.WARNING)
        reportSanctionNotifier.notifyImposed(sanction) shouldBe 2

        reportSanctionNotifier.notifyImposed(sanction) shouldBe 0

        notificationRepository.findAll().size shouldBe 2
    }

    "신고 없이 건 제재는 피신고자에게만 알린다" {
        val member = memberRepository.save(MemberFixture.create(nickname = "회원", email = "c@ditto.pics"))
        val sanction = sanctionRepository.save(SanctionFixture.create(memberId = member.id))

        reportSanctionNotifier.notifyImposed(sanction) shouldBe 1

        notificationRepository.findAll().single().type shouldBe NotificationType.SANCTION_IMPOSED
    }
})
