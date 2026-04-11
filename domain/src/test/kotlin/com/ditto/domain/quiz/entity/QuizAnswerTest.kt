package com.ditto.domain.quiz.entity

import com.ditto.domain.quiz.QuizAnswerFixture
import com.ditto.domain.quiz.QuizChoiceFixture
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.sql.DataSource

class QuizAnswerTest(
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizChoiceRepository: QuizChoiceRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    dataSource: DataSource,
) : IntegrationTest(
        dataSource,
        {

            "QuizAnswer 생성" - {
                "QuizAnswer를 생성하고 저장할 수 있다" {
                    val quizSet = quizSetRepository.save(QuizSetFixture.create())
                    val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id))
                    val choice = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id))

                    val answer =
                        quizAnswerRepository.save(
                            QuizAnswerFixture.create(memberId = 1L, quizId = quiz.id, choiceId = choice.id),
                        )

                    answer.id shouldNotBe 0L
                    answer.memberId shouldBe 1L
                    answer.quizId shouldBe quiz.id
                    answer.choiceId shouldBe choice.id
                }

                "같은 memberId, quizId로 중복 저장하면 예외가 발생한다" {
                    val quizSet = quizSetRepository.save(QuizSetFixture.create())
                    val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id))
                    val choice1 =
                        quizChoiceRepository.save(
                            QuizChoiceFixture.create(
                                quizId = quiz.id,
                                content = "A",
                                displayOrder = 1,
                            ),
                        )
                    val choice2 =
                        quizChoiceRepository.save(
                            QuizChoiceFixture.create(
                                quizId = quiz.id,
                                content = "B",
                                displayOrder = 2,
                            ),
                        )

                    quizAnswerRepository.save(
                        QuizAnswerFixture.create(memberId = 1L, quizId = quiz.id, choiceId = choice1.id),
                    )

                    shouldThrow<Exception> {
                        quizAnswerRepository.saveAndFlush(
                            QuizAnswerFixture.create(memberId = 1L, quizId = quiz.id, choiceId = choice2.id),
                        )
                    }
                }
            }

            "QuizAnswer 수정" - {
                "updateChoice()로 선택지를 변경할 수 있다" {
                    val quizSet = quizSetRepository.save(QuizSetFixture.create())
                    val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id))
                    val choice1 =
                        quizChoiceRepository.save(
                            QuizChoiceFixture.create(
                                quizId = quiz.id,
                                content = "A",
                                displayOrder = 1,
                            ),
                        )
                    val choice2 =
                        quizChoiceRepository.save(
                            QuizChoiceFixture.create(
                                quizId = quiz.id,
                                content = "B",
                                displayOrder = 2,
                            ),
                        )

                    val answer =
                        quizAnswerRepository.save(
                            QuizAnswerFixture.create(memberId = 1L, quizId = quiz.id, choiceId = choice1.id),
                        )

                    answer.changeChoice(choice2.id)
                    quizAnswerRepository.saveAndFlush(answer)

                    val found = quizAnswerRepository.findByMemberIdAndQuizId(1L, quiz.id)
                    found shouldNotBe null
                    found!!.choiceId shouldBe choice2.id
                }
            }
        },
    )
