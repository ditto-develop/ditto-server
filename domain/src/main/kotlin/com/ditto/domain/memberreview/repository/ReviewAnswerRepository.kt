package com.ditto.domain.memberreview.repository

import com.ditto.domain.memberreview.entity.ReviewAnswer
import org.springframework.data.jpa.repository.JpaRepository

interface ReviewAnswerRepository : JpaRepository<ReviewAnswer, Long> {
    /** 생성 순서(= 화면 노출 순서)로 반환한다. */
    fun findAllByMemberReviewIdOrderByIdAsc(memberReviewId: Long): List<ReviewAnswer>

    /** 남은 미응답 대상 수 — 마지막 대상 제출 여부 판정에 쓴다. */
    fun countByMemberReviewIdAndAnsweredAtIsNull(memberReviewId: Long): Long
}
