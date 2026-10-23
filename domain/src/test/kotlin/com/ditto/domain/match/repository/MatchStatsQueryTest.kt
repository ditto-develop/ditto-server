package com.ditto.domain.match.repository

import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.MatchCandidateFixture
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.InvitationStatus
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.querydsl.ActiveGroupMemberCount
import com.ditto.domain.match.repository.querydsl.GroupMatchMemberCount
import com.ditto.domain.match.repository.querydsl.GroupMatchScore
import com.ditto.domain.match.repository.querydsl.MatchCandidateCount
import com.ditto.domain.match.repository.querydsl.PersonalMatchMemberCount
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.member.repository.querydsl.MemberFilter
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import java.time.LocalDate
import javax.sql.DataSource

private val WEEK = LocalDate.of(2026, 10, 19)

class MatchStatsQueryTest(
    private val matchCandidateRepository: MatchCandidateRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val quizSetRepository: QuizSetRepository,
    private val memberRepository: MemberRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    var memberIndex = 0

    fun saveMember(gender: Gender? = Gender.MALE, role: MemberRole = MemberRole.USER): Long {
        memberIndex += 1
        val nickname = "m$memberIndex"
        return memberRepository.save(
            MemberFixture.create(nickname = nickname, email = "$nickname@ditto.pics", gender = gender, role = role),
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

    // 후보 행은 두 방향으로 저장된다.
    fun saveCandidatePair(quizSet: QuizSet, oneMemberId: Long, otherMemberId: Long, score: Double = 80.0) {
        matchCandidateRepository.save(MatchCandidateFixture.create(oneMemberId, otherMemberId, quizSet.id, score))
        matchCandidateRepository.save(MatchCandidateFixture.create(otherMemberId, oneMemberId, quizSet.id, score))
    }

    fun saveGroup(quizSet: QuizSet, acceptedCount: Int = 0, score: Double = 80.0): Long =
        groupMatchRepository.save(
            GroupMatchFixture.create(quizSetId = quizSet.id, score = score, acceptedCount = acceptedCount),
        ).id

    fun saveInvitation(groupMatchId: Long, memberId: Long, status: InvitationStatus = InvitationStatus.PENDING) {
        val invitation = GroupMatchMember.candidate(groupMatchId, memberId)
        when (status) {
            InvitationStatus.ACCEPTED -> invitation.accept()
            InvitationStatus.DECLINED -> invitation.decline()
            InvitationStatus.PENDING -> Unit
        }
        groupMatchMemberRepository.save(invitation)
    }

    "countCandidateOwnersInWeek" - {
        "그 주 활성 퀴즈셋에서 후보를 받은 회원을 성별로 한 번씩 센다" {
            val quizSet = saveQuizSet()
            val man = saveMember(Gender.MALE)
            val woman = saveMember(Gender.FEMALE)
            val otherWoman = saveMember(Gender.FEMALE)
            saveCandidatePair(quizSet, man, woman)
            saveCandidatePair(quizSet, man, otherWoman)
            saveCandidatePair(saveQuizSet(isActive = false), saveMember(), saveMember(Gender.FEMALE))
            saveCandidatePair(saveQuizSet(weekStartedOn = WEEK.minusWeeks(1)), saveMember(), saveMember(Gender.FEMALE))

            val counts = matchCandidateRepository.countCandidateOwnersInWeek(WEEK, MemberFilter.NONE)

            counts shouldContainExactlyInAnyOrder listOf(
                MatchCandidateCount(Gender.MALE, 1L),
                MatchCandidateCount(Gender.FEMALE, 2L),
            )
        }

        "관리자는 빼고 회원 조건을 건다" {
            val quizSet = saveQuizSet()
            saveCandidatePair(quizSet, saveMember(Gender.MALE), saveMember(Gender.FEMALE))
            saveCandidatePair(quizSet, saveMember(Gender.FEMALE, MemberRole.ADMIN), saveMember(Gender.MALE))

            val counts = matchCandidateRepository.countCandidateOwnersInWeek(WEEK, MemberFilter(gender = Gender.FEMALE))

            counts shouldContainExactlyInAnyOrder listOf(MatchCandidateCount(Gender.FEMALE, 1L))
        }
    }

    "countAcceptedMembersInWeek" - {
        "성사된 매칭의 두 사람을 각각 성별로 센다" {
            val quizSet = saveQuizSet()
            personalMatchRepository.save(
                PersonalMatchFixture.create(
                    saveMember(Gender.MALE),
                    saveMember(Gender.FEMALE),
                    quizSet.id,
                    PersonalMatchStatus.ACCEPTED,
                ),
            )
            personalMatchRepository.save(
                PersonalMatchFixture.create(
                    saveMember(Gender.FEMALE),
                    saveMember(Gender.FEMALE),
                    quizSet.id,
                    PersonalMatchStatus.ACCEPTED,
                ),
            )
            personalMatchRepository.save(PersonalMatchFixture.create(saveMember(), saveMember(), quizSet.id))
            personalMatchRepository.save(
                PersonalMatchFixture.create(saveMember(), saveMember(), quizSet.id, PersonalMatchStatus.CANCELLED),
            )
            personalMatchRepository.save(
                PersonalMatchFixture.create(
                    saveMember(),
                    saveMember(),
                    saveQuizSet(weekStartedOn = WEEK.minusWeeks(1)).id,
                    PersonalMatchStatus.ACCEPTED,
                ),
            )

            val counts = personalMatchRepository.countAcceptedMembersInWeek(WEEK, MemberFilter.NONE)

            // 같은 성별이 두 행으로 올 수 있다.
            counts shouldContainExactlyInAnyOrder listOf(
                PersonalMatchMemberCount(Gender.MALE, 1L),
                PersonalMatchMemberCount(Gender.FEMALE, 1L),
                PersonalMatchMemberCount(Gender.FEMALE, 2L),
            )
        }

        "회원 조건은 두 사람에게 따로 건다" {
            val quizSet = saveQuizSet()
            personalMatchRepository.save(
                PersonalMatchFixture.create(
                    saveMember(Gender.MALE),
                    saveMember(Gender.FEMALE),
                    quizSet.id,
                    PersonalMatchStatus.ACCEPTED,
                ),
            )

            val counts = personalMatchRepository.countAcceptedMembersInWeek(WEEK, MemberFilter(gender = Gender.FEMALE))

            counts shouldContainExactlyInAnyOrder listOf(PersonalMatchMemberCount(Gender.FEMALE, 1L))
        }
    }

    "countInvitedMembersInWeek" - {
        "그 주 그룹 초대를 그룹 성사 여부·상태·성별로 묶는다" {
            val quizSet = saveQuizSet(matchingType = MatchingType.GROUP)
            val formedGroupId = saveGroup(quizSet, acceptedCount = 3)
            repeat(3) { saveInvitation(formedGroupId, saveMember(), InvitationStatus.ACCEPTED) }
            saveInvitation(formedGroupId, saveMember(Gender.FEMALE), InvitationStatus.DECLINED)
            val unformedGroupId = saveGroup(quizSet, acceptedCount = 1)
            saveInvitation(unformedGroupId, saveMember(Gender.FEMALE), InvitationStatus.ACCEPTED)
            saveInvitation(unformedGroupId, saveMember(Gender.FEMALE, MemberRole.ADMIN), InvitationStatus.ACCEPTED)
            saveInvitation(saveGroup(saveQuizSet(weekStartedOn = WEEK.minusWeeks(1))), saveMember())

            val counts = groupMatchMemberRepository.countInvitedMembersInWeek(WEEK, MemberFilter.NONE)

            counts shouldContainExactlyInAnyOrder listOf(
                GroupMatchMemberCount(true, InvitationStatus.ACCEPTED, Gender.MALE, 3L),
                GroupMatchMemberCount(true, InvitationStatus.DECLINED, Gender.FEMALE, 1L),
                GroupMatchMemberCount(false, InvitationStatus.ACCEPTED, Gender.FEMALE, 1L),
            )
        }
    }

    "findPairScoresInWeek" - {
        "두 방향 행 중 한 번만 세고 비활성 셋과 다른 주는 뺀다" {
            val quizSet = saveQuizSet()
            saveCandidatePair(quizSet, saveMember(), saveMember(), score = 70.0)
            saveCandidatePair(quizSet, saveMember(), saveMember(), score = 55.5)
            saveCandidatePair(saveQuizSet(isActive = false), saveMember(), saveMember())
            saveCandidatePair(saveQuizSet(weekStartedOn = WEEK.minusWeeks(1)), saveMember(), saveMember())

            matchCandidateRepository.findPairScoresInWeek(WEEK) shouldContainExactlyInAnyOrder listOf(70.0, 55.5)
        }
    }

    "findAcceptedPairScoresInWeek" - {
        "성사된 1:1 쌍의 후보 점수만 준다" {
            val quizSet = saveQuizSet()
            val (one, other) = saveMember() to saveMember()
            saveCandidatePair(quizSet, one, other, score = 91.0)
            personalMatchRepository.save(
                PersonalMatchFixture.create(other, one, quizSet.id, PersonalMatchStatus.ACCEPTED),
            )
            val (pending, partner) = saveMember() to saveMember()
            saveCandidatePair(quizSet, pending, partner, score = 40.0)
            personalMatchRepository.save(PersonalMatchFixture.create(pending, partner, quizSet.id))

            matchCandidateRepository.findAcceptedPairScoresInWeek(WEEK) shouldContainExactlyInAnyOrder listOf(91.0)
        }

        "후보를 다시 만들어 후보 행이 없는 성사 쌍은 null 로 준다" {
            val quizSet = saveQuizSet()
            personalMatchRepository.save(
                PersonalMatchFixture.create(saveMember(), saveMember(), quizSet.id, PersonalMatchStatus.ACCEPTED),
            )

            matchCandidateRepository.findAcceptedPairScoresInWeek(WEEK) shouldContainExactlyInAnyOrder listOf(null)
        }
    }

    "findGroupScoresInWeek" - {
        "후보 그룹마다 점수와 성사 여부를 준다" {
            val quizSet = saveQuizSet(matchingType = MatchingType.GROUP)
            saveGroup(quizSet, acceptedCount = 3, score = 66.0)
            saveGroup(quizSet, score = 61.0)
            saveGroup(saveQuizSet(matchingType = MatchingType.GROUP, isActive = false))

            groupMatchRepository.findGroupScoresInWeek(WEEK) shouldContainExactlyInAnyOrder listOf(
                GroupMatchScore(isGroupActive = true, score = 66.0),
                GroupMatchScore(isGroupActive = false, score = 61.0),
            )
        }
    }

    "countAcceptedMembersPerActiveGroupInWeek" - {
        "성사된 그룹의 수락자만 그룹·성별로 세고 관리자도 센다" {
            val quizSet = saveQuizSet(matchingType = MatchingType.GROUP)
            val active = saveGroup(quizSet, acceptedCount = 3)
            saveInvitation(active, saveMember(Gender.MALE), InvitationStatus.ACCEPTED)
            saveInvitation(active, saveMember(Gender.FEMALE), InvitationStatus.ACCEPTED)
            saveInvitation(active, saveMember(Gender.FEMALE, MemberRole.ADMIN), InvitationStatus.ACCEPTED)
            saveInvitation(active, saveMember(Gender.MALE), InvitationStatus.DECLINED)
            val unformed = saveGroup(quizSet)
            saveInvitation(unformed, saveMember(Gender.MALE), InvitationStatus.ACCEPTED)

            groupMatchMemberRepository.countAcceptedMembersPerActiveGroupInWeek(WEEK) shouldContainExactlyInAnyOrder
                listOf(
                    ActiveGroupMemberCount(active, Gender.MALE, 1L),
                    ActiveGroupMemberCount(active, Gender.FEMALE, 2L),
                )
        }
    }
})
