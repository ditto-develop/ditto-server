package com.ditto.domain.member.repository

import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.Location
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.querydsl.GenderBirthDateCount
import com.ditto.domain.member.repository.querydsl.LeaveReasonCount
import com.ditto.domain.member.repository.querydsl.LocationCount
import com.ditto.domain.member.repository.querydsl.MemberFilter
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

private val JOINED_AT = LocalDateTime.of(2026, 10, 20, 12, 0)

class MemberStatsQueryTest(
    private val memberRepository: MemberRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun save(
        nickname: String,
        status: MemberStatus = MemberStatus.ACTIVE,
        role: MemberRole = MemberRole.USER,
        gender: Gender? = Gender.MALE,
        location: Location? = Location.SEOUL,
        birthDate: LocalDate? = LocalDate.of(2000, 5, 1),
        joinedAt: LocalDateTime? = JOINED_AT,
        leftAt: LocalDateTime? = null,
        leaveReason: String? = null,
    ) = memberRepository.save(
        MemberFixture.create(
            nickname = nickname,
            email = "$nickname@ditto.pics",
            status = status,
            role = role,
            gender = gender,
            location = location,
            birthDate = birthDate,
            joinedAt = joinedAt,
        ).apply { leftAt?.let { leave(leaveReason, it) } },
    )

    "countMembersByStatus" - {
        "가입 전 회원까지 상태별로 세고 관리자와 가입하지 않고 탈퇴한 회원은 뺀다" {
            save("a")
            save("b")
            save("left", status = MemberStatus.LEFT)
            save("admin", role = MemberRole.ADMIN)
            save("pending", status = MemberStatus.PENDING, joinedAt = null)
            save("left-before-join", joinedAt = null, leftAt = JOINED_AT, leaveReason = "APPLE_CONSENT_REVOKED")

            memberRepository.countMembersByStatus(MemberFilter.NONE) shouldBe
                mapOf(MemberStatus.ACTIVE to 2L, MemberStatus.LEFT to 1L, MemberStatus.PENDING to 1L)
        }

        "관리자 포함을 켜면 관리자도 센다" {
            save("a")
            save("admin", role = MemberRole.ADMIN)

            memberRepository.countMembersByStatus(MemberFilter(includesAdmins = true)) shouldBe
                mapOf(MemberStatus.ACTIVE to 2L)
        }

        "성별·생년월일·가입 시각 조건을 모두 건다" {
            save("match", gender = Gender.FEMALE, birthDate = LocalDate.of(2003, 1, 1))
            save("male", gender = Gender.MALE, birthDate = LocalDate.of(2003, 1, 1))
            save("older", gender = Gender.FEMALE, birthDate = LocalDate.of(1999, 12, 31))
            save("joined-later", gender = Gender.FEMALE, joinedAt = JOINED_AT.plusWeeks(1))
            val filter = MemberFilter(
                gender = Gender.FEMALE,
                birthDateRange = LocalDate.of(2000, 1, 1)..LocalDate.of(2003, 1, 1),
                joinedAtRange = JOINED_AT.minusDays(1)..<JOINED_AT.plusDays(1),
            )

            memberRepository.countMembersByStatus(filter) shouldBe mapOf(MemberStatus.ACTIVE to 1L)
        }

        "조건에 맞는 회원이 없으면 빈 맵이다" {
            save("male")

            memberRepository.countMembersByStatus(MemberFilter(gender = Gender.FEMALE)).shouldBeEmpty()
        }
    }

    "findAllJoinedAtsWithin" - {
        "끝 시각은 포함하지 않는다" {
            save("start", joinedAt = JOINED_AT)
            save("end", joinedAt = JOINED_AT.plusDays(1))

            val joinedAts = memberRepository.findAllJoinedAtsWithin(JOINED_AT..<JOINED_AT.plusDays(1), MemberFilter.NONE)

            joinedAts shouldContainExactly listOf(JOINED_AT)
        }
    }

    "countCurrentMembersByGenderAndBirthDate" - {
        "탈퇴 회원을 빼고 성별·생년월일이 같은 회원을 묶는다" {
            val birthDate = LocalDate.of(2001, 3, 3)
            save("a", gender = Gender.FEMALE, birthDate = birthDate)
            save("b", gender = Gender.FEMALE, birthDate = birthDate)
            save("unknown", gender = null, birthDate = null)
            save("left", status = MemberStatus.LEFT)

            val counts = memberRepository.countCurrentMembersByGenderAndBirthDate(MemberFilter.NONE)

            counts shouldContainExactlyInAnyOrder listOf(
                GenderBirthDateCount(Gender.FEMALE, birthDate, 2L),
                GenderBirthDateCount(null, null, 1L),
            )
        }
    }

    "countCurrentMembersByLocation" - {
        "탈퇴 회원을 빼고 지역별로 묶고 미입력도 한 줄이다" {
            save("seoul-1", location = Location.SEOUL)
            save("seoul-2", location = Location.SEOUL)
            save("busan", location = Location.BUSAN)
            save("unknown", location = null)
            save("left", status = MemberStatus.LEFT, location = Location.BUSAN)
            save("pending", status = MemberStatus.PENDING, joinedAt = null)

            val counts = memberRepository.countCurrentMembersByLocation(MemberFilter.NONE)

            counts shouldContainExactlyInAnyOrder listOf(
                LocationCount(Location.SEOUL, 2L),
                LocationCount(Location.BUSAN, 1L),
                LocationCount(null, 1L),
            )
        }
    }

    "countMembersLeftWithinByLeaveReason" - {
        "그 기간에 탈퇴한 회원을 사유별로 세고 끝 시각은 포함하지 않는다" {
            val leftAt = LocalDateTime.of(2026, 10, 19, 0, 0)
            save("a", leftAt = leftAt, leaveReason = "low_usage")
            save("b", leftAt = leftAt.plusDays(6), leaveReason = "low_usage")
            save("c", leftAt = leftAt.plusDays(1))
            save("next-week", leftAt = leftAt.plusDays(7), leaveReason = "low_usage")
            save("admin", role = MemberRole.ADMIN, leftAt = leftAt, leaveReason = "other")
            save("left-before-join", joinedAt = null, leftAt = leftAt, leaveReason = "APPLE_CONSENT_REVOKED")

            val counts = memberRepository.countMembersLeftWithinByLeaveReason(
                leftAt..<leftAt.plusDays(7),
                MemberFilter.NONE,
            )

            counts shouldContainExactlyInAnyOrder listOf(
                LeaveReasonCount("low_usage", 2L),
                LeaveReasonCount(null, 1L),
            )
        }
    }
})
