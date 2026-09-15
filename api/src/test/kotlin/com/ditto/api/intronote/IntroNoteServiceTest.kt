package com.ditto.api.intronote

import com.ditto.api.intronote.service.IntroNoteService
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.intronote.entity.IntroQuestion
import com.ditto.domain.intronote.repository.IntroNoteRepository
import com.ditto.domain.match.MatchCandidateFixture
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.GroupMatch
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.entity.MemberBlock
import com.ditto.domain.member.repository.MemberBlockRepository
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.system.OperationWeek
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import javax.sql.DataSource

class IntroNoteServiceTest(
    private val introNoteService: IntroNoteService,
    private val introNoteRepository: IntroNoteRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val memberBlockRepository: MemberBlockRepository,
    private val quizSetRepository: QuizSetRepository,
    private val quizProgressRepository: QuizProgressRepository,
    dataSource: DataSource,
) : IntegrationTest(
    dataSource,
    {
        val memberId = 1L

        fun answerOf(result: com.ditto.api.intronote.dto.IntroNotesResponse, code: String) =
            result.answers.first { it.questionCode == code }.answer

        // 회원이 완료(COMPLETED)한 1:1 퀴즈셋. 후보 열람 권한의 기준 퀴즈셋이 된다.
        fun completeOneToOneQuizSet(memberId: Long): QuizSet {
            val quizSet = quizSetRepository.save(QuizSetFixture.currentWeek(matchingType = MatchingType.ONE_TO_ONE))
            val progress = QuizProgressFixture.create(memberId = memberId, quizSetId = quizSet.id, totalCount = 1)
            progress.recordAnswer() // NOT_STARTED -> COMPLETED
            quizProgressRepository.save(progress)
            return quizSet
        }

        // 회원이 완료한 그룹 퀴즈셋. 그룹 후보 열람 권한의 기준 퀴즈셋이 된다.
        fun completeGroupQuizSet(memberId: Long): QuizSet {
            val quizSet = quizSetRepository.save(QuizSetFixture.currentWeek(matchingType = MatchingType.GROUP))
            val progress = QuizProgressFixture.create(memberId = memberId, quizSetId = quizSet.id, totalCount = 1)
            progress.recordAnswer()
            quizProgressRepository.save(progress)
            return quizSet
        }

        fun putInSameCandidateGroup(quizSetId: Long, vararg memberIds: Long): Long {
            val room = groupMatchRepository.save(GroupMatch.candidate(quizSetId, score = 80.0))
            memberIds.forEach { groupMatchMemberRepository.save(GroupMatchMember.candidate(room.id, it)) }
            return room.id
        }

        fun exposeAsCandidates(quizSetId: Long, oneId: Long, otherId: Long) {
            matchCandidateRepository.save(
                MatchCandidateFixture.create(ownerMemberId = oneId, otherMemberId = otherId, quizSetId = quizSetId),
            )
            matchCandidateRepository.save(
                MatchCandidateFixture.create(ownerMemberId = otherId, otherMemberId = oneId, quizSetId = quizSetId),
            )
        }

        fun answerAll(memberId: Long) {
            IntroQuestion.entries.forEach { question ->
                introNoteService.saveAnswer(memberId, question.code, "${question.code} 답변")
            }
        }

        "소개노트 저장(upsert)" - {
            "질문 하나의 답변을 저장하면 해당 답변과 completedCount가 반영된다" {
                val result = introNoteService.saveAnswer(memberId, "travel-items", "이어폰, 선크림, 카메라")

                result.answers.size shouldBe 10
                answerOf(result, "travel-items") shouldBe "이어폰, 선크림, 카메라"
                result.completedCount shouldBe 1
            }

            "같은 질문을 다시 저장하면 행이 늘지 않고 답변이 갱신된다" {
                introNoteService.saveAnswer(memberId, "travel-items", "첫 답변")
                val result = introNoteService.saveAnswer(memberId, "travel-items", "수정된 답변")

                answerOf(result, "travel-items") shouldBe "수정된 답변"
                introNoteRepository.findAllByMemberId(memberId).size shouldBe 1
            }

            "빈 답변도 저장되며 completedCount에는 포함되지 않는다" {
                val result = introNoteService.saveAnswer(memberId, "one-word", "")

                answerOf(result, "one-word") shouldBe ""
                result.completedCount shouldBe 0
            }

            "유효하지 않은 questionCode면 BAD_REQUEST 예외가 발생한다" {
                val exception = shouldThrow<WarnException> {
                    introNoteService.saveAnswer(memberId, "invalid-code", "답변")
                }
                exception.errorCode shouldBe ErrorCode.BAD_REQUEST
            }
        }

        "본인 소개노트 조회" - {
            "미작성이면 10개 질문이 모두 빈 문자열로 반환된다" {
                val result = introNoteService.getMyIntroNotes(memberId)

                result.answers.size shouldBe 10
                result.answers.all { it.answer.isEmpty() } shouldBe true
                result.completedCount shouldBe 0
            }
        }

        "타인 소개노트 조회 권한" - {
            "본인 ID로 조회하면 권한 검사 없이 허용된다" {
                introNoteService.saveAnswer(memberId, "one-word", "나")

                val result = introNoteService.getIntroNotes(memberId, memberId)

                answerOf(result, "one-word") shouldBe "나"
            }

            "매칭이 성사(ACCEPTED)된 상대의 소개노트는 조회할 수 있다" {
                val targetId = 2L
                introNoteService.saveAnswer(targetId, "one-word", "상대답변")
                personalMatchRepository.save(
                    PersonalMatchFixture.create(
                        requesterId = memberId,
                        receiverId = targetId,
                        status = PersonalMatchStatus.ACCEPTED,
                    ),
                )

                val result = introNoteService.getIntroNotes(memberId, targetId)

                answerOf(result, "one-word") shouldBe "상대답변"
            }

            "같은 그룹 채팅방 참여자의 소개노트는 조회할 수 있다" {
                val targetId = 3L
                introNoteService.saveAnswer(targetId, "one-word", "그룹원답변")
                // 후보로 묶이기만 해서는 안 되고, 둘 다 수락해 그룹이 성사돼야 열람 권한이 생긴다.
                val room = groupMatchRepository.save(GroupMatch.candidate(quizSetId = 1L, score = 80.0))
                repeat(3) { room.recordAcceptance() }
                groupMatchRepository.save(room)
                listOf(memberId, targetId).forEach {
                    groupMatchMemberRepository.save(
                        GroupMatchMember.candidate(roomId = room.id, memberId = it).apply { accept() },
                    )
                }

                val result = introNoteService.getIntroNotes(memberId, targetId)

                answerOf(result, "one-word") shouldBe "그룹원답변"
            }

            "같은 후보 그룹의 상대는 성사 전에도 미리보기 3문항을 조회할 수 있다" {
                // 그룹 프로필 선택 → 소개노트도 참여 여부를 정하는 화면이라 1:1과 같은 구간이다.
                val targetId = 11L
                answerAll(targetId)
                val quizSet = completeGroupQuizSet(memberId)
                putInSameCandidateGroup(quizSet.id, memberId, targetId)

                val result = introNoteService.getIntroNotes(memberId, targetId)

                result.answers.size shouldBe 3
                result.answers.map { it.questionCode } shouldContain IntroQuestion.ONE_WORD.code
            }

            "내가 거절한 후보 그룹의 상대는 조회할 수 없다" {
                val targetId = 12L
                answerAll(targetId)
                val quizSet = completeGroupQuizSet(memberId)
                val roomId = putInSameCandidateGroup(quizSet.id, memberId, targetId)
                val mine = groupMatchMemberRepository.findByRoomIdAndMemberId(roomId, memberId)!!
                mine.decline()
                groupMatchMemberRepository.save(mine)

                shouldThrow<WarnException> { introNoteService.getIntroNotes(memberId, targetId) }
            }

            "다른 후보 그룹의 상대는 조회할 수 없다" {
                val targetId = 13L
                answerAll(targetId)
                val quizSet = completeGroupQuizSet(memberId)
                putInSameCandidateGroup(quizSet.id, memberId, 99L)
                putInSameCandidateGroup(quizSet.id, targetId, 98L)

                shouldThrow<WarnException> { introNoteService.getIntroNotes(memberId, targetId) }
            }

            "매칭도 그룹 채팅도 없는 상대면 FORBIDDEN 예외가 발생한다" {
                val targetId = 99L

                val exception = shouldThrow<WarnException> {
                    introNoteService.getIntroNotes(memberId, targetId)
                }
                exception.errorCode shouldBe ErrorCode.FORBIDDEN
            }

            "매칭 후보(성사 전)의 소개노트는 미리보기 3문항만 조회된다" {
                val targetId = 5L
                answerAll(targetId)
                val quizSet = completeOneToOneQuizSet(memberId)
                exposeAsCandidates(quizSet.id, memberId, targetId)

                val result = introNoteService.getIntroNotes(memberId, targetId)

                result.answers.size shouldBe 3
                result.answers.map { it.questionCode } shouldContain IntroQuestion.ONE_WORD.code
                result.completedCount shouldBe 3
            }

            "미리보기 문항은 다시 조회해도 같다 — 새로고침마다 바뀌면 반복 호출로 전체를 긁을 수 있다" {
                val targetId = 6L
                answerAll(targetId)
                val quizSet = completeOneToOneQuizSet(memberId)
                exposeAsCandidates(quizSet.id, memberId, targetId)

                val first = introNoteService.getIntroNotes(memberId, targetId).answers.map { it.questionCode }
                val second = introNoteService.getIntroNotes(memberId, targetId).answers.map { it.questionCode }

                first shouldBe second
            }

            "미리보기의 무작위 2문항은 작성된 답변에서만 뽑고, 고정 문항(one-word)은 미작성이어도 포함된다" {
                val targetId = 7L
                introNoteService.saveAnswer(targetId, "travel-items", "이어폰")
                val quizSet = completeOneToOneQuizSet(memberId)
                exposeAsCandidates(quizSet.id, memberId, targetId)

                val result = introNoteService.getIntroNotes(memberId, targetId)

                result.answers.map { it.questionCode } shouldBe listOf("travel-items", "one-word")
                answerOf(result, "one-word") shouldBe ""
                result.completedCount shouldBe 1
            }

            "매칭 후보라도 차단 관계면 FORBIDDEN 예외가 발생한다" {
                val targetId = 8L
                answerAll(targetId)
                val quizSet = completeOneToOneQuizSet(memberId)
                exposeAsCandidates(quizSet.id, memberId, targetId)
                memberBlockRepository.save(MemberBlock.create(blockerId = memberId, blockedMemberId = targetId))

                val exception = shouldThrow<WarnException> {
                    introNoteService.getIntroNotes(memberId, targetId)
                }
                exception.errorCode shouldBe ErrorCode.FORBIDDEN
            }

            "지난 주 후보는 조회할 수 없다 — 기준은 이번 운영 주에 완주한 퀴즈셋이다" {
                val targetId = 9L
                answerAll(targetId)
                // 이번 주에 이 퀴즈를 풀지 않았어도, 지난 주 후보 행은 그대로 남아 있다.
                val lastWeekMonday = OperationWeek.containing(LocalDate.now()).startedOn.minusWeeks(1)
                val lastWeek = quizSetRepository.save(
                    QuizSetFixture.create(
                        matchingType = MatchingType.ONE_TO_ONE,
                        startDate = lastWeekMonday.atStartOfDay(),
                        endDate = lastWeekMonday.plusDays(2).atTime(23, 59, 59),
                    ),
                )
                val progress =
                    QuizProgressFixture.create(memberId = memberId, quizSetId = lastWeek.id, totalCount = 1)
                progress.recordAnswer()
                quizProgressRepository.save(progress)
                exposeAsCandidates(lastWeek.id, memberId, targetId)

                val exception = shouldThrow<WarnException> {
                    introNoteService.getIntroNotes(memberId, targetId)
                }
                exception.errorCode shouldBe ErrorCode.FORBIDDEN
            }

            "매칭이 성사된 상대는 후보 미리보기가 아니라 전체 문항이 조회된다" {
                val targetId = 10L
                answerAll(targetId)
                val quizSet = completeOneToOneQuizSet(memberId)
                exposeAsCandidates(quizSet.id, memberId, targetId)
                personalMatchRepository.save(
                    PersonalMatchFixture.create(
                        requesterId = memberId,
                        receiverId = targetId,
                        status = PersonalMatchStatus.ACCEPTED,
                    ),
                )

                val result = introNoteService.getIntroNotes(memberId, targetId)

                result.answers.size shouldBe IntroQuestion.entries.size
            }

            "매칭 상태가 PENDING이면 조회할 수 없다" {
                val targetId = 4L
                personalMatchRepository.save(
                    PersonalMatchFixture.create(
                        requesterId = memberId,
                        receiverId = targetId,
                        status = PersonalMatchStatus.PENDING,
                    ),
                )

                val exception = shouldThrow<WarnException> {
                    introNoteService.getIntroNotes(memberId, targetId)
                }
                exception.errorCode shouldBe ErrorCode.FORBIDDEN
            }
        }
    },
)
