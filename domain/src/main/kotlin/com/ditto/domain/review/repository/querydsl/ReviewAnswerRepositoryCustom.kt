package com.ditto.domain.review.repository.querydsl

import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.member.repository.querydsl.MemberFilter
import com.ditto.domain.review.entity.MeetingStatus
import com.ditto.domain.review.entity.ReviewAnswer
import java.time.LocalDate

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

    /** 그 주 평가에서 확정된 답을 유형·만남 상태·별점으로 묶은 수. 회원 조건은 평가를 쓴 사람에게 건다. */
    fun countAnsweredInWeek(
        weekStartedOn: LocalDate,
        matchType: ChatRoomType?,
        filter: MemberFilter,
    ): List<ReviewAnswerCount>

    /**
     * 그 주 같은 방에서 서로를 평가한 쌍을 (내가 준 별점, 상대가 준 별점)으로 묶은 수. 쌍은 작성자 id 가 작은 쪽에서 한 번 센다.
     * 쌍 단위라 회원 조건과 관리자 제외를 걸지 않는다.
     */
    fun countMutualRatingsInWeek(weekStartedOn: LocalDate, matchType: ChatRoomType?): List<MutualRatingCount>

    /**
     * 그 주 확정된 답마다 별점과 그 매칭의 점수. 1:1 은 작성자 → 대상 후보 점수, 그룹은 그룹 점수다.
     * 화면이 점수를 구간으로 묶는다. 쌍·그룹 단위라 회원 조건과 관리자 제외를 걸지 않는다.
     */
    fun findRatingsWithMatchScoreInWeek(weekStartedOn: LocalDate, matchType: ChatRoomType?): List<RatingWithMatchScore>

    /** 그 주 평가를 낸 회원마다 준 별점의 평균. 회원 조건은 평가를 쓴 사람에게 건다. */
    fun findAverageGivenRatingsInWeek(
        weekStartedOn: LocalDate,
        matchType: ChatRoomType?,
        filter: MemberFilter,
    ): List<Double>

    /** findAverageGivenRatingsInWeek 중 다음 회차에도 퀴즈를 완주한 회원만. */
    fun findAverageGivenRatingsOfMembersCompletedAgain(
        weekStartedOn: LocalDate,
        againWeekStartedOn: LocalDate,
        matchType: ChatRoomType?,
        filter: MemberFilter,
    ): List<Double>
}
