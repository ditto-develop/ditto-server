package com.ditto.api.admin.sanction

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.sanction.SanctionFixture
import com.ditto.domain.sanction.entity.SanctionLevel
import com.ditto.domain.sanction.entity.SanctionOrigin
import com.ditto.domain.sanction.entity.SanctionStatus
import com.ditto.domain.sanction.repository.SanctionRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

class AdminSanctionServiceTest(
    private val adminSanctionService: AdminSanctionService,
    private val memberRepository: MemberRepository,
    private val sanctionRepository: SanctionRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val now = LocalDateTime.of(2026, 7, 15, 10, 0)
    val admin = AdminPrincipal(memberId = 999L, name = "관리자", email = "admin@ditto.pics")

    fun saveActiveMember(nickname: String): Member =
        memberRepository.save(MemberFixture.create(nickname = nickname, status = MemberStatus.ACTIVE))

    "직권 제재" - {
        "허위 신고자 제재(FALSE_REPORT)는 정지가 적용되지만 차수에는 산입되지 않는다" {
            val member = saveActiveMember("허위신고자")

            adminSanctionService.impose(
                memberId = member.id,
                level = SanctionLevel.SUSPENSION,
                origin = SanctionOrigin.FALSE_REPORT,
                admin = admin,
                now = now,
            )

            memberRepository.findById(member.id).orElseThrow().status shouldBe MemberStatus.SUSPENDED
            adminSanctionService.memberSanctions(member.id).strikeCount shouldBe 0
        }

        "존재하지 않는 회원은 거부한다" {
            val exception = shouldThrow<WarnException> {
                adminSanctionService.impose(
                    memberId = 99999L,
                    level = SanctionLevel.WARNING,
                    origin = SanctionOrigin.MANUAL,
                    admin = admin,
                    now = now,
                )
            }
            exception.errorCode shouldBe ErrorCode.NOT_FOUND
        }
    }

    "직권 해제" - {
        "정지 제재를 해제하면 회원이 ACTIVE로 원복된다" {
            val member = saveActiveMember("해제대상")
            val sanction = adminSanctionService.impose(
                memberId = member.id,
                level = SanctionLevel.SUSPENSION,
                origin = SanctionOrigin.MANUAL,
                admin = admin,
                now = now,
            )

            adminSanctionService.lift(sanction.id, now)

            sanctionRepository.findById(sanction.id).orElseThrow().status shouldBe SanctionStatus.LIFTED
            val reloaded = memberRepository.findById(member.id).orElseThrow()
            reloaded.status shouldBe MemberStatus.ACTIVE
            reloaded.suspendedUntil shouldBe null
        }

        "다른 유효 제재가 남아 있으면 그 수위로 상태를 재계산한다" {
            val member = saveActiveMember("이중제재")
            val suspension = adminSanctionService.impose(
                memberId = member.id,
                level = SanctionLevel.SUSPENSION,
                origin = SanctionOrigin.MANUAL,
                admin = admin,
                now = now,
            )
            val ban = adminSanctionService.impose(
                memberId = member.id,
                level = SanctionLevel.PERMANENT_BAN,
                origin = SanctionOrigin.MANUAL,
                admin = admin,
                now = now,
            )

            adminSanctionService.lift(ban.id, now)

            // 영구 차단을 해제해도 유효한 정지가 남아 SUSPENDED로 내려온다.
            val reloaded = memberRepository.findById(member.id).orElseThrow()
            reloaded.status shouldBe MemberStatus.SUSPENDED
            reloaded.suspendedUntil shouldBe sanctionRepository.findById(suspension.id).orElseThrow().endsAt
        }

        "경고 해제는 계정 상태를 건드리지 않는다" {
            val member = saveActiveMember("경고해제")
            val warning = adminSanctionService.impose(
                memberId = member.id,
                level = SanctionLevel.WARNING,
                origin = SanctionOrigin.MANUAL,
                admin = admin,
                now = now,
            )

            adminSanctionService.lift(warning.id, now)

            sanctionRepository.findById(warning.id).orElseThrow().status shouldBe SanctionStatus.LIFTED
            memberRepository.findById(member.id).orElseThrow().status shouldBe MemberStatus.ACTIVE
        }

        "이미 종결된 제재는 해제할 수 없다" {
            val member = saveActiveMember("재해제")
            val sanction = sanctionRepository.save(
                SanctionFixture.create(memberId = member.id, startsAt = now.minusDays(20), endsAt = now.minusDays(6))
                    .apply { expire() },
            )

            val exception = shouldThrow<WarnException> {
                adminSanctionService.lift(sanction.id, now)
            }
            exception.errorCode shouldBe ErrorCode.INVALID_STATUS_TRANSITION
        }
    }
})
