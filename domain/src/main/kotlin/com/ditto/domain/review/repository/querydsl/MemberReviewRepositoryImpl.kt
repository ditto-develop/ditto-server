package com.ditto.domain.review.repository.querydsl

import com.ditto.domain.chat.entity.ChatRoomStatus
import com.ditto.domain.chat.entity.QChatRoom.chatRoom
import com.ditto.domain.review.entity.MemberReview
import com.ditto.domain.review.entity.QMemberReview.memberReview
import com.ditto.domain.review.entity.ReviewProgressStatus
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
class MemberReviewRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : MemberReviewRepositoryCustom {

    override fun findPendingByAuthorOldestFirst(authorMemberId: Long): List<MemberReview> =
        queryFactory
            .selectFrom(memberReview)
            .where(
                memberReview.authorMemberId.eq(authorMemberId),
                memberReview.status.ne(ReviewProgressStatus.COMPLETED),
            )
            .orderBy(memberReview.availableAt.asc(), memberReview.id.asc())
            .fetch()

    override fun findEndedChatRoomIdsWithoutReview(limit: Int): List<Long> =
        queryFactory
            .select(chatRoom.id)
            .from(chatRoom)
            .where(
                chatRoom.status.eq(ChatRoomStatus.ENDED),
                // 평가를 열지 않는 유형은 영원히 "평가 없음"이라 빼지 않으면 배치 앞자리를 점유한다.
                chatRoom.sourceType.`in`(MemberReview.REVIEWABLE_MATCH_TYPES),
                queryFactory.selectOne()
                    .from(memberReview)
                    .where(memberReview.chatRoomId.eq(chatRoom.id))
                    .notExists(),
            )
            // 오래 밀린 것부터 — 가장 오래 기다린 참여자가 먼저 평가를 받는다.
            .orderBy(chatRoom.endedAt.asc(), chatRoom.id.asc())
            .limit(limit.toLong())
            .fetch()
}
