package com.ditto.api.launchnotification

import com.ditto.api.launchnotification.dto.LaunchNotificationSignupRequest
import com.ditto.api.launchnotification.scheduler.LaunchNotificationPurgeScheduler
import com.ditto.api.launchnotification.service.LaunchNotificationMetrics
import com.ditto.api.launchnotification.service.LaunchNotificationService
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.launchnotification.LaunchNotificationRequestFixture
import com.ditto.domain.launchnotification.entity.LaunchPlatform
import com.ditto.domain.launchnotification.repository.LaunchNotificationRequestRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.MeterRegistry
import java.time.LocalDateTime
import javax.sql.DataSource

class LaunchNotificationServiceTest(
    private val launchNotificationService: LaunchNotificationService,
    private val launchNotificationRequestRepository: LaunchNotificationRequestRepository,
    private val launchNotificationPurgeScheduler: LaunchNotificationPurgeScheduler,
    private val meterRegistry: MeterRegistry,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun signup(email: String = "visitor@example.com", consentVersion: String = "2026-10-19", platform: String = "ANDROID") =
        LaunchNotificationSignupRequest(email = email, platform = platform, privacyConsent = true, consentVersion = consentVersion)

    // 레지스트리가 테스트 간에 공유돼 값이 누적되므로 호출 전후 차이로 단언한다.
    fun requestedCount(result: String): Double =
        meterRegistry.find(LaunchNotificationMetrics.METRIC).tags("platform", "ANDROID", "result", result)
            .counter()?.count() ?: 0.0

    "출시 알림 신청" - {

        "처음 신청하면 정규화한 이메일로 한 행이 생긴다" {
            val before = requestedCount(LaunchNotificationMetrics.RESULT_CREATED)

            launchNotificationService.request(signup(email = "  Visitor@Example.COM"))

            val saved = launchNotificationRequestRepository.findAll().single()
            saved.email shouldBe "visitor@example.com"
            saved.platform shouldBe LaunchPlatform.ANDROID
            saved.consentVersion shouldBe "2026-10-19"
            saved.notifiedAt shouldBe null
            requestedCount(LaunchNotificationMetrics.RESULT_CREATED) shouldBe before + 1
        }

        "대소문자만 다른 같은 주소로 다시 신청하면 새 행 없이 동의 버전만 바뀐다" {
            launchNotificationService.request(signup(email = "visitor@example.com", consentVersion = "2026-10-19"))
            val first = launchNotificationRequestRepository.findAll().single()
            val before = requestedCount(LaunchNotificationMetrics.RESULT_REFRESHED)

            launchNotificationService.request(signup(email = "VISITOR@example.com", consentVersion = "2026-11-01"))

            val after = launchNotificationRequestRepository.findAll().single()
            after.id shouldBe first.id
            after.consentVersion shouldBe "2026-11-01"
            (after.consentedAt >= first.consentedAt) shouldBe true
            requestedCount(LaunchNotificationMetrics.RESULT_REFRESHED) shouldBe before + 1
        }

        "동의하지 않으면 저장하지 않고 거부한다" {
            shouldThrow<WarnException> {
                launchNotificationService.request(signup().copy(privacyConsent = false))
            }.errorCode shouldBe ErrorCode.BAD_REQUEST

            launchNotificationRequestRepository.count() shouldBe 0
        }

        "모르는 플랫폼은 거부한다" {
            shouldThrow<WarnException> {
                launchNotificationService.request(signup(platform = "IOS"))
            }.errorCode shouldBe ErrorCode.BAD_REQUEST
        }
    }

    "보유 기간 파기" - {

        "안내 후 30일이 지난 신청만 지우고, 안내 전 신청은 남긴다" {
            val now = LocalDateTime.now()
            val expired = launchNotificationRequestRepository.save(LaunchNotificationRequestFixture.create(email = "old@example.com"))
            val recent = launchNotificationRequestRepository.save(LaunchNotificationRequestFixture.create(email = "recent@example.com"))
            val pending = launchNotificationRequestRepository.save(LaunchNotificationRequestFixture.create(email = "pending@example.com"))
            launchNotificationRequestRepository.markNotified(LaunchPlatform.ANDROID, expired.id, now.minusDays(31))
            launchNotificationRequestRepository.markNotified(LaunchPlatform.ANDROID, recent.id, now.minusDays(29))

            launchNotificationPurgeScheduler.purgeExpired()

            launchNotificationRequestRepository.findAll().map { it.id }.toSet() shouldBe setOf(recent.id, pending.id)
        }
    }
})
