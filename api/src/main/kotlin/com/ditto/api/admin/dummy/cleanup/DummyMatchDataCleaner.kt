package com.ditto.api.admin.dummy.cleanup

import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.rematch.repository.RematchRepository
import com.ditto.domain.review.repository.MemberReviewRepository
import com.ditto.domain.review.repository.ReviewAnswerRepository
import org.springframework.stereotype.Component

/** 더미가 낀 매칭과 그 뒤에 이어지는 평가·재매칭. */
@Component
class DummyMatchDataCleaner(
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
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

    fun deletePersonalMatchesWith(dummyIds: Collection<Long>): Set<Long> {
        val matchIds = personalMatchRepository.findByMemberId1InOrMemberId2In(dummyIds, dummyIds).map { it.id }.toSet()
        personalMatchRepository.deleteAllByIdInBatch(matchIds)
        return matchIds
    }

    /** 구성원 전원의 초대까지 지운다. */
    fun deleteGroupMatches(groupMatchIds: Collection<Long>) {
        if (groupMatchIds.isEmpty()) return

        val invitationIds = groupMatchMemberRepository.findByRoomIdIn(groupMatchIds.toList()).map { it.id }
        groupMatchMemberRepository.deleteAllByIdInBatch(invitationIds)
        groupMatchRepository.deleteAllByIdInBatch(groupMatchIds)
    }

    fun deleteRematches(rematchIds: Collection<Long>) {
        rematchRepository.deleteAllByIdInBatch(rematchIds)
    }

    /** 지운 방에서 열린 평가와, 더미가 쓰거나 받은 평가. */
    fun deleteReviewsWith(dummyIds: Collection<Long>, deletedRoomIds: Collection<Long>) {
        val reviewIds = memberReviewRepository.findByChatRoomIdInOrAuthorMemberIdIn(deletedRoomIds, dummyIds)
            .map { it.id }
        val answerIds = reviewAnswerRepository.findByMemberReviewIdInOrReviewedMemberIdIn(reviewIds, dummyIds)
            .map { it.id }
        reviewAnswerRepository.deleteAllByIdInBatch(answerIds)
        memberReviewRepository.deleteAllByIdInBatch(reviewIds)
    }
}
