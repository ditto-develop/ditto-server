package com.ditto.api.match

import com.ditto.api.match.service.GroupCandidateService
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.entity.GroupMatch
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.InvitationStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.Location
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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

class GroupCandidateServiceTest(
    private val groupCandidateService: GroupCandidateService,
    private val memberRepository: MemberRepository,
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    dataSource: DataSource,
) : IntegrationTest(
    dataSource,
    {

        // location·caricature 는 가입 완료 시 필수라 후보 카드 조립이 이 둘을 요구한다.
        fun saveMember(nickname: String): Long =
            memberRepository.save(
                MemberFixture.create(
                    nickname = nickname,
                    status = MemberStatus.ACTIVE,
                    gender = Gender.FEMALE,
                    age = 27,
                    location = Location.SEOUL,
                    caricature = "caricature-$nickname",
                ),
            ).id

        fun saveGroupQuizSetWithTwoQuizzes(): Triple<Long, Long, Long> {
            val quizSetId = quizSetRepository.save(QuizSetFixture.currentWeek(matchingType = MatchingType.GROUP)).id
            val quizId1 = quizRepository.save(QuizFixture.create(quizSetId = quizSetId, displayOrder = 1)).id
            val quizId2 = quizRepository.save(QuizFixture.create(quizSetId = quizSetId, displayOrder = 2)).id
            return Triple(quizSetId, quizId1, quizId2)
        }

        fun saveAnswers(memberId: Long, vararg quizIdToChoice: Pair<Long, Long>) {
            quizIdToChoice.forEach { (quizId, choiceId) ->
                quizAnswerRepository.save(
                    QuizAnswerFixture.create(memberId = memberId, quizId = quizId, choiceId = choiceId),
                )
            }
        }

        fun saveCompletedProgress(memberId: Long, quizSetId: Long, total: Int) {
            val progress = QuizProgressFixture.create(memberId = memberId, quizSetId = quizSetId, totalCount = total)
            repeat(total) { progress.recordAnswer() }
            quizProgressRepository.save(progress)
        }

        fun saveCandidateGroup(quizSetId: Long, score: Double, memberIds: List<Long>): Long {
            val room = groupMatchRepository.save(GroupMatch.candidate(quizSetId, score))
            groupMatchMemberRepository.saveAll(memberIds.map { GroupMatchMember.candidate(room.id, it) })
            return room.id
        }

        "getGroupCandidates" - {

            "나를 뺀 구성원을 일치 문항 수 내림차순으로 준다" {
                val (quizSetId, quizId1, quizId2) = saveGroupQuizSetWithTwoQuizzes()
                val me = saveMember("나")
                val twoMatched = saveMember("두개일치")
                val oneMatched = saveMember("한개일치")
                val noneMatched = saveMember("무일치")
                saveAnswers(me, quizId1 to 1L, quizId2 to 1L)
                saveAnswers(twoMatched, quizId1 to 1L, quizId2 to 1L)
                saveAnswers(oneMatched, quizId1 to 1L, quizId2 to 2L)
                saveAnswers(noneMatched, quizId1 to 2L, quizId2 to 2L)
                saveCompletedProgress(me, quizSetId, total = 2)
                saveCandidateGroup(quizSetId, score = 50.0, memberIds = listOf(me, twoMatched, oneMatched, noneMatched))

                val result = groupCandidateService.getGroupCandidates(me)

                val group = result.groups.single()
                group.members.map { it.userId } shouldBe listOf(twoMatched, oneMatched, noneMatched)
                group.members.map { it.scoreBreakdown.matchedQuestions } shouldBe listOf(2, 1, 0)
            }

            "평균 일치 문항 수는 나와 각 구성원 일치 수의 평균이다" {
                val (quizSetId, quizId1, quizId2) = saveGroupQuizSetWithTwoQuizzes()
                val me = saveMember("평균나")
                val peers = listOf("평균A", "평균B", "평균C").map { saveMember(it) }
                saveAnswers(me, quizId1 to 1L, quizId2 to 1L)
                saveAnswers(peers[0], quizId1 to 1L, quizId2 to 1L) // 2개 일치
                saveAnswers(peers[1], quizId1 to 1L, quizId2 to 2L) // 1개 일치
                saveAnswers(peers[2], quizId1 to 2L, quizId2 to 2L) // 0개 일치
                saveCompletedProgress(me, quizSetId, total = 2)
                saveCandidateGroup(quizSetId, score = 50.0, memberIds = listOf(me) + peers)

                val result = groupCandidateService.getGroupCandidates(me)

                // (2 + 1 + 0) / 3 = 1
                result.groups.single().averageMatchedQuestions shouldBe 1
                result.groups.single().totalQuestions shouldBe 2
            }

            "후보 그룹이 여럿이면 그룹 점수 내림차순으로 준다" {
                val (quizSetId, quizId1, quizId2) = saveGroupQuizSetWithTwoQuizzes()
                val me = saveMember("정렬나")
                val others = listOf("정렬A", "정렬B", "정렬C", "정렬D").map { saveMember(it) }
                (listOf(me) + others).forEach { saveAnswers(it, quizId1 to 1L, quizId2 to 1L) }
                saveCompletedProgress(me, quizSetId, total = 2)
                val lowRoomId = saveCandidateGroup(quizSetId, 40.0, listOf(me, others[0], others[1]))
                val highRoomId = saveCandidateGroup(quizSetId, 90.0, listOf(me, others[2], others[3]))

                val result = groupCandidateService.getGroupCandidates(me)

                result.groups.map { it.groupMatchId } shouldBe listOf(highRoomId, lowRoomId)
            }

            "아직 응답하지 않은 그룹은 PENDING·미성사로 내려간다" {
                val (quizSetId, quizId1, quizId2) = saveGroupQuizSetWithTwoQuizzes()
                val me = saveMember("대기나")
                val peers = listOf("대기A", "대기B").map { saveMember(it) }
                (listOf(me) + peers).forEach { saveAnswers(it, quizId1 to 1L, quizId2 to 1L) }
                saveCompletedProgress(me, quizSetId, total = 2)
                saveCandidateGroup(quizSetId, 90.0, listOf(me) + peers)

                val group = groupCandidateService.getGroupCandidates(me).groups.single()

                group.myStatus shouldBe InvitationStatus.PENDING
                group.isFormed shouldBe false
            }

            "거절한 그룹은 목록에서 빠진다" {
                val (quizSetId, quizId1, quizId2) = saveGroupQuizSetWithTwoQuizzes()
                val me = saveMember("거절나")
                val peers = listOf("거절A", "거절B", "거절C", "거절D").map { saveMember(it) }
                (listOf(me) + peers).forEach { saveAnswers(it, quizId1 to 1L, quizId2 to 1L) }
                saveCompletedProgress(me, quizSetId, total = 2)
                val declinedRoomId = saveCandidateGroup(quizSetId, 90.0, listOf(me, peers[0], peers[1]))
                val keptRoomId = saveCandidateGroup(quizSetId, 40.0, listOf(me, peers[2], peers[3]))

                val declined = groupMatchMemberRepository.findByRoomIdAndMemberId(declinedRoomId, me)!!
                declined.decline()
                groupMatchMemberRepository.save(declined)

                val result = groupCandidateService.getGroupCandidates(me)

                result.groups.map { it.groupMatchId } shouldBe listOf(keptRoomId)
            }

            "배정받은 그룹이 없으면 빈 목록이다" {
                val (quizSetId, quizId1, quizId2) = saveGroupQuizSetWithTwoQuizzes()
                val me = saveMember("미배정나")
                saveAnswers(me, quizId1 to 1L, quizId2 to 1L)
                saveCompletedProgress(me, quizSetId, total = 2)

                groupCandidateService.getGroupCandidates(me).groups.shouldBeEmpty()
            }

            "참여한 그룹 퀴즈셋이 없으면 NOT_FOUND 다" {
                val me = saveMember("퀴즈없음나")

                shouldThrow<WarnException> { groupCandidateService.getGroupCandidates(me) }
            }
        }
    },
)
