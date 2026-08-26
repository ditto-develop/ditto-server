package com.ditto.api.notification

import com.ditto.api.notification.service.MemberDeviceService
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.notification.MemberDeviceFixture
import com.ditto.domain.notification.entity.DevicePlatform
import com.ditto.domain.notification.repository.MemberDeviceRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

private const val ME = 1L
private const val OTHER = 2L
private const val TOKEN = "fcm-token-mine"

class MemberDeviceServiceTest(
    private val memberDeviceService: MemberDeviceService,
    private val memberDeviceRepository: MemberDeviceRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "등록" - {
        "새 토큰이면 행이 생기고 registered 가 true 다" {
            val registered = memberDeviceService.register(ME, TOKEN, DevicePlatform.ANDROID)

            registered shouldBe true
            val device = memberDeviceRepository.findByToken(TOKEN)!!
            device.memberId shouldBe ME
            device.platform shouldBe DevicePlatform.ANDROID
        }

        "같은 회원이 다시 등록해도 행이 늘지 않고 false 다" {
            memberDeviceService.register(ME, TOKEN, DevicePlatform.ANDROID)

            val registered = memberDeviceService.register(ME, TOKEN, DevicePlatform.ANDROID)

            registered shouldBe false
            memberDeviceRepository.count() shouldBe 1
        }

        // 토큰은 기기의 것이라 로그아웃해도 그대로다. 소유자를 갱신하지 않으면
        // 공용 기기에서 이전 회원의 알림이 남의 폰에 뜬다.
        "남의 토큰이면 행을 만들지 않고 소유자를 넘겨받는다" {
            memberDeviceRepository.save(MemberDeviceFixture.create(memberId = OTHER, token = TOKEN))

            val registered = memberDeviceService.register(ME, TOKEN, DevicePlatform.ANDROID)

            registered shouldBe true
            memberDeviceRepository.count() shouldBe 1
            memberDeviceRepository.findByToken(TOKEN)!!.memberId shouldBe ME
        }

        "한 회원이 여러 기기를 등록할 수 있다" {
            memberDeviceService.register(ME, "fcm-token-phone", DevicePlatform.ANDROID)
            memberDeviceService.register(ME, "fcm-token-tablet", DevicePlatform.IOS)

            memberDeviceRepository.count() shouldBe 2
        }
    }

    "해제" - {
        "내 토큰이면 행이 지워진다" {
            memberDeviceRepository.save(MemberDeviceFixture.create(memberId = ME, token = TOKEN))

            memberDeviceService.unregister(ME, TOKEN)

            memberDeviceRepository.findByToken(TOKEN) shouldBe null
        }

        "없는 토큰이어도 성공한다" {
            memberDeviceService.unregister(ME, "fcm-token-unknown")
        }

        "남의 토큰이면 지우지 않고 DEVICE_NOT_FOUND 다" {
            memberDeviceRepository.save(MemberDeviceFixture.create(memberId = OTHER, token = TOKEN))

            val exception = shouldThrow<WarnException> {
                memberDeviceService.unregister(ME, TOKEN)
            }

            exception.errorCode shouldBe ErrorCode.DEVICE_NOT_FOUND
            memberDeviceRepository.findByToken(TOKEN)!!.memberId shouldBe OTHER
        }
    }
})
