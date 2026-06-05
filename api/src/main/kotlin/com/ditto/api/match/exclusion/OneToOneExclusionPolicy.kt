package com.ditto.api.match.exclusion

import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.entity.MatchingType
import org.springframework.stereotype.Component

/**
 * 1:1 매칭 제외 정책.
 * - 이미 성사(ACCEPTED)된 회원
 * - ACTIVE 가 아닌 회원
 *
 * (차단/신고/정지 등은 해당 기능이 생기면 여기에 함께 추가한다.)
 */
@Component
class OneToOneExclusionPolicy(
    private val personalMatchRepository: PersonalMatchRepository,
    private val memberRepository: MemberRepository,
) : MatchExclusionPolicy {

    override val matchingType: MatchingType = MatchingType.ONE_TO_ONE

    override fun excludedMemberIds(quizSetId: Long, memberIds: Set<Long>): Set<Long> {
        val alreadyMatched = personalMatchRepository
            .findByQuizSetIdAndStatus(quizSetId, PersonalMatchStatus.ACCEPTED)
            .flatMap { listOf(it.memberId1, it.memberId2) }
            .toSet()
        val inactive = memberRepository.findAllById(memberIds)
            .filter { !it.isActive() }
            .map { it.id }
            .toSet()

        return alreadyMatched + inactive
    }
}
