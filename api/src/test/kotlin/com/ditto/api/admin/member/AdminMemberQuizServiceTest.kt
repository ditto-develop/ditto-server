package com.ditto.api.admin.member

import com.ditto.api.admin.quiz.dto.GroupResponse
import com.ditto.api.admin.quiz.dto.MatchMissReason
import com.ditto.api.admin.quiz.dto.PersonalRequestState
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.MatchCandidate
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

class AdminMemberQuizServiceTest(
    private val adminMemberQuizService: AdminMemberQuizService,
    private val quizSetRepository: QuizSetRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val memberRepository: MemberRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveQuizSet(
        title: String,
        weekStart: LocalDateTime,
        matchingType: MatchingType = MatchingType.ONE_TO_ONE,
    ): Long {
        val quizSet = QuizSetFixture.create(
            title = title,
            startDate = weekStart,
            endDate = weekStart.plusDays(2),
            matchingType = matchingType,
        )
        return quizSetRepository.save(quizSet).id
    }

    fun saveQuizSet(matchingType: MatchingType = MatchingType.ONE_TO_ONE): Long =
        saveQuizSet("퀴즈셋", LocalDateTime.of(2026, 4, 6, 0, 0), matchingType)

    fun saveMember(nickname: String, gender: Gender = Gender.MALE, status: MemberStatus = MemberStatus.ACTIVE): Long =
        memberRepository.save(
            MemberFixture.create(nickname = nickname, email = "$nickname@ex.com", status = status, gender = gender),
        ).id

    fun saveCompleted(memberId: Long, quizSetId: Long) {
        val progress = QuizProgressFixture.create(memberId = memberId, quizSetId = quizSetId, totalCount = 2)
        repeat(2) { progress.recordAnswer() }
        quizProgressRepository.save(progress)
    }

    fun saveCandidatePair(quizSetId: Long, memberAId: Long, memberBId: Long, score: Double) {
        matchCandidateRepository.save(MatchCandidate.create(memberAId, memberBId, quizSetId, score, 1, 2))
        matchCandidateRepository.save(MatchCandidate.create(memberBId, memberAId, quizSetId, score, 1, 2))
    }

    "퀴즈셋 행" - {
        "참여하지 않은 셋도 최신 주부터 행으로 남기고 진행·매칭은 비운다" {
            val older = saveQuizSet("지난주", LocalDateTime.of(2026, 3, 30, 0, 0))
            val newer = saveQuizSet("이번주", LocalDateTime.of(2026, 4, 6, 0, 0))
            val me = saveMember("나")
            quizProgressRepository.save(QuizProgressFixture.create(memberId = me, quizSetId = older, totalCount = 2))

            val view = adminMemberQuizService.getMemberQuizzes(me)

            view.rows.map { it.quizSet.id } shouldContainExactly listOf(newer, older)
            view.participatedCount shouldBe 1
            view.rows.first().progress.shouldBeNull()
            view.rows.first().matching.shouldBeNull()
            view.rows.last().matching?.missReason shouldBe MatchMissReason.NOT_COMPLETED
        }

        "없는 회원이면 NOT_FOUND 경고" {
            val exception = shouldThrow<WarnException> { adminMemberQuizService.getMemberQuizzes(99999L) }

            exception.message shouldBe "없는 회원입니다: #99999"
        }
    }

    "1:1" - {
        "저장된 후보를 점수 순으로 담고 신청 상태를 내 쪽에서 본 값으로 붙인다" {
            val quizSetId = saveQuizSet()
            val me = saveMember("나", gender = Gender.FEMALE)
            val sentTo = saveMember("신청보냄")
            val other = saveMember("후보만")
            listOf(me, sentTo, other).forEach { saveCompleted(it, quizSetId) }
            saveCandidatePair(quizSetId, me, sentTo, score = 50.0)
            saveCandidatePair(quizSetId, me, other, score = 100.0)
            personalMatchRepository.save(PersonalMatchFixture.create(me, sentTo, quizSetId))

            val matching = adminMemberQuizService.getMemberQuizzes(me).rows.single().matching

            matching?.personalCandidates?.map { it.otherNickname to it.requestState } shouldBe
                listOf("후보만" to null, "신청보냄" to PersonalRequestState.SENT)
            matching?.missReason.shouldBeNull()
        }

        "셋에 후보가 하나도 없어도 매칭 전으로 단정하지 않고 풀 단계 이유를 계산하지 않는다" {
            val quizSetId = saveQuizSet()
            val me = saveMember("나")
            saveCompleted(me, quizSetId)

            val missReason = adminMemberQuizService.getMemberQuizzes(me).rows.single().matching?.missReason

            missReason shouldBe MatchMissReason.POOL_REASON_NOT_COMPUTED
        }

        "후보가 만들어졌는데 내 후보가 없으면 풀 단계 이유는 계산하지 않는다" {
            val quizSetId = saveQuizSet()
            val me = saveMember("나")
            saveCompleted(me, quizSetId)
            saveCandidatePair(quizSetId, saveMember("다른남"), saveMember("다른여", gender = Gender.FEMALE), score = 100.0)

            val missReason = adminMemberQuizService.getMemberQuizzes(me).rows.single().matching?.missReason

            missReason shouldBe MatchMissReason.POOL_REASON_NOT_COMPUTED
        }

        "후보 생성 뒤에 완주했으면 매칭 이후 완주다" {
            val quizSetId = saveQuizSet()
            saveCandidatePair(quizSetId, saveMember("다른남"), saveMember("다른여", gender = Gender.FEMALE), score = 100.0)
            // 완주 시각이 후보 생성 시각보다 뒤여야 한다.
            Thread.sleep(5)
            val me = saveMember("나")
            saveCompleted(me, quizSetId)

            val missReason = adminMemberQuizService.getMemberQuizzes(me).rows.single().matching?.missReason

            missReason shouldBe MatchMissReason.COMPLETED_AFTER_GENERATION
        }

        "이미 성사됐으면 제외 이유를 받고 성사는 후보 밖 신청으로 보인다" {
            val quizSetId = saveQuizSet()
            val matched = saveMember("성사됨")
            val partner = saveMember("성사상대", gender = Gender.FEMALE)
            listOf(matched, partner).forEach { saveCompleted(it, quizSetId) }
            personalMatchRepository.save(
                PersonalMatchFixture.create(matched, partner, quizSetId, status = PersonalMatchStatus.ACCEPTED),
            )

            val matching = adminMemberQuizService.getMemberQuizzes(matched).rows.single().matching

            matching?.missReason shouldBe MatchMissReason.EXCLUDED_ALREADY_MATCHED
            matching?.outsideRequests?.map { it.otherNickname to it.requestState } shouldBe
                listOf("성사상대" to PersonalRequestState.ACCEPTED)
        }

        "비활성 회원이면 제외 이유를 받는다" {
            val quizSetId = saveQuizSet()
            val suspended = saveMember("정지", status = MemberStatus.SUSPENDED)
            saveCompleted(suspended, quizSetId)

            val missReason = adminMemberQuizService.getMemberQuizzes(suspended).rows.single().matching?.missReason

            missReason shouldBe MatchMissReason.EXCLUDED_INACTIVE
        }
    }

    "그룹" - {
        "초대된 그룹은 다른 구성원의 응답과 함께 보이고 다른 셋의 그룹은 섞이지 않는다" {
            val quizSetId = saveQuizSet(MatchingType.GROUP)
            val otherQuizSetId = saveQuizSet("다른 셋", LocalDateTime.of(2026, 3, 30, 0, 0), MatchingType.GROUP)
            val me = saveMember("나")
            val accepted = saveMember("수락한사람")
            saveCompleted(me, quizSetId)
            val group = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSetId, score = 75.0))
            groupMatchMemberRepository.save(GroupMatchMember.candidate(group.id, me))
            groupMatchMemberRepository.save(GroupMatchMember.candidate(group.id, accepted).also { it.accept() })
            val otherGroup = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = otherQuizSetId))
            groupMatchMemberRepository.save(GroupMatchMember.candidate(otherGroup.id, me))

            val rows = adminMemberQuizService.getMemberQuizzes(me).rows
            val groupCandidate = rows.first { it.quizSet.id == quizSetId }.matching?.groupCandidates?.single()

            groupCandidate?.groupMatchId shouldBe group.id
            groupCandidate?.otherMembers?.map { it.nickname to it.response } shouldBe
                listOf("수락한사람" to GroupResponse.ACCEPTED)
            rows.first { it.quizSet.id == otherQuizSetId }.matching.shouldBeNull()
        }

        "그룹이 만들어졌는데 초대받지 못하면 미배정, 만들기 전이면 매칭 전이다" {
            val generatedQuizSetId = saveQuizSet(MatchingType.GROUP)
            val notGeneratedQuizSetId = saveQuizSet("만들기 전", LocalDateTime.of(2026, 3, 30, 0, 0), MatchingType.GROUP)
            val me = saveMember("나")
            listOf(generatedQuizSetId, notGeneratedQuizSetId).forEach { saveCompleted(me, it) }
            val group = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = generatedQuizSetId))
            groupMatchMemberRepository.save(GroupMatchMember.candidate(group.id, saveMember("그룹원")))

            val missByQuizSetId = adminMemberQuizService.getMemberQuizzes(me).rows
                .associate { it.quizSet.id to it.matching?.missReason }

            missByQuizSetId[generatedQuizSetId] shouldBe MatchMissReason.NOT_ASSIGNED_TO_GROUP
            missByQuizSetId[notGeneratedQuizSetId] shouldBe MatchMissReason.NOT_GENERATED
        }
    }
})
