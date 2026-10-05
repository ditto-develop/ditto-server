package com.ditto.api.admin.dummy.cleanup

import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.rematch.repository.RematchRepository
import com.ditto.domain.review.repository.MemberReviewRepository
import com.ditto.domain.review.repository.ReviewAnswerRepository
import org.springframework.stereotype.Component

/** 더미가 낀 매칭·재매칭을 찾고, 더미가 쓰거나 받은 평가를 지운다. 찾은 매칭 기록은 MatchingRecordEraser 가 지운다. */
@Component
class DummyMatchDataCleaner(
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val rematchRepository: RematchRepository,
    private val memberReviewRepository: MemberReviewRepository,
    private val reviewAnswerRepository: ReviewAnswerRepository,
) {
    /** 더미가 초대받은 그룹. 응답 상태와 무관하다. 거절한 더미가 있던 그룹도 QA 데이터로 본다. */
    fun findGroupMatchIdsWith(dummyIds: Collection<Long>): Set<Long> =
        groupMatchMemberRepository.findByMemberIdIn(dummyIds).map { it.roomId }.toSet()

    /** 더미가 낀 쌍과, 지울 그룹에서 나온 쌍. */
    fun findRematchIdsWith(dummyIds: Collection<Long>, groupMatchIds: Collection<Long>): Set<Long> =
        rematchRepository.findByMemberId1InOrMemberId2InOrSourceGroupMatchIdIn(dummyIds, dummyIds, groupMatchIds)
            .map { it.id }
            .toSet()

    fun findPersonalMatchIdsWith(dummyIds: Collection<Long>): Set<Long> =
        personalMatchRepository.findByMemberId1InOrMemberId2In(dummyIds, dummyIds).map { it.id }.toSet()

    /** 지운 방 밖에서 더미가 쓰거나 받은 평가. 지운 방의 평가는 MatchingRecordEraser 가 먼저 지운다. */
    fun deleteReviewsOf(dummyIds: Collection<Long>) {
        val reviewIds = memberReviewRepository.findByChatRoomIdInOrAuthorMemberIdIn(emptyList(), dummyIds)
            .map { it.id }
        val answerIds = reviewAnswerRepository.findByMemberReviewIdInOrReviewedMemberIdIn(reviewIds, dummyIds)
            .map { it.id }
        reviewAnswerRepository.deleteAllByIdInBatch(answerIds)
        memberReviewRepository.deleteAllByIdInBatch(reviewIds)
    }
}
