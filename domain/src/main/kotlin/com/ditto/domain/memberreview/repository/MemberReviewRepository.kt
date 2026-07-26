package com.ditto.domain.memberreview.repository

import com.ditto.domain.memberreview.entity.MemberReview
import org.springframework.data.jpa.repository.JpaRepository

interface MemberReviewRepository : JpaRepository<MemberReview, Long> {
    /** 동일 종료 이벤트 재처리 시 기존 진행 단위를 찾아 멱등 처리한다. */
    fun findByChatRoomIdAndAuthorMemberId(chatRoomId: Long, authorMemberId: Long): MemberReview?

    fun findAllByChatRoomId(chatRoomId: Long): List<MemberReview>
}
