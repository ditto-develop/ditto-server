package com.ditto.api.match

import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import org.springframework.stereotype.Component

/**
 * 두 회원 간 "매칭 관계" 여부를 판단한다.
 * - 개인 매칭 성사(ACCEPTED) 또는
 * - 같은 그룹 채팅방 참여
 *
 * 타인 소개노트·프로필 조회 권한 등에서 공통으로 사용한다.
 */
@Component
class MatchAccessChecker(
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
) {
    fun isMatched(memberId: Long, otherMemberId: Long): Boolean {
        val matched = personalMatchRepository.existsByMemberId1AndMemberId2AndStatus(
            minOf(memberId, otherMemberId),
            maxOf(memberId, otherMemberId),
            PersonalMatchStatus.ACCEPTED,
        )
        if (matched) return true
        return groupMatchMemberRepository.existsSharedRoom(memberId, otherMemberId)
    }
}
