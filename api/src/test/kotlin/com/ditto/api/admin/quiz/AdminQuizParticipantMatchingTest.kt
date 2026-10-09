package com.ditto.api.admin.quiz

import com.ditto.api.admin.quiz.dto.GroupResponse
import com.ditto.api.admin.quiz.dto.MatchMissReason
import com.ditto.api.admin.quiz.dto.PersonalRequestState
import com.ditto.api.support.IntegrationTest
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
            view.matching.of(me).missReason.shouldBeNull()
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

            matching.of(notCompleted).missReason shouldBe MatchMissReason.NOT_COMPLETED
            matching.of(suspended).missReason shouldBe MatchMissReason.EXCLUDED_INACTIVE
            matching.of(alreadyMatched).missReason shouldBe MatchMissReason.EXCLUDED_ALREADY_MATCHED
            matching.of(99999L).missReason shouldBe MatchMissReason.MEMBER_DELETED
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

            matching.of(late).missReason shouldBe MatchMissReason.COMPLETED_AFTER_GENERATION
        }

        "풀 안에서 짝 자격이 없으면 성별·나이 미상과 그 밖의 경우를 나눈다" {
            val (quizSetId, firstQuizId, secondQuizId) = saveQuizSetWithTwoQuizzes()
            val unknownAge = saveMember("나이미상", age = null)
            val onlySameGender = saveMember("남자만")
            listOf(unknownAge, onlySameGender).forEach { saveCompleted(it, quizSetId, mapOf(firstQuizId to 1L, secondQuizId to 1L)) }

            val matching = adminQuizParticipantService.getParticipants(quizSetId).matching

            matching.isGenerated shouldBe false
            matching.of(unknownAge).missReason shouldBe MatchMissReason.UNKNOWN_GENDER_OR_AGE
            matching.of(onlySameGender).missReason shouldBe MatchMissReason.NO_ELIGIBLE_PAIR
        }

        "자격 상대가 있어도 매칭을 돌리기 전이면 매칭 전으로 표시한다" {
            val (quizSetId, firstQuizId, secondQuizId) = saveQuizSetWithTwoQuizzes()
            val male = saveMember("남")
            val female = saveMember("여", gender = Gender.FEMALE)
            listOf(male, female).forEach { saveCompleted(it, quizSetId, mapOf(firstQuizId to 1L, secondQuizId to 1L)) }

            val matching = adminQuizParticipantService.getParticipants(quizSetId).matching

            matching.isGenerated shouldBe false
            matching.of(male).missReason shouldBe MatchMissReason.NOT_GENERATED
        }

        "자격 상대가 있는데 후보가 없으면 매칭 뒤 상태가 바뀐 것이다" {
            val (quizSetId, firstQuizId, secondQuizId) = saveQuizSetWithTwoQuizzes()
            val matchedMale = saveMember("저장남")
            val matchedFemale = saveMember("저장여", gender = Gender.FEMALE)
            val changedMale = saveMember("바뀐남")
            val changedFemale = saveMember("바뀐여", gender = Gender.FEMALE)
            listOf(matchedMale, matchedFemale, changedMale, changedFemale).forEach {
                saveCompleted(it, quizSetId, mapOf(firstQuizId to 1L, secondQuizId to 1L))
            }
            saveCandidatePair(quizSetId, matchedMale, matchedFemale, score = 100.0)

            val matching = adminQuizParticipantService.getParticipants(quizSetId).matching

            matching.of(changedMale).missReason shouldBe MatchMissReason.STATE_CHANGED_AFTER_GENERATION
        }

        "매칭 뒤에 완주한 사람은 다시 계산하는 풀에 넣지 않는다" {
            val (quizSetId, firstQuizId, secondQuizId) = saveQuizSetWithTwoQuizzes()
            val matchedMale = saveMember("저장남")
            val matchedFemale = saveMember("저장여", gender = Gender.FEMALE)
            val lonelyMale = saveMember("혼자남", age = 50)
            listOf(matchedMale, matchedFemale, lonelyMale).forEach {
                saveCompleted(it, quizSetId, mapOf(firstQuizId to 1L, secondQuizId to 1L))
            }
            saveCandidatePair(quizSetId, matchedMale, matchedFemale, score = 100.0)
            Thread.sleep(5)
            val lateFemale = saveMember("나중여", gender = Gender.FEMALE, age = 50)
            saveCompleted(lateFemale, quizSetId, mapOf(firstQuizId to 1L, secondQuizId to 1L))

            val matching = adminQuizParticipantService.getParticipants(quizSetId).matching

            matching.of(lonelyMale).missReason shouldBe MatchMissReason.NO_ELIGIBLE_PAIR
            matching.of(lateFemale).missReason shouldBe MatchMissReason.COMPLETED_AFTER_GENERATION
        }

        "후보 밖의 신청·성사도 상대와 상태를 보여 주고 성사는 실제 기록으로 확인한다" {
            val (quizSetId, firstQuizId, secondQuizId) = saveQuizSetWithTwoQuizzes()
            val me = saveMember("나")
            val partner = saveMember("성사상대", gender = Gender.FEMALE)
            val rejecter = saveMember("거절한사람", gender = Gender.FEMALE)
            listOf(me, partner, rejecter).forEach { saveCompleted(it, quizSetId, mapOf(firstQuizId to 1L, secondQuizId to 1L)) }
            personalMatchRepository.save(PersonalMatchFixture.create(me, partner, quizSetId, status = PersonalMatchStatus.ACCEPTED))
            personalMatchRepository.save(PersonalMatchFixture.create(me, rejecter, quizSetId, status = PersonalMatchStatus.REJECTED))

            val matching = adminQuizParticipantService.getParticipants(quizSetId).matching.of(me)

            matching.outsideRequests.map { it.otherNickname to it.requestState } shouldContainExactlyInAnyOrder listOf(
                "성사상대" to PersonalRequestState.ACCEPTED,
                "거절한사람" to PersonalRequestState.REJECTED,
            )
            matching.missReason shouldBe MatchMissReason.EXCLUDED_ALREADY_MATCHED
        }

        "진행 기록이 없는 후보 상대도 닉네임을 보여 준다" {
            val (quizSetId, firstQuizId, secondQuizId) = saveQuizSetWithTwoQuizzes()
            val me = saveMember("나")
            saveCompleted(me, quizSetId, mapOf(firstQuizId to 1L, secondQuizId to 1L))
            val progressReset = saveMember("진행초기화", gender = Gender.FEMALE)
            saveCandidatePair(quizSetId, me, progressReset, score = 100.0)

            val candidate = adminQuizParticipantService.getParticipants(quizSetId).matching.of(me).personalCandidates.single()

            candidate.otherNickname shouldBe "진행초기화"
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

            val groupCandidate = adminQuizParticipantService.getParticipants(quizSetId).matching.of(me).groupCandidates.single()

            groupCandidate.groupMatchId shouldBe group.id
            groupCandidate.score shouldBe 75.0
            groupCandidate.isFormed shouldBe false
            groupCandidate.acceptedCount shouldBe 1
            groupCandidate.myResponse shouldBe GroupResponse.PENDING
            groupCandidate.otherMembers.map { it.nickname to it.response } shouldContainExactlyInAnyOrder
                listOf("수락한사람" to GroupResponse.ACCEPTED, "대기중" to GroupResponse.PENDING)
        }

        "예전에 겹쳐 만든 그룹에 함께 있으면 모든 그룹을 보여 준다" {
            val (quizSetId, firstQuizId, secondQuizId) = saveQuizSetWithTwoQuizzes(MatchingType.GROUP)
            val me = saveMember("나")
            saveCompleted(me, quizSetId, mapOf(firstQuizId to 1L, secondQuizId to 1L))
            val groups = (1..2).map { groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSetId)) }
            groups.forEach { groupMatchMemberRepository.save(GroupMatchMember.candidate(it.id, me)) }

            val groupCandidates = adminQuizParticipantService.getParticipants(quizSetId).matching.of(me).groupCandidates

            groupCandidates.map { it.groupMatchId } shouldContainExactlyInAnyOrder groups.map { it.id }
        }

        "그룹에서도 미완주·삭제된 회원은 풀에 들기 전 이유를 받는다" {
            val (quizSetId, _, _) = saveQuizSetWithTwoQuizzes(MatchingType.GROUP)
            val notCompleted = saveMember("미완주")
            quizProgressRepository.save(QuizProgressFixture.create(memberId = notCompleted, quizSetId = quizSetId, totalCount = 2))
            quizProgressRepository.save(QuizProgressFixture.create(memberId = 99999L, quizSetId = quizSetId, totalCount = 2))

            val matching = adminQuizParticipantService.getParticipants(quizSetId).matching

            matching.of(notCompleted).missReason shouldBe MatchMissReason.NOT_COMPLETED
            matching.of(99999L).missReason shouldBe MatchMissReason.MEMBER_DELETED
        }

        "그룹을 만들기 전이면 완주자는 미배정이 아니라 매칭 전이다" {
            val (quizSetId, firstQuizId, secondQuizId) = saveQuizSetWithTwoQuizzes(MatchingType.GROUP)
            val member = saveMember("완주자")
            saveCompleted(member, quizSetId, mapOf(firstQuizId to 1L, secondQuizId to 1L))

            val matching = adminQuizParticipantService.getParticipants(quizSetId).matching.of(member)

            matching.missReason shouldBe MatchMissReason.NOT_GENERATED
        }

        "그룹에 들지 못한 완주자는 미배정으로 표시하고 1:1 후보는 비어 있다" {
            val (quizSetId, firstQuizId, secondQuizId) = saveQuizSetWithTwoQuizzes(MatchingType.GROUP)
            val leftOver = saveMember("남은사람")
            saveCompleted(leftOver, quizSetId, mapOf(firstQuizId to 1L, secondQuizId to 1L))
            val group = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSetId))
            val grouped = saveMember("그룹원")
            groupMatchMemberRepository.save(GroupMatchMember.candidate(group.id, grouped))

            val matching = adminQuizParticipantService.getParticipants(quizSetId).matching.of(leftOver)

            matching.missReason shouldBe MatchMissReason.NOT_ASSIGNED_TO_GROUP
            matching.personalCandidates.shouldBeEmpty()
        }
    }
})
