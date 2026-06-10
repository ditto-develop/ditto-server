package com.ditto.api.match

import com.ditto.api.match.service.MatchmakingService
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.GenderPreference
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
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

class MatchmakingServiceTest(
    private val matchmakingService: MatchmakingService,
    private val memberRepository: MemberRepository,
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    dataSource: DataSource,
) : IntegrationTest(
    dataSource,
    {

        // 성별·나이 미상 회원은 매칭에서 제외되므로, 성별/나이 무관 점수 테스트의 기본 회원은 둘 다 갖게 둔다.
        fun saveMember(
            nickname: String,
            status: MemberStatus = MemberStatus.ACTIVE,
            gender: Gender? = Gender.MALE,
            age: Int? = 25,
        ): Long =
            memberRepository.save(
                MemberFixture.create(nickname = nickname, status = status, gender = gender, age = age),
            ).id

        fun saveOneToOneQuizSetWithTwoQuizzes(): Triple<Long, Long, Long> {
            val quizSetId = quizSetRepository.save(QuizSetFixture.create(matchingType = MatchingType.ONE_TO_ONE)).id
            val quizId1 = quizRepository.save(QuizFixture.create(quizSetId = quizSetId, displayOrder = 1)).id
            val quizId2 = quizRepository.save(QuizFixture.create(quizSetId = quizSetId, displayOrder = 2)).id
            return Triple(quizSetId, quizId1, quizId2)
        }

        fun saveAnswers(memberId: Long, vararg quizIdToChoice: Pair<Long, Long>) {
            quizIdToChoice.forEach { (quizId, choiceId) ->
                quizAnswerRepository.save(
                    QuizAnswerFixture.create(
                        memberId = memberId,
                        quizId = quizId,
                        choiceId = choiceId,
                    ),
                )
            }
        }

        fun saveCompletedProgress(
            memberId: Long,
            quizSetId: Long,
            total: Int,
            preferredGender: GenderPreference = GenderPreference.ANY,
        ) {
            val progress = QuizProgressFixture.create(memberId = memberId, quizSetId = quizSetId, totalCount = total)
            repeat(total) { progress.recordAnswer() } // COMPLETED 로 만든다
            progress.selectPreferredGender(preferredGender)
            quizProgressRepository.save(progress)
        }

        "generateCandidates" - {

            "완료자들의 페어를 계산해 양방향 후보로 저장한다" {
                val (quizSetId, quizId1, quizId2) = saveOneToOneQuizSetWithTwoQuizzes()
                val a = saveMember("회원A")
                val b = saveMember("회원B")
                val c = saveMember("회원C")
                // A·B 두 문항 모두 일치(100점), C 는 두 번째 문항이 달라 A·B 와 각각 50점
                saveAnswers(a, quizId1 to 1L, quizId2 to 1L)
                saveAnswers(b, quizId1 to 1L, quizId2 to 1L)
                saveAnswers(c, quizId1 to 1L, quizId2 to 2L)
                listOf(a, b, c).forEach { saveCompletedProgress(it, quizSetId, total = 2) }

                matchmakingService.generateMatchingCandidates(quizSetId)

                // 상위 20%(+동점) → A-B(100) 만 선발 → (A→B), (B→A) 양방향 2행
                matchCandidateRepository.findByOwnerMemberIdAndQuizSetId(a, quizSetId)
                    .map { it.otherMemberId } shouldBe listOf(b)
                matchCandidateRepository.findByOwnerMemberIdAndQuizSetId(b, quizSetId)
                    .map { it.otherMemberId } shouldBe listOf(a)
                matchCandidateRepository.findByOwnerMemberIdAndQuizSetId(c, quizSetId) shouldHaveSize 0

                // 일치/전체 문항 수(scoreBreakdown)도 함께 저장된다 — A·B 두 문항 모두 일치
                val abCandidate = matchCandidateRepository.findByOwnerMemberIdAndQuizSetId(a, quizSetId).first()
                abCandidate.matchedQuestionCount shouldBe 2
                abCandidate.totalQuestionCount shouldBe 2
            }

            "완료한 참여자가 없으면 후보를 만들지 않는다" {
                val (quizSetId, _, _) = saveOneToOneQuizSetWithTwoQuizzes()
                val a = saveMember("회원A")
                // 답변/완료 처리 없음 → 완료자 0명

                matchmakingService.generateMatchingCandidates(quizSetId)

                matchCandidateRepository.findByOwnerMemberIdAndQuizSetId(a, quizSetId) shouldHaveSize 0
            }

            "이미 ACCEPTED 매칭된 회원은 제외된다" {
                val (quizSetId, quizId1, quizId2) = saveOneToOneQuizSetWithTwoQuizzes()
                val a = saveMember("회원A")
                val b = saveMember("회원B")
                val c = saveMember("회원C")
                listOf(a, b, c).forEach { saveAnswers(it, quizId1 to 1L, quizId2 to 1L) }
                listOf(a, b, c).forEach { saveCompletedProgress(it, quizSetId, total = 2) }
                // A-B 가 이미 성사 → A,B 제외 → 남는 후보 풀은 C 1명뿐 → 후보 없음
                personalMatchRepository.save(
                    PersonalMatchFixture.create(
                        requesterId = a,
                        receiverId = b,
                        quizSetId = quizSetId,
                        status = PersonalMatchStatus.ACCEPTED,
                    ),
                )

                matchmakingService.generateMatchingCandidates(quizSetId)

                matchCandidateRepository.findByOwnerMemberIdAndQuizSetId(a, quizSetId) shouldHaveSize 0
                matchCandidateRepository.findByOwnerMemberIdAndQuizSetId(b, quizSetId) shouldHaveSize 0
                matchCandidateRepository.findByOwnerMemberIdAndQuizSetId(c, quizSetId) shouldHaveSize 0
            }

            "성별 선호가 맞지 않는 페어는 후보에서 제외된다 (상호호환 하드 필터)" {
                val (quizSetId, quizId1, quizId2) = saveOneToOneQuizSetWithTwoQuizzes()
                val maleA = saveMember("남A", gender = Gender.MALE)
                val femaleB = saveMember("여B", gender = Gender.FEMALE)
                val maleC = saveMember("남C", gender = Gender.MALE)
                // 모두 두 문항 일치(100점). 전부 이성 선호 → 남-남(A-C)은 비호환.
                listOf(maleA, femaleB, maleC).forEach { saveAnswers(it, quizId1 to 1L, quizId2 to 1L) }
                listOf(maleA, femaleB, maleC).forEach {
                    saveCompletedProgress(it, quizSetId, total = 2, preferredGender = GenderPreference.OPPOSITE)
                }

                matchmakingService.generateMatchingCandidates(quizSetId)

                // A-B, B-C 만 상호호환 → 저장. A-C(남-남)는 제외.
                matchCandidateRepository.findByOwnerMemberIdAndQuizSetId(maleA, quizSetId)
                    .map { it.otherMemberId } shouldBe listOf(femaleB)
                matchCandidateRepository.findByOwnerMemberIdAndQuizSetId(maleC, quizSetId)
                    .map { it.otherMemberId } shouldBe listOf(femaleB)
                matchCandidateRepository.findByOwnerMemberIdAndQuizSetId(femaleB, quizSetId)
                    .map { it.otherMemberId }.toSet() shouldBe setOf(maleA, maleC)
            }

            "성별 미상(null) 회원은 매칭에서 제외된다 (성별 필수)" {
                val (quizSetId, quizId1, quizId2) = saveOneToOneQuizSetWithTwoQuizzes()
                val male = saveMember("남A", gender = Gender.MALE)
                val female = saveMember("여B", gender = Gender.FEMALE)
                val unknown = saveMember("성별미상", gender = null)
                listOf(male, female, unknown).forEach { saveAnswers(it, quizId1 to 1L, quizId2 to 1L) }
                listOf(male, female, unknown).forEach {
                    saveCompletedProgress(it, quizSetId, total = 2, preferredGender = GenderPreference.OPPOSITE)
                }

                matchmakingService.generateMatchingCandidates(quizSetId)

                // 남-여만 매칭, 성별 미상 회원은 후보 풀에서 제외되어 어디에도 노출되지 않음
                matchCandidateRepository.findByOwnerMemberIdAndQuizSetId(male, quizSetId)
                    .map { it.otherMemberId } shouldBe listOf(female)
                matchCandidateRepository.findByOwnerMemberIdAndQuizSetId(female, quizSetId)
                    .map { it.otherMemberId } shouldBe listOf(male)
                matchCandidateRepository.findByOwnerMemberIdAndQuizSetId(unknown, quizSetId) shouldHaveSize 0
            }

            "나이차가 10을 넘는 페어는 후보에서 제외된다" {
                val (quizSetId, quizId1, quizId2) = saveOneToOneQuizSetWithTwoQuizzes()
                val young = saveMember("남20", gender = Gender.MALE, age = 20)
                val old = saveMember("여35", gender = Gender.FEMALE, age = 35) // 나이차 15
                listOf(young, old).forEach { saveAnswers(it, quizId1 to 1L, quizId2 to 1L) }
                listOf(young, old).forEach {
                    saveCompletedProgress(it, quizSetId, total = 2, preferredGender = GenderPreference.OPPOSITE)
                }

                matchmakingService.generateMatchingCandidates(quizSetId)

                matchCandidateRepository.findByOwnerMemberIdAndQuizSetId(young, quizSetId) shouldHaveSize 0
                matchCandidateRepository.findByOwnerMemberIdAndQuizSetId(old, quizSetId) shouldHaveSize 0
            }

            "나이 미상(null) 회원은 매칭에서 제외된다 (나이 필수)" {
                val (quizSetId, quizId1, quizId2) = saveOneToOneQuizSetWithTwoQuizzes()
                val male = saveMember("남A", gender = Gender.MALE, age = 25)
                val female = saveMember("여B", gender = Gender.FEMALE, age = 25)
                val ageUnknown = saveMember("나이미상", gender = Gender.FEMALE, age = null)
                listOf(male, female, ageUnknown).forEach { saveAnswers(it, quizId1 to 1L, quizId2 to 1L) }
                listOf(male, female, ageUnknown).forEach {
                    saveCompletedProgress(it, quizSetId, total = 2, preferredGender = GenderPreference.OPPOSITE)
                }

                matchmakingService.generateMatchingCandidates(quizSetId)

                matchCandidateRepository.findByOwnerMemberIdAndQuizSetId(male, quizSetId)
                    .map { it.otherMemberId } shouldBe listOf(female)
                matchCandidateRepository.findByOwnerMemberIdAndQuizSetId(ageUnknown, quizSetId) shouldHaveSize 0
            }

            "ACTIVE 가 아닌(PENDING) 회원은 제외된다" {
                val (quizSetId, quizId1, quizId2) = saveOneToOneQuizSetWithTwoQuizzes()
                val a = saveMember("회원A")
                val b = saveMember("회원B")
                val pending = saveMember("대기회원", MemberStatus.PENDING)
                listOf(a, b, pending).forEach { saveAnswers(it, quizId1 to 1L, quizId2 to 1L) }
                listOf(a, b, pending).forEach { saveCompletedProgress(it, quizSetId, total = 2) }

                matchmakingService.generateMatchingCandidates(quizSetId)

                // pending 제외 → A-B 만 후보, pending 은 어디에도 노출되지 않음
                matchCandidateRepository.findByOwnerMemberIdAndQuizSetId(a, quizSetId)
                    .map { it.otherMemberId } shouldBe listOf(b)
                matchCandidateRepository.findByOwnerMemberIdAndQuizSetId(b, quizSetId)
                    .map { it.otherMemberId } shouldBe listOf(a)
                matchCandidateRepository.findByOwnerMemberIdAndQuizSetId(pending, quizSetId) shouldHaveSize 0
            }
        }
    },
)
