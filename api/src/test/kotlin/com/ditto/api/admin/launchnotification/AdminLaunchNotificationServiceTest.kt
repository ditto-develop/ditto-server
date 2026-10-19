package com.ditto.api.admin.launchnotification

import com.ditto.api.support.IntegrationTest
import com.ditto.domain.launchnotification.LaunchNotificationRequestFixture
import com.ditto.domain.launchnotification.entity.LaunchPlatform
import com.ditto.domain.launchnotification.repository.LaunchNotificationRequestRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import java.time.LocalDateTime
import javax.sql.DataSource

class AdminLaunchNotificationServiceTest(
    private val adminLaunchNotificationService: AdminLaunchNotificationService,
    private val launchNotificationRequestRepository: LaunchNotificationRequestRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun save(email: String) = launchNotificationRequestRepository.save(LaunchNotificationRequestFixture.create(email = email))

    "출시 알림 신청 어드민" - {

        "요약은 대기·완료 건수와 마지막 대기 id를 준다" {
            val first = save("a@example.com")
            val last = save("b@example.com")
            adminLaunchNotificationService.markNotified(LaunchPlatform.ANDROID, first.id, LocalDateTime.now())

            val summary = adminLaunchNotificationService.summary(LaunchPlatform.ANDROID)

            summary.pendingCount shouldBe 1
            summary.notifiedCount shouldBe 1
            summary.latestPendingId shouldBe last.id
        }

        "내보내기와 발송 완료는 화면을 연 시점의 마지막 id까지만 다룬다" {
            val shown = save("a@example.com")
            val upToId = adminLaunchNotificationService.summary(LaunchPlatform.ANDROID).latestPendingId!!
            val arrivedLater = save("late@example.com")

            val csv = adminLaunchNotificationService.exportPendingCsv(LaunchPlatform.ANDROID, upToId)
            val marked = adminLaunchNotificationService.markNotified(LaunchPlatform.ANDROID, upToId, LocalDateTime.now())

            csv.lines().first() shouldBe "email,requested_at"
            csv shouldNotContain "late@example.com"
            marked shouldBe 1
            launchNotificationRequestRepository.findById(shown.id).get().notifiedAt.let { (it != null) shouldBe true }
            launchNotificationRequestRepository.findById(arrivedLater.id).get().notifiedAt shouldBe null
        }

        "수식으로 시작하는 이메일은 엑셀이 실행하지 않게 '를 붙여 내보낸다" {
            val formula = save("=cmd@example.com")

            val csv = adminLaunchNotificationService.exportPendingCsv(LaunchPlatform.ANDROID, formula.id)

            csv.lines()[1].startsWith("'=cmd@example.com,") shouldBe true
        }
    }
})
