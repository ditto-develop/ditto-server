package com.ditto.domain.memberreview.entity

import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.memberreview.MemberReviewFixture
import com.ditto.domain.memberreview.repository.MemberReviewRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource
import org.springframework.dao.DataIntegrityViolationException

class MemberReviewTest(
    private val memberReviewRepository: MemberReviewRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val answeredAt = LocalDateTime.of(2026, 4, 13, 10, 0)

    "생성" - {
        "저장 직후에는 아직 시작하지 않은 상태다" {
            val review = memberReviewRepository.save(MemberReviewFixture.create())

            review.id shouldNotBe 0L
            review.status shouldBe ReviewProgressStatus.NOT_STARTED
            review.completedAt shouldBe null
        }

        "매칭 식별자는 채팅방의 (source_type, source_id)와 같은 값을 담는다" {
            val review = memberReviewRepository.save(
                MemberReviewFixture.create(matchType = ChatRoomType.GROUP, matchId = 42L),
            )

            val found = memberReviewRepository.findById(review.id).get()
            found.matchType shouldBe ChatRoomType.GROUP
            found.matchId shouldBe 42L
        }

        "주간 식별자와 평가 가능 시각이 보존된다" {
            val review = memberReviewRepository.save(
                MemberReviewFixture.create(
                    weekStartedOn = LocalDate.of(2026, 7, 27),
                    availableAt = LocalDateTime.of(2026, 8, 2, 23, 59, 59),
                ),
            )

            val found = memberReviewRepository.findById(review.id).get()
            found.weekStartedOn shouldBe LocalDate.of(2026, 7, 27)
            found.availableAt shouldBe LocalDateTime.of(2026, 8, 2, 23, 59, 59)
        }
    }

    "동일 종료 이벤트 중복 방지" - {
        "같은 채팅방·작성자 조합은 하나만 저장된다" {
            memberReviewRepository.saveAndFlush(MemberReviewFixture.create(chatRoomId = 10L, authorMemberId = 1L))

            shouldThrow<DataIntegrityViolationException> {
                memberReviewRepository.saveAndFlush(
                    MemberReviewFixture.create(chatRoomId = 10L, authorMemberId = 1L),
                )
            }
        }

        "같은 채팅방이라도 작성자가 다르면 각각 저장된다" {
            memberReviewRepository.save(MemberReviewFixture.create(chatRoomId = 10L, authorMemberId = 1L))
            memberReviewRepository.save(MemberReviewFixture.create(chatRoomId = 10L, authorMemberId = 2L))

            memberReviewRepository.findAllByChatRoomId(10L).size shouldBe 2
        }

        "채팅방·작성자로 기존 진행 단위를 찾는다" {
            val saved = memberReviewRepository.save(
                MemberReviewFixture.create(chatRoomId = 10L, authorMemberId = 1L),
            )

            val found = memberReviewRepository.findByChatRoomIdAndAuthorMemberId(10L, 1L)

            found?.id shouldBe saved.id
        }
    }

    "진행 상태 전이" - {
        "남은 대상이 있으면 진행 중이 되고 완료 시각은 비어 있다" {
            val review = MemberReviewFixture.create()

            review.recordAnswer(hasRemainingTarget = true, answeredAt = answeredAt)

            review.status shouldBe ReviewProgressStatus.IN_PROGRESS
            review.completedAt shouldBe null
        }

        "마지막 대상이 확정되면 완료로 전이하고 완료 시각을 남긴다" {
            val review = MemberReviewFixture.create()
            review.recordAnswer(hasRemainingTarget = true, answeredAt = answeredAt)

            review.recordAnswer(hasRemainingTarget = false, answeredAt = answeredAt)

            review.status shouldBe ReviewProgressStatus.COMPLETED
            review.completedAt shouldBe answeredAt
        }

        "대상이 하나뿐인 1:1은 첫 제출로 바로 완료된다" {
            val review = MemberReviewFixture.create()

            review.recordAnswer(hasRemainingTarget = false, answeredAt = answeredAt)

            review.status shouldBe ReviewProgressStatus.COMPLETED
        }
    }
})
