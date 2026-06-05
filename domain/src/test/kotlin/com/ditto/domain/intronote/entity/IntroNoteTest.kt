package com.ditto.domain.intronote.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class IntroNoteTest : FreeSpec(
    {
        "IntroNote" - {
            "create로 생성하면 회원·질문·답변이 채워진다" {
                val note = IntroNote.create(memberId = 1L, question = IntroQuestion.ONE_WORD, answer = "도전")

                note.memberId shouldBe 1L
                note.question shouldBe IntroQuestion.ONE_WORD
                note.answer shouldBe "도전"
            }

            "updateAnswer로 답변을 갱신한다" {
                val note = IntroNote.create(memberId = 1L, question = IntroQuestion.ONE_WORD, answer = "초안")

                note.updateAnswer("수정된 답변")

                note.answer shouldBe "수정된 답변"
            }
        }

        "IntroQuestion.from" - {
            "유효한 code면 해당 질문을 반환한다" {
                IntroQuestion.from("travel-items") shouldBe IntroQuestion.TRAVEL_ITEMS
            }

            "유효하지 않은 code면 BAD_REQUEST 예외가 발생한다" {
                val exception = shouldThrow<WarnException> {
                    IntroQuestion.from("invalid-code")
                }
                exception.errorCode shouldBe ErrorCode.BAD_REQUEST
            }
        }
    },
)
