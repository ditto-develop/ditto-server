package com.ditto.domain.review.repository

import com.ditto.domain.review.entity.MeetingStatus
import com.ditto.domain.review.entity.ReviewAnswer
import org.springframework.data.jpa.repository.JpaRepository

interface ReviewAnswerRepository : JpaRepository<ReviewAnswer, Long> {
    /** 생성 순서(= 화면 노출 순서)로 반환한다. */
    fun findAllByMemberReviewIdOrderByIdAsc(memberReviewId: Long): List<ReviewAnswer>

    /** 남은 미응답 대상 수 — 마지막 대상 제출 여부 판정에 쓴다. */
    fun countByMemberReviewIdAndAnsweredAtIsNull(memberReviewId: Long): Long

    /** 여러 평가의 대상을 한 번에 읽는다. 반환 순서는 단건 조회와 동일(생성 순). */
    fun findAllByMemberReviewIdInOrderByIdAsc(memberReviewIds: Collection<Long>): List<ReviewAnswer>

    /**
     * 내가 **받은** 확정 평가를 최신순으로 읽는다 — 프로필의 "받은 평가" 집계용.
     * 미응답(`answeredAt == null`)은 아직 평가가 아니므로 제외한다.
     */
    fun findAllByReviewedMemberIdAndAnsweredAtIsNotNullOrderByAnsweredAtDesc(
        reviewedMemberId: Long,
    ): List<ReviewAnswer>

    /** 내가 받은 확정 평가 중 특정 만남 상태의 개수 — 만남 횟수(MET)·노쇼 횟수(NO_SHOW)용. */
    fun countByReviewedMemberIdAndMeetingStatusAndAnsweredAtIsNotNull(
        reviewedMemberId: Long,
        meetingStatus: MeetingStatus,
    ): Long
}
