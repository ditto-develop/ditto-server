package com.ditto.domain.member.repository

import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.MemberBlock
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.member.repository.querydsl.MemberFilter
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.support.IntegrationTest
import com.ditto.domain.system.OperationWeek
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

// 차단 시각은 저장할 때 감사 기능이 지금으로 채워서 이번 주를 기준으로 본다.
private val WEEK = OperationWeek.containing(LocalDate.now())

class MemberBlockStatsQueryTest(
    private val memberBlockRepository: MemberBlockRepository,
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

    fun block(blockedMemberId: Long): MemberBlock =
        memberBlockRepository.save(MemberBlock.create(blockerId = saveMember(), blockedMemberId = blockedMemberId))

    fun moveCreatedAt(block: MemberBlock, createdAt: LocalDateTime) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE member_block SET created_at = ? WHERE id = ?").use { statement ->
                statement.setObject(1, createdAt)
                statement.setLong(2, block.id)
                statement.executeUpdate()
            }
        }
    }

    "countBlocksCreatedInWeek" - {
        "그 주에 생긴 차단을 세고 다른 주와 관리자가 차단된 건은 뺀다" {
            block(saveMember())
            block(saveMember())
            moveCreatedAt(block(saveMember()), WEEK.dateTimeRange.start.minusSeconds(1))
            block(saveMember(role = MemberRole.ADMIN))

            memberBlockRepository.countBlocksCreatedInWeek(WEEK.startedOn, null, MemberFilter.NONE) shouldBe 2L
        }

        "회원 조건은 차단된 사람에게, 매칭 유형은 차단된 사람이 그 주에 완주한 퀴즈셋으로 건다" {
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
            block(groupParticipant)
            block(saveMember(Gender.FEMALE))
            block(saveMember(Gender.MALE))

            memberBlockRepository.countBlocksCreatedInWeek(
                WEEK.startedOn,
                MatchingType.GROUP,
                MemberFilter(gender = Gender.FEMALE),
            ) shouldBe 1L
        }
    }
})
