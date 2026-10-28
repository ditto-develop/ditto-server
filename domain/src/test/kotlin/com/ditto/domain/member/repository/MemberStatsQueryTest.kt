package com.ditto.domain.member.repository

import com.ditto.domain.intronote.entity.IntroNote
import com.ditto.domain.intronote.entity.IntroQuestion
import com.ditto.domain.intronote.repository.IntroNoteRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.Location
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.entity.NicknameReservation
import com.ditto.domain.member.repository.querydsl.GenderBirthDateCount
import com.ditto.domain.member.repository.querydsl.LeaveReasonCount
import com.ditto.domain.member.repository.querydsl.LocationCount
import com.ditto.domain.member.repository.querydsl.MemberFilter
import com.ditto.domain.member.repository.querydsl.QuizBlockedMember
import com.ditto.domain.member.repository.querydsl.SignupDropout
import com.ditto.domain.member.repository.querydsl.SocialProviderCount
import com.ditto.domain.notification.MemberDeviceFixture
import com.ditto.domain.notification.repository.MemberDeviceRepository
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.collections.shouldBeEmpty
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
    private val socialAccountRepository: SocialAccountRepository,
    private val nicknameReservationRepository: NicknameReservationRepository,
    private val memberDeviceRepository: MemberDeviceRepository,
    private val introNoteRepository: IntroNoteRepository,
    private val quizProgressRepository: QuizProgressRepository,
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

            memberRepository.countMembersByStatus(MemberFilter(includeAdmins = true)) shouldBe
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

    fun signUpWith(memberId: Long, provider: SocialProvider) =
        socialAccountRepository.save(
            SocialAccount.create(memberId = memberId, provider = provider, providerUserId = "$provider-$memberId"),
        )

    "findSignupDropoutsStartedBefore" - {
        "기준 시각 전에 가입을 시작하고 마치지 않은 회원을 닉네임 예약·푸시 기기 여부와 함께 찾는다" {
            val checked = save("checked", status = MemberStatus.PENDING, joinedAt = null)
            signUpWith(checked.id, SocialProvider.KAKAO)
            nicknameReservationRepository.save(NicknameReservation.reserve(checked.id, "checked", JOINED_AT))
            memberDeviceRepository.save(MemberDeviceFixture.create(memberId = checked.id, token = "token-1"))
            memberDeviceRepository.save(MemberDeviceFixture.create(memberId = checked.id, token = "token-2"))
            val loginOnly = save("login-only", status = MemberStatus.PENDING, joinedAt = null)
            signUpWith(loginOnly.id, SocialProvider.APPLE)
            save("joined")
            save("admin", status = MemberStatus.PENDING, role = MemberRole.ADMIN, joinedAt = null)

            val dropouts = memberRepository.findSignupDropoutsStartedBefore(LocalDateTime.now().plusMinutes(1))

            dropouts.map { it.copy(signupStartedAt = JOINED_AT) } shouldContainExactlyInAnyOrder listOf(
                SignupDropout(checked.id, JOINED_AT, SocialProvider.KAKAO, true, true),
                SignupDropout(loginOnly.id, JOINED_AT, SocialProvider.APPLE, false, false),
            )
        }

        "기준 시각 뒤에 가입을 시작한 회원은 아직 가입 중이라 뺀다" {
            save("signing-up", status = MemberStatus.PENDING, joinedAt = null)

            memberRepository.findSignupDropoutsStartedBefore(LocalDateTime.now().minusMinutes(1)).shouldBeEmpty()
        }
    }

    "countJoinedMembersBySocialProvider" - {
        "가입을 마친 회원을 로그인 수단별로 세고 탈퇴한 회원은 넣고 가입 중인 회원과 관리자는 뺀다" {
            signUpWith(save("kakao").id, SocialProvider.KAKAO)
            signUpWith(save("kakao-left", status = MemberStatus.LEFT).id, SocialProvider.KAKAO)
            signUpWith(save("apple").id, SocialProvider.APPLE)
            save("no-social-account")
            signUpWith(save("pending", status = MemberStatus.PENDING, joinedAt = null).id, SocialProvider.KAKAO)
            signUpWith(save("admin", role = MemberRole.ADMIN).id, SocialProvider.KAKAO)

            memberRepository.countJoinedMembersBySocialProvider() shouldContainExactlyInAnyOrder listOf(
                SocialProviderCount(SocialProvider.KAKAO, 2L),
                SocialProviderCount(SocialProvider.APPLE, 1L),
                SocialProviderCount(null, 1L),
            )
        }
    }

    "findActiveMembersWithIntroNotesUnder" - {
        "빈 답을 빼고 소개노트가 기준보다 적은 이용 중인 회원을 퀴즈 시작 여부와 함께 찾는다" {
            fun writeIntroNotes(memberId: Long, vararg answers: String) = answers.forEachIndexed { index, answer ->
                introNoteRepository.save(IntroNote.create(memberId, IntroQuestion.entries[index], answer))
            }
            val noNote = save("no-note")
            val blankNotes = save("blank-notes")
            writeIntroNotes(blankNotes.id, "답", "답", " ")
            quizProgressRepository.save(QuizProgressFixture.create(memberId = blankNotes.id))
            writeIntroNotes(save("enough").id, "답", "답", "답")
            save("left", status = MemberStatus.LEFT)
            save("pending", status = MemberStatus.PENDING, joinedAt = null)
            save("admin", role = MemberRole.ADMIN)

            memberRepository.findActiveMembersWithIntroNotesUnder(3L) shouldContainExactlyInAnyOrder listOf(
                QuizBlockedMember(noNote.id, "no-note", JOINED_AT, introNoteCount = 0L, hasStartedQuiz = false),
                QuizBlockedMember(blankNotes.id, "blank-notes", JOINED_AT, introNoteCount = 2L, hasStartedQuiz = true),
            )
        }
    }
})
