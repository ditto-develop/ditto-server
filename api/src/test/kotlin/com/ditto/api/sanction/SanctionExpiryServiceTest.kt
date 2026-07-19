package com.ditto.api.sanction

import com.ditto.api.sanction.service.SanctionExpiryService
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.sanction.SanctionFixture
import com.ditto.domain.sanction.entity.SanctionLevel
import com.ditto.domain.sanction.entity.SanctionStatus
import com.ditto.domain.sanction.repository.SanctionRepository
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

class SanctionExpiryServiceTest(
    private val sanctionExpiryService: SanctionExpiryService,
    private val memberRepository: MemberRepository,
    private val sanctionRepository: SanctionRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val now = LocalDateTime.of(2026, 7, 27, 5, 0)

    fun saveSuspendedMember(nickname: String, until: LocalDateTime) =
        memberRepository.save(
            MemberFixture.create(nickname = nickname, status = MemberStatus.ACTIVE).apply { suspendUntil(until) },
        )

    "expireDue (배치 일괄 원복)" - {

        "해제 예정일이 지난 정지 회원을 ACTIVE로 원복하고 제재를 EXPIRED로 종결한다" {
            val member = saveSuspendedMember("만료정지", until = now.minusDays(1))
            val sanction = sanctionRepository.save(
                SanctionFixture.create(
                    memberId = member.id,
                    level = SanctionLevel.SUSPENSION,
                    startsAt = now.minusDays(15),
                    endsAt = now.minusDays(1),
                ),
            )

            sanctionExpiryService.expireDue(now)

            val reloadedMember = memberRepository.findById(member.id).orElseThrow()
            reloadedMember.status shouldBe MemberStatus.ACTIVE
            reloadedMember.suspendedUntil.shouldBeNull()
            sanctionRepository.findById(sanction.id).orElseThrow().status shouldBe SanctionStatus.EXPIRED
        }

        "아직 유효한 정지는 건드리지 않는다" {
            val member = saveSuspendedMember("유효정지", until = now.plusDays(7))
            val sanction = sanctionRepository.save(
                SanctionFixture.create(
                    memberId = member.id,
                    level = SanctionLevel.SUSPENSION,
                    startsAt = now.minusDays(7),
                    endsAt = now.plusDays(7),
                ),
            )

            sanctionExpiryService.expireDue(now)

            memberRepository.findById(member.id).orElseThrow().status shouldBe MemberStatus.SUSPENDED
            sanctionRepository.findById(sanction.id).orElseThrow().status shouldBe SanctionStatus.ACTIVE
        }

        "기한이 지난 경고 제재도 함께 종결한다 (회원 status는 무관)" {
            val member = memberRepository.save(MemberFixture.create(nickname = "경고만료", status = MemberStatus.ACTIVE))
            val sanction = sanctionRepository.save(
                SanctionFixture.create(
                    memberId = member.id,
                    level = SanctionLevel.WARNING,
                    startsAt = now.minusDays(8),
                    endsAt = now.minusDays(1),
                ),
            )

            sanctionExpiryService.expireDue(now)

            sanctionRepository.findById(sanction.id).orElseThrow().status shouldBe SanctionStatus.EXPIRED
            memberRepository.findById(member.id).orElseThrow().status shouldBe MemberStatus.ACTIVE
        }
    }

    "reinstateIfExpired (로그인 개별 원복)" - {

        "만료 지난 정지 회원을 원복하고 해당 제재를 종결한다" {
            val member = saveSuspendedMember("로그인원복", until = now.minusDays(1))
            val sanction = sanctionRepository.save(
                SanctionFixture.create(
                    memberId = member.id,
                    level = SanctionLevel.SUSPENSION,
                    startsAt = now.minusDays(15),
                    endsAt = now.minusDays(1),
                ),
            )

            sanctionExpiryService.reinstateIfExpired(member, now)

            memberRepository.findById(member.id).orElseThrow().status shouldBe MemberStatus.ACTIVE
            sanctionRepository.findById(sanction.id).orElseThrow().status shouldBe SanctionStatus.EXPIRED
        }

        "아직 유효한 정지 회원은 원복하지 않는다" {
            val member = saveSuspendedMember("유효정지-로그인", until = now.plusDays(7))

            sanctionExpiryService.reinstateIfExpired(member, now)

            memberRepository.findById(member.id).orElseThrow().status shouldBe MemberStatus.SUSPENDED
        }

        "정지 상태가 아닌 회원은 아무것도 하지 않는다" {
            val member = memberRepository.save(MemberFixture.create(nickname = "활성회원", status = MemberStatus.ACTIVE))

            sanctionExpiryService.reinstateIfExpired(member, now)

            memberRepository.findById(member.id).orElseThrow().status shouldBe MemberStatus.ACTIVE
        }
    }
})
