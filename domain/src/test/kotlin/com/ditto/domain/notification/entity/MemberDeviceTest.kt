package com.ditto.domain.notification.entity

import com.ditto.domain.notification.repository.MemberDeviceRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.sql.DataSource
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

class MemberDeviceTest(
    private val memberDeviceRepository: MemberDeviceRepository,
    transactionManager: PlatformTransactionManager,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    // @Modifying 쿼리는 트랜잭션 안에서만 돈다.
    val transactionTemplate = TransactionTemplate(transactionManager)

    "생성" - {
        "create 로 만들어 저장하면 요청 회원의 소유로 시작한다" {
            val device = memberDeviceRepository.save(
                MemberDevice.create(memberId = 1L, token = "fcm-token", platform = DevicePlatform.IOS),
            )

            device.id shouldNotBe 0L
            device.isOwnedBy(1L) shouldBe true
            device.isOwnedBy(2L) shouldBe false
        }
    }

    "transferTo — 소유자 갱신" - {
        "다른 회원에게 넘기면 소유가 바뀐다" {
            val device = MemberDevice.create(memberId = 1L, token = "fcm-token", platform = DevicePlatform.ANDROID)

            device.transferTo(2L)

            device.isOwnedBy(2L) shouldBe true
        }
    }

    "token 유일 제약" - {
        "같은 토큰을 두 번 저장하면 두 번째가 막힌다 — 한 토큰 = 한 회원" {
            memberDeviceRepository.save(MemberDevice.create(memberId = 1L, token = "fcm-token", platform = DevicePlatform.IOS))

            shouldThrow<DataIntegrityViolationException> {
                memberDeviceRepository.save(MemberDevice.create(memberId = 2L, token = "fcm-token", platform = DevicePlatform.IOS))
            }
        }
    }

    "deleteAllByTokenIn — 죽은 토큰 일괄 삭제" - {
        "목록의 토큰만 지우고 지운 수를 준다" {
            memberDeviceRepository.save(MemberDevice.create(memberId = 1L, token = "dead-1", platform = DevicePlatform.IOS))
            memberDeviceRepository.save(MemberDevice.create(memberId = 2L, token = "dead-2", platform = DevicePlatform.ANDROID))
            memberDeviceRepository.save(MemberDevice.create(memberId = 3L, token = "alive", platform = DevicePlatform.IOS))

            transactionTemplate.execute {
                memberDeviceRepository.deleteAllByTokenIn(listOf("dead-1", "dead-2"))
            } shouldBe 2

            memberDeviceRepository.count() shouldBe 1
            memberDeviceRepository.findByToken("alive")!!.memberId shouldBe 3L
        }
    }

    "deleteByTokenAndMemberId — 소유자 조건부 삭제" - {
        "소유자가 맞으면 지우고 1 을 준다" {
            memberDeviceRepository.save(MemberDevice.create(memberId = 1L, token = "fcm-token", platform = DevicePlatform.IOS))

            transactionTemplate.execute {
                memberDeviceRepository.deleteByTokenAndMemberId("fcm-token", 1L)
            } shouldBe 1
            memberDeviceRepository.findByToken("fcm-token") shouldBe null
        }

        "소유자가 다르면 지우지 않고 0 을 준다" {
            memberDeviceRepository.save(MemberDevice.create(memberId = 1L, token = "fcm-token", platform = DevicePlatform.IOS))

            transactionTemplate.execute {
                memberDeviceRepository.deleteByTokenAndMemberId("fcm-token", 2L)
            } shouldBe 0
            memberDeviceRepository.findByToken("fcm-token") shouldNotBe null
        }
    }
})
