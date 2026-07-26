package com.ditto.domain.memberreview

import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.memberreview.entity.MemberReview
import com.ditto.domain.withId
import java.time.LocalDate
import java.time.LocalDateTime

object MemberReviewFixture {

    fun create(
        authorMemberId: Long = 1L,
        matchType: ChatRoomType = ChatRoomType.PERSONAL,
        matchId: Long = 1L,
        chatRoomId: Long = 1L,
        quizSetId: Long = 1L,
        weekStartedOn: LocalDate = LocalDate.of(2026, 4, 6),
        availableAt: LocalDateTime = LocalDateTime.of(2026, 4, 12, 23, 59, 59),
        id: Long = 0L,
    ): MemberReview = MemberReview.create(
        authorMemberId = authorMemberId,
        matchType = matchType,
        matchId = matchId,
        chatRoomId = chatRoomId,
        quizSetId = quizSetId,
        weekStartedOn = weekStartedOn,
        availableAt = availableAt,
    ).withId(id)
}
