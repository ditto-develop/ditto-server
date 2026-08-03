package com.ditto.domain.review.repository.querydsl

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
}
