package com.ditto.api.review

import com.ditto.api.review.dto.EndedChatRoom
import com.ditto.api.review.service.MemberReviewService
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.Location
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.review.entity.MeetingStatus
import com.ditto.domain.review.entity.ReviewProgressStatus
import com.ditto.domain.review.repository.MemberReviewRepository
import com.ditto.domain.review.repository.ReviewAnswerRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

class MemberReviewServiceTest(
    private val memberReviewService: MemberReviewService,
    private val memberReviewRepository: MemberReviewRepository,
    private val reviewAnswerRepository: ReviewAnswerRepository,
    private val memberRepository: MemberRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val endedAt = LocalDateTime.of(2026, 8, 2, 23, 59, 59)

    fun endedChatRoom(
        chatRoomId: Long = 100L,
        matchType: ChatRoomType = ChatRoomType.PERSONAL,
        participantIds: List<Long> = listOf(1L, 2L),
        chatEndedAt: LocalDateTime = endedAt,
    ) = EndedChatRoom(
        chatRoomId = chatRoomId,
        matchType = matchType,
        matchId = 7L,
        quizSetId = 3L,
        weekStartedOn = LocalDate.of(2026, 7, 27),
        participantIds = participantIds,
        endedAt = chatEndedAt,
    )

    "1:1 채팅 종료" - {
        "참여자 두 명에게 각각 진행 단위가 생기고 대상은 상대 한 명이다" {
            memberReviewService.createReviews(endedChatRoom())

            val reviews = memberReviewRepository.findAllByChatRoomId(100L)
            reviews.size shouldBe 2

            val myReview = reviews.first { it.authorMemberId == 1L }
            val targets = reviewAnswerRepository.findAllByMemberReviewIdOrderByIdAsc(myReview.id)
            targets.map { it.reviewedMemberId } shouldContainExactly listOf(2L)
        }

        "생성 직후에는 아직 시작하지 않은 상태이고 모든 대상이 미응답이다" {
            memberReviewService.createReviews(endedChatRoom())

            val review = memberReviewRepository.findByChatRoomIdAndAuthorMemberId(100L, 1L)!!
            review.status shouldBe ReviewProgressStatus.NOT_STARTED
            reviewAnswerRepository.countByMemberReviewIdAndAnsweredAtIsNull(review.id) shouldBe 1L
        }

        "종료 시점의 매칭 식별자와 주간 식별자가 그대로 기록된다" {
            memberReviewService.createReviews(endedChatRoom())

            val review = memberReviewRepository.findByChatRoomIdAndAuthorMemberId(100L, 1L)!!
            review.matchType shouldBe ChatRoomType.PERSONAL
            review.matchId shouldBe 7L
            review.quizSetId shouldBe 3L
            review.weekStartedOn shouldBe LocalDate.of(2026, 7, 27)
            review.availableAt shouldBe endedAt
        }
    }

    "그룹 채팅 종료" - {
        "참여자 4명이면 각자 자기 자신을 뺀 3명이 대상이 된다" {
            memberReviewService.createReviews(
                endedChatRoom(matchType = ChatRoomType.GROUP, participantIds = listOf(1L, 2L, 3L, 4L)),
            )

            val reviews = memberReviewRepository.findAllByChatRoomId(100L)
            reviews.size shouldBe 4

            reviews.forEach { review ->
                val targets = reviewAnswerRepository.findAllByMemberReviewIdOrderByIdAsc(review.id)
                targets.size shouldBe 3
                targets.map { it.reviewedMemberId } shouldNotContain review.authorMemberId
            }
        }

        "대상은 참여자 명단 순서대로 생성된다" {
            memberReviewService.createReviews(
                endedChatRoom(matchType = ChatRoomType.GROUP, participantIds = listOf(1L, 2L, 3L, 4L)),
            )

            val review = memberReviewRepository.findByChatRoomIdAndAuthorMemberId(100L, 2L)!!
            val targets = reviewAnswerRepository.findAllByMemberReviewIdOrderByIdAsc(review.id)

            targets.map { it.reviewedMemberId } shouldContainExactly listOf(1L, 3L, 4L)
        }
    }

    "종료 이벤트 재처리" - {
        "같은 채팅방으로 다시 호출해도 진행 단위와 대상이 중복 생성되지 않는다" {
            memberReviewService.createReviews(endedChatRoom(participantIds = listOf(1L, 2L)))

            memberReviewService.createReviews(endedChatRoom(participantIds = listOf(1L, 2L)))

            memberReviewRepository.findAllByChatRoomId(100L).size shouldBe 2
            val review = memberReviewRepository.findByChatRoomIdAndAuthorMemberId(100L, 1L)!!
            reviewAnswerRepository.findAllByMemberReviewIdOrderByIdAsc(review.id).size shouldBe 1
        }

        "재처리는 기존 진행 단위를 그대로 반환한다" {
            val created = memberReviewService.createReviews(endedChatRoom())

            val reprocessed = memberReviewService.createReviews(endedChatRoom())

            reprocessed.map { it.id } shouldContainExactly created.map { it.id }
        }
    }

    "참여자 명단 검증" - {
        "평가할 상대가 없으면(참여자 한 명) 아무것도 만들지 않는다" {
            val created = memberReviewService.createReviews(endedChatRoom(participantIds = listOf(1L)))

            created.shouldBeEmpty()
            memberReviewRepository.findAllByChatRoomId(100L).shouldBeEmpty()
        }

        "중복된 참여자는 한 번만 센다" {
            val created = memberReviewService.createReviews(endedChatRoom(participantIds = listOf(1L, 1L, 2L)))

            created.size shouldBe 2
            val review = memberReviewRepository.findByChatRoomIdAndAuthorMemberId(100L, 1L)!!
            reviewAnswerRepository.findAllByMemberReviewIdOrderByIdAsc(review.id)
                .map { it.reviewedMemberId } shouldContainExactly listOf(2L)
        }

        "참여자가 아예 없으면 잘못된 입력으로 거부한다" {
            val exception = shouldThrow<WarnException> {
                memberReviewService.createReviews(endedChatRoom(participantIds = emptyList()))
            }

            exception.errorCode shouldBe ErrorCode.INVALID_REVIEW_TARGET
        }
    }
    "내 미완료 평가 목록 조회" - {
        // 회원 ID는 저장 시 생성되므로, 저장하지 않은 참여자 ID는 저장된 ID에서 충분히 떨어뜨려 겹치지 않게 한다.
        val unsavedIdGap = 1_000L

        "미완료 평가만 반환하고 완료된 평가는 목록에서 빠진다" {
            val pendingRoomId = 100L
            val completedRoomId = 200L
            memberReviewService.createReviews(endedChatRoom(chatRoomId = pendingRoomId))
            memberReviewService.createReviews(endedChatRoom(chatRoomId = completedRoomId))
            val completed = memberReviewRepository.findByChatRoomIdAndAuthorMemberId(completedRoomId, 1L)!!
            completed.recordAnswer(hasRemainingTarget = false, answeredAt = endedAt)
            memberReviewRepository.save(completed)

            val reviews = memberReviewService.getMyPendingReviews(1L)

            reviews.map { it.chatRoomId } shouldContainExactly listOf(pendingRoomId)
        }

        "내 평가만 반환한다" {
            memberReviewService.createReviews(endedChatRoom(participantIds = listOf(1L, 2L)))
            val myReview = memberReviewRepository.findByChatRoomIdAndAuthorMemberId(100L, 1L)!!

            val reviews = memberReviewService.getMyPendingReviews(1L)

            reviews.map { it.reviewId } shouldContainExactly listOf(myReview.id)
        }

        "평가 가능해진 순서(오래된 순)로 반환한다" {
            val earlierRoomId = 100L
            val laterRoomId = 300L
            memberReviewService.createReviews(
                endedChatRoom(chatRoomId = laterRoomId, chatEndedAt = endedAt.plusDays(1)),
            )
            memberReviewService.createReviews(endedChatRoom(chatRoomId = earlierRoomId))

            val reviews = memberReviewService.getMyPendingReviews(1L)

            reviews.map { it.chatRoomId } shouldContainExactly listOf(earlierRoomId, laterRoomId)
        }

        "대상 프로필과 진행률이 함께 반환된다" {
            val target = memberRepository.save(
                MemberFixture.create(
                    nickname = "댕이누나",
                    gender = Gender.FEMALE,
                    age = 27,
                    location = Location.SEOUL,
                    caricature = "m1",
                ),
            )
            val authorId = target.id + unsavedIdGap
            memberReviewService.createReviews(
                endedChatRoom(matchType = ChatRoomType.GROUP, participantIds = listOf(authorId, target.id)),
            )

            val review = memberReviewService.getMyPendingReviews(authorId).single()

            review.matchType shouldBe ChatRoomType.GROUP
            review.status shouldBe ReviewProgressStatus.NOT_STARTED
            review.totalTargetCount shouldBe 1
            review.answeredTargetCount shouldBe 0
            val targetResponse = review.targets.single()
            targetResponse.memberId shouldBe target.id
            targetResponse.nickname shouldBe "댕이누나"
            targetResponse.gender shouldBe Gender.FEMALE.name
            targetResponse.age shouldBe 27
            targetResponse.location shouldBe Location.SEOUL.code
            targetResponse.profileImageUrl shouldBe "m1"
        }

        "탈퇴 등으로 회원 정보가 없는 대상은 프로필이 비어 온다" {
            val withdrawnTargetId = 2L
            memberReviewService.createReviews(endedChatRoom(participantIds = listOf(1L, withdrawnTargetId)))

            val targetResponse = memberReviewService.getMyPendingReviews(1L).single().targets.single()

            targetResponse.memberId shouldBe withdrawnTargetId
            targetResponse.nickname shouldBe null
            targetResponse.location shouldBe null
            targetResponse.profileImageUrl shouldBe null
        }

        // 여러 평가를 한 번에 조립하므로, 대상이 다른 평가로 넘어가지 않는지 확인한다.
        "평가가 여러 건이어도 각 평가의 대상이 섞이지 않는다" {
            memberReviewService.createReviews(
                endedChatRoom(chatRoomId = 100L, participantIds = listOf(1L, 2L)),
            )
            memberReviewService.createReviews(
                endedChatRoom(
                    chatRoomId = 200L,
                    matchType = ChatRoomType.GROUP,
                    participantIds = listOf(1L, 3L, 4L),
                    chatEndedAt = endedAt.plusDays(1),
                ),
            )

            val reviews = memberReviewService.getMyPendingReviews(1L)

            reviews.size shouldBe 2
            val personalReview = reviews.first { it.chatRoomId == 100L }
            personalReview.totalTargetCount shouldBe 1
            personalReview.targets.map { it.memberId } shouldContainExactly listOf(2L)
            val groupReview = reviews.first { it.chatRoomId == 200L }
            groupReview.totalTargetCount shouldBe 2
            groupReview.targets.map { it.memberId } shouldContainExactly listOf(3L, 4L)
        }

        // 상대가 나를 어떻게 평가했는지는 어떤 경로로도 나가면 안 된다.
        "상대가 나를 평가해도 내 목록에는 그 답변이 실리지 않는다" {
            memberReviewService.createReviews(endedChatRoom(participantIds = listOf(1L, 2L)))
            val counterpartReview = memberReviewRepository.findByChatRoomIdAndAuthorMemberId(100L, 2L)!!
            val counterpartAnswer =
                reviewAnswerRepository.findAllByMemberReviewIdOrderByIdAsc(counterpartReview.id).single()
            counterpartAnswer.answer(MeetingStatus.MET, 5, "좋았어요", endedAt)
            reviewAnswerRepository.save(counterpartAnswer)

            val myTarget = memberReviewService.getMyPendingReviews(1L).single().targets.single()

            myTarget.memberId shouldBe 2L
            myTarget.answeredAt shouldBe null
            myTarget.rating shouldBe null
            myTarget.comment shouldBe null
            myTarget.meetingStatus shouldBe null
        }

        "평가가 없으면 빈 목록을 반환한다" {
            memberReviewService.getMyPendingReviews(1L).shouldBeEmpty()
        }
    }
})
