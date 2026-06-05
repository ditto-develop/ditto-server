package com.ditto.api.intronote

import com.ditto.api.intronote.service.IntroNoteService
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.intronote.repository.IntroNoteRepository
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.GroupMatch
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

class IntroNoteServiceTest(
    private val introNoteService: IntroNoteService,
    private val introNoteRepository: IntroNoteRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    dataSource: DataSource,
) : IntegrationTest(
    dataSource,
    {
        val memberId = 1L

        fun answerOf(result: com.ditto.api.intronote.dto.IntroNotesResponse, code: String) =
            result.answers.first { it.questionCode == code }.answer

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
                val room = groupMatchRepository.save(GroupMatch.create(quizSetId = 1L))
                groupMatchMemberRepository.save(GroupMatchMember.of(roomId = room.id, memberId = memberId))
                groupMatchMemberRepository.save(GroupMatchMember.of(roomId = room.id, memberId = targetId))

                val result = introNoteService.getIntroNotes(memberId, targetId)

                answerOf(result, "one-word") shouldBe "그룹원답변"
            }

            "매칭도 그룹 채팅도 없는 상대면 FORBIDDEN 예외가 발생한다" {
                val targetId = 99L

                val exception = shouldThrow<WarnException> {
                    introNoteService.getIntroNotes(memberId, targetId)
                }
                exception.errorCode shouldBe ErrorCode.FORBIDDEN
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
