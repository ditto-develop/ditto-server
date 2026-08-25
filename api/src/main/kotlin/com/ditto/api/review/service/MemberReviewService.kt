package com.ditto.api.review.service

import com.ditto.api.review.dto.EndedChatRoom
import com.ditto.api.review.dto.MemberReviewResponse
import com.ditto.api.review.dto.RematchResultResponse
import com.ditto.api.review.dto.ReviewAnswerSubmitRequest
import com.ditto.api.review.dto.ReviewAnswerSubmitResponse
import com.ditto.api.system.ServerTimeProvider
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.review.entity.MemberReview
import com.ditto.domain.review.entity.ReviewAnswer
import com.ditto.domain.review.entity.ReviewAnswerContent
import com.ditto.domain.review.repository.MemberReviewRepository
import com.ditto.domain.review.repository.ReviewAnswerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class MemberReviewService(
    private val memberReviewRepository: MemberReviewRepository,
    private val reviewAnswerRepository: ReviewAnswerRepository,
    private val memberRepository: MemberRepository,
    private val rematchSubmitter: RematchSubmitter,
    private val serverTimeProvider: ServerTimeProvider,
) {
    /**
     * 아직 완료하지 않은 내 평가를 평가 가능해진 순서(오래된 순)로 반환한다.
     *
     * 대상과 프로필은 평가 수만큼 조회하지 않고 각각 한 번에 읽는다.
     * 채팅 종료 어댑터가 연결되기 전에는 운영 평가가 생성되지 않아 빈 목록이 정상이다.
     */
    @Transactional(readOnly = true)
    fun getMyPendingReviews(memberId: Long): List<MemberReviewResponse> {
        val reviews = memberReviewRepository.findPendingByAuthorOldestFirst(memberId)
        if (reviews.isEmpty()) {
            return emptyList()
        }

        val answers = reviewAnswerRepository.findAllByMemberReviewIdInOrderByIdAsc(reviews.map { it.id })
        val answersByReviewId = answers.groupBy { it.memberReviewId }
        val reviewedMembersById = findReviewedMembersById(answers)

        return reviews.map { review ->
            MemberReviewResponse.of(
                review = review,
                answers = answersByReviewId[review.id].orEmpty(),
                membersById = reviewedMembersById,
            )
        }
    }

    /**
     * 대상 한 명에 대한 답변을 **최종 확정**한다. 마지막 미응답 대상이 확정되면 평가가 자동 완료된다.
     *
     * 같은 내용을 다시 보내면 쓰기 없이 현재 상태를 돌려주고, 다른 내용이면 거부한다 —
     * 그룹 평가는 재매칭 의사까지 함께 비교한다.
     */
    fun submitAnswer(
        memberId: Long,
        reviewId: Long,
        reviewedMemberId: Long,
        request: ReviewAnswerSubmitRequest,
    ): ReviewAnswerSubmitResponse {
        val review = findMyReviewWithLock(memberId, reviewId)
        val answers = reviewAnswerRepository.findAllByMemberReviewIdOrderByIdAsc(reviewId)
        val answer = targetAnswerOf(answers, reviewedMemberId)

        // 값 검증을 분기보다 먼저 끝낸다 — 최초 제출과 재제출이 같은 오류로 답해야 한다.
        val content = ReviewAnswerContent.of(
            meetingStatus = request.meetingStatus,
            rating = request.rating,
            comment = request.comment,
        )
        // 재매칭 의사도 답변 저장 전에 검증한다 — 값이 잘못됐으면 답변도 남기지 않는다.
        // 그룹 평가인데 의사가 빠진 경우는 값을 쓰는 RematchSubmitter 가 잡는다.
        review.validateRematchAnswerAllowed(request.wantsOneToOneRematch)

        // 같은 내용의 재전송은 쓰기 없이 현재 상태를 돌려준다 — 네트워크 재시도로 사용자가 막히지 않게 한다.
        if (answer.isAnswered) {
            validateSameAnswer(answer, content)
            rematchSubmitter.validateUnchanged(review, memberId, reviewedMemberId, request.wantsOneToOneRematch)
            return submitResponseOf(review, answers, memberId, reviewedMemberId)
        }

        // 변이 전에 고정한다 — answer 는 answers 안의 같은 인스턴스라 아래 호출이 리스트를 바꾼다.
        val otherTargetsRemain = answers.any { it.id != answer.id && !it.isAnswered }
        val now = serverTimeProvider.now()

        answer.answer(content, now)
        review.recordAnswer(hasRemainingTarget = otherTargetsRemain, answeredAt = now)
        rematchSubmitter.submit(review, memberId, reviewedMemberId, request.wantsOneToOneRematch, now)

        return submitResponseOf(review, answers, memberId, reviewedMemberId)
    }

    /** 이미 성사된 재매칭은 재전송에도 다시 돌려준다 — 성사 응답이 유실됐을 수 있다. */
    private fun submitResponseOf(
        review: MemberReview,
        answers: List<ReviewAnswer>,
        memberId: Long,
        reviewedMemberId: Long,
    ): ReviewAnswerSubmitResponse {
        val rematch = rematchSubmitter.findMatched(review, memberId, reviewedMemberId)
        return ReviewAnswerSubmitResponse.of(review, answers, rematch)
    }

    private fun findMyReviewWithLock(memberId: Long, reviewId: Long): MemberReview {
        val review = memberReviewRepository.findWithLockById(reviewId)
        if (review == null || !review.isAuthor(memberId)) {
            // 남의 평가는 "권한 없음"이 아니라 "없음"으로 답한다 — 존재 여부 자체를 알려주지 않는다.
            throw WarnException(ErrorCode.REVIEW_NOT_FOUND)
        }
        return review
    }

    private fun targetAnswerOf(answers: List<ReviewAnswer>, reviewedMemberId: Long): ReviewAnswer =
        answers.firstOrNull { it.reviewedMemberId == reviewedMemberId }
            ?: throw WarnException(ErrorCode.INVALID_REVIEW_TARGET, "이 평가의 대상이 아닙니다.")

    /** 확정한 답변은 수정할 수 없다 — 재전송은 내용이 같을 때만 허용한다. */
    private fun validateSameAnswer(answer: ReviewAnswer, content: ReviewAnswerContent) {
        if (answer.content() != content) {
            throw WarnException(ErrorCode.REVIEW_ANSWER_NOT_MODIFIABLE)
        }
    }

    /**
     * 종료된 채팅방의 참여자별 평가를 연다. 참여자마다 진행 단위 1건과 자기 자신을 제외한 대상 행을 만든다.
     *
     * **같은 스냅샷으로** 다시 호출하면 기존 진행 단위를 그대로 반환한다. 다만 이미 만들어진 진행 단위의
     * 대상 행은 다시 맞추지 않으므로, 참여자 명단이 달라진 채로 재전달되면 새 대상이 반영되지 않는다 —
     * 종료 이벤트는 동일한 참여자 스냅샷으로 재전달돼야 한다.
     *
     * 같은 채팅방으로 **동시에** 들어오면 유일키가 중복 생성을 막고 진 쪽 호출이 실패한다.
     * 재전달·재시도·누락 복구는 채팅 종료 어댑터가 책임진다.
     *
     * 평가할 상대가 없으면(참여자 1명) 아무것도 만들지 않고 빈 목록을 반환한다 —
     * 시스템이 부르는 흐름이라 여기서 예외를 던지면 채팅 종료 자체가 실패한다.
     */
    fun createReviews(endedChatRoom: EndedChatRoom): List<MemberReview> {
        val reviewerIds = endedChatRoom.reviewerIds
        if (reviewerIds.isEmpty()) {
            throw WarnException(ErrorCode.INVALID_REVIEW_TARGET, "참여자가 없는 채팅방입니다.")
        }
        if (reviewerIds.size < MIN_REVIEWER_COUNT) {
            return emptyList()
        }
        return reviewerIds.map { createReviewFor(it, endedChatRoom) }
    }

    private fun createReviewFor(
        authorMemberId: Long,
        endedChatRoom: EndedChatRoom,
    ): MemberReview {
        val existing =
            memberReviewRepository.findByChatRoomIdAndAuthorMemberId(endedChatRoom.chatRoomId, authorMemberId)
        if (existing != null) {
            return existing
        }

        val review = memberReviewRepository.save(
            MemberReview.create(
                authorMemberId = authorMemberId,
                matchType = endedChatRoom.matchType,
                matchId = endedChatRoom.matchId,
                chatRoomId = endedChatRoom.chatRoomId,
                quizSetId = endedChatRoom.quizSetId,
                weekStartedOn = endedChatRoom.weekStartedOn,
                availableAt = endedChatRoom.endedAt,
            ),
        )
        val pendingAnswers = endedChatRoom
            .targetIdsFor(authorMemberId)
            .map { ReviewAnswer.pending(memberReviewId = review.id, reviewedMemberId = it) }

        reviewAnswerRepository.saveAll(pendingAnswers)
        return review
    }

    /** 탈퇴한 대상은 회원 행이 없어 결과에서 빠진다 — 응답에서는 프로필이 `null`이 된다. */
    private fun findReviewedMembersById(answers: List<ReviewAnswer>): Map<Long, Member> =
        memberRepository.findAllById(answers.map { it.reviewedMemberId }.distinct())
            .associateBy { it.id }

    companion object {
        /** 평가는 최소 두 명이 있어야 성립한다(자기 자신은 대상에서 빠지므로). 알림([ReviewRequestNotifier])도 같은 기준을 쓴다. */
        const val MIN_REVIEWER_COUNT = 2
    }
}
