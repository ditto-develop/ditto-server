package com.ditto.domain.review.repository

import com.ditto.domain.review.entity.ReviewAnswer
import org.springframework.data.jpa.repository.JpaRepository

interface ReviewAnswerRepository : JpaRepository<ReviewAnswer, Long> {
    /** 생성 순서(= 화면 노출 순서)로 반환한다. */
    fun findAllByMemberReviewIdOrderByIdAsc(memberReviewId: Long): List<ReviewAnswer>

    /** 남은 미응답 대상 수 — 마지막 대상 제출 여부 판정에 쓴다. */
    fun countByMemberReviewIdAndAnsweredAtIsNull(memberReviewId: Long): Long

    /** 여러 평가의 대상을 한 번에 읽는다. 반환 순서는 단건 조회와 동일(생성 순). */
    fun findAllByMemberReviewIdInOrderByIdAsc(memberReviewIds: Collection<Long>): List<ReviewAnswer>
}
