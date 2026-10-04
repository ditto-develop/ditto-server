package com.ditto.domain.review.repository.querydsl

import com.ditto.domain.review.entity.MeetingStatus
import com.ditto.domain.review.entity.ReviewAnswer

interface ReviewAnswerRepositoryCustom {

    /**
     * 내가 받은 확정 평가를 최신순으로 읽는다. 프로필의 "받은 평가" 집계용.
     * 방이 끝난 평가만 센다. 열린 방에서 나간 사람이 먼저 쓴 평가는 방이 끝날 때까지 드러나지 않는다.
     */
    fun findAllAnsweredInEndedRoomsByReviewedMemberId(reviewedMemberId: Long): List<ReviewAnswer>

    /** 내가 받은 확정 평가 중 특정 만남 상태의 개수. 만남 횟수(MET)용이며 방이 끝난 평가만 센다. */
    fun countAnsweredInEndedRoomsByReviewedMemberIdAndMeetingStatus(
        reviewedMemberId: Long,
        meetingStatus: MeetingStatus,
    ): Long
}
