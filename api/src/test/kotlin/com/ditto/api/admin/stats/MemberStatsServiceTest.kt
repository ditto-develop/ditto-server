package com.ditto.api.admin.stats

import com.ditto.api.admin.stats.dto.StatsFilter
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.member.repository.querydsl.GenderBirthDateCount
import com.ditto.domain.member.repository.querydsl.LeaveReasonCount
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.system.OperationWeek
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

private val TODAY = LocalDate.of(2026, 10, 22)
private val WEEK = OperationWeek.containing(TODAY)

class MemberStatsServiceTest(
    private val memberStatsService: MemberStatsService,
    private val memberRepository: MemberRepository,
    private val quizSetRepository: QuizSetRepository,
    private val quizProgressRepository: QuizProgressRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun save(
        nickname: String,
        joinedAt: LocalDateTime,
        status: MemberStatus = MemberStatus.ACTIVE,
        role: MemberRole = MemberRole.USER,
        birthDate: LocalDate = LocalDate.of(2003, 1, 1),
    ) = memberRepository.save(
        MemberFixture.create(
            nickname = nickname,
            email = "$nickname@ditto.pics",
            status = status,
            role = role,
            gender = Gender.MALE,
            birthDate = birthDate,
            joinedAt = joinedAt,
        ),
    )

    "회원 지표 원본" - {
        "관리자를 빼고 상태별 수와 현재 회원의 성별·생년월일 묶음을 준다" {
            save("a", joinedAt = WEEK.dateTimeRange.start)
            save("left", joinedAt = WEEK.dateTimeRange.start, status = MemberStatus.LEFT)
            save("admin", joinedAt = WEEK.dateTimeRange.start, role = MemberRole.ADMIN)

            val stats = memberStatsService.findStats(StatsFilter(), WEEK, TODAY)

            stats.countsByStatus shouldBe mapOf(MemberStatus.ACTIVE to 1L, MemberStatus.LEFT to 1L)
            stats.adminMemberCount shouldBe 1L
            stats.genderBirthDateCounts shouldContainExactly listOf(
                GenderBirthDateCount(Gender.MALE, LocalDate.of(2003, 1, 1), 1L),
            )
        }

        "가입 시각은 선택한 회차까지 최근 8주 것만 준다" {
            val firstWeekStart = WEEK.minusWeeks(7).dateTimeRange.start
            save("in-first-week", joinedAt = firstWeekStart)
            save("before-window", joinedAt = firstWeekStart.minusSeconds(1))
            save("after-window", joinedAt = WEEK.dateTimeRange.endExclusive)

            val stats = memberStatsService.findStats(StatsFilter(), WEEK, TODAY)

            stats.recentWeekCount shouldBe 8
            stats.recentJoinedAts shouldContainExactly listOf(firstWeekStart)
        }

        "연령대를 고르면 그 구간에 태어난 회원만 센다" {
            save("early-20s", joinedAt = WEEK.dateTimeRange.start, birthDate = LocalDate.of(2004, 1, 1))
            save("thirties", joinedAt = WEEK.dateTimeRange.start, birthDate = LocalDate.of(1994, 1, 1))

            val stats = memberStatsService.findStats(StatsFilter(ageBand = AgeBand.EARLY_20S), WEEK, TODAY)

            stats.countsByStatus shouldBe mapOf(MemberStatus.ACTIVE to 1L)
        }
    }

    "계정 상태와 탈퇴 사유" - {
        "활성은 고른 회차가 아니라 지금 회차까지 최근 회차의 완주자로 센다" {
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = WEEK.dateTimeRange.start,
                    endDate = WEEK.dateTimeRange.start.plusDays(2),
                ),
            )
            val completed = save("completed", joinedAt = WEEK.dateTimeRange.start)
            val progress = QuizProgressFixture.create(memberId = completed.id, quizSetId = quizSet.id, totalCount = 1)
            progress.recordAnswer()
            quizProgressRepository.save(progress)

            val stats = memberStatsService.findStats(StatsFilter(), WEEK.minusWeeks(3), TODAY)

            stats.currentActiveRoundWeeks shouldBe listOf(WEEK.startedOn)
            stats.recentlyCompletedActiveStatusCount shouldBe 1L
        }

        "탈퇴 사유는 고른 회차 주에 탈퇴한 회원만 센다" {
            val selectedWeek = WEEK.minusWeeks(1)
            memberRepository.save(
                MemberFixture.create(
                    nickname = "left-then",
                    email = "left-then@ditto.pics",
                    status = MemberStatus.ACTIVE,
                    joinedAt = WEEK.minusWeeks(3).dateTimeRange.start,
                ).apply { leave("low_usage", selectedWeek.dateTimeRange.start) },
            )
            memberRepository.save(
                MemberFixture.create(
                    nickname = "left-now",
                    email = "left-now@ditto.pics",
                    status = MemberStatus.ACTIVE,
                    joinedAt = WEEK.minusWeeks(3).dateTimeRange.start,
                ).apply { leave("other", WEEK.dateTimeRange.start) },
            )

            val stats = memberStatsService.findStats(StatsFilter(), selectedWeek, TODAY)

            stats.leaveReasonCounts shouldContainExactly listOf(LeaveReasonCount("low_usage", 1L))
        }
    }
})
