package com.ditto.api.admin.dummy.cleanup

import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.rematch.repository.RematchRepository
import org.springframework.stereotype.Component

/** 더미가 낀 1:1 신청·그룹·재매칭을 찾는다. 지우는 일은 MatchingRecordEraser 가 한다. */
@Component
class DummyMatchingTargetFinder(
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val rematchRepository: RematchRepository,
) {
    /** 응답 상태와 상관없이 더미가 초대받은 그룹. 거절한 더미가 있던 그룹도 QA 데이터로 본다. */
    fun findGroupMatchIdsWith(dummyIds: Collection<Long>): Set<Long> =
        groupMatchMemberRepository.findByMemberIdIn(dummyIds).map { it.roomId }.toSet()

    /** 더미가 낀 쌍과, 지울 그룹에서 나온 쌍. */
    fun findRematchIdsWith(dummyIds: Collection<Long>, groupMatchIds: Collection<Long>): Set<Long> =
        rematchRepository.findByMemberId1InOrMemberId2InOrSourceGroupMatchIdIn(dummyIds, dummyIds, groupMatchIds)
            .map { it.id }
            .toSet()

    fun findPersonalMatchIdsWith(dummyIds: Collection<Long>): Set<Long> =
        personalMatchRepository.findByMemberId1InOrMemberId2In(dummyIds, dummyIds).map { it.id }.toSet()
}
