package com.ditto.domain.match.repository

import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.MatchCandidateFixture
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.InvitationStatus
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.querydsl.GroupMatchMemberCount
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
    fun saveCandidatePair(quizSet: QuizSet, oneMemberId: Long, otherMemberId: Long) {
        matchCandidateRepository.save(MatchCandidateFixture.create(oneMemberId, otherMemberId, quizSet.id))
        matchCandidateRepository.save(MatchCandidateFixture.create(otherMemberId, oneMemberId, quizSet.id))
    }

    fun saveGroup(quizSet: QuizSet, acceptedCount: Int = 0): Long =
        groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSet.id, acceptedCount = acceptedCount)).id

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
        "그 주 활성 셋에서 후보를 받은 회원을 성별로 한 번씩 센다" {
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

            // 두 쪽을 따로 세서 같은 성별이 두 행으로 올 수 있다.
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

    "countMembersInWeek" - {
        "그 주 그룹 초대를 그룹 성사 여부·상태·성별로 묶는다" {
            val quizSet = saveQuizSet(matchingType = MatchingType.GROUP)
            val formedGroupId = saveGroup(quizSet, acceptedCount = 3)
            repeat(3) { saveInvitation(formedGroupId, saveMember(), InvitationStatus.ACCEPTED) }
            saveInvitation(formedGroupId, saveMember(Gender.FEMALE), InvitationStatus.DECLINED)
            val unformedGroupId = saveGroup(quizSet, acceptedCount = 1)
            saveInvitation(unformedGroupId, saveMember(Gender.FEMALE), InvitationStatus.ACCEPTED)
            saveInvitation(unformedGroupId, saveMember(Gender.FEMALE, MemberRole.ADMIN), InvitationStatus.ACCEPTED)
            saveInvitation(saveGroup(saveQuizSet(weekStartedOn = WEEK.minusWeeks(1))), saveMember())

            val counts = groupMatchMemberRepository.countMembersInWeek(WEEK, MemberFilter.NONE)

            counts shouldContainExactlyInAnyOrder listOf(
                GroupMatchMemberCount(true, InvitationStatus.ACCEPTED, Gender.MALE, 3L),
                GroupMatchMemberCount(true, InvitationStatus.DECLINED, Gender.FEMALE, 1L),
                GroupMatchMemberCount(false, InvitationStatus.ACCEPTED, Gender.FEMALE, 1L),
            )
        }
    }
})
