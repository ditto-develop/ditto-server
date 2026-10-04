package com.ditto.api.admin.quiz

import com.ditto.api.admin.quiz.dto.MatchMissReason
import com.ditto.api.admin.quiz.dto.PersonalRequestState
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.InvitationStatus
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
import com.ditto.domain.quiz.QuizAnswerFixture
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

class AdminQuizParticipantMatchingTest(
    private val adminQuizParticipantService: AdminQuizParticipantService,
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val memberRepository: MemberRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    // 두 문항짜리 퀴즈셋. 답은 선택지 id 대신 1·2로 넣어 일치 여부만 맞춘다.
    fun saveQuizSetWithTwoQuizzes(matchingType: MatchingType = MatchingType.ONE_TO_ONE): Triple<Long, Long, Long> {
        val quizSetId = quizSetRepository.save(QuizSetFixture.create(matchingType = matchingType)).id
        val firstQuizId = quizRepository.save(QuizFixture.create(quizSetId = quizSetId, displayOrder = 1)).id
        val secondQuizId = quizRepository.save(QuizFixture.create(quizSetId = quizSetId, displayOrder = 2)).id
        return Triple(quizSetId, firstQuizId, secondQuizId)
    }

    fun saveMember(
        nickname: String,
        gender: Gender? = Gender.MALE,
        age: Int? = 25,
        status: MemberStatus = MemberStatus.ACTIVE,
    ): Long = memberRepository.save(
        MemberFixture.create(nickname = nickname, email = "$nickname@example.com", status = status, gender = gender, age = age),
    ).id

    fun saveCompleted(memberId: Long, quizSetId: Long, answers: Map<Long, Long>) {
        val progress = QuizProgressFixture.create(memberId = memberId, quizSetId = quizSetId, totalCount = 2)
        repeat(2) { progress.recordAnswer() }
        quizProgressRepository.save(progress)
        answers.forEach { (quizId, choiceId) ->
            quizAnswerRepository.save(QuizAnswerFixture.create(memberId = memberId, quizId = quizId, choiceId = choiceId))
        }
    }

    fun saveCandidatePair(quizSetId: Long, memberAId: Long, memberBId: Long, score: Double) {
        matchCandidateRepository.save(MatchCandidate.create(memberAId, memberBId, quizSetId, score, 1, 2))
        matchCandidateRepository.save(MatchCandidate.create(memberBId, memberAId, quizSetId, score, 1, 2))
    }

    "1:1 후보" - {
        "저장된 후보를 점수 순으로 담고 신청 상태를 내 쪽에서 본 값으로 붙인다" {
            val (quizSetId, firstQuizId, secondQuizId) = saveQuizSetWithTwoQuizzes()
            val me = saveMember("나", gender = Gender.FEMALE)
            val sentTo = saveMember("신청보냄")
            val receivedFrom = saveMember("신청받음")
            listOf(me, sentTo, receivedFrom).forEach { saveCompleted(it, quizSetId, mapOf(firstQuizId to 1L, secondQuizId to 1L)) }
            saveCandidatePair(quizSetId, me, sentTo, score = 50.0)
            saveCandidatePair(quizSetId, me, receivedFrom, score = 100.0)
            personalMatchRepository.save(PersonalMatchFixture.create(requesterId = me, receiverId = sentTo, quizSetId = quizSetId))
            personalMatchRepository.save(PersonalMatchFixture.create(requesterId = receivedFrom, receiverId = me, quizSetId = quizSetId))

            val view = adminQuizParticipantService.getParticipants(quizSetId)

            view.matching.isGenerated shouldBe true
            val candidates = view.matching.of(me).personalCandidates
            candidates.map { it.otherNickname } shouldContainExactly listOf("신청받음", "신청보냄")
            candidates.map { it.requestState } shouldContainExactly
                listOf(PersonalRequestState.RECEIVED, PersonalRequestState.SENT)
            view.matching.of(me).miss.shouldBeNull()
        }

        "풀에 들기 전 단계에서 빠진 참여자는 그 이유를 받는다" {
            val (quizSetId, firstQuizId, secondQuizId) = saveQuizSetWithTwoQuizzes()
            val notCompleted = saveMember("미완주")
            quizProgressRepository.save(QuizProgressFixture.create(memberId = notCompleted, quizSetId = quizSetId, totalCount = 2))
            val suspended = saveMember("정지", status = MemberStatus.SUSPENDED)
            saveCompleted(suspended, quizSetId, mapOf(firstQuizId to 1L, secondQuizId to 1L))
            val alreadyMatched = saveMember("성사됨")
            val partner = saveMember("성사상대", gender = Gender.FEMALE)
            listOf(alreadyMatched, partner).forEach { saveCompleted(it, quizSetId, mapOf(firstQuizId to 1L, secondQuizId to 1L)) }
            personalMatchRepository.save(
                PersonalMatchFixture.create(alreadyMatched, partner, quizSetId, status = PersonalMatchStatus.ACCEPTED),
            )
            quizProgressRepository.save(QuizProgressFixture.create(memberId = 99999L, quizSetId = quizSetId, totalCount = 2))

            val matching = adminQuizParticipantService.getParticipants(quizSetId).matching

            matching.of(notCompleted).miss?.reason shouldBe MatchMissReason.NOT_COMPLETED
            matching.of(suspended).miss?.reason shouldBe MatchMissReason.EXCLUDED_INACTIVE
            matching.of(alreadyMatched).miss?.reason shouldBe MatchMissReason.EXCLUDED_ALREADY_MATCHED
            matching.of(99999L).miss?.reason shouldBe MatchMissReason.MEMBER_DELETED
        }

        "매칭을 돌린 뒤에 완주한 참여자는 재생성이 필요하다고 표시한다" {
            val (quizSetId, firstQuizId, secondQuizId) = saveQuizSetWithTwoQuizzes()
            val early = saveMember("먼저")
            val partner = saveMember("짝", gender = Gender.FEMALE)
            listOf(early, partner).forEach { saveCompleted(it, quizSetId, mapOf(firstQuizId to 1L, secondQuizId to 1L)) }
            saveCandidatePair(quizSetId, early, partner, score = 100.0)
            Thread.sleep(5)
            val late = saveMember("나중", gender = Gender.FEMALE)
            saveCompleted(late, quizSetId, mapOf(firstQuizId to 1L, secondQuizId to 1L))

            val matching = adminQuizParticipantService.getParticipants(quizSetId).matching

            matching.of(late).miss?.reason shouldBe MatchMissReason.COMPLETED_AFTER_GENERATION
        }

        "풀 안에서 짝 자격이 없으면 성별·나이 미상과 그 밖의 경우를 나눈다" {
            val (quizSetId, firstQuizId, secondQuizId) = saveQuizSetWithTwoQuizzes()
            val unknownAge = saveMember("나이미상", age = null)
            val onlySameGender = saveMember("남자만")
            listOf(unknownAge, onlySameGender).forEach { saveCompleted(it, quizSetId, mapOf(firstQuizId to 1L, secondQuizId to 1L)) }

            val matching = adminQuizParticipantService.getParticipants(quizSetId).matching

            matching.isGenerated shouldBe false
            matching.of(unknownAge).miss?.reason shouldBe MatchMissReason.UNKNOWN_GENDER_OR_AGE
            matching.of(onlySameGender).miss?.reason shouldBe MatchMissReason.NO_ELIGIBLE_PAIR
        }

        "짝 자격은 있지만 상위 20% 컷을 못 넘으면 최고 점수와 컷을 함께 보여 준다" {
            val (quizSetId, firstQuizId, secondQuizId) = saveQuizSetWithTwoQuizzes()
            val topMale = saveMember("상위남")
            val topFemale = saveMember("상위여", gender = Gender.FEMALE)
            val lowFemale = saveMember("하위여", gender = Gender.FEMALE)
            saveCompleted(topMale, quizSetId, mapOf(firstQuizId to 1L, secondQuizId to 1L))
            saveCompleted(topFemale, quizSetId, mapOf(firstQuizId to 1L, secondQuizId to 1L))
            saveCompleted(lowFemale, quizSetId, mapOf(firstQuizId to 2L, secondQuizId to 2L))

            val miss = adminQuizParticipantService.getParticipants(quizSetId).matching.of(lowFemale).miss.shouldNotBeNull()

            miss.reason shouldBe MatchMissReason.CUT_BY_TOP_RATIO
            miss.detail shouldBe "최고 0.0 < 컷 100.0"
        }

        "컷을 넘었는데 저장된 후보가 없으면 5명 제한에서 빠진 것으로 본다" {
            val (quizSetId, firstQuizId, secondQuizId) = saveQuizSetWithTwoQuizzes()
            val male = saveMember("남")
            val female = saveMember("여", gender = Gender.FEMALE)
            listOf(male, female).forEach { saveCompleted(it, quizSetId, mapOf(firstQuizId to 1L, secondQuizId to 1L)) }

            val matching = adminQuizParticipantService.getParticipants(quizSetId).matching

            matching.of(male).miss?.reason shouldBe MatchMissReason.CUT_BY_HARD_LIMIT
        }
    }

    "그룹 후보" - {
        "그룹에 든 참여자는 그룹 점수·성사 여부·다른 구성원의 응답을 받는다" {
            val (quizSetId, firstQuizId, secondQuizId) = saveQuizSetWithTwoQuizzes(MatchingType.GROUP)
            val me = saveMember("나")
            val accepted = saveMember("수락한사람")
            val pending = saveMember("대기중")
            listOf(me, accepted, pending).forEach { saveCompleted(it, quizSetId, mapOf(firstQuizId to 1L, secondQuizId to 1L)) }
            val group = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSetId, score = 75.0, acceptedCount = 1))
            groupMatchMemberRepository.save(GroupMatchMember.candidate(group.id, me))
            groupMatchMemberRepository.save(GroupMatchMember.candidate(group.id, accepted).also { it.accept() })
            groupMatchMemberRepository.save(GroupMatchMember.candidate(group.id, pending))

            val groupCandidate = adminQuizParticipantService.getParticipants(quizSetId).matching.of(me).groupCandidate.shouldNotBeNull()

            groupCandidate.groupMatchId shouldBe group.id
            groupCandidate.score shouldBe 75.0
            groupCandidate.isFormed shouldBe false
            groupCandidate.myStatus shouldBe InvitationStatus.PENDING
            groupCandidate.otherMembers.map { it.nickname to it.status } shouldContainExactlyInAnyOrder
                listOf("수락한사람" to InvitationStatus.ACCEPTED, "대기중" to InvitationStatus.PENDING)
        }

        "그룹에 들지 못한 완주자는 미배정으로 표시하고 1:1 후보는 비어 있다" {
            val (quizSetId, firstQuizId, secondQuizId) = saveQuizSetWithTwoQuizzes(MatchingType.GROUP)
            val leftOver = saveMember("남은사람")
            saveCompleted(leftOver, quizSetId, mapOf(firstQuizId to 1L, secondQuizId to 1L))

            val matching = adminQuizParticipantService.getParticipants(quizSetId).matching.of(leftOver)

            matching.miss?.reason shouldBe MatchMissReason.NOT_ASSIGNED_TO_GROUP
            matching.personalCandidates.shouldBeEmpty()
        }
    }
})
