package com.ditto.domain.memberreport.repository

import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.member.repository.querydsl.MemberFilter
import com.ditto.domain.memberreport.MemberReportFixture
import com.ditto.domain.memberreport.entity.MemberReport
import com.ditto.domain.memberreport.entity.MemberReportReason
import com.ditto.domain.memberreport.entity.MemberReportStatus
import com.ditto.domain.memberreport.repository.querydsl.MemberReportCount
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.support.IntegrationTest
import com.ditto.domain.system.OperationWeek
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

// 접수 시각은 저장할 때 감사 기능이 지금으로 채워서 이번 주를 기준으로 본다.
private val WEEK = OperationWeek.containing(LocalDate.now())

class MemberReportStatsQueryTest(
    private val memberReportRepository: MemberReportRepository,
    private val memberRepository: MemberRepository,
    private val quizSetRepository: QuizSetRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val dataSource: DataSource,
) : IntegrationTest(dataSource, {

    var memberIndex = 0

    fun saveMember(gender: Gender = Gender.MALE, role: MemberRole = MemberRole.USER): Long {
        memberIndex += 1
        val nickname = "m$memberIndex"
        return memberRepository.save(
            MemberFixture.create(nickname = nickname, email = "$nickname@ditto.pics", gender = gender, role = role),
        ).id
    }

    // 기타 사유는 상세 설명이 있어야 접수된다.
    fun report(reportedMemberId: Long, vararg reasons: MemberReportReason): MemberReport =
        memberReportRepository.save(
            MemberReportFixture.create(
                reporterId = saveMember(),
                reportedMemberId = reportedMemberId,
                reasons = reasons.toSet(),
                detail = "상세",
            ),
        )

    fun moveReceivedAt(report: MemberReport, receivedAt: LocalDateTime) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE member_report SET created_at = ? WHERE id = ?").use { statement ->
                statement.setObject(1, receivedAt)
                statement.setLong(2, report.id)
                statement.executeUpdate()
            }
        }
    }

    "countReceivedInWeek" - {
        "그 주 접수된 신고를 사유 조합·상태로 묶고 다른 주와 관리자 피신고는 뺀다" {
            report(saveMember(), MemberReportReason.MONEY_DEMAND, MemberReportReason.ETC)
            report(saveMember(), MemberReportReason.ETC, MemberReportReason.MONEY_DEMAND)
            val reviewed = report(saveMember(), MemberReportReason.FALSE_INFORMATION)
            memberReportRepository.completeReview(
                reviewed.id,
                MemberReportStatus.REJECTED,
                1L,
                "관리자",
                LocalDateTime.now(),
                null,
            )
            moveReceivedAt(report(saveMember(), MemberReportReason.ETC), WEEK.dateTimeRange.start.minusSeconds(1))
            report(saveMember(role = MemberRole.ADMIN), MemberReportReason.ETC)

            val counts = memberReportRepository.countReceivedInWeek(WEEK.startedOn, null, MemberFilter.NONE)

            counts shouldContainExactlyInAnyOrder listOf(
                MemberReportCount(
                    setOf(MemberReportReason.MONEY_DEMAND, MemberReportReason.ETC),
                    MemberReportStatus.RECEIVED,
                    2L,
                ),
                MemberReportCount(setOf(MemberReportReason.FALSE_INFORMATION), MemberReportStatus.REJECTED, 1L),
            )
        }

        "회원 조건은 피신고자에게, 매칭 유형은 피신고자가 그 주에 완주한 퀴즈셋으로 건다" {
            val groupSet = quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = WEEK.dateTimeRange.start,
                    endDate = WEEK.startedOn.plusDays(2).atTime(23, 59, 59),
                    matchingType = MatchingType.GROUP,
                ),
            )
            val groupParticipant = saveMember(Gender.FEMALE)
            quizProgressRepository.save(
                QuizProgressFixture.create(memberId = groupParticipant, quizSetId = groupSet.id, totalCount = 1)
                    .apply { recordAnswer() },
            )
            report(groupParticipant, MemberReportReason.ETC)
            report(saveMember(Gender.FEMALE), MemberReportReason.ETC)
            report(saveMember(Gender.MALE), MemberReportReason.ETC)

            val femaleGroupOnly = memberReportRepository.countReceivedInWeek(
                WEEK.startedOn,
                MatchingType.GROUP,
                MemberFilter(gender = Gender.FEMALE),
            )

            femaleGroupOnly shouldContainExactlyInAnyOrder listOf(
                MemberReportCount(setOf(MemberReportReason.ETC), MemberReportStatus.RECEIVED, 1L),
            )
        }
    }
})
