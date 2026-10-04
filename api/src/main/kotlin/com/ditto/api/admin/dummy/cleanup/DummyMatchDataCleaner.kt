package com.ditto.api.admin.dummy.cleanup

import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.rematch.repository.RematchRepository
import com.ditto.domain.review.repository.MemberReviewRepository
import com.ditto.domain.review.repository.ReviewAnswerRepository
import org.springframework.stereotype.Component

/** 더미가 낀 매칭과 그 뒤에 이어지는 평가·재매칭. 지운 id 는 그것을 가리키는 알림을 지우는 데 쓴다. */
@Component
class DummyMatchDataCleaner(
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val rematchRepository: RematchRepository,
    private val memberReviewRepository: MemberReviewRepository,
    private val reviewAnswerRepository: ReviewAnswerRepository,
) {
    fun deletePersonalMatchesWith(dummyIds: Collection<Long>): Set<Long> {
        val matches = personalMatchRepository.findByMemberId1InOrMemberId2In(dummyIds, dummyIds)
        personalMatchRepository.deleteAllInBatch(matches)
        return matches.map { it.id }.toSet()
    }

    /** 더미가 한 명이라도 있는 그룹은 구성원 전원의 초대까지 통째로 지운다. */
    fun deleteGroupMatchesWith(dummyIds: Collection<Long>): Set<Long> {
        val groupMatchIds = groupMatchMemberRepository.findByMemberIdIn(dummyIds).map { it.roomId }.toSet()
        if (groupMatchIds.isEmpty()) return emptySet()

        groupMatchMemberRepository.deleteAllInBatch(groupMatchMemberRepository.findByRoomIdIn(groupMatchIds.toList()))
        groupMatchRepository.deleteAllByIdInBatch(groupMatchIds)
        return groupMatchIds
    }

    /** 더미가 낀 쌍과, 지운 그룹 방에서 나온 쌍. */
    fun deleteRematchesWith(dummyIds: Collection<Long>, deletedRoomIds: Collection<Long>): Set<Long> {
        val rematches = rematchRepository.findByMemberId1InOrMemberId2InOrSourceChatRoomIdIn(
            dummyIds,
            dummyIds,
            deletedRoomIds,
        )
        rematchRepository.deleteAllInBatch(rematches)
        return rematches.map { it.id }.toSet()
    }

    /** 지운 방에서 열린 평가와, 더미가 쓰거나 받은 평가. */
    fun deleteReviewsWith(dummyIds: Collection<Long>, deletedRoomIds: Collection<Long>) {
        val reviews = memberReviewRepository.findByChatRoomIdInOrAuthorMemberIdIn(deletedRoomIds, dummyIds)
        val answers = reviewAnswerRepository.findByMemberReviewIdInOrReviewedMemberIdIn(reviews.map { it.id }, dummyIds)
        reviewAnswerRepository.deleteAllInBatch(answers)
        memberReviewRepository.deleteAllInBatch(reviews)
    }
}
