package com.ditto.domain.match.repository.querydsl

import com.ditto.domain.match.entity.GroupMatch
import java.time.LocalDate

interface GroupMatchRepositoryCustom {

    /**
     * 수락 마감이 지났는데도 성사되지 못한 그룹들.
     *
     * @param lastWeekStartedOn 포함할 운영 주의 상한(월요일). 마감은 그 주 금요일 00:00 이므로
     *   부르는 쪽이 `기준일 - 4일`을 넘긴다.
     */
    fun findUnformedUntil(lastWeekStartedOn: LocalDate): List<GroupMatch>
}
