package com.ditto.api.match

import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.repository.QuizSetRepository
import org.springframework.stereotype.Component

/**
 * 두 회원 간 "매칭 관계" 여부를 판단한다.
 * - [isMatched]: 개인 매칭 성사(ACCEPTED) 또는 같은 그룹 채팅방 참여 — 성사 **후** 관계
 * - [isMatchCandidate]: 이번 주 후보로 서로에게 노출된 관계 — 성사 **전** 관계
 *
 * 타인 소개노트·프로필 조회 권한 등에서 공통으로 사용한다.
 */
@Component
class MatchAccessChecker(
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val quizSetRepository: QuizSetRepository,
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

    /**
     * 상대가 **이번 주 매칭 후보로 서로에게 노출된 상대**인지 판단한다.
     *
     * 기준 퀴즈셋은 후보 목록(`GET /api/v1/matches/1on1`)과 같다 — "조회자가 최근 완료한 1:1 퀴즈셋".
     * 후보 행은 지난 주 것도 남으므로 퀴즈셋으로 좁히지 않으면 열람 권한이 영구히 열린다.
     * 다음 주 퀴즈셋을 완료하는 순간 지난 주 후보는 자연히 닫힌다.
     *
     * 그룹 매칭은 후보 테이블을 쓰지 않는다(그룹용 `MatchingProcessor` 부재) — 그룹 구성원은
     * 같은 방 참여로 [isMatched] 가 이미 통과시킨다.
     */
    fun isMatchCandidate(memberId: Long, otherMemberId: Long): Boolean {
        val quizSet = quizSetRepository.findLatestCompletedQuizSet(memberId, MatchingType.ONE_TO_ONE)
            ?: return false
        return matchCandidateRepository.existsPairByQuizSetId(memberId, otherMemberId, quizSet.id)
    }
}
