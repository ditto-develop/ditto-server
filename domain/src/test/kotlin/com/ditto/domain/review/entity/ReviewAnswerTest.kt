package com.ditto.domain.review.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.review.ReviewAnswerFixture
import com.ditto.domain.review.repository.ReviewAnswerRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

class ReviewAnswerTest(
    private val reviewAnswerRepository: ReviewAnswerRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val answeredAt = LocalDateTime.of(2026, 4, 13, 10, 0)

    "생성 직후" - {
        "미응답 상태로 저장된다" {
            val answer = reviewAnswerRepository.save(ReviewAnswerFixture.pending())

            answer.isAnswered shouldBe false
            answer.answeredAt shouldBe null
            answer.rating shouldBe null
            answer.meetingStatus shouldBe null
        }
    }

    "답변 확정" - {
        "만남 여부·별점·코멘트가 저장되고 응답 완료가 된다" {
            val answer = reviewAnswerRepository.save(ReviewAnswerFixture.pending())

            answer.answer(ReviewAnswerContent.of(MeetingStatus.MET, 4, "친절하고 재밌어요"), answeredAt)
            reviewAnswerRepository.save(answer)

            val found = reviewAnswerRepository.findById(answer.id).get()
            found.meetingStatus shouldBe MeetingStatus.MET
            found.rating shouldBe 4
            found.comment shouldBe "친절하고 재밌어요"
            found.answeredAt shouldBe answeredAt
            found.isAnswered shouldBe true
        }

        "검증에 실패하면 어떤 필드도 바뀌지 않는다" {
            val answer = ReviewAnswerFixture.pending()

            shouldThrow<WarnException> {
                answer.answer(ReviewAnswerContent.of(MeetingStatus.NO_SHOW, 0, "남으면 안 됨"), answeredAt)
            }

            answer.meetingStatus shouldBe null
            answer.rating shouldBe null
            answer.comment shouldBe null
            answer.isAnswered shouldBe false
        }

        "이미 확정한 대상에 재제출하면 거부한다" {
            val answer = ReviewAnswerFixture.pending()
            answer.answer(ReviewAnswerContent.of(MeetingStatus.MET, 4, null), answeredAt)

            val exception = shouldThrow<WarnException> {
                answer.answer(ReviewAnswerContent.of(MeetingStatus.CHAT_ONLY, 1, null), answeredAt)
            }

            exception.errorCode shouldBe ErrorCode.REVIEW_ALREADY_ANSWERED
        }
    }

    "별점 검증" - {
        "1 미만이면 거부한다" {
            val exception = shouldThrow<WarnException> {
                ReviewAnswerFixture.pending().answer(ReviewAnswerContent.of(MeetingStatus.MET, 0, null), answeredAt)
            }

            exception.errorCode shouldBe ErrorCode.INVALID_REVIEW_ANSWER
        }

        "5를 넘으면 거부한다" {
            shouldThrow<WarnException> {
                ReviewAnswerFixture.pending().answer(ReviewAnswerContent.of(MeetingStatus.MET, 6, null), answeredAt)
            }
        }

        "경계값 1과 5는 허용한다" {
            ReviewAnswerFixture.pending().answer(ReviewAnswerContent.of(MeetingStatus.MET, 1, null), answeredAt)
            ReviewAnswerFixture.pending().answer(ReviewAnswerContent.of(MeetingStatus.MET, 5, null), answeredAt)
        }
    }

    "코멘트 정규화" - {
        "공백만 입력하면 null로 저장한다" {
            val answer = ReviewAnswerFixture.pending()

            answer.answer(ReviewAnswerContent.of(MeetingStatus.MET, 3, "   "), answeredAt)

            answer.comment shouldBe null
        }

        "앞뒤 공백을 제거한다" {
            val answer = ReviewAnswerFixture.pending()

            answer.answer(ReviewAnswerContent.of(MeetingStatus.MET, 3, "  좋았어요  "), answeredAt)

            answer.comment shouldBe "좋았어요"
        }

        "50자를 넘으면 거부한다" {
            val exception = shouldThrow<WarnException> {
                ReviewAnswerFixture.pending()
                    .answer(ReviewAnswerContent.of(MeetingStatus.MET, 3, "가".repeat(51)), answeredAt)
            }

            exception.errorCode shouldBe ErrorCode.INVALID_REVIEW_ANSWER
        }

        "50자는 허용한다" {
            val answer = ReviewAnswerFixture.pending()

            answer.answer(ReviewAnswerContent.of(MeetingStatus.MET, 3, "가".repeat(50)), answeredAt)

            answer.comment shouldBe "가".repeat(50)
        }
    }
})
