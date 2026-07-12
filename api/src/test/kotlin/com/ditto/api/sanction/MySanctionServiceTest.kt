package com.ditto.api.sanction

import com.ditto.api.sanction.service.MySanctionService
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.memberreport.MemberReportFixture
import com.ditto.domain.memberreport.entity.MemberReportReason
import com.ditto.domain.memberreport.repository.MemberReportRepository
import com.ditto.domain.sanction.SanctionFixture
import com.ditto.domain.sanction.entity.SanctionLevel
import com.ditto.domain.sanction.entity.SanctionOrigin
import com.ditto.domain.sanction.repository.SanctionRepository
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

class MySanctionServiceTest(
    private val mySanctionService: MySanctionService,
    private val sanctionRepository: SanctionRepository,
    private val memberReportRepository: MemberReportRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val now = LocalDateTime.of(2026, 7, 20, 12, 0)
    val memberId = 1L

    "내 제재 조회" - {

        "유효한 제재가 없으면 sanction이 null이다" {
            val result = mySanctionService.getMySanction(memberId, now)

            result.sanction.shouldBeNull()
        }

        "기한이 지난 제재는 유효하지 않다" {
            sanctionRepository.save(
                SanctionFixture.create(
                    memberId = memberId,
                    startsAt = now.minusDays(20),
                    endsAt = now.minusDays(6),
                ),
            )

            val result = mySanctionService.getMySanction(memberId, now)

            result.sanction.shouldBeNull()
        }

        "신고 기반 제재는 사유 카테고리를 함께 반환한다" {
            val report = memberReportRepository.save(
                MemberReportFixture.create(
                    reporterId = 2L,
                    reportedMemberId = memberId,
                    reason = MemberReportReason.MONEY_DEMAND,
                ),
            )
            sanctionRepository.save(
                SanctionFixture.create(
                    memberId = memberId,
                    memberReportId = report.id,
                    origin = SanctionOrigin.REPORTED,
                    level = SanctionLevel.SUSPENSION,
                    startsAt = now.minusDays(1),
                    endsAt = now.plusDays(13),
                ),
            )

            val result = mySanctionService.getMySanction(memberId, now)

            val sanction = result.sanction.shouldNotBeNull()
            sanction.level shouldBe "SUSPENSION"
            sanction.reason shouldBe "money-demand"
            sanction.endsAt shouldBe now.plusDays(13)
        }

        "직권 제재는 사유가 null이다" {
            sanctionRepository.save(
                SanctionFixture.create(
                    memberId = memberId,
                    origin = SanctionOrigin.MANUAL,
                    level = SanctionLevel.PERMANENT_BAN,
                    startsAt = now.minusDays(1),
                    endsAt = null,
                ),
            )

            val result = mySanctionService.getMySanction(memberId, now)

            val sanction = result.sanction.shouldNotBeNull()
            sanction.level shouldBe "PERMANENT_BAN"
            sanction.reason.shouldBeNull()
            sanction.endsAt.shouldBeNull()
        }

        "유효한 제재가 여러 개면 가장 무거운 것을 반환한다" {
            sanctionRepository.save(
                SanctionFixture.create(
                    memberId = memberId,
                    level = SanctionLevel.WARNING,
                    startsAt = now.minusDays(1),
                    endsAt = now.plusDays(6),
                ),
            )
            sanctionRepository.save(
                SanctionFixture.create(
                    memberId = memberId,
                    level = SanctionLevel.SUSPENSION,
                    startsAt = now.minusDays(1),
                    endsAt = now.plusDays(13),
                ),
            )

            val result = mySanctionService.getMySanction(memberId, now)

            result.sanction.shouldNotBeNull().level shouldBe "SUSPENSION"
        }
    }
})
