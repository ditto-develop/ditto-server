package com.ditto.api.admin

import com.ditto.api.admin.quiz.AdminQuizService
import com.ditto.api.admin.quiz.dto.QuizChoiceForm
import com.ditto.api.admin.quiz.dto.QuizForm
import com.ditto.api.admin.quiz.dto.QuizSetForm
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.quiz.QuizAnswerFixture
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

class AdminQuizServiceTest(
    private val adminQuizService: AdminQuizService,
    private val quizRepository: QuizRepository,
    private val quizChoiceRepository: QuizChoiceRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val quizProgressRepository: QuizProgressRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun quizSetForm(startDate: LocalDateTime, endDate: LocalDateTime) = QuizSetForm(
        category = "성격",
        title = "주간 검증 테스트",
        startDate = startDate,
        endDate = endDate,
    )

    fun quizForm(question: String, vararg choices: String) = QuizForm(
        question = question,
        choices = choices.map { QuizChoiceForm(content = it) }.toMutableList(),
    )

    fun formWithQuizzes(vararg quizzes: QuizForm) = QuizSetForm(
        category = "성격",
        title = "문항 저장 테스트",
        startDate = LocalDateTime.of(2026, 7, 27, 0, 0),
        endDate = LocalDateTime.of(2026, 8, 2, 23, 59),
        quizzes = quizzes.toMutableList(),
    )

    "퀴즈셋 기간 검증" - {
        "기간이 한 운영 주 안이면 생성되고 weekStartedOn이 시작일의 월요일로 파생된다" {
            val quizSet = adminQuizService.createQuizSet(
                quizSetForm(
                    startDate = LocalDateTime.of(2026, 7, 29, 0, 0),
                    endDate = LocalDateTime.of(2026, 8, 2, 23, 59),
                ),
            )

            quizSet.weekStartedOn shouldBe LocalDate.of(2026, 7, 27)
        }

        "기간이 두 운영 주에 걸치면 생성이 거부된다" {
            val exception = shouldThrow<WarnException> {
                adminQuizService.createQuizSet(
                    quizSetForm(
                        startDate = LocalDateTime.of(2026, 7, 24, 0, 0),
                        endDate = LocalDateTime.of(2026, 7, 28, 23, 59),
                    ),
                )
            }

            exception.errorCode shouldBe ErrorCode.BAD_REQUEST
        }

        "수정 기간이 두 운영 주에 걸치면 수정이 거부된다" {
            val quizSet = adminQuizService.createQuizSet(
                quizSetForm(
                    startDate = LocalDateTime.of(2026, 7, 27, 0, 0),
                    endDate = LocalDateTime.of(2026, 8, 2, 23, 59),
                ),
            )

            shouldThrow<WarnException> {
                adminQuizService.updateQuizSet(
                    quizSet.id,
                    quizSetForm(
                        startDate = LocalDateTime.of(2026, 7, 31, 0, 0),
                        endDate = LocalDateTime.of(2026, 8, 3, 23, 59),
                    ),
                )
            }
        }

        "수정으로 시작일을 다른 주로 옮기면 weekStartedOn이 재파생된다" {
            val quizSet = adminQuizService.createQuizSet(
                quizSetForm(
                    startDate = LocalDateTime.of(2026, 7, 27, 0, 0),
                    endDate = LocalDateTime.of(2026, 8, 2, 23, 59),
                ),
            )

            adminQuizService.updateQuizSet(
                quizSet.id,
                quizSetForm(
                    startDate = LocalDateTime.of(2026, 8, 3, 0, 0),
                    endDate = LocalDateTime.of(2026, 8, 9, 23, 59),
                ),
            )

            adminQuizService.getQuizSet(quizSet.id).weekStartedOn shouldBe LocalDate.of(2026, 8, 3)
        }
    }

    "문항 일괄 저장" - {
        "화면에 보인 순서가 displayOrder 로 매겨진다" {
            val quizSet = adminQuizService.createQuizSet(
                formWithQuizzes(
                    quizForm("치약 짤 때?", "아래부터", "중간부터"),
                    quizForm("여행 계획은?", "분 단위로", "즉흥적으로"),
                ),
            )

            val quizzes = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSet.id)
            quizzes.map { it.displayOrder } shouldBe listOf(1, 2)
            quizzes.map { it.question } shouldBe listOf("치약 짤 때?", "여행 계획은?")
            quizChoiceRepository.findByQuizIdOrderByDisplayOrderAsc(quizzes[0].id)
                .map { it.content to it.displayOrder } shouldBe listOf("아래부터" to 1, "중간부터" to 2)
        }

        "빈 행이 섞여 있으면 저장을 거부한다" {
            val exception = shouldThrow<WarnException> {
                adminQuizService.createQuizSet(
                    formWithQuizzes(
                        quizForm("치약 짤 때?", "아래부터", "중간부터"),
                        quizForm("", "", ""),
                    ),
                )
            }

            exception.errorCode shouldBe ErrorCode.BAD_REQUEST
            exception.message shouldBe "2번 문항의 질문 항목이 비어 있습니다."
        }

        "오류 메시지의 문항 번호는 화면에 보이는 번호와 같다" {
            val exception = shouldThrow<WarnException> {
                adminQuizService.createQuizSet(
                    formWithQuizzes(
                        quizForm("1번 문항", "A", "B"),
                        quizForm("2번 문항", "A", ""),
                        quizForm("3번 문항", "A", "B"),
                    ),
                )
            }

            exception.message shouldBe "2번 문항의 선택지 항목이 비어 있습니다."
        }

        "같은 문항 id 를 두 번 보내면 거부한다" {
            val quizSet = adminQuizService.createQuizSet(
                formWithQuizzes(quizForm("원래 문항", "A", "B")),
            )
            val quiz = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSet.id).first()

            val exception = shouldThrow<WarnException> {
                adminQuizService.updateQuizSet(
                    quizSet.id,
                    formWithQuizzes(
                        QuizForm(id = quiz.id, question = "첫 번째", choices = mutableListOf(QuizChoiceForm(content = "A"), QuizChoiceForm(content = "B"))),
                        QuizForm(id = quiz.id, question = "두 번째", choices = mutableListOf(QuizChoiceForm(content = "C"), QuizChoiceForm(content = "D"))),
                    ),
                )
            }

            exception.errorCode shouldBe ErrorCode.BAD_REQUEST
        }

        "답변이 달린 문항에 선택지를 더 붙이면 거부한다" {
            val quizSet = adminQuizService.createQuizSet(
                formWithQuizzes(quizForm("답변 달린 문항", "A", "B")),
            )
            val quiz = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSet.id).first()
            val choices = quizChoiceRepository.findByQuizIdOrderByDisplayOrderAsc(quiz.id)
            quizAnswerRepository.save(
                QuizAnswerFixture.create(memberId = 1L, quizId = quiz.id, choiceId = choices[0].id),
            )

            val exception = shouldThrow<WarnException> {
                adminQuizService.updateQuizSet(
                    quizSet.id,
                    formWithQuizzes(
                        QuizForm(
                            id = quiz.id,
                            question = "답변 달린 문항",
                            choices = mutableListOf(
                                QuizChoiceForm(id = choices[0].id, content = "A"),
                                QuizChoiceForm(id = choices[1].id, content = "B"),
                                QuizChoiceForm(content = "C"),
                            ),
                        ),
                    ),
                )
            }

            exception.errorCode shouldBe ErrorCode.BAD_REQUEST
        }

        "참여가 시작되면 개수가 같아도 문항 교체를 거부한다" {
            val quizSet = adminQuizService.createQuizSet(
                formWithQuizzes(
                    quizForm("1번", "A", "B"),
                    quizForm("2번", "C", "D"),
                ),
            )
            val quizzes = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSet.id)
            quizProgressRepository.save(QuizProgressFixture.create(memberId = 1L, quizSetId = quizSet.id, totalCount = 2))

            // 2번 문항을 빼고 새 문항을 넣는다. 개수는 2 → 2 로 같다.
            val exception = shouldThrow<WarnException> {
                adminQuizService.updateQuizSet(
                    quizSet.id,
                    formWithQuizzes(
                        QuizForm(
                            id = quizzes[0].id,
                            question = "1번",
                            choices = mutableListOf(QuizChoiceForm(content = "A"), QuizChoiceForm(content = "B")),
                        ),
                        quizForm("교체된 새 문항", "E", "F"),
                    ),
                )
            }

            exception.errorCode shouldBe ErrorCode.BAD_REQUEST
            quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSet.id).map { it.id } shouldBe quizzes.map { it.id }
        }

        "참여가 시작된 퀴즈셋은 문항 개수를 바꿀 수 없다" {
            val quizSet = adminQuizService.createQuizSet(
                formWithQuizzes(
                    quizForm("1번", "A", "B"),
                    quizForm("2번", "C", "D"),
                ),
            )
            val quizzes = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSet.id)
            quizProgressRepository.save(QuizProgressFixture.create(memberId = 1L, quizSetId = quizSet.id, totalCount = 2))

            val exception = shouldThrow<WarnException> {
                adminQuizService.updateQuizSet(
                    quizSet.id,
                    formWithQuizzes(
                        QuizForm(
                            id = quizzes[0].id,
                            question = "1번",
                            choices = mutableListOf(QuizChoiceForm(content = "A"), QuizChoiceForm(content = "B")),
                        ),
                    ),
                )
            }

            exception.errorCode shouldBe ErrorCode.BAD_REQUEST
        }

        "참여가 시작돼도 문구 수정은 된다" {
            val quizSet = adminQuizService.createQuizSet(
                formWithQuizzes(quizForm("원래 질문", "A", "B")),
            )
            val quiz = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSet.id).first()
            val choices = quizChoiceRepository.findByQuizIdOrderByDisplayOrderAsc(quiz.id)
            quizProgressRepository.save(QuizProgressFixture.create(memberId = 1L, quizSetId = quizSet.id, totalCount = 1))

            adminQuizService.updateQuizSet(
                quizSet.id,
                formWithQuizzes(
                    QuizForm(
                        id = quiz.id,
                        question = "고친 질문",
                        choices = mutableListOf(
                            QuizChoiceForm(id = choices[0].id, content = "A"),
                            QuizChoiceForm(id = choices[1].id, content = "B"),
                        ),
                    ),
                ),
            )

            quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSet.id).first().question shouldBe "고친 질문"
        }

        "선택지 한 칸이 비면 저장을 거부한다" {
            val exception = shouldThrow<WarnException> {
                adminQuizService.createQuizSet(
                    formWithQuizzes(quizForm("치약 짤 때?", "아래부터", "")),
                )
            }

            exception.errorCode shouldBe ErrorCode.BAD_REQUEST
        }

        "제출에 없는 기존 문항은 선택지까지 삭제된다" {
            val quizSet = adminQuizService.createQuizSet(
                formWithQuizzes(
                    quizForm("남길 문항", "A", "B"),
                    quizForm("지울 문항", "C", "D"),
                ),
            )
            val kept = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSet.id).first()
            val removedQuizId = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSet.id)[1].id

            adminQuizService.updateQuizSet(
                quizSet.id,
                formWithQuizzes(
                    QuizForm(
                        id = kept.id,
                        question = "남길 문항",
                        choices = mutableListOf(QuizChoiceForm(content = "A"), QuizChoiceForm(content = "B")),
                    ),
                ),
            )

            quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSet.id).map { it.id } shouldBe listOf(kept.id)
            quizChoiceRepository.findByQuizIdOrderByDisplayOrderAsc(removedQuizId).size shouldBe 0
        }

        "id 를 함께 보내면 새로 만들지 않고 그 문항을 수정한다" {
            val quizSet = adminQuizService.createQuizSet(
                formWithQuizzes(quizForm("원래 질문", "A", "B")),
            )
            val quiz = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSet.id).first()
            val choices = quizChoiceRepository.findByQuizIdOrderByDisplayOrderAsc(quiz.id)

            adminQuizService.updateQuizSet(
                quizSet.id,
                formWithQuizzes(
                    QuizForm(
                        id = quiz.id,
                        question = "고친 질문",
                        choices = mutableListOf(
                            QuizChoiceForm(id = choices[0].id, content = "A"),
                            QuizChoiceForm(id = choices[1].id, content = "B로 수정"),
                        ),
                    ),
                ),
            )

            val updated = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSet.id)
            updated.map { it.id } shouldBe listOf(quiz.id)
            updated.first().question shouldBe "고친 질문"
            quizChoiceRepository.findByQuizIdOrderByDisplayOrderAsc(quiz.id)
                .map { it.id to it.content } shouldBe listOf(choices[0].id to "A", choices[1].id to "B로 수정")
        }

        "답변이 달린 문항은 삭제를 거부한다" {
            val quizSet = adminQuizService.createQuizSet(
                formWithQuizzes(quizForm("답변 달린 문항", "A", "B")),
            )
            val quiz = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSet.id).first()
            val choice = quizChoiceRepository.findByQuizIdOrderByDisplayOrderAsc(quiz.id).first()
            quizAnswerRepository.save(
                QuizAnswerFixture.create(memberId = 1L, quizId = quiz.id, choiceId = choice.id),
            )

            val exception = shouldThrow<WarnException> {
                adminQuizService.updateQuizSet(
                    quizSet.id,
                    formWithQuizzes(quizForm("다른 문항으로 교체", "C", "D")),
                )
            }

            exception.errorCode shouldBe ErrorCode.BAD_REQUEST
        }

        "quizzes 가 비어 있으면 기존 문항을 건드리지 않는다" {
            val quizSet = adminQuizService.createQuizSet(
                formWithQuizzes(quizForm("유지될 문항", "A", "B")),
            )

            adminQuizService.updateQuizSet(
                quizSet.id,
                quizSetForm(
                    startDate = LocalDateTime.of(2026, 7, 27, 0, 0),
                    endDate = LocalDateTime.of(2026, 8, 2, 23, 59),
                ),
            )

            quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSet.id).size shouldBe 1
        }
    }
})
