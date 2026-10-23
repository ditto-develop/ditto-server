package com.ditto.domain.quiz.repository

import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.member.repository.querydsl.MemberFilter
import com.ditto.domain.notification.MemberDeviceFixture
import com.ditto.domain.notification.repository.MemberDeviceRepository
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizProgressStatus
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.querydsl.CompletedAgainCount
import com.ditto.domain.quiz.repository.querydsl.CompletionWithJoinedAt
import com.ditto.domain.quiz.repository.querydsl.MatchedCompletionCount
import com.ditto.domain.quiz.repository.querydsl.QuizProgressCount
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

private val WEEK = LocalDate.of(2026, 10, 19)

class QuizProgressStatsQueryTest(
    private val quizProgressRepository: QuizProgressRepository,
    private val quizSetRepository: QuizSetRepository,
    private val memberRepository: MemberRepository,
    private val memberDeviceRepository: MemberDeviceRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    var memberIndex = 0

    fun saveMember(
        gender: Gender? = Gender.MALE,
        role: MemberRole = MemberRole.USER,
        status: MemberStatus = MemberStatus.ACTIVE,
        joinedAt: LocalDateTime? = LocalDateTime.of(2026, 10, 1, 12, 0),
    ): Long {
        memberIndex += 1
        val nickname = "m$memberIndex"
        return memberRepository.save(
            MemberFixture.create(
                nickname = nickname,
                email = "$nickname@ditto.pics",
                gender = gender,
                role = role,
                status = status,
                joinedAt = joinedAt,
            ),
        ).id
    }

    fun saveQuizSet(
        weekStartedOn: LocalDate = WEEK,
        matchingType: MatchingType = MatchingType.ONE_TO_ONE,
        isActive: Boolean = true,
    ): QuizSet = quizSetRepository.save(
        QuizSetFixture.create(
            startDate = weekStartedOn.atStartOfDay(),
            endDate = weekStartedOn.plusDays(2).atTime(23, 59, 59),
            matchingType = matchingType,
            isActive = isActive,
        ),
    )

    fun saveProgress(memberId: Long, quizSet: QuizSet, completed: Boolean = true) {
        val progress = QuizProgressFixture.create(memberId = memberId, quizSetId = quizSet.id, totalCount = 2)
        repeat(if (completed) 2 else 1) { progress.recordAnswer() }
        quizProgressRepository.save(progress)
    }

    "countProgressesInWeek" - {
        "그 주 활성 셋의 진행을 유형·상태·성별로 묶는다" {
            val oneToOne = saveQuizSet(matchingType = MatchingType.ONE_TO_ONE)
            val group = saveQuizSet(matchingType = MatchingType.GROUP)
            saveProgress(saveMember(Gender.MALE), oneToOne)
            saveProgress(saveMember(Gender.MALE), oneToOne)
            saveProgress(saveMember(Gender.FEMALE), oneToOne, completed = false)
            saveProgress(saveMember(null), group)

            val counts = quizProgressRepository.countProgressesInWeek(WEEK, null, MemberFilter.NONE)

            counts shouldContainExactlyInAnyOrder listOf(
                QuizProgressCount(MatchingType.ONE_TO_ONE, QuizProgressStatus.COMPLETED, Gender.MALE, 2L),
                QuizProgressCount(MatchingType.ONE_TO_ONE, QuizProgressStatus.IN_PROGRESS, Gender.FEMALE, 1L),
                QuizProgressCount(MatchingType.GROUP, QuizProgressStatus.COMPLETED, null, 1L),
            )
        }

        "비활성 셋·다른 주·관리자는 빼고 유형과 회원 조건을 건다" {
            val oneToOne = saveQuizSet(matchingType = MatchingType.ONE_TO_ONE)
            saveProgress(saveMember(Gender.FEMALE), oneToOne)
            saveProgress(saveMember(Gender.MALE), oneToOne)
            saveProgress(saveMember(Gender.FEMALE), saveQuizSet(matchingType = MatchingType.GROUP))
            saveProgress(saveMember(Gender.FEMALE), saveQuizSet(isActive = false))
            saveProgress(saveMember(Gender.FEMALE), saveQuizSet(weekStartedOn = WEEK.minusWeeks(1)))
            saveProgress(saveMember(Gender.FEMALE, role = MemberRole.ADMIN), oneToOne)

            val counts = quizProgressRepository.countProgressesInWeek(
                WEEK,
                MatchingType.ONE_TO_ONE,
                MemberFilter(gender = Gender.FEMALE),
            )

            counts shouldContainExactlyInAnyOrder listOf(
                QuizProgressCount(MatchingType.ONE_TO_ONE, QuizProgressStatus.COMPLETED, Gender.FEMALE, 1L),
            )
        }
    }

    "countMembersCompletedAgain" - {
        "이전 주 완주자 중 이번 주에도 완주한 회원을 이전 주 유형과 이번 주 유형별로 센다" {
            val previousOneToOne = saveQuizSet(weekStartedOn = WEEK.minusWeeks(1))
            val previousGroup = saveQuizSet(weekStartedOn = WEEK.minusWeeks(1), matchingType = MatchingType.GROUP)
            val thisOneToOne = saveQuizSet()
            val thisGroup = saveQuizSet(matchingType = MatchingType.GROUP)
            val stayed = saveMember()
            saveProgress(stayed, previousOneToOne)
            saveProgress(stayed, thisOneToOne)
            val switched = saveMember()
            saveProgress(switched, previousOneToOne)
            saveProgress(switched, thisGroup)
            val quitMidway = saveMember()
            saveProgress(quitMidway, previousGroup)
            saveProgress(quitMidway, thisGroup, completed = false)
            saveProgress(saveMember(), previousGroup)

            val counts = quizProgressRepository.countMembersCompletedAgain(WEEK.minusWeeks(1), WEEK, MemberFilter.NONE)

            counts shouldContainExactlyInAnyOrder listOf(
                CompletedAgainCount(MatchingType.ONE_TO_ONE, MatchingType.ONE_TO_ONE, 1L),
                CompletedAgainCount(MatchingType.ONE_TO_ONE, MatchingType.GROUP, 1L),
            )
        }

        "회원 조건은 이전 주 완주자에게 건다" {
            val previous = saveQuizSet(weekStartedOn = WEEK.minusWeeks(1))
            val current = saveQuizSet()
            listOf(Gender.MALE, Gender.FEMALE).forEach { gender ->
                val memberId = saveMember(gender)
                saveProgress(memberId, previous)
                saveProgress(memberId, current)
            }

            val counts = quizProgressRepository.countMembersCompletedAgain(
                WEEK.minusWeeks(1),
                WEEK,
                MemberFilter(gender = Gender.FEMALE),
            )

            counts shouldContainExactlyInAnyOrder listOf(
                CompletedAgainCount(MatchingType.ONE_TO_ONE, MatchingType.ONE_TO_ONE, 1L),
            )
        }
    }

    "countMembersCompletedInWeeks" - {
        "여러 주에 완주한 회원은 한 명으로 세고 완주하지 않은 진행은 세지 않는다" {
            val thisWeek = saveQuizSet()
            val lastWeek = saveQuizSet(weekStartedOn = WEEK.minusWeeks(1))
            val everyWeek = saveMember()
            saveProgress(everyWeek, thisWeek)
            saveProgress(everyWeek, lastWeek)
            saveProgress(saveMember(), lastWeek)
            saveProgress(saveMember(), thisWeek, completed = false)

            quizProgressRepository.countMembersCompletedInWeeks(
                listOf(WEEK, WEEK.minusWeeks(1)),
                null,
                MemberFilter.NONE,
            ) shouldBe 2L
        }

        "주 목록이 비면 0이다" {
            quizProgressRepository.countMembersCompletedInWeeks(emptyList(), null, MemberFilter.NONE) shouldBe 0L
        }
    }

    "countMembersCompletedInWeeksWithPushDevice" - {
        "완주한 회원 중 기기 토큰이 있는 회원만 한 번씩 센다" {
            val quizSet = saveQuizSet()
            val twoDevices = saveMember()
            val noDevice = saveMember()
            listOf(twoDevices, noDevice).forEach { saveProgress(it, quizSet) }
            memberDeviceRepository.save(MemberDeviceFixture.create(memberId = twoDevices, token = "token-1"))
            memberDeviceRepository.save(MemberDeviceFixture.create(memberId = twoDevices, token = "token-2"))

            quizProgressRepository.countMembersCompletedInWeeksWithPushDevice(
                listOf(WEEK),
                null,
                MemberFilter.NONE,
            ) shouldBe 1L
        }

        "주 목록이 비면 0이다" {
            quizProgressRepository.countMembersCompletedInWeeksWithPushDevice(
                emptyList(),
                null,
                MemberFilter.NONE,
            ) shouldBe 0L
        }
    }

    "countAdminMembersCompletedInWeek" - {
        "그 주 활성 셋을 완주한 관리자만 센다" {
            val quizSet = saveQuizSet()
            saveProgress(saveMember(role = MemberRole.ADMIN), quizSet)
            saveProgress(saveMember(role = MemberRole.ADMIN), quizSet, completed = false)
            saveProgress(saveMember(role = MemberRole.ADMIN), saveQuizSet(weekStartedOn = WEEK.minusWeeks(1)))
            saveProgress(saveMember(role = MemberRole.ADMIN), saveQuizSet(isActive = false))
            saveProgress(saveMember(), quizSet)

            quizProgressRepository.countAdminMembersCompletedInWeek(WEEK) shouldBe 1L
        }
    }

    "countActiveStatusMembersCompletedInWeeks" - {
        "여러 주에 완주한 회원은 한 번만 세고 지금 ACTIVE 가 아니거나 가입하지 않은 회원은 빼고 유형은 따지지 않는다" {
            val previousWeek = WEEK.minusWeeks(1)
            val oneToOne = saveQuizSet()
            val group = saveQuizSet(weekStartedOn = previousWeek, matchingType = MatchingType.GROUP)
            val twice = saveMember()
            saveProgress(twice, oneToOne)
            saveProgress(twice, group)
            saveProgress(saveMember(), group)
            saveProgress(saveMember(status = MemberStatus.SUSPENDED), oneToOne)
            saveProgress(saveMember(), oneToOne, completed = false)
            saveProgress(saveMember(role = MemberRole.ADMIN), oneToOne)
            saveProgress(saveMember(joinedAt = null), oneToOne)

            quizProgressRepository.countActiveStatusMembersCompletedInWeeks(
                listOf(WEEK, previousWeek),
                MemberFilter.NONE,
            ) shouldBe 2L
        }

        "주 목록이 비면 0이다" {
            quizProgressRepository.countActiveStatusMembersCompletedInWeeks(emptyList(), MemberFilter.NONE) shouldBe 0L
        }
    }

    "성사된 회원의 완주와 재참여" - {
        "1:1 은 ACCEPTED 쌍, 그룹은 성사된 그룹 수락자만 성사로 센다" {
            val previousOneToOne = saveQuizSet(weekStartedOn = WEEK.minusWeeks(1))
            val previousGroup = saveQuizSet(weekStartedOn = WEEK.minusWeeks(1), matchingType = MatchingType.GROUP)
            val current = saveQuizSet()
            val (one, other) = saveMember() to saveMember()
            val pending = saveMember()
            listOf(one, other, pending).forEach { saveProgress(it, previousOneToOne) }
            personalMatchRepository.save(
                PersonalMatchFixture.create(one, other, previousOneToOne.id, PersonalMatchStatus.ACCEPTED),
            )
            personalMatchRepository.save(PersonalMatchFixture.create(pending, saveMember(), previousOneToOne.id))
            val (grouped, unformed) = saveMember() to saveMember()
            listOf(grouped, unformed).forEach { saveProgress(it, previousGroup) }
            val formedGroup = groupMatchRepository.save(
                GroupMatchFixture.create(quizSetId = previousGroup.id, acceptedCount = 3),
            )
            groupMatchMemberRepository.save(GroupMatchMember.candidate(formedGroup.id, grouped).apply { accept() })
            val unformedGroup = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = previousGroup.id))
            groupMatchMemberRepository.save(GroupMatchMember.candidate(unformedGroup.id, unformed).apply { accept() })
            listOf(one, grouped, unformed).forEach { saveProgress(it, current) }

            quizProgressRepository.countMatchedMembersCompletedInWeek(
                WEEK.minusWeeks(1),
                MemberFilter.NONE,
            ) shouldContainExactlyInAnyOrder listOf(
                MatchedCompletionCount(MatchingType.ONE_TO_ONE, 2L),
                MatchedCompletionCount(MatchingType.GROUP, 1L),
            )
            quizProgressRepository.countMatchedMembersCompletedAgain(
                WEEK.minusWeeks(1),
                WEEK,
                MemberFilter.NONE,
            ) shouldContainExactlyInAnyOrder listOf(
                CompletedAgainCount(MatchingType.ONE_TO_ONE, MatchingType.ONE_TO_ONE, 1L),
                CompletedAgainCount(MatchingType.GROUP, MatchingType.ONE_TO_ONE, 1L),
            )
        }
    }

    "findCompletionsWithJoinedAtInWeeks" - {
        "가입 시각이 범위 안인 회원의 완주마다 가입 시각과 회차를 준다" {
            val previous = saveQuizSet(weekStartedOn = WEEK.minusWeeks(1))
            val current = saveQuizSet()
            val joinedAt = LocalDateTime.of(2026, 10, 13, 9, 0)
            val newcomer = saveMember(joinedAt = joinedAt)
            saveProgress(newcomer, previous)
            saveProgress(newcomer, current)
            saveProgress(saveMember(joinedAt = joinedAt), current, completed = false)
            saveProgress(saveMember(joinedAt = LocalDateTime.of(2026, 9, 1, 9, 0)), current)

            quizProgressRepository.findCompletionsWithJoinedAtInWeeks(
                listOf(WEEK.minusWeeks(1), WEEK),
                LocalDateTime.of(2026, 10, 12, 0, 0)..<LocalDateTime.of(2026, 10, 26, 0, 0),
                MemberFilter.NONE,
            ) shouldContainExactlyInAnyOrder listOf(
                CompletionWithJoinedAt(joinedAt, WEEK.minusWeeks(1)),
                CompletionWithJoinedAt(joinedAt, WEEK),
            )
        }
    }
})
