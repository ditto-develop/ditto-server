package com.ditto.api.match

import com.ditto.api.match.service.MatchCandidateService
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.common.exception.WarnException
import com.ditto.domain.intronote.entity.IntroNote
import com.ditto.domain.intronote.entity.IntroQuestion
import com.ditto.domain.intronote.repository.IntroNoteRepository
import com.ditto.domain.match.MatchCandidateFixture
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.Location
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

class MatchCandidateServiceTest(
    private val matchCandidateService: MatchCandidateService,
    private val quizSetRepository: QuizSetRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val memberRepository: MemberRepository,
    private val introNoteRepository: IntroNoteRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveMember(
        nickname: String,
        gender: Gender? = Gender.FEMALE,
        age: Int? = 27,
        location: Location? = Location.SEOUL,
        caricature: String? = "c1",
    ): Member = memberRepository.save(
        MemberFixture.create(
            nickname = nickname,
            status = MemberStatus.ACTIVE,
            gender = gender,
            age = age,
            location = location,
            caricature = caricature,
        ),
    )

    // 회원이 완료(COMPLETED)한 1:1 퀴즈셋을 만든다. 후보는 마감된 셋에만 생기므로 노출 기준이 된다.
    fun completeOneToOneQuizSet(ownerId: Long): QuizSet {
        val quizSet = quizSetRepository.save(QuizSetFixture.create(matchingType = MatchingType.ONE_TO_ONE))
        val progress = QuizProgressFixture.create(memberId = ownerId, quizSetId = quizSet.id, totalCount = 1)
        progress.recordAnswer() // NOT_STARTED -> COMPLETED
        quizProgressRepository.save(progress)
        return quizSet
    }

    fun saveCandidate(quizSetId: Long, ownerId: Long, otherId: Long, score: Double, matched: Int = 7, total: Int = 8) {
        matchCandidateRepository.save(
            MatchCandidateFixture.create(
                ownerMemberId = ownerId,
                otherMemberId = otherId,
                quizSetId = quizSetId,
                score = score,
                matchedQuestionCount = matched,
                totalQuestionCount = total,
            ),
        )
    }

    "참여(완료)한 1:1 퀴즈셋이 없으면 NOT_FOUND 를 던진다" {
        shouldThrow<WarnException> {
            matchCandidateService.getMatchCandidates(memberId = 999L)
        }.errorCode shouldBe ErrorCode.NOT_FOUND
    }

    "후보를 score 내림차순, 동점이면 otherMemberId 오름차순으로 반환한다" {
        val owner = saveMember("주인")
        val quizSet = completeOneToOneQuizSet(owner.id)
        val a = saveMember("후보A") // a.id < b.id
        val b = saveMember("후보B")
        val c = saveMember("후보C")
        // 동점(90)인 a·b 의 후보 행을 b 먼저 저장해 DB 자연 반환순서를 b→a 로 만든다.
        // 안정 정렬 특성상 in-memory thenBy{otherMemberId} 가 있어야만 a(작은 id)가 앞으로 재정렬되며,
        // thenBy 가 빠지면 [b, a, c] 가 되어 아래 단언이 실패한다 → 2차 정렬을 실제로 검증한다.
        saveCandidate(quizSet.id, owner.id, b.id, score = 90.0)
        saveCandidate(quizSet.id, owner.id, a.id, score = 90.0)
        saveCandidate(quizSet.id, owner.id, c.id, score = 70.0)

        val result = matchCandidateService.getMatchCandidates(owner.id)

        result.quizSetId shouldBe quizSet.id
        result.matchingType shouldBe MatchingType.ONE_TO_ONE
        result.algorithmVersion shouldBe "1.0"
        result.candidates.map { it.userId } shouldBe listOf(a.id, b.id, c.id)
        result.candidates.first().matchRate shouldBe 90.0
    }

    "멤버 프로필과 소개노트(ONE_WORD)를 후보 응답에 매핑한다" {
        val owner = saveMember("주인")
        val quizSet = completeOneToOneQuizSet(owner.id)
        val member = saveMember("디토", gender = Gender.FEMALE, age = 27, location = Location.SEOUL, caricature = "f1")
        introNoteRepository.save(
            IntroNote.create(memberId = member.id, question = IntroQuestion.ONE_WORD, answer = "한 단어로 표현하면 디토"),
        )
        saveCandidate(quizSet.id, owner.id, member.id, score = 87.5, matched = 7, total = 8)

        val candidate = matchCandidateService.getMatchCandidates(owner.id).candidates.single()

        candidate.userId shouldBe member.id
        candidate.nickname shouldBe "디토"
        candidate.gender shouldBe "FEMALE"
        candidate.age shouldBe 27
        candidate.location shouldBe "seoul"
        candidate.profileImageUrl shouldBe "f1"
        candidate.introduction shouldBe "한 단어로 표현하면 디토"
        candidate.matchRate shouldBe 87.5
        candidate.scoreBreakdown.quizMatchRate shouldBe 87.5
        candidate.scoreBreakdown.matchedQuestions shouldBe 7
        candidate.scoreBreakdown.totalQuestions shouldBe 8
        candidate.scoreBreakdown.reasons shouldBe listOf("전체 8문항 중 7문항이 일치했어요")
    }

    "gender·age 가 없는 후보는 null 로 매핑한다" {
        val owner = saveMember("주인")
        val quizSet = completeOneToOneQuizSet(owner.id)
        val member = saveMember("미상", gender = null, age = null)
        saveCandidate(quizSet.id, owner.id, member.id, score = 80.0)

        val candidate = matchCandidateService.getMatchCandidates(owner.id).candidates.single()

        candidate.gender shouldBe null
        candidate.age shouldBe null
    }

    "소개노트가 없으면 introduction 은 null 이다" {
        val owner = saveMember("주인")
        val quizSet = completeOneToOneQuizSet(owner.id)
        val member = saveMember("무소개")
        saveCandidate(quizSet.id, owner.id, member.id, score = 80.0)

        matchCandidateService.getMatchCandidates(owner.id).candidates.single().introduction shouldBe null
    }

    "후보 회원의 사는곳(location)이 없으면 INTERNAL_ERROR 를 던진다" {
        val owner = saveMember("주인")
        val quizSet = completeOneToOneQuizSet(owner.id)
        val member = saveMember("노로케이션", location = null, caricature = "c1")
        saveCandidate(quizSet.id, owner.id, member.id, score = 80.0)

        shouldThrow<ErrorException> {
            matchCandidateService.getMatchCandidates(owner.id)
        }.errorCode shouldBe ErrorCode.INTERNAL_ERROR
    }

    "후보 회원의 캐리커쳐(profileImageUrl)가 없으면 INTERNAL_ERROR 를 던진다" {
        val owner = saveMember("주인")
        val quizSet = completeOneToOneQuizSet(owner.id)
        val member = saveMember("노캐리커쳐", location = Location.SEOUL, caricature = null)
        saveCandidate(quizSet.id, owner.id, member.id, score = 80.0)

        shouldThrow<ErrorException> {
            matchCandidateService.getMatchCandidates(owner.id)
        }.errorCode shouldBe ErrorCode.INTERNAL_ERROR
    }

    "완료한 1:1 퀴즈셋은 있으나 후보가 없으면 빈 목록을 반환한다" {
        val owner = saveMember("주인")
        val quizSet = completeOneToOneQuizSet(owner.id)

        val result = matchCandidateService.getMatchCandidates(owner.id)

        result.quizSetId shouldBe quizSet.id
        result.matchingType shouldBe MatchingType.ONE_TO_ONE
        result.candidates.size shouldBe 0
    }

    "프로필(회원)이 존재하지 않는 후보는 목록에서 제외한다" {
        val owner = saveMember("주인")
        val quizSet = completeOneToOneQuizSet(owner.id)
        val present = saveMember("정상후보")
        saveCandidate(quizSet.id, owner.id, present.id, score = 80.0)
        saveCandidate(quizSet.id, owner.id, otherId = 9_999_999L, score = 90.0) // 회원 레코드 없음

        val result = matchCandidateService.getMatchCandidates(owner.id)

        result.candidates.map { it.userId } shouldBe listOf(present.id)
    }

    "소개노트 답변이 공백이면 introduction 은 null 이다" {
        val owner = saveMember("주인")
        val quizSet = completeOneToOneQuizSet(owner.id)
        val member = saveMember("공백소개")
        introNoteRepository.save(
            IntroNote.create(memberId = member.id, question = IntroQuestion.ONE_WORD, answer = "   "),
        )
        saveCandidate(quizSet.id, owner.id, member.id, score = 80.0)

        matchCandidateService.getMatchCandidates(owner.id).candidates.single().introduction shouldBe null
    }
})
