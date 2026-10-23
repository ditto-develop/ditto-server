package com.ditto.domain.member.repository.querydsl

import com.ditto.domain.member.entity.MemberBlock
import com.ditto.domain.quiz.entity.MatchingType
import java.time.LocalDate

/**
 * 차단은 (차단한 사람 → 차단된 사람) 한 방향으로 저장되지만, 효력 판정은 **방향과 무관**하다.
 * 그 "양방향" 조건이 메서드명 파생 쿼리로는 표현이 비대해져 여기로 뺐다.
 */
interface MemberBlockRepositoryCustom {

    /** 두 회원 사이에 방향 무관하게 차단이 있는지 — 프로필 조회 차단·매칭 제외의 공통 판정. */
    fun existsBetween(oneId: Long, otherId: Long): Boolean

    /**
     * 주어진 회원들이 관여한 차단 전체 — 매칭 후보 풀에 한 번에 싣기 위한 벌크 조회.
     * 회원 수만큼 쿼리를 날리지 않도록 매칭 배치는 이 메서드를 쓴다.
     */
    fun findAllInvolving(memberIds: Collection<Long>): List<MemberBlock>

    /**
     * 그 주에 생긴 차단 수. 차단을 풀면 행이 지워져 지금 유지 중인 차단만 센다. 회원 조건은 차단된 사람에게 건다.
     * matchingType 을 주면 차단된 사람이 그 주에 그 유형 퀴즈셋을 완주한 차단만 센다.
     */
    fun countBlocksCreatedInWeek(weekStartedOn: LocalDate, matchingType: MatchingType?, filter: MemberFilter): Long
}
